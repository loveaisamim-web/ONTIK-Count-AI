package com.ontik.countai.ui.screens

import android.app.Activity
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ontik.countai.billing.PurchaseUiState
import com.ontik.countai.domain.model.AppLanguage
import com.ontik.countai.ui.theme.OntikAmberAccent
import com.ontik.countai.ui.theme.OntikGreenBright
import com.ontik.countai.ui.theme.OntikGreenPrimary
import com.ontik.countai.ui.viewmodel.CountViewModel

@Composable
fun PaywallScreen(
    viewModel: CountViewModel,
    onClose: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val settings by viewModel.settings.collectAsState()
    val planState by viewModel.planState.collectAsState()
    val pricing by viewModel.storePricing.collectAsState()
    val purchaseState by viewModel.purchaseUiState.collectAsState()
    val isBn = settings.language == AppLanguage.BANGLA

    var selectedYearly by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F19))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(18.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = OntikAmberAccent,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "ONTIK COUNT AI PRO",
                    color = Color.Black,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        Text(
            text = if (isBn) {
                "আনলিমিটেড শস্য গণনা, PDF রিপোর্ট ও ক্লাউড ব্যাকআপ আনলক করুন"
            } else {
                "Unlock Unlimited Counting, PDF Reports & Cloud Sync"
            },
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold
        )

        // Purchase Status Banner
        when (val st = purchaseState) {
            is PurchaseUiState.Success -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OntikGreenPrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isBn) st.messageBn else st.messageEn,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
            is PurchaseUiState.Error -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isBn) st.messageBn else st.messageEn,
                        color = Color.White,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
            PurchaseUiState.PendingPayment -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = OntikAmberAccent),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isBn) "পেমেন্ট পেন্ডিং আছে। নিশ্চিত হলে অটোমেটিক প্রো চালু হবে।" else "Payment is pending in Google Play. PRO will unlock once confirmed.",
                        color = Color.Black,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
            else -> {}
        }

        // Free vs Pro Comparison Table
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (isBn) "ফিচার (Feature)" else "Feature",
                        color = Color.White.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1.5f)
                    )
                    Text(
                        text = "FREE",
                        color = Color.White.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(0.75f)
                    )
                    Text(
                        text = "PRO",
                        color = OntikAmberAccent,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(0.75f)
                    )
                }

                ComparisonRow(
                    feature = if (isBn) "দৈনিক গণনা লিমিট" else "Daily Counting Limit",
                    freeVal = if (isBn) "আনলিমিটেড" else "Unlimited",
                    proVal = if (isBn) "আনলিমিটেড" else "Unlimited"
                )
                ComparisonRow(
                    feature = if (isBn) "সেভ করা সেশন হিস্ট্রি" else "Saved History Sessions",
                    freeVal = "${planState.freeHistoryLimit} max",
                    proVal = if (isBn) "আনলিমিটেড" else "Unlimited"
                )
                ComparisonRow(
                    feature = if (isBn) "CSV ও প্রো PDF রিপোর্ট" else "CSV & Branded PDF Report",
                    freeVal = "—",
                    proVal = "✓"
                )
                ComparisonRow(
                    feature = if (isBn) "ক্লাউড ব্যাকআপ ও ৩টি ফোন সিঙ্ক" else "Cloud Backup & Sync (3 Devices)",
                    freeVal = "—",
                    proVal = "✓"
                )
                ComparisonRow(
                    feature = if (isBn) "কাস্টম প্রিসেট ও টার্গেট অ্যালার্ম" else "Custom Presets & Target Alarm",
                    freeVal = "—",
                    proVal = "✓"
                )
                ComparisonRow(
                    feature = if (isBn) "বিজ্ঞাপন মুক্ত (No Ads)" else "100% Ad-Free Experience",
                    freeVal = "—",
                    proVal = "✓"
                )
            }
        }

        // Plan Selector Cards (Yearly with Save 40% + 7-Day Free Trial vs Monthly)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Yearly Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { selectedYearly = true },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedYearly) OntikGreenPrimary.copy(alpha = 0.25f) else Color(0xFF1E293B)
                ),
                border = BorderStroke(
                    width = if (selectedYearly) 2.dp else 1.dp,
                    color = if (selectedYearly) OntikGreenBright else Color.White.copy(alpha = 0.15f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = OntikAmberAccent,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (isBn) "৪০% সাশ্রয় + ৭ দিন ফ্রি ট্রায়াল" else "SAVE 40% • 7-DAY TRIAL",
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = if (isBn) "বাৎসরিক (Yearly)" else "Yearly Plan",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = pricing.yearlyPriceFormatted.ifBlank { planState.proYearlyPriceText },
                        color = OntikGreenBright,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                }
            }

            // Monthly Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { selectedYearly = false },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (!selectedYearly) OntikGreenPrimary.copy(alpha = 0.25f) else Color(0xFF1E293B)
                ),
                border = BorderStroke(
                    width = if (!selectedYearly) 2.dp else 1.dp,
                    color = if (!selectedYearly) OntikGreenBright else Color.White.copy(alpha = 0.15f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isBn) "মাসিক (Monthly)" else "Monthly Plan",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = pricing.monthlyPriceFormatted.ifBlank { planState.proMonthlyPriceText },
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = if (isBn) "যেকোনো সময় বাতিলযোগ্য" else "Cancel anytime",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Primary Subscribe CTA
        Button(
            onClick = {
                if (activity != null) {
                    val pkg = if (selectedYearly) pricing.yearlyPackage else pricing.monthlyPackage
                    viewModel.purchaseProPackage(activity, pkg)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = OntikGreenPrimary)
        ) {
            Text(
                text = if (selectedYearly) {
                    if (isBn) "৭ দিনের ফ্রি ট্রায়াল শুরু করুন" else "Start 7-Day Free Trial (Yearly)"
                } else {
                    if (isBn) "মাসিক প্রো সাবস্ক্রাইব করুন" else "Subscribe to Monthly PRO"
                },
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            )
        }

        // Restore Purchases & Legal Links + Google Play Auto-Renew Disclosure
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = viewModel::restorePurchases) {
                Text(
                    text = if (isBn) "পারচেজ রিস্টোর (Restore)" else "Restore Purchases",
                    color = Color.White
                )
            }
            TextButton(onClick = onOpenTerms) {
                Text("Terms", color = Color.White.copy(alpha = 0.75f))
            }
            TextButton(onClick = onOpenPrivacy) {
                Text("Privacy", color = Color.White.copy(alpha = 0.75f))
            }
        }

        Text(
            text = if (isBn) {
                "সাবস্ক্রিপশন স্বয়ংক্রিয়ভাবে রিনিউ হবে যদি না মেয়াদ শেষের অন্তত ২৪ ঘণ্টা আগে Google Play -> Subscriptions থেকে বাতিল করা হয়।"
            } else {
                "Subscriptions automatically renew unless canceled at least 24 hours before the end of the current period in Google Play Store -> Subscriptions."
            },
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ComparisonRow(feature: String, freeVal: String, proVal: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = feature,
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier.weight(1.5f)
        )
        Text(
            text = freeVal,
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(0.75f)
        )
        Text(
            text = proVal,
            color = OntikGreenBright,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(0.75f)
        )
    }
}
