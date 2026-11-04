package com.ontik.countai.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ontik.countai.BuildConfig
import com.ontik.countai.domain.model.AppLanguage
import com.ontik.countai.domain.model.AppThemeMode
import com.ontik.countai.domain.model.CountingDirection
import com.ontik.countai.domain.model.GrainPreset
import com.ontik.countai.ui.components.AdMobBannerCard
import com.ontik.countai.ui.theme.DangerRed
import com.ontik.countai.ui.theme.OntikGreenPrimary
import com.ontik.countai.ui.viewmodel.CountViewModel
import kotlin.math.roundToInt

/**
 * Clean, human-friendly Settings screen with zero confusing technical jargon and no PRO locks.
 * Shows the AdMob Banner at the bottom to generate ad revenue.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: CountViewModel,
    onBack: () -> Unit,
    onNavigateToPaywall: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val lang = settings.language
    val isBn = lang == AppLanguage.BANGLA

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Column {
                    Text(
                        text = if (isBn) "সেটিংস (Settings)" else "Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isBn) "ভাষা, শস্যের ধরন, সেনসিটিভিটি ও শব্দ" else "Language, Grain Type, Sensitivity & Sound",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onNavigateToProfile) {
                Icon(Icons.Default.Person, contentDescription = "Account Profile")
            }
        }

        // Card 1: Language & Theme
        SettingsCard(
            title = if (isBn) "১. ভাষা ও থিম (Language & Theme)" else "1. Language & Theme",
            subtitle = if (isBn) "অ্যাপের ভাষা (বাংলা / English) এবং কালার থিম নির্বাচন করুন" else "Choose your preferred language and display theme"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppLanguage.entries.forEach { option ->
                    FilterChip(
                        selected = settings.language == option,
                        onClick = { viewModel.updateLanguage(option) },
                        label = { Text(option.nativeName, fontWeight = FontWeight.SemiBold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OntikGreenPrimary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.updateThemeMode(mode) },
                        label = { Text(mode.title(lang)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OntikGreenPrimary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }

        // Card 2: Select Grain / Seed Type
        SettingsCard(
            title = if (isBn) "২. আপনি কী গুনতে চান? (Select Item)" else "2. What Are You Counting?",
            subtitle = if (isBn) "যেটি গুনবেন তার ওপর ট্যাপ করলে আকার স্বয়ংক্রিয়ভাবে সেট হয়ে যাবে" else "Tap the item type to automatically configure detection size"
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GrainPreset.entries.filter { it != GrainPreset.CUSTOM }.forEach { preset ->
                    FilterChip(
                        selected = settings.activePreset == preset,
                        onClick = {
                            viewModel.applyPreset(preset = preset)
                            Toast.makeText(
                                context,
                                if (isBn) "${preset.nameBn} সেট করা হয়েছে" else "${preset.nameEn} selected",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        label = { Text(preset.title(lang)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OntikGreenPrimary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }

        // Card 3: Detection Sensitivity & Counting Direction
        SettingsCard(
            title = if (isBn) "৩. শনাক্তকরণ ক্ষমতা ও দিক (Sensitivity & Direction)" else "3. Detection Sensitivity & Direction",
            subtitle = if (isBn) "সাদা কাগজে হালকা দানার জন্য সেনসিটিভিটি বাড়ান; দাগ বা ছায়া এড়াতে কমান" else "Increase for light grains on white paper; decrease to ignore surface marks"
        ) {
            val sensPercent = (settings.sensitivity * 100).roundToInt()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isBn) "সেনসিটিভিটি (Sensitivity)" else "Sensitivity",
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$sensPercent%",
                    color = OntikGreenPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Slider(
                value = settings.sensitivity,
                onValueChange = viewModel::updateSensitivity,
                valueRange = 0.15f..0.85f,
                colors = SliderDefaults.colors(
                    thumbColor = OntikGreenPrimary,
                    activeTrackColor = OntikGreenPrimary
                )
            )

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isBn) "দানা কোন দিক থেকে কোন দিকে যাবে?" else "Counting Direction Across Line",
                fontWeight = FontWeight.SemiBold
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CountingDirection.entries.forEach { dir ->
                    FilterChip(
                        selected = settings.countingDirection == dir,
                        onClick = { viewModel.updateDirection(dir) },
                        label = { Text(dir.title(lang)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OntikGreenPrimary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }

        // Card 4: Target Alarm, Batch Mode & Sound/Vibration
        SettingsCard(
            title = if (isBn) "৪. টার্গেট অ্যালার্ম, ব্যাচ ও শব্দ (Alarm & Sound)" else "4. Target Alarm, Batch & Sound",
            subtitle = if (isBn) "নির্দিষ্ট সংখ্যা পূর্ণ হলে অ্যালার্ম বাজবে ও গণনা থামবে" else "Auto-pause and play chime when target count is reached"
        ) {
            val targetGoals = listOf(0, 50, 100, 250, 500, 1000)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                targetGoals.forEach { g ->
                    val label = if (g == 0) (if (isBn) "অফ (Off)" else "Off") else "$g"
                    FilterChip(
                        selected = settings.targetCountGoal == g,
                        onClick = { viewModel.updateTargetCountGoal(g) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OntikGreenPrimary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            SettingToggleRow(
                title = if (isBn) "ব্যাচ মোড (Batch Counting)" else "Multi-Batch Counting Mode",
                subtitle = if (isBn) "একাধিক ব্যাচ আলাদাভাবে যোগ করে সর্বমোট হিসাব রাখবে" else "Track multiple batch subtotals and grand total",
                checked = settings.batchModeEnabled,
                onCheckedChange = viewModel::updateBatchMode
            )

            SettingToggleRow(
                title = if (isBn) "গণনার বিপ শব্দ (Sound Beep)" else "Count Tick Sound",
                subtitle = if (isBn) "দানা দাগ পার হলে টিক শব্দ হবে" else "Play audio beep when a grain is counted",
                checked = settings.soundBeepEnabled,
                onCheckedChange = viewModel::updateSoundBeep
            )

            SettingToggleRow(
                title = if (isBn) "ভাইব্রেশন (Vibration)" else "Haptic Vibration",
                subtitle = if (isBn) "গণনার সময় হালকা ভাইব্রেট করবে" else "Vibrate gently on count",
                checked = settings.hapticEnabled,
                onCheckedChange = viewModel::updateHaptic
            )

            SettingToggleRow(
                title = if (isBn) "স্ক্রিন সবসময় চালু রাখুন (Keep Screen On)" else "Keep Screen On",
                subtitle = if (isBn) "গণনার সময় ফোনের ডিসপ্লে বন্ধ হবে না" else "Prevent screen from sleeping while counting",
                checked = settings.keepScreenOn,
                onCheckedChange = viewModel::updateKeepScreenOn
            )
        }

        // Card 5: Camera, Debug Mask & Reset to Defaults
        SettingsCard(
            title = if (isBn) "৫. ক্যামেরা ও ডিফল্ট রিসেট (Camera & Reset)" else "5. Camera & Reset"
        ) {
            SettingToggleRow(
                title = if (isBn) "ক্যালিব্রেশনের পর ফোকাস ও আলো লক করুন" else "Lock Focus & Exposure After Calibration",
                subtitle = if (isBn) "নির্ভুল গণনার জন্য এটি চালু রাখা উত্তম" else "Recommended for consistent accuracy",
                checked = settings.lockCameraExposureFocus,
                onCheckedChange = viewModel::updateLockCamera
            )

            SettingToggleRow(
                title = if (isBn) "ডিবাগ মাস্ক দেখুন (Show Detection Mask)" else "Show OpenCV Binary Mask",
                subtitle = if (isBn) "ক্যামেরা দানা কীভাবে দেখছে তা সাদা-কালোতে দেখুন" else "Preview the thresholded binary mask inside the ROI box",
                checked = settings.showDebugMask,
                onCheckedChange = viewModel::updateShowDebugMask
            )

            SettingToggleRow(
                title = if (isBn) "দানার ট্র্যাক নম্বর দেখুন (#ID)" else "Show Track IDs (#1, #2)",
                subtitle = if (isBn) "প্রতিটি দানার পাশে ট্র্যাকিং নম্বর দেখাবে" else "Display tracking ID next to each grain",
                checked = settings.showTrackIds,
                onCheckedChange = viewModel::updateShowTrackIds
            )

            OutlinedButton(
                onClick = {
                    viewModel.resetSettingsToDefaults()
                    Toast.makeText(
                        context,
                        if (isBn) "সব সেটিংস ডিফল্ট অবস্থায় ফিরিয়ে আনা হয়েছে" else "All settings reset to defaults",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, DangerRed)
            ) {
                Text(
                    text = if (isBn) "ডিফল্ট সেটিংসে ফিরে যান (Reset to Defaults)" else "Reset All Settings to Defaults",
                    color = DangerRed,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onOpenPrivacy) {
                        Text(if (isBn) "গোপনীয়তা নীতি" else "Privacy Policy", fontSize = 12.sp)
                    }
                    TextButton(onClick = onOpenTerms) {
                        Text(if (isBn) "শর্তাবলী" else "Terms", fontSize = 12.sp)
                    }
                }
            }
        }

        // AdMob Banner Ad on Settings Screen
        AdMobBannerCard(isVisible = true)
    }
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            content()
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = OntikGreenPrimary
            )
        )
    }
}
