package com.ontik.countai.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.ontik.countai.R
import com.ontik.countai.domain.model.AppLanguage
import com.ontik.countai.ui.theme.DangerRed
import com.ontik.countai.ui.theme.OntikAmberAccent
import com.ontik.countai.ui.theme.OntikGreenPrimary
import com.ontik.countai.ui.viewmodel.CountViewModel
import kotlinx.coroutines.launch

/**
 * Reads the Google Web Client ID from the generated R.string.default_web_client_id resource
 * (automatically generated from app/google-services.json by com.google.gms.google-services).
 */
fun resolveDefaultWebClientId(context: Context): String {
    return try {
        context.getString(R.string.default_web_client_id).trim()
    } catch (_: Throwable) {
        ""
    }
}

@Composable
fun AuthLoginScreen(
    viewModel: CountViewModel,
    onNavigateToSignUp: () -> Unit,
    onContinueAsGuestOrSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsState()
    val isLoading by viewModel.authLoading.collectAsState()
    val errorBanner by viewModel.authErrorBanner.collectAsState()
    val isBn = settings.language == AppLanguage.BANGLA

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "ONTIK Count AI",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = OntikGreenPrimary
        )
        Text(
            text = if (isBn) "ক্লাউড ব্যাকআপ ও প্রো সিঙ্কের জন্য লগইন করুন" else "Sign in for Cloud Backup & Pro Multi-Device Sync",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        errorBanner?.let { (en, bn) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = DangerRed.copy(alpha = 0.14f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isBn) bn else en,
                    color = DangerRed,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                emailError = null
                viewModel.clearAuthError()
            },
            label = { Text(if (isBn) "ইমেইল (Email)" else "Email Address") },
            isError = emailError != null,
            supportingText = { emailError?.let { Text(it, color = DangerRed) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                passwordError = null
                viewModel.clearAuthError()
            },
            label = { Text(if (isBn) "পাসওয়ার্ড (Password)" else "Password") },
            visualTransformation = PasswordVisualTransformation(),
            isError = passwordError != null,
            supportingText = { passwordError?.let { Text(it, color = DangerRed) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = {
                    if (!email.contains("@")) {
                        emailError = if (isBn) "সঠিক ইমেইল লিখুন" else "Enter a valid email first"
                    } else {
                        viewModel.sendPasswordResetEmail(email) { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            ) {
                Text(if (isBn) "পাসওয়ার্ড ভুলে গেছেন?" else "Forgot password?")
            }
        }

        Button(
            onClick = {
                if (!email.contains("@")) {
                    emailError = if (isBn) "সঠিক ইমেইল দিন" else "Valid email required"
                    return@Button
                }
                if (password.length < 6) {
                    passwordError = if (isBn) "পাসওয়ার্ড অন্তত ৬ অক্ষরের হতে হবে" else "Password too short"
                    return@Button
                }
                viewModel.signInWithEmail(email, password, onContinueAsGuestOrSuccess)
            },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = OntikGreenPrimary)
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White)
            } else {
                Text(if (isBn) "লগইন করুন (Sign In)" else "Sign In", fontWeight = FontWeight.Bold)
            }
        }

        // Continue with Google via Credential Manager API using R.string.default_web_client_id
        OutlinedButton(
            onClick = {
                val webClientId = resolveDefaultWebClientId(context)
                if (webClientId.isBlank()) {
                    Toast.makeText(
                        context,
                        if (isBn) "Firebase Console-এ Google Sign-In চালু করে নতুন google-services.json নিন অথবা গেস্ট মোডে চালান" else "Enable Google Sign-In in Firebase Console to generate default_web_client_id, or continue as Guest",
                        Toast.LENGTH_LONG
                    ).show()
                    return@OutlinedButton
                }
                scope.launch {
                    try {
                        val cm = CredentialManager.create(context)
                        val googleIdOption = GetGoogleIdOption.Builder()
                            .setFilterByAuthorizedAccounts(false)
                            .setServerClientId(webClientId)
                            .build()
                        val request = GetCredentialRequest.Builder()
                            .addCredentialOption(googleIdOption)
                            .build()
                        val result = cm.getCredential(context, request)
                        val googleToken = GoogleIdTokenCredential.createFrom(result.credential.data).idToken
                        viewModel.signInWithGoogleToken(googleToken, onContinueAsGuestOrSuccess)
                    } catch (e: Throwable) {
                        val msg = if (isBn) {
                            "Google সাইন-ইন বাতিল হয়েছে বা ইন্টারনেট সংযোগ নেই"
                        } else {
                            e.localizedMessage ?: "Google Sign-In cancelled"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Text(
                text = if (isBn) "Google দিয়ে চালিয়ে যান (Continue with Google)" else "Continue with Google",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold
            )
        }

        TextButton(onClick = onNavigateToSignUp) {
            Text(
                text = if (isBn) "নতুন অ্যাকাউন্ট খুলুন (Create Account)" else "Don't have an account? Create account",
                color = OntikGreenPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        TextButton(onClick = onContinueAsGuestOrSuccess) {
            Text(
                text = if (isBn) "গেস্ট মোডে অফলাইনে চালান (Continue as Guest)" else "Continue as Guest (100% Offline)",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AuthSignUpScreen(
    viewModel: CountViewModel,
    onBackToLogin: () -> Unit,
    onSignUpSuccess: () -> Unit,
    onOpenTerms: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {}
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val isLoading by viewModel.authLoading.collectAsState()
    val errorBanner by viewModel.authErrorBanner.collectAsState()
    val isBn = settings.language == AppLanguage.BANGLA

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var acceptedTerms by remember { mutableStateOf(false) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmError by remember { mutableStateOf<String?>(null) }

    val strengthScore = remember(password) {
        var score = 0
        if (password.length >= 8) score++
        if (password.any { it.isUpperCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        score
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isBn) "নতুন অ্যাকাউন্ট তৈরি করুন" else "Create Your Account",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = OntikGreenPrimary
        )

        errorBanner?.let { (en, bn) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = DangerRed.copy(alpha = 0.14f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isBn) bn else en,
                    color = DangerRed,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                nameError = null
            },
            label = { Text(if (isBn) "আপনার নাম (Full Name)" else "Full Name") },
            isError = nameError != null,
            supportingText = { nameError?.let { Text(it, color = DangerRed) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                emailError = null
            },
            label = { Text(if (isBn) "ইমেইল (Email)" else "Email Address") },
            isError = emailError != null,
            supportingText = { emailError?.let { Text(it, color = DangerRed) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                passwordError = null
            },
            label = { Text(if (isBn) "পাসওয়ার্ড (কমপক্ষে ৮ অক্ষর)" else "Password (min 8 chars)") },
            visualTransformation = PasswordVisualTransformation(),
            isError = passwordError != null,
            supportingText = { passwordError?.let { Text(it, color = DangerRed) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        // Password strength indicator
        if (password.isNotEmpty()) {
            val strengthProgress = (strengthScore / 4f).coerceIn(0.15f, 1f)
            val strengthColor = when {
                strengthScore <= 1 -> DangerRed
                strengthScore == 2 -> OntikAmberAccent
                else -> OntikGreenPrimary
            }
            val strengthLabel = when {
                strengthScore <= 1 -> if (isBn) "দুর্বল (Weak)" else "Weak"
                strengthScore == 2 -> if (isBn) "মাঝারি (Medium)" else "Medium"
                else -> if (isBn) "শক্তিশালী (Strong)" else "Strong"
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = { strengthProgress },
                    color = strengthColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                )
                Text(
                    text = strengthLabel,
                    fontSize = 11.sp,
                    color = strengthColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = {
                confirmPassword = it
                confirmError = null
            },
            label = { Text(if (isBn) "পাসওয়ার্ড নিশ্চিত করুন (Confirm Password)" else "Confirm Password") },
            visualTransformation = PasswordVisualTransformation(),
            isError = confirmError != null,
            supportingText = { confirmError?.let { Text(it, color = DangerRed) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { acceptedTerms = !acceptedTerms },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = acceptedTerms,
                onCheckedChange = { acceptedTerms = it }
            )
            Text(
                text = if (isBn) "আমি শর্তাবলী ও গোপনীয়তা নীতি মেনে নিচ্ছি (Accept Terms & Privacy Policy)" else "I accept the Terms of Service & Privacy Policy",
                fontSize = 13.sp
            )
        }

        Button(
            onClick = {
                if (name.trim().length < 2) {
                    nameError = if (isBn) "আপনার নাম লিখুন" else "Enter your name"
                    return@Button
                }
                if (!email.contains("@")) {
                    emailError = if (isBn) "সঠিক ইমেইল দিন" else "Enter a valid email"
                    return@Button
                }
                if (password.length < 8) {
                    passwordError = if (isBn) "পাসওয়ার্ড কমপক্ষে ৮ অক্ষরের হতে হবে" else "Password must be at least 8 characters"
                    return@Button
                }
                if (password != confirmPassword) {
                    confirmError = if (isBn) "দুইটি পাসওয়ার্ড মেলেনি" else "Passwords do not match"
                    return@Button
                }
                if (!acceptedTerms) {
                    Toast.makeText(
                        context,
                        if (isBn) "অনুগ্রহ করে শর্তাবলী ও গোপনীয়তা নীতিতে টিক দিন" else "Please accept Terms & Privacy Policy",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@Button
                }
                viewModel.signUpWithEmail(name, email, password) {
                    Toast.makeText(
                        context,
                        if (isBn) "ভেরিফিকেশন ইমেইল পাঠানো হয়েছে!" else "Verification email sent!",
                        Toast.LENGTH_LONG
                    ).show()
                    onSignUpSuccess()
                }
            },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = OntikGreenPrimary)
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White)
            } else {
                Text(
                    text = if (isBn) "অ্যাকাউন্ট তৈরি করুন (Sign Up)" else "Create Account",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        TextButton(onClick = onBackToLogin) {
            Text(
                text = if (isBn) "আগে থেকেই অ্যাকাউন্ট আছে? লগইন করুন" else "Already have an account? Sign In",
                color = OntikGreenPrimary
            )
        }
    }
}
