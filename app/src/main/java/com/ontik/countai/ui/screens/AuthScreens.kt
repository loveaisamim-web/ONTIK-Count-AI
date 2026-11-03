package com.ontik.countai.ui.screens

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
import com.ontik.countai.BuildConfig
import com.ontik.countai.domain.model.AppLanguage
import com.ontik.countai.ui.theme.DangerRed
import com.ontik.countai.ui.theme.OntikAmberAccent
import com.ontik.countai.ui.theme.OntikGreenPrimary
import com.ontik.countai.ui.viewmodel.CountViewModel
import kotlinx.coroutines.launch

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

        // Continue with Google via Credential Manager API
        OutlinedButton(
            onClick = {
                val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
                if (webClientId.isBlank()) {
                    Toast.makeText(
                        context,
                        if (isBn) "GOOGLE_WEB_CLIENT_ID gradle.properties-এ যোগ করুন বা গেস্ট মোডে চালান" else "Set GOOGLE_WEB_CLIENT_ID in gradle.properties or use Guest Mode",
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
                        Toast.makeText(
                            context,
                            e.localizedMessage ?: "Google Sign-In cancelled",
                            Toast.LENGTH_SHORT
                        ).show()
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
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val isLoading by viewModel.authLoading.collectAsState()
    val errorBanner by viewModel.authErrorBanner.collectAsState()
    val isBn = settings.language == AppLanguage.BANGLA

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var acceptedTerms by remember { mutableStateOf(false) }
    var inlineError by remember { mutableStateOf<String?>(null) }

    // Password strength score [0.0 .. 1.0]
    val strengthScore = remember(password) {
        var score = 0f
        if (password.length >= 8) score += 0.35f
        if (password.any { it.isUpperCase() }) score += 0.20f
        if (password.any { it.isDigit() }) score += 0.25f
        if (password.any { !it.isLetterOrDigit() }) score += 0.20f
        score.coerceIn(0f, 1f)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (isBn) "নতুন অ্যাকাউন্ট তৈরি করুন" else "Create Your Account",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (isBn) {
                "গেস্ট মোডের আগের সব লোকাল হিস্ট্রি স্বয়ংক্রিয়ভাবে আপনার নতুন অ্যাকাউন্টে যুক্ত হয়ে যাবে।"
            } else {
                "Your existing local guest history will automatically migrate to your account."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val banner = inlineError ?: errorBanner?.let { if (isBn) it.second else it.first }
        if (banner != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DangerRed.copy(alpha = 0.14f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = banner,
                    color = DangerRed,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it; inlineError = null },
            label = { Text(if (isBn) "আপনার নাম (Name)" else "Full Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; inlineError = null },
            label = { Text(if (isBn) "ইমেইল (Email)" else "Email Address") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; inlineError = null },
            label = { Text(if (isBn) "পাসওয়ার্ড (কমপক্ষে ৮ অক্ষর)" else "Password (min 8 characters)") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        // Password Strength Meter
        LinearProgressIndicator(
            progress = { strengthScore },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = when {
                strengthScore < 0.4f -> DangerRed
                strengthScore < 0.75f -> OntikAmberAccent
                else -> OntikGreenPrimary
            }
        )

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it; inlineError = null },
            label = { Text(if (isBn) "পাসওয়ার্ড নিশ্চিত করুন (Confirm Password)" else "Confirm Password") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = acceptedTerms,
                onCheckedChange = { acceptedTerms = it }
            )
            Column {
                Text(
                    text = if (isBn) "আমি শর্তাবলী (Terms) ও গোপনীয়তা নীতি (Privacy Policy) মেনে নিচ্ছি" else "I accept the Terms of Service & Privacy Policy",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (isBn) "Terms" else "Terms",
                        color = OntikGreenPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onOpenTerms() }
                    )
                    Text(
                        text = if (isBn) "Privacy Policy" else "Privacy Policy",
                        color = OntikGreenPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onOpenPrivacy() }
                    )
                }
            }
        }

        Button(
            onClick = {
                when {
                    name.trim().length < 2 -> {
                        inlineError = if (isBn) "অনুগ্রহ করে আপনার নাম লিখুন" else "Please enter your name"
                    }
                    !email.contains("@") -> {
                        inlineError = if (isBn) "সঠিক ইমেইল ঠিকানা দিন" else "Please enter a valid email"
                    }
                    password.length < 8 -> {
                        inlineError = if (isBn) "পাসওয়ার্ড কমপক্ষে ৮ অক্ষরের হতে হবে" else "Password must be at least 8 characters"
                    }
                    password != confirmPassword -> {
                        inlineError = if (isBn) "দুটি পাসওয়ার্ড মেলেনি" else "Passwords do not match"
                    }
                    !acceptedTerms -> {
                        inlineError = if (isBn) "শর্তাবলী ও প্রাইভেসি পলিসি চেকবক্সে টিক দিন" else "Please accept Terms & Privacy Policy"
                    }
                    else -> {
                        viewModel.signUpWithEmail(name, email, password, onSignUpSuccess)
                    }
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
                Text(if (isBn) "অ্যাকাউন্ট তৈরি করুন (Sign Up)" else "Create Account", fontWeight = FontWeight.Bold)
            }
        }

        TextButton(
            onClick = onBackToLogin,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(if (isBn) "আগে থেকেই অ্যাকাউন্ট আছে? লগইন করুন" else "Already have an account? Sign In")
        }
    }
}
