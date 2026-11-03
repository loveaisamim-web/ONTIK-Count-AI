package com.ontik.countai.auth

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.ontik.countai.billing.PlanManager
import com.ontik.countai.data.repository.CountRepository
import com.ontik.countai.domain.model.AppLanguage
import com.ontik.countai.domain.model.PlanTier
import com.ontik.countai.domain.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthResult {
    data class Success(val profile: UserProfile) : AuthResult()
    data class Error(val messageEn: String, val messageBn: String) : AuthResult()
}

/**
 * Handles Firebase Authentication (Email/Password, Google Sign-In via Credential Manager ID token,
 * Guest Mode, Password Reset, Email Verification, Re-authentication, and Mandatory Play Store Account Deletion)
 * plus Firestore `users/{uid}` document synchronization.
 *
 * Offline-safe: If `google-services.json` is not configured yet or there is no internet connection,
 * all methods return friendly localized states and never crash the app.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val context: Context,
    private val countRepository: CountRepository,
    private val planManager: PlanManager
) {
    private val firebaseAuth: FirebaseAuth? by lazy {
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) FirebaseAuth.getInstance() else null
        } catch (_: Throwable) {
            null
        }
    }

    private val firestore: FirebaseFirestore? by lazy {
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) FirebaseFirestore.getInstance() else null
        } catch (_: Throwable) {
            null
        }
    }

    private val _currentUser = MutableStateFlow(loadCurrentSessionUser())
    val currentUser: StateFlow<UserProfile> = _currentUser.asStateFlow()

    val deviceHardwareId: String by lazy {
        try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?: Build.MODEL
        } catch (_: Throwable) {
            "android_device"
        }
    }

    private fun loadCurrentSessionUser(): UserProfile {
        val fbUser = try { firebaseAuth?.currentUser } catch (_: Throwable) { null }
        if (fbUser != null) {
            val isGoogle = fbUser.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }
            return UserProfile(
                uid = fbUser.uid,
                name = fbUser.displayName ?: fbUser.email?.substringBefore("@") ?: "User",
                email = fbUser.email ?: "",
                photoUrl = fbUser.photoUrl?.toString(),
                isGuest = false,
                isEmailVerified = fbUser.isEmailVerified || isGoogle,
                isGoogleAccount = isGoogle,
                planTier = planManager.planState.value.tier,
                proExpiresAtMs = planManager.planState.value.proExpiresAtMs
            )
        }
        return UserProfile(isGuest = true)
    }

    suspend fun refreshSessionAndSyncFirestore(): UserProfile = withContext(Dispatchers.IO) {
        try {
            val fbUser = firebaseAuth?.currentUser
            if (fbUser == null) {
                val guest = UserProfile(isGuest = true)
                _currentUser.value = guest
                return@withContext guest
            }
            fbUser.reload().await()
            val isGoogle = fbUser.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }
            val verified = fbUser.isEmailVerified || isGoogle

            val db = firestore
            if (db != null) {
                val snap = db.collection("users").document(fbUser.uid).get().await()
                if (snap.exists()) {
                    val planStr = snap.getString("plan") ?: "free"
                    val tier = if (planStr == "pro") PlanTier.PRO else PlanTier.FREE
                    val expiresTs = snap.getTimestamp("proExpiresAt")
                    val expiresMs = expiresTs?.toDate()?.time
                    val proSource = snap.getString("proSource")

                    // Update server-verified plan in PlanManager
                    planManager.updateVerifiedPlanFromServer(tier, expiresMs)

                    // Reconcile anti-tamper daily count
                    val dailyMap = snap.get("dailyCount") as? Map<*, *>
                    val remoteDate = dailyMap?.get("date") as? String ?: ""
                    val remoteCount = (dailyMap?.get("count") as? Number)?.toInt() ?: 0
                    planManager.reconcileRemoteDailyCount(remoteDate, remoteCount)

                    val updatedProfile = UserProfile(
                        uid = fbUser.uid,
                        name = snap.getString("name") ?: fbUser.displayName ?: "User",
                        email = fbUser.email ?: "",
                        photoUrl = fbUser.photoUrl?.toString(),
                        isGuest = false,
                        isEmailVerified = verified,
                        isGoogleAccount = isGoogle,
                        planTier = tier,
                        proExpiresAtMs = expiresMs,
                        proSource = proSource
                    )
                    _currentUser.value = updatedProfile
                    return@withContext updatedProfile
                }
            }

            val fallback = loadCurrentSessionUser()
            _currentUser.value = fallback
            fallback
        } catch (_: Throwable) {
            // Offline or network timeout: keep cached session silently without crashing
            val cached = loadCurrentSessionUser()
            _currentUser.value = cached
            cached
        }
    }

    suspend fun signInWithEmail(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        val auth = firebaseAuth ?: return@withContext AuthResult.Error(
            "Firebase is not configured yet (add google-services.json) or use Continue as Guest.",
            "Firebase কনফিগার করা হয়নি (google-services.json যোগ করুন) অথবা গেস্ট মোডে চালান।"
        )
        try {
            val res = auth.signInWithEmailAndPassword(email.trim(), password).await()
            val user = res.user ?: return@withContext AuthResult.Error("Sign in failed", "লগইন ব্যর্থ হয়েছে")
            upsertFirestoreUserDocument(
                uid = user.uid,
                name = user.displayName ?: email.substringBefore("@"),
                email = user.email ?: email,
                photoUrl = user.photoUrl?.toString(),
                isEmailVerified = user.isEmailVerified
            )
            val profile = refreshSessionAndSyncFirestore()
            AuthResult.Success(profile)
        } catch (e: Throwable) {
            mapFirebaseAuthException(e)
        }
    }

    suspend fun signUpWithEmail(
        name: String,
        email: String,
        password: String
    ): AuthResult = withContext(Dispatchers.IO) {
        val auth = firebaseAuth ?: return@withContext AuthResult.Error(
            "Firebase is not configured yet (add google-services.json) or use Continue as Guest.",
            "Firebase কনফিগার করা হয়নি (google-services.json যোগ করুন) অথবা গেস্ট মোডে চালান।"
        )
        try {
            val res = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val user = res.user ?: return@withContext AuthResult.Error("Account creation failed", "অ্যাকাউন্ট তৈরি ব্যর্থ হয়েছে")

            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(name.trim())
                .build()
            user.updateProfile(profileUpdates).await()

            // Send verification email (Mandatory before Pro purchase for email accounts)
            try {
                user.sendEmailVerification().await()
            } catch (_: Throwable) {
            }

            upsertFirestoreUserDocument(
                uid = user.uid,
                name = name.trim(),
                email = email.trim(),
                photoUrl = null,
                isEmailVerified = false
            )

            // Guest-to-Account Migration: local Room sessions are already marked PENDING_UPLOAD
            // and will automatically sync to users/{uid}/sessions when SyncWorker runs.
            val profile = refreshSessionAndSyncFirestore()
            AuthResult.Success(profile)
        } catch (e: Throwable) {
            mapFirebaseAuthException(e)
        }
    }

    suspend fun signInWithGoogleIdToken(idToken: String): AuthResult = withContext(Dispatchers.IO) {
        val auth = firebaseAuth ?: return@withContext AuthResult.Error(
            "Firebase is not configured yet.",
            "Firebase এখনো কনফিগার করা হয়নি।"
        )
        try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val res = auth.signInWithCredential(credential).await()
            val user = res.user ?: return@withContext AuthResult.Error("Google sign-in failed", "গুগল লগইন ব্যর্থ হয়েছে")

            upsertFirestoreUserDocument(
                uid = user.uid,
                name = user.displayName ?: "ONTIK User",
                email = user.email ?: "",
                photoUrl = user.photoUrl?.toString(),
                isEmailVerified = true
            )
            val profile = refreshSessionAndSyncFirestore()
            AuthResult.Success(profile)
        } catch (e: Throwable) {
            mapFirebaseAuthException(e)
        }
    }

    suspend fun sendPasswordReset(email: String): AuthResult = withContext(Dispatchers.IO) {
        val auth = firebaseAuth ?: return@withContext AuthResult.Error(
            "Firebase not configured.",
            "Firebase কনফিগার করা নেই।"
        )
        try {
            auth.sendPasswordResetEmail(email.trim()).await()
            AuthResult.Success(_currentUser.value)
        } catch (e: Throwable) {
            mapFirebaseAuthException(e)
        }
    }

    suspend fun updateDisplayName(newName: String): Boolean = withContext(Dispatchers.IO) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed.length > 100) return@withContext false
        try {
            val user = firebaseAuth?.currentUser
            if (user != null) {
                val req = UserProfileChangeRequest.Builder().setDisplayName(trimmed).build()
                user.updateProfile(req).await()
                firestore?.collection("users")?.document(user.uid)?.set(
                    mapOf("name" to trimmed),
                    SetOptions.merge()
                )?.await()
            }
            _currentUser.value = _currentUser.value.copy(name = trimmed)
            true
        } catch (_: Throwable) {
            _currentUser.value = _currentUser.value.copy(name = trimmed)
            false
        }
    }

    suspend fun syncDailyCountToFirestore(dateIso: String, count: Int) = withContext(Dispatchers.IO) {
        try {
            val uid = firebaseAuth?.currentUser?.uid ?: return@withContext
            firestore?.collection("users")?.document(uid)?.set(
                mapOf(
                    "dailyCount" to mapOf(
                        "date" to dateIso,
                        "count" to count
                    )
                ),
                SetOptions.merge()
            )?.await()
        } catch (_: Throwable) {
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            firebaseAuth?.signOut()
        } catch (_: Throwable) {
        }
        planManager.updateVerifiedPlanFromServer(PlanTier.FREE, null)
        _currentUser.value = UserProfile(isGuest = true)
    }

    /**
     * Mandatory Google Play Delete Account flow:
     * 1. Re-authenticates if password is provided.
     * 2. Deletes `users/{uid}/sessions`, `users/{uid}/presets`, and `users/{uid}` in Firestore.
     * 3. Deletes Firebase Auth user record.
     * 4. Clears all local Room history & resets plan state.
     */
    suspend fun deleteAccountAndAllData(
        currentPasswordIfEmailUser: String?,
        googleIdTokenIfGoogleUser: String? = null
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            val user = firebaseAuth?.currentUser
            if (user != null) {
                if (!currentPasswordIfEmailUser.isNullOrBlank() && !user.email.isNullOrBlank()) {
                    val cred = EmailAuthProvider.getCredential(user.email!!, currentPasswordIfEmailUser)
                    user.reauthenticate(cred).await()
                } else if (!googleIdTokenIfGoogleUser.isNullOrBlank()) {
                    val cred = GoogleAuthProvider.getCredential(googleIdTokenIfGoogleUser, null)
                    user.reauthenticate(cred).await()
                }

                val uid = user.uid
                val db = firestore
                if (db != null) {
                    val sessionsSnap = db.collection("users").document(uid).collection("sessions").get().await()
                    for (doc in sessionsSnap.documents) {
                        doc.reference.delete().await()
                    }
                    val presetsSnap = db.collection("users").document(uid).collection("presets").get().await()
                    for (doc in presetsSnap.documents) {
                        doc.reference.delete().await()
                    }
                    db.collection("users").document(uid).delete().await()
                }
                user.delete().await()
            }

            countRepository.clearAllLocalData()
            planManager.updateVerifiedPlanFromServer(PlanTier.FREE, null)
            val guest = UserProfile(isGuest = true)
            _currentUser.value = guest
            AuthResult.Success(guest)
        } catch (e: Throwable) {
            mapFirebaseAuthException(e)
        }
    }

    private suspend fun upsertFirestoreUserDocument(
        uid: String,
        name: String,
        email: String,
        photoUrl: String?,
        isEmailVerified: Boolean
    ) {
        try {
            val db = firestore ?: return
            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()

            val baseData = mutableMapOf<String, Any?>(
                "name" to name.take(100),
                "email" to email.take(180),
                "photoUrl" to photoUrl,
                "lastLoginAt" to FieldValue.serverTimestamp(),
                "isEmailVerified" to isEmailVerified,
                "language" to AppLanguage.BANGLA.code
            )

            if (!existing.exists()) {
                baseData["createdAt"] = FieldValue.serverTimestamp()
                baseData["plan"] = "free"
                baseData["deviceIds"] = listOf(deviceHardwareId)
                baseData["dailyCount"] = mapOf(
                    "date" to PlanManager.todayIsoString(),
                    "count" to planManager.planState.value.todayUsedCount
                )
            } else {
                val currentDevices = (existing.get("deviceIds") as? List<*>)
                    ?.mapNotNull { it as? String }
                    ?.toMutableList() ?: mutableListOf()
                if (deviceHardwareId !in currentDevices) {
                    currentDevices.add(deviceHardwareId)
                    while (currentDevices.size > 3) {
                        currentDevices.removeAt(0)
                    }
                }
                baseData["deviceIds"] = currentDevices
            }

            docRef.set(baseData, SetOptions.merge()).await()
        } catch (_: Throwable) {
        }
    }

    private fun mapFirebaseAuthException(e: Throwable): AuthResult.Error {
        val code = (e as? FirebaseAuthException)?.errorCode ?: ""
        return when (code) {
            "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> AuthResult.Error(
                "Incorrect email or password. Please try again.",
                "ইমেইল বা পাসওয়ার্ড ভুল হয়েছে। আবার চেষ্টা করুন।"
            )
            "ERROR_USER_NOT_FOUND" -> AuthResult.Error(
                "No account found with this email.",
                "এই ইমেইলে কোনো অ্যাকাউন্ট পাওয়া যায়নি।"
            )
            "ERROR_EMAIL_ALREADY_IN_USE" -> AuthResult.Error(
                "This email is already registered. Please sign in instead.",
                "এই ইমেইল দিয়ে আগেই অ্যাকাউন্ট খোলা হয়েছে। অনুগ্রহ করে লগইন করুন।"
            )
            "ERROR_TOO_MANY_REQUESTS" -> AuthResult.Error(
                "Too many attempts. Please wait a moment and try again.",
                "অতিরিক্ত প্রচেষ্টার কারণে সাময়িকভাবে ব্লক করা হয়েছে। কিছুক্ষণ পর চেষ্টা করুন।"
            )
            else -> AuthResult.Error(
                e.localizedMessage ?: "Network error. Please check your internet connection.",
                "নেটওয়ার্ক ত্রুটি। আপনার ইন্টারনেট সংযোগ পরীক্ষা করুন।"
            )
        }
    }
}
