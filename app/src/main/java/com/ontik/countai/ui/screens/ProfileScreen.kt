package com.ontik.countai.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
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
import com.ontik.countai.domain.model.AppLanguage
import com.ontik.countai.ui.theme.DangerRed
import com.ontik.countai.ui.theme.OntikGreenPrimary
import com.ontik.countai.ui.viewmodel.CountViewModel
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    viewModel: CountViewModel,
    onBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToPaywall: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user by viewModel.currentUser.collectAsState()
    val planState by viewModel.planState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val isBn = settings.language == AppLanguage.BANGLA

    var editName by remember(user.name) { mutableStateOf(user.name) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var reauthPassword by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = if (isBn) "অ্যাকাউন্ট ও প্রোফাইল (Profile)" else "Account Profile",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(OntikGreenPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    val initials = user.name.trim().take(2).uppercase()
                    if (initials.isNotEmpty() && !user.isGuest) {
                        Text(
                            text = initials,
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    } else {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color.White)
                    }
                }

                Text(
                    text = if (user.isGuest) {
                        if (isBn) "গেস্ট মোড (লগইন করা হয়নি)" else "Guest Mode (Not Signed In)"
                    } else {
                        user.email
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = if (isBn) "আজকের মোট গণনা: ${planState.todayUsedCount} (আনলিমিটেড)" else "Today's Count: ${planState.todayUsedCount} (Unlimited)",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = OntikGreenPrimary
                )

                if (user.isGuest) {
                    Button(
                        onClick = onNavigateToLogin,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OntikGreenPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isBn) "ক্লাউড ব্যাকআপের জন্য লগইন / সাইন-আপ করুন" else "Sign In / Create Account for Cloud Backup",
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text(if (isBn) "নাম পরিবর্তন করুন" else "Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedButton(
                        onClick = {
                            viewModel.updateProfileName(editName)
                            Toast.makeText(
                                context,
                                if (isBn) "নাম আপডেট হয়েছে" else "Name updated",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (isBn) "নাম সেভ করুন" else "Save Name")
                    }
                }
            }
        }

        if (!user.isGuest) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!user.isGoogleAccount) {
                        OutlinedButton(
                            onClick = {
                                viewModel.sendPasswordResetEmail(user.email) { _, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (isBn) "পাসওয়ার্ড পরিবর্তন করুন (Change Password)" else "Change Password via Email")
                        }
                    }

                    OutlinedButton(
                        onClick = { showLogoutDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (isBn) "লগআউট করুন (Logout)" else "Logout")
                    }

                    Button(
                        onClick = { showDeleteAccountDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                    ) {
                        Text(
                            text = if (isBn) "অ্যাকাউন্ট ও সমস্ত ডেটা মুছে ফেলুন (Delete Account)" else "Delete Account & All Cloud Data",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(if (isBn) "লগআউট করবেন?" else "Confirm Logout") },
            text = {
                Text(
                    if (isBn) "আপনি কি আপনার অ্যাকাউন্ট থেকে লগআউট করতে চান?" else "Are you sure you want to log out?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.signOut(onBack)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OntikGreenPrimary)
                ) {
                    Text(if (isBn) "লগআউট" else "Logout")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(if (isBn) "বাতিল" else "Cancel")
                }
            }
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = {
                Text(
                    text = if (isBn) "স্থায়ীভাবে অ্যাকাউন্ট মুছে ফেলবেন?" else "Permanently Delete Account?",
                    color = DangerRed,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (isBn) {
                            "এটি আপনার ক্লাউড ব্যাকআপ, প্রোফাইল এবং লোকাল হিস্ট্রি চিরতরে মুছে ফেলবে।"
                        } else {
                            "This permanently deletes your cloud backups, profile, and local history."
                        }
                    )
                    if (!user.isGoogleAccount) {
                        OutlinedTextField(
                            value = reauthPassword,
                            onValueChange = { reauthPassword = it },
                            label = { Text(if (isBn) "নিশ্চিত করতে পাসওয়ার্ড দিন" else "Enter current password to confirm") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (user.isGoogleAccount) {
                            scope.launch {
                                val webClientId = resolveDefaultWebClientId(context)
                                var googleIdToken: String? = null
                                if (webClientId.isNotBlank()) {
                                    try {
                                        val cm = CredentialManager.create(context)
                                        val googleIdOption = GetGoogleIdOption.Builder()
                                            .setFilterByAuthorizedAccounts(false)
                                            .setServerClientId(webClientId)
                                            .build()
                                        val req = GetCredentialRequest.Builder()
                                            .addCredentialOption(googleIdOption)
                                            .build()
                                        val res = cm.getCredential(context, req)
                                        googleIdToken = GoogleIdTokenCredential.createFrom(res.credential.data).idToken
                                    } catch (_: Throwable) {
                                    }
                                }
                                viewModel.deleteAccountAndData(
                                    passwordIfEmail = null,
                                    googleIdTokenIfGoogle = googleIdToken
                                ) { ok, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    if (ok) {
                                        showDeleteAccountDialog = false
                                        onBack()
                                    }
                                }
                            }
                        } else {
                            viewModel.deleteAccountAndData(
                                passwordIfEmail = reauthPassword,
                                googleIdTokenIfGoogle = null
                            ) { ok, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                if (ok) {
                                    showDeleteAccountDialog = false
                                    onBack()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text(if (isBn) "স্থায়ীভাবে মুছুন" else "Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text(if (isBn) "বাতিল" else "Cancel")
                }
            }
        )
    }
}
