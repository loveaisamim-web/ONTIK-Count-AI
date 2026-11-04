package com.ontik.countai.auth

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
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
 * Handles real Firebase Authentication (Email/Password, Google Sign-In via Credential Manager ID token,
 * Guest Mode, Password Reset, Email Verification, Re-authentication, and Account Deletion)
 * plus Firestore `users/{uid}` document synchronization (P10 fix).
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
                planTier = PlanTier.FREE,
                proExpiresAtMs = null
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
                        planTier = PlanTier.FREE,
                        proExpiresAtMs = null
                    )
                    _currentUser.value = updatedProfile
                    return@withContext updatedProfile
                }
            }

            val fallback = loadCurrentSessionUser()
            _currentUser.value = fallback
            fallback
        } catch (_: Throwable) {
            val cached = loadCurrentSessionUser()
            _currentUser.value = cached
            cached
        }
    }

    suspend fun signInWithEmail(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        val auth = firebaseAuth ?: return@withContext AuthResult.Error(
            "Firebase is not initialized.",
            "Firebase চালু হয়নি।"
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
            "Firebase is not initialized.",
            "Firebase চালু হয়নি।"
        )
        try {
            val res = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val user = res.user ?: return@withContext AuthResult.Error("Account creation failed", "অ্যাকাউন্ট তৈরি ব্যর্থ হয়েছে")

            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(name.trim())
                .build()
            try { user.updateProfile(profileUpdates).await() } catch (_: Throwable) {}
            try { user.sendEmailVerification().await() } catch (_: Throwable) {}

            upsertFirestoreUserDocument(
                uid = user.uid,
                name = name.trim(),
                email = email.trim(),
                photoUrl = null,
                isEmailVerified = false
            )

            val profile = refreshSessionAndSyncFirestore()
            AuthResult.Success(profile)
        } catch (e: Throwable) {
            mapFirebaseAuthException(e)
        }
    }

    suspend fun signInWithGoogleIdToken(idToken: String): AuthResult = withContext(Dispatchers.IO) {
        val auth = firebaseAuth ?: return@withContext AuthResult.Error(
            "Firebase is not initialized.",
            "Firebase চালু হয়নি।"
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
            "Firebase is not initialized.",
            "Firebase চালু হয়নি।"
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
        _currentUser.value = UserProfile(isGuest = true)
    }

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
                    try {
                        val sessionsSnap = db.collection("users").document(uid).collection("sessions").get().await()
                        for (doc in sessionsSnap.documents) {
                            doc.reference.delete().await()
                        }
                        val presetsSnap = db.collection("users").document(uid).collection("presets").get().await()
                        for (doc in presetsSnap.documents) {
                            doc.reference.delete().await()
                        }
                        db.collection("users").document(uid).delete().await()
                    } catch (_: Throwable) {}
                }
                user.delete().await()
            }

            countRepository.clearAllLocalData()
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
        val msg = e.localizedMessage ?: ""
        return when {
            code == "ERROR_WRONG_PASSWORD" || code == "ERROR_INVALID_CREDENTIAL" -> AuthResult.Error(
                "Incorrect email or password. Please try again.",
                "ইমেইল বা পাসওয়ার্ড ভুল হয়েছে। আবার চেষ্টা করুন।"
            )
            code == "ERROR_USER_NOT_FOUND" -> AuthResult.Error(
                "No account found with this email.",
                "এই ইমেইলে কোনো অ্যাকাউন্ট পাওয়া যায়নি।"
            )
            code == "ERROR_EMAIL_ALREADY_IN_USE" -> AuthResult.Error(
                "This email is already registered. Please sign in instead.",
                "এই ইমেইল দিয়ে আগেই অ্যাকাউন্ট খোলা হয়েছে। অনুগ্রহ করে লগইন করুন।"
            )
            code == "ERROR_OPERATION_NOT_ALLOWED" || msg.contains("OPERATION_NOT_ALLOWED", ignoreCase = true) || msg.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) -> AuthResult.Error(
                "Enable Email/Password in Firebase Console -> Authentication -> Sign-in method.",
                "Firebase Console -> Authentication -> Sign-in method থেকে Email/Password চালু করুন।"
            )
            code == "ERROR_TOO_MANY_REQUESTS" -> AuthResult.Error(
                "Too many attempts. Please wait a moment and try again.",
                "অতিরিক্ত প্রচেষ্টার কারণে সাময়িকভাবে ব্লক করা হয়েছে। কিছুক্ষণ পর চেষ্টা করুন।"
            )
            e is FirebaseNetworkException -> AuthResult.Error(
                "Network error. Please check your Wi-Fi or Mobile Data connection.",
                "নেটওয়ার্ক ত্রুটি। আপনার Wi-Fi বা Mobile Data সংযোগ পরীক্ষা করুন।"
            )
            else -> AuthResult.Error(
                msg.ifBlank { "Authentication failed. Please try again." },
                "অ্যাকাউন্ট ভেরিফিকেশন ত্রুটি: ${msg.ifBlank { "আবার চেষ্টা করুন" }}"
            )
        }
    }
}
