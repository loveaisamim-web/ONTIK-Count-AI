package com.ontik.countai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ontik.countai.domain.model.AppLanguage
import com.ontik.countai.ui.components.AdMobBannerCard
import com.ontik.countai.ui.theme.OntikAmberAccent
import com.ontik.countai.ui.theme.OntikGreenPrimary
import com.ontik.countai.ui.viewmodel.CountViewModel
import java.util.Calendar

@Composable
fun HomeDashboardScreen(
    viewModel: CountViewModel,
    onStartCounting: () -> Unit,
    onNavigateToPaywall: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val sessions by viewModel.historySessions.collectAsState()
    val user by viewModel.currentUser.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val isBn = settings.language == AppLanguage.BANGLA

    val startOfTodayMs = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val sevenDaysAgoMs = System.currentTimeMillis() - 7L * 24L * 3600L * 1000L

    val todayTotal = sessions.filter { it.timestampMs >= startOfTodayMs }.sumOf { it.finalCount }
    val weeklyTotal = sessions.filter { it.timestampMs >= sevenDaysAgoMs }.sumOf { it.finalCount }

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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "ONTIK Count AI",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = if (isBn) "হোম ড্যাশবোর্ড ও সারাংশ" else "Home Dashboard & Summary",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = onNavigateToProfile,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (isBn) "অ্যাকাউন্ট" else "Account", fontWeight = FontWeight.Bold)
            }
        }

        if (user.isGuest) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = OntikAmberAccent.copy(alpha = 0.18f)),
                border = BorderStroke(1.dp, OntikAmberAccent),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isBn) "ডেটা ক্লাউডে সুরক্ষিত রাখতে সাইন ইন করুন" else "Sign in to back up your counting data",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = onNavigateToLogin,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OntikGreenPrimary)
                    ) {
                        Text(if (isBn) "লগইন" else "Sign In")
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (isBn) "আজকের মোট গণনা" else "TODAY'S TOTAL",
                            style = MaterialTheme.typography.labelLarge,
                            color = OntikGreenPrimary
                        )
                        Text(
                            text = "$todayTotal",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (isBn) "এই সপ্তাহের মোট" else "THIS WEEK",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$weeklyTotal",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        Button(
            onClick = onStartCounting,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = OntikGreenPrimary)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Text(
                text = if (isBn) "  লাইভ ক্যামেরা গণনা শুরু করুন" else "  Launch Live Camera Counter",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onNavigateToHistory,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (isBn) "ইতিহাস (History)" else "History")
            }
            OutlinedButton(
                onClick = onNavigateToSettings,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (isBn) "সেটিংস (Settings)" else "Settings")
            }
        }

        AdMobBannerCard(isVisible = true)
    }
}

@Composable
fun LegalDocumentScreen(
    isPrivacyPolicy: Boolean,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = if (isPrivacyPolicy) "গোপনীয়তা নীতি (Privacy Policy)" else "শর্তাবলী (Terms of Service)",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isPrivacyPolicy) {
                    Text("১. ক্যামেরা প্রসেসিং (On-Device Camera Processing)", fontWeight = FontWeight.Bold)
                    Text("ONTIK Count AI আপনার ফোনের ক্যামেরা ফ্রেম সরাসরি ফোনের ভেতরেই OpenCV প্রযুক্তিতে প্রসেস করে। কোনো ছবি বা ভিডিও সার্ভারে আপলোড করা হয় না।")
                    Text("২. সংগৃহীত তথ্য ও বিজ্ঞাপন (Account & Ads)", fontWeight = FontWeight.Bold)
                    Text("আপনি লগইন করলে আপনার নাম, ইমেইল ও গণনার সেশন হিস্ট্রি Google Cloud Firestore-এ ব্যাকআপ রাখা হয় এবং অ্যাপে Google AdMob বিজ্ঞাপন প্রদর্শিত হয়।")
                    Text("৩. অ্যাকাউন্ট ও ডেটা মুছে ফেলা (Account Deletion)", fontWeight = FontWeight.Bold)
                    Text("আপনি যেকোনো সময় প্রোফাইল (Profile) স্ক্রিনে গিয়ে 'Delete Account' বাটনে চাপ দিয়ে আপনার অ্যাকাউন্ট ও সমস্ত ডেটা স্থায়ীভাবে মুছে ফেলতে পারবেন।")
                } else {
                    Text("১. সেবা ও ইন্টারনেট সংযোগ (Online Service)", fontWeight = FontWeight.Bold)
                    Text("ONTIK Count AI একটি সম্পূর্ণ অনলাইনভিত্তিক শস্য ও বীজ গণনাকারী অ্যাপ। অ্যাপটি ব্যবহারের সময় ইন্টারনেট সংযোগ চালু থাকা আবশ্যক।")
                    Text("২. আনলিমিটেড ব্যবহার (Unlimited Counting)", fontWeight = FontWeight.Bold)
                    Text("ব্যবহারকারীরা কোনো সাবস্ক্রিপশন ফি ছাড়াই আনলিমিটেড গণনা, সেশন হিস্ট্রি এবং CSV/TXT/PDF রিপোর্ট এক্সপোর্ট করতে পারবেন।")
                }
            }
        }

        AdMobBannerCard(isVisible = true)
    }
}
