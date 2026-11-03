package com.ontik.countai.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ontik.countai.domain.model.AppLanguage
import com.ontik.countai.domain.model.AppThemeMode
import com.ontik.countai.domain.model.CountingDirection
import com.ontik.countai.domain.model.GrainPreset
import com.ontik.countai.ui.theme.OntikGreenPrimary
import com.ontik.countai.ui.viewmodel.CountViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: CountViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
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
        // Top Bar
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Column {
                Text(
                    text = if (isBn) "সেটিংস ও ওপেনসিভি টিউনিং" else "Settings & OpenCV Tuning",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (isBn) "শস্য প্রিসেট, সেন্সিটিভিটি, ট্র্যাকিং ও থিম নিয়ন্ত্রণ" else "Grain presets, sensitivity, tracking, camera & theme",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Card 1: Language & Theme
        SettingsCard(title = if (isBn) "ভাষা ও থিম (Language & Theme)" else "Language & Theme") {
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

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isBn) "অ্যাপ থিম (App Theme)" else "App Theme",
                style = MaterialTheme.typography.titleMedium
            )
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

        // Card 2: Grain Presets (Rice, Dal/Lentil, Sesame, Beans, Tablets + Save Custom)
        SettingsCard(
            title = if (isBn) "শস্য ও বীজের প্রিসেট (Grain Presets)" else "Grain & Seed Presets",
            subtitle = if (isBn) {
                "যেকোনো একটি সিলেক্ট করলে সেটির আকার অনুযায়ী সব স্লাইডার অটো-সেট হয়ে যাবে"
            } else {
                "Selecting a preset configures area limits, morphology, sensitivity & tracking"
            }
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GrainPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = settings.activePreset == preset,
                        onClick = { viewModel.applyPreset(preset) },
                        label = { Text(preset.title(lang)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OntikGreenPrimary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    viewModel.saveCustomPreset()
                    Toast.makeText(
                        context,
                        if (isBn) "কাস্টম প্রিসেট হিসেবে সেভ হয়েছে!" else "Saved current sliders to Custom Preset!",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, OntikGreenPrimary)
            ) {
                Text(
                    text = if (isBn) "বর্তমান সেটিংস 'আমার কাস্টম প্রিসেট'-এ সেভ করুন" else "Save Current Parameters as My Custom Preset",
                    color = OntikGreenPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Card 3: Count Direction (Segmented Chips) & Verification Line B
        SettingsCard(
            title = if (isBn) "গণনার দিক (Count Direction)" else "Count Direction & Verification Line",
            subtitle = if (isBn) "যেদিক থেকে দানা দাগের ওপারে যাবে সেটি সিলেক্ট করুন" else "Select the direction grains cross the dashed line"
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CountingDirection.entries.forEach { dir ->
                    FilterChip(
                        selected = settings.countingDirection == dir,
                        onClick = { viewModel.updateDirection(dir) },
                        label = { Text(dir.chip(lang)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OntikGreenPrimary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
            Text(
                text = settings.countingDirection.subtitle(lang),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SettingToggleRow(
                title = if (isBn) "দ্বিতীয় ভেরিফিকেশন লাইন (Line B)" else "Second Verification Line (Line B)",
                subtitle = if (isBn) "লাইন A ও B এর গণনা না মিললে সতর্কবার্তা দেখাবে" else "Warns if grains crossing Line B differ from Line A",
                checked = settings.enableVerificationLineB,
                onCheckedChange = viewModel::updateVerificationLineB
            )
        }

        // Card 4: Single-Grain Size (Auto Median vs Manual + Calibrate Button)
        SettingsCard(
            title = if (isBn) "একক দানার আকার (Single-Grain Area)" else "Single-Grain Size & Touching Split",
            subtitle = if (isBn) {
                "একাধিক দানা একসাথে লেগে থাকলে (Area > 1.5x) অ্যাপ এই আকার দিয়ে ভাগ করে সঠিক সংখ্যা বের করে"
            } else {
                "Used with Watershed to split touching grains (Area > 1.5x) and clumps (> 6x)"
            }
        ) {
            SettingToggleRow(
                title = if (isBn) "স্বয়ংক্রিয় দানার আকার (Auto Running Median)" else "Auto-Learn Single-Grain Size (Running Median)",
                subtitle = if (isBn) {
                    "বর্তমান শেখা আকার: ${uiState.learnedSingleGrainAreaPx.toInt()} px²"
                } else {
                    "Currently learned median area: ${uiState.learnedSingleGrainAreaPx.toInt()} px²"
                },
                checked = settings.autoSingleGrainSize,
                onCheckedChange = viewModel::updateAutoSingleGrainSize
            )

            if (!settings.autoSingleGrainSize) {
                SliderWithHelp(
                    title = if (isBn) "ম্যানুয়াল একক দানার আকার: ${settings.manualSingleGrainAreaPx.toInt()} px²" else "Manual Single-Grain Area: ${settings.manualSingleGrainAreaPx.toInt()} px²",
                    help = if (isBn) "কম রাখলে জোড়া লাগা দানা বেশি ভাগ হবে" else "Lower value splits touching blobs into more grains",
                    value = settings.manualSingleGrainAreaPx,
                    valueRange = 15f..1200f,
                    onValueChange = viewModel::updateManualSingleGrainArea
                )
            }

            OutlinedButton(
                onClick = {
                    viewModel.triggerSingleGrainSizeCalibration()
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, OntikGreenPrimary)
            ) {
                Text(
                    text = if (isBn) "দানার আকার নতুন করে ক্যালিব্রেট করুন (৩০টি দানা)" else "Calibrate Grain Size Now (Drop 30 Isolated Grains)",
                    color = OntikGreenPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Card 5: OpenCV Segmentation & Tracking Sliders
        SettingsCard(
            title = if (isBn) "ওপেনসিভি সেন্সিটিভিটি ও ট্র্যাকিং স্লাইডার" else "OpenCV Sensitivity, Area & Tracking Sliders"
        ) {
            SliderWithHelp(
                title = if (isBn) "সেন্সিটিভিটি (Sensitivity): ${(settings.sensitivity * 100).toInt()}%" else "Sensitivity: ${(settings.sensitivity * 100).toInt()}%",
                help = if (isBn) "দানা মিস হলে বাড়ান; ছায়া বা ধুলো গুনলে কমান" else "Increase if pale grains are missed; decrease if background noise appears",
                value = settings.sensitivity,
                valueRange = 0.15f..0.90f,
                onValueChange = viewModel::updateSensitivity
            )

            SliderWithHelp(
                title = if (isBn) "সর্বনিম্ন আকার (Min Area): ${settings.minAreaPx} px²" else "Minimum Grain Area: ${settings.minAreaPx} px²",
                help = if (isBn) "এর চেয়ে ছোট ধুলোবালি বাদ যাবে" else "Rejects tiny dust specs smaller than this pixel area",
                value = settings.minAreaPx.toFloat(),
                valueRange = 4f..250f,
                onValueChange = { viewModel.updateMinArea(it.toInt()) }
            )

            SliderWithHelp(
                title = if (isBn) "সর্বোচ্চ আকার (Max Area): ${settings.maxAreaPx} px²" else "Maximum Blob Area: ${settings.maxAreaPx} px²",
                help = if (isBn) "হাত বা বিশাল ছায়া বাদ দেওয়ার সীমা" else "Rejects hands or large foreign objects above this area",
                value = settings.maxAreaPx.toFloat(),
                valueRange = 500f..25000f,
                onValueChange = { viewModel.updateMaxArea(it.toInt()) }
            )

            SliderWithHelp(
                title = if (isBn) "ট্র্যাকিং দূরত্ব (Tracking Gate): ${(settings.trackingDistanceNormalized * 100).toInt()}%" else "Tracking Distance Gate: ${(settings.trackingDistanceNormalized * 100).toInt()}%",
                help = if (isBn) "দানা খুব দ্রুত পড়লে বাড়ান যাতে ফ্রেমের মাঝে ট্র্যাক না হারায়" else "Increase for fast-falling grains so Hungarian matcher connects long jumps",
                value = settings.trackingDistanceNormalized,
                valueRange = 0.06f..0.32f,
                onValueChange = viewModel::updateTrackingDistance
            )

            SliderWithHelp(
                title = if (isBn) "লাইন হিস্টেরেসিস (Line Hysteresis): ${String.format(java.util.Locale.US, "%.1f%%", settings.lineHysteresisNormalized * 100)}" else "Line Hysteresis Band: ${String.format(java.util.Locale.US, "%.1f%%", settings.lineHysteresisNormalized * 100)}",
                help = if (isBn) "দাগের ওপর স্থির থাকা দানা ডাবল গোনা রোধ করে" else "Prevents jitter double-counts right on the counting line",
                value = settings.lineHysteresisNormalized,
                valueRange = 0.005f..0.045f,
                onValueChange = viewModel::updateLineHysteresis
            )

            SliderWithHelp(
                title = if (isBn) "মরফোলজি কার্নেল (Morphology Kernel): ${settings.morphKernelSize}x${settings.morphKernelSize}" else "Morphology Strength: ${settings.morphKernelSize}x${settings.morphKernelSize} px",
                help = if (isBn) "ছোট বীজের জন্য 3x3 এবং বড় বাদাম/ট্যাবলেটের জন্য 5x5" else "3x3 for tiny seeds/rice; 5x5 or 7x7 for large beans/tablets",
                value = settings.morphKernelSize.toFloat(),
                valueRange = 3f..7f,
                onValueChange = { viewModel.updateMorphKernel(it.toInt()) }
            )
        }

        // Card 6: Camera & Feedback Options
        SettingsCard(
            title = if (isBn) "ক্যামেরা, সাউন্ড ও ডিবাগ অপশন" else "Camera, Feedback & Debug Options"
        ) {
            SettingToggleRow(
                title = if (isBn) "ক্যালিব্রেশনের পর AE/AWB লক করুন" else "Lock Exposure & White Balance After Calibration",
                subtitle = if (isBn) "হাতের ছায়ায় আলো ওঠানামা রোধ করে" else "Prevents auto-exposure flicker while dropping grains",
                checked = settings.lockCameraExposureFocus,
                onCheckedChange = viewModel::updateLockCamera
            )

            SettingToggleRow(
                title = if (isBn) "উচ্চ ফ্রেম রেট (Target 60 FPS)" else "High Frame Rate Mode (Target 60 FPS)",
                subtitle = if (isBn) "দ্রুত পড়ন্ত দানার জন্য (সাপোর্ট করলে ৬০ FPS)" else "Requests 60 FPS via Camera2Interop where hardware supports it",
                checked = settings.targetFps >= 60,
                onCheckedChange = { viewModel.updateTargetFps(if (it) 60 else 30) }
            )

            SettingToggleRow(
                title = if (isBn) "পিছনের ক্যামেরা (Rear Camera)" else "Use Rear Camera",
                subtitle = if (isBn) "বন্ধ করলে সামনের ক্যামেরা চালু হবে" else "Switch off for front camera",
                checked = settings.useBackCamera,
                onCheckedChange = viewModel::updateCameraFacing
            )

            SettingToggleRow(
                title = if (isBn) "ভাইব্রেশন (Haptic Tick & 100 Milestone)" else "Haptic Tick & 100-Grain Milestone Vibration",
                subtitle = if (isBn) "প্রতি গণনায় টিক এবং ১০০ পূর্ণ হলে দীর্ঘ ভাইব্রেশন" else "Subtle tick per grain + strong pulse every 100 count",
                checked = settings.hapticEnabled,
                onCheckedChange = viewModel::updateHaptic
            )

            SettingToggleRow(
                title = if (isBn) "সাউন্ড বিপ (Soft Audio Beep)" else "Soft Audio Beep on Count",
                subtitle = if (isBn) "প্রতি দানা গণনায় হালকা বিপ সাউন্ড" else "Plays short acoustic tick when a grain crosses the line",
                checked = settings.soundBeepEnabled,
                onCheckedChange = viewModel::updateSoundBeep
            )

            if (settings.soundBeepEnabled) {
                SliderWithHelp(
                    title = if (isBn) "সাউন্ড ভলিউম: ${(settings.soundVolume * 100).toInt()}%" else "Beep Volume: ${(settings.soundVolume * 100).toInt()}%",
                    help = "",
                    value = settings.soundVolume,
                    valueRange = 0.1f..1.0f,
                    onValueChange = viewModel::updateSoundVolume
                )
            }

            SettingToggleRow(
                title = if (isBn) "স্ক্রিন সবসময় চালু রাখুন (Keep Screen On)" else "Keep Screen On",
                subtitle = if (isBn) "গণনার সময় ফোনের আলো বন্ধ হবে না" else "Prevents phone from sleeping during counting",
                checked = settings.keepScreenOn,
                onCheckedChange = viewModel::updateKeepScreenOn
            )

            SettingToggleRow(
                title = if (isBn) "ট্র্যাক আইডি দেখান (Show Track IDs)" else "Show Track IDs (#1, #2...)",
                subtitle = if (isBn) "প্রতিটি দানার সাথে ট্র্যাকিং নম্বর দেখাবে" else "Displays Kalman tracker ID numbers next to grains",
                checked = settings.showTrackIds,
                onCheckedChange = viewModel::updateShowTrackIds
            )

            SettingToggleRow(
                title = if (isBn) "বাইনারি মাস্ক ওভারলে (Debug Mask View)" else "Show Binary Mask Overlay (Debug View)",
                subtitle = if (isBn) "ওপেনসিভি সাদা-কালো মাস্ক ক্যামেরার ওপর দেখাবে" else "Overlays OpenCV thresholded foreground mask on camera",
                checked = settings.showDebugMask,
                onCheckedChange = viewModel::updateShowDebugMask
            )

            OutlinedButton(
                onClick = viewModel::exportLast10SecondsDebugLog,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (isBn) "শেষ ১০ সেকেন্ডের ডিবাগ লগ শেয়ার করুন (CSV)" else "Export Last 10 Seconds Debug Recorder Log (CSV)",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Reset All to Defaults Button
        Button(
            onClick = {
                viewModel.resetSettingsToDefaults()
                Toast.makeText(
                    context,
                    if (isBn) "সব সেটিংস ডিফল্ট করা হয়েছে" else "All settings reset to defaults",
                    Toast.LENGTH_SHORT
                ).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = OntikGreenPrimary)
        ) {
            Text(
                text = if (isBn) "সব সেটিংস ডিফল্ট অবস্থায় ফিরিয়ে আনুন (Reset to Defaults)" else "Reset All Settings to Defaults",
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
    }
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            content()
        }
    }
}

@Composable
private fun SliderWithHelp(
    title: String,
    help: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (help.isNotBlank()) {
            Text(
                text = help,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = OntikGreenPrimary,
                activeTrackColor = OntikGreenPrimary
            )
        )
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
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = OntikGreenPrimary)
        )
    }
}
