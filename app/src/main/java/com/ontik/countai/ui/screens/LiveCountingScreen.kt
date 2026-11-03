package com.ontik.countai.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.play.core.review.ReviewManagerFactory
import com.ontik.countai.domain.model.AccuracyConfidenceLevel
import com.ontik.countai.domain.model.AppLanguage
import com.ontik.countai.ui.components.CameraPreviewWithZoneOverlay
import com.ontik.countai.ui.theme.AppStrings
import com.ontik.countai.ui.theme.DangerRed
import com.ontik.countai.ui.theme.OntikAmberAccent
import com.ontik.countai.ui.theme.OntikAmberDark
import com.ontik.countai.ui.theme.OntikGreenBright
import com.ontik.countai.ui.theme.OntikGreenPrimary
import com.ontik.countai.ui.theme.OntikIcons
import com.ontik.countai.ui.viewmodel.CountViewModel
import com.ontik.countai.ui.viewmodel.EngineStatus

@Composable
fun LiveCountingScreen(
    viewModel: CountViewModel,
    onNavigateToHome: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPaywall: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val planState by viewModel.planState.collectAsState()
    val lang = settings.language
    val isBn = lang == AppLanguage.BANGLA

    var showResetConfirmDialog by remember { mutableStateOf(false) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(uiState.lastCountPulseTimestampMs) {
        if (uiState.lastCountPulseTimestampMs > 0L && settings.hapticEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    LaunchedEffect(uiState.lastMilestoneHundredTimestampMs) {
        if (uiState.lastMilestoneHundredTimestampMs > 0L && settings.hapticEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    // Play In-App Review prompt after 3 saved sessions (never during active counting)
    LaunchedEffect(settings.completedSessionsCountForReview, uiState.status) {
        if (settings.completedSessionsCountForReview == 3 &&
            uiState.status != EngineStatus.COUNTING &&
            activity != null
        ) {
            try {
                val manager = ReviewManagerFactory.create(context)
                manager.requestReviewFlow().addOnSuccessListener { reviewInfo ->
                    manager.launchReviewFlow(activity, reviewInfo)
                }
            } catch (_: Throwable) {
            }
        }
    }

    val countBounceScale by animateFloatAsState(
        targetValue = if (android.os.SystemClock.elapsedRealtime() - uiState.lastCountPulseTimestampMs < 160L) 1.08f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "countBounceScale"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            CameraPreviewWithZoneOverlay(
                modifier = Modifier.fillMaxSize(),
                useBackCamera = settings.useBackCamera,
                targetFps = settings.targetFps,
                lockCameraExposureFocus = uiState.cameraLockedAfterCalibration,
                torchEnabled = uiState.torchEnabled,
                roiRect = settings.roiRect,
                lineAPosition = settings.lineAPosition,
                enableVerificationLineB = settings.enableVerificationLineB,
                lineBPosition = settings.lineBPosition,
                countingDirection = settings.countingDirection,
                trackedGrains = uiState.trackedGrains,
                showTrackIds = settings.showTrackIds,
                showDebugMask = settings.showDebugMask,
                debugMaskBitmap = uiState.debugMaskBitmap,
                onFrameAvailable = viewModel::processCameraFrame,
                onRoiAndLinesUpdated = viewModel::updateRoiAndLines
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isBn) "ক্যামেরা পারমিশন প্রয়োজন" else "Camera Access Needed",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (isBn) {
                        "ONTIK Count AI সম্পূর্ণ অফলাইনে ওপেনসিভি (OpenCV) প্রযুক্তির মাধ্যমে ক্যামেরার সামনে থাকা চাল, ডাল, বীজ বা ট্যাবলেট গণনা করে।"
                    } else {
                        "ONTIK Count AI processes frames 100% offline using OpenCV to count grains and seeds passing through the camera zone."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(22.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OntikGreenPrimary),
                    modifier = Modifier.height(52.dp)
                ) {
                    Text(
                        text = if (isBn) "ক্যামেরা পারমিশন দিন" else "Grant Camera Permission",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Top Translucent Glass Bar
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xCC0F172A),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "ONTIK Count AI",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                maxLines = 1
                            )
                            StatusChipWithDot(status = uiState.status, lang = lang)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            IconButton(onClick = onNavigateToHome, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.Home, contentDescription = "Home Dashboard", tint = Color.White)
                            }
                            IconButton(onClick = viewModel::toggleTorch, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    imageVector = if (uiState.torchEnabled) OntikIcons.FlashOn else OntikIcons.FlashOff,
                                    contentDescription = "Torch Toggle",
                                    tint = if (uiState.torchEnabled) OntikAmberAccent else Color.White
                                )
                            }
                            IconButton(
                                onClick = { viewModel.updateShowDebugMask(!settings.showDebugMask) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = OntikIcons.BugMask,
                                    contentDescription = "Debug Mask",
                                    tint = if (settings.showDebugMask) OntikGreenBright else Color.White.copy(alpha = 0.75f)
                                )
                            }
                            IconButton(onClick = onNavigateToHistory, modifier = Modifier.size(34.dp)) {
                                Icon(OntikIcons.History, contentDescription = "History", tint = Color.White)
                            }
                            IconButton(onClick = onNavigateToProfile, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.Person, contentDescription = "Profile", tint = Color.White)
                            }
                            IconButton(onClick = onNavigateToSettings, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Telemetry Sub-Row: FPS, Confidence Badge, Plan Chip, 100-Test Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val fpsColor = if (uiState.lowFpsWarning) DangerRed else OntikGreenBright
                            BadgePill("${uiState.currentFps} FPS", fpsColor, fpsColor.copy(alpha = 0.16f))

                            val confColor = when (uiState.confidenceBadge) {
                                AccuracyConfidenceLevel.GOOD -> OntikGreenBright
                                AccuracyConfidenceLevel.FAIR -> OntikAmberAccent
                                AccuracyConfidenceLevel.POOR -> DangerRed
                            }
                            BadgePill(uiState.confidenceBadge.label(lang), confColor, confColor.copy(alpha = 0.16f))

                            // Plan Badge (Click to open Paywall if Free)
                            Surface(
                                color = if (planState.isEffectivePro) OntikAmberAccent else Color.White.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable {
                                    if (!planState.isEffectivePro) onNavigateToPaywall() else onNavigateToProfile()
                                }
                            ) {
                                Text(
                                    text = if (planState.isEffectivePro) "PRO" else "Today: ${planState.todayUsedCount}/${planState.freeDailyLimit}",
                                    color = if (planState.isEffectivePro) Color.Black else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Surface(
                            onClick = viewModel::toggleTestMode100,
                            shape = RoundedCornerShape(10.dp),
                            color = if (uiState.isTestMode100Active) OntikAmberAccent else Color.White.copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = OntikIcons.ScienceTest,
                                    contentDescription = "100-Grain Test Mode",
                                    tint = if (uiState.isTestMode100Active) Color.Black else Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (isBn) "১০০ টেস্ট" else "100 Test",
                                    color = if (uiState.isTestMode100Active) Color.Black else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Free Daily Limit Progress Bar ("Today: 320 / 500")
                    if (!planState.isEffectivePro) {
                        Spacer(modifier = Modifier.height(5.dp))
                        val quotaProgress = (planState.todayUsedCount.toFloat() / planState.freeDailyLimit.coerceAtLeast(1)).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { quotaProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = if (quotaProgress >= 0.9f) DangerRed else OntikGreenBright,
                            trackColor = Color.White.copy(alpha = 0.18f)
                        )
                    }

                    if (uiState.status == EngineStatus.CALIBRATING) {
                        Spacer(modifier = Modifier.height(5.dp))
                        LinearProgressIndicator(
                            progress = { uiState.calibrationProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = OntikAmberAccent,
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )
                    }
                }
            }

            // Offline Grace Period Expired Warning or Contextual Hints
            val graceExpiredBanner = if (planState.requiresOnlineVerification) {
                if (isBn) "আপনার প্রো প্ল্যান যাচাই করতে ইন্টারনেটে যুক্ত হোন (৭ দিনের অফলাইন গ্রেস শেষ)"
                else "Connect to internet to verify your PRO plan (7-day offline grace period ended)"
            } else null

            val activeHint = if (isBn) uiState.contextualHintBn else uiState.contextualHintEn
            val mismatchHint = if (uiState.verificationMismatchWarning) {
                if (isBn) "সতর্কতা: লাইন A (${uiState.autoCountLineA}) ও লাইন B (${uiState.autoCountLineB}) এর সংখ্যায় পার্থক্য"
                else "Verification Mismatch: Line A (${uiState.autoCountLineA}) vs Line B (${uiState.autoCountLineB})"
            } else null

            val bannerText = graceExpiredBanner ?: mismatchHint ?: activeHint
            AnimatedVisibility(visible = bannerText != null, enter = fadeIn(), exit = fadeOut()) {
                Surface(
                    color = Color(0xD91E293B),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, OntikAmberAccent.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = bannerText ?: "",
                        color = OntikAmberAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }

        // Bottom Glassmorphism Sheet Card
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xD90F172A),
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Batch Mode Subtotals Row (if Batch Mode enabled)
                    if (settings.batchModeEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isBn) {
                                    "ব্যাচ: ${uiState.batches.size} টি • সর্বমোট (Grand Total): ${uiState.batchGrandTotal}"
                                } else {
                                    "Batches: ${uiState.batches.size} • Grand Total: ${uiState.batchGrandTotal}"
                                },
                                color = OntikAmberAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(
                                onClick = viewModel::saveCurrentBatchAndResetCounter,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(
                                    text = if (isBn) "+ ব্যাচ যোগ" else "+ Next Batch",
                                    color = OntikGreenBright,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // Target Count Progress Ring (Phase 6.1)
                            if (settings.targetCountGoal > 0) {
                                val targetProg = (uiState.totalCount.toFloat() / settings.targetCountGoal).coerceIn(0f, 1f)
                                Box(contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(
                                        progress = { targetProg },
                                        modifier = Modifier.size(54.dp),
                                        color = OntikAmberAccent,
                                        trackColor = Color.White.copy(alpha = 0.15f),
                                        strokeWidth = 5.dp
                                    )
                                    Text(
                                        text = "${(targetProg * 100).toInt()}%",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = if (settings.targetCountGoal > 0) {
                                        if (isBn) "টার্গেট: ${uiState.totalCount} / ${settings.targetCountGoal}"
                                        else "TARGET: ${uiState.totalCount} / ${settings.targetCountGoal}"
                                    } else {
                                        AppStrings.totalCountHeader(lang, uiState.isTestMode100Active)
                                    },
                                    color = if (uiState.isTestMode100Active) OntikAmberAccent else OntikGreenBright,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                AnimatedContent(
                                    targetState = uiState.totalCount,
                                    transitionSpec = {
                                        (slideInVertically { h -> h / 2 } + fadeIn()).togetherWith(
                                            slideOutVertically { h -> -h / 2 } + fadeOut()
                                        )
                                    },
                                    label = "giantCountRoll"
                                ) { targetCount ->
                                    Text(
                                        text = "$targetCount",
                                        fontSize = 64.sp,
                                        lineHeight = 68.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        modifier = Modifier.scale(countBounceScale)
                                    )
                                }

                                Text(
                                    text = AppStrings.subStatsLine(
                                        lang = lang,
                                        inZone = uiState.inZoneGrainCount,
                                        ratePerSec = uiState.countRatePerSec
                                    ),
                                    color = Color.White.copy(alpha = 0.78f),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Undo, -1, +1 Buttons
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledIconButton(
                                onClick = viewModel::undoLastCount,
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.14f),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(OntikIcons.Undo, contentDescription = "Undo Last Count")
                            }

                            FilledIconButton(
                                onClick = { viewModel.adjustCountManually(-1) },
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.14f),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(OntikIcons.Minus, contentDescription = "Minus 1")
                            }

                            FilledIconButton(
                                onClick = { viewModel.adjustCountManually(1) },
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = OntikGreenPrimary,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Plus 1")
                            }
                        }
                    }

                    // Primary Action Row: Start/Pause, Auto Calibrate, Reset, Save
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isCounting = uiState.status == EngineStatus.COUNTING
                        Button(
                            onClick = viewModel::toggleStartPause,
                            modifier = Modifier
                                .weight(1.3f)
                                .height(50.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isCounting) OntikAmberDark else OntikGreenPrimary
                            )
                        ) {
                            Icon(
                                imageVector = if (isCounting) OntikIcons.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isCounting) AppStrings.btnPause(lang) else AppStrings.btnStart(lang),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = viewModel::triggerAutoCalibrate,
                            modifier = Modifier
                                .weight(1.15f)
                                .height(50.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, OntikAmberAccent)
                        ) {
                            Icon(
                                imageVector = OntikIcons.CalibrateTarget,
                                contentDescription = null,
                                tint = OntikAmberAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = AppStrings.btnAutoCalibrate(lang),
                                color = OntikAmberAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        FilledIconButton(
                            onClick = {
                                if (uiState.totalCount > 0) {
                                    showResetConfirmDialog = true
                                } else {
                                    viewModel.resetCount()
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.White.copy(alpha = 0.14f),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.size(50.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = AppStrings.btnReset(lang))
                        }

                        FilledIconButton(
                            onClick = {
                                val saved = viewModel.saveSessionToHistory()
                                if (saved != null) {
                                    val msg = if (isBn) {
                                        "ইতিহাসে সেভ হয়েছে (মোট: ${saved.finalCount})"
                                    } else {
                                        "Saved to History (Total: ${saved.finalCount})"
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = OntikGreenPrimary,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.size(50.dp)
                        ) {
                            Icon(OntikIcons.BookmarkCheck, contentDescription = AppStrings.btnSave(lang))
                        }
                    }
                }
            }
        }

        // Reset Confirmation Dialog
        if (showResetConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showResetConfirmDialog = false },
                title = { Text(AppStrings.resetDialogTitle(lang), fontWeight = FontWeight.Bold) },
                text = { Text(AppStrings.resetDialogBody(lang, uiState.totalCount)) },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.resetCount()
                            showResetConfirmDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                    ) {
                        Text(AppStrings.confirmYes(lang))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetConfirmDialog = false }) {
                        Text(AppStrings.confirmCancel(lang))
                    }
                }
            )
        }

        // Target Count Reached Dialog (Phase 6.1: Save / Continue / Reset)
        if (uiState.targetGoalReachedDialogVisible) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissTargetReachedDialog() },
                title = {
                    Text(
                        text = if (isBn) "টার্গেট পূর্ণ হয়েছে! (${uiState.totalCount})" else "Target Reached! (${uiState.totalCount})",
                        fontWeight = FontWeight.ExtraBold,
                        color = OntikGreenPrimary
                    )
                },
                text = {
                    Text(
                        if (isBn) "আপনার নির্ধারিত ${settings.targetCountGoal} দানার লক্ষ্য পূর্ণ হয়েছে এবং গণনা বিরতি দেওয়া হয়েছে।"
                        else "You have reached your target of ${settings.targetCountGoal} grains. Counting is paused."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.saveSessionToHistory()
                            viewModel.dismissTargetReachedDialog()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OntikGreenPrimary)
                    ) {
                        Text(if (isBn) "সেভ করুন" else "Save")
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                viewModel.dismissTargetReachedDialog()
                                viewModel.startCounting()
                            }
                        ) {
                            Text(if (isBn) "চালিয়ে যান" else "Continue")
                        }
                        TextButton(
                            onClick = {
                                viewModel.dismissTargetReachedDialog()
                                viewModel.resetCount()
                            }
                        ) {
                            Text(if (isBn) "রিসেট" else "Reset")
                        }
                    }
                }
            )
        }

        // Free Daily Limit Reached Paywall Prompt (Never loses current count!)
        if (uiState.dailyLimitHitPaywallPromptVisible) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissDailyLimitPaywallPrompt() },
                title = {
                    Text(
                        text = if (isBn) "আজকের ফ্রি লিমিট (${planState.freeDailyLimit}) শেষ হয়েছে" else "Daily Free Limit (${planState.freeDailyLimit}) Reached",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        if (isBn) "আপনার বর্তমান গণনা (${uiState.totalCount}) সুরক্ষিত আছে। আনলিমিটেড গণনা চালিয়ে যেতে ONTIK PRO আনলক করুন।"
                        else "Your current count (${uiState.totalCount}) is safely preserved. Upgrade to ONTIK PRO for unlimited daily counting."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.dismissDailyLimitPaywallPrompt()
                            onNavigateToPaywall()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OntikGreenPrimary)
                    ) {
                        Text(if (isBn) "PRO আনলক করুন" else "Unlock PRO")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissDailyLimitPaywallPrompt() }) {
                        Text(if (isBn) "পরে" else "Later")
                    }
                }
            )
        }

        // First-Launch Onboarding + GDPR Analytics Consent
        if (!settings.onboardingCompleted) {
            OnboardingOverlayDialog(
                lang = lang,
                onFinish = {
                    viewModel.completeOnboarding()
                    viewModel.triggerAutoCalibrate()
                }
            )
        } else if (!settings.analyticsConsentAsked) {
            AlertDialog(
                onDismissRequest = { viewModel.setAnalyticsConsent(false) },
                title = {
                    Text(
                        text = if (isBn) "অ্যাপের মান উন্নয়নে সহযোগিতা (Privacy Consent)" else "Analytics & Crash Diagnostics Consent",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        if (isBn) "আমরা কি অ্যাপের ক্র্যাশ রিপোর্ট ও পারফরম্যান্স উন্নত করতে বেনামী (Anonymous) ডায়াগনস্টিক ডেটা ব্যবহার করতে পারি? আপনি সেটিংস থেকে যেকোনো সময় এটি পরিবর্তন করতে পারবেন।"
                        else "May we collect anonymous crash and performance diagnostics to improve counting accuracy? No personal images are ever shared. You can toggle this anytime in Settings."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.setAnalyticsConsent(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = OntikGreenPrimary)
                    ) {
                        Text(if (isBn) "অনুমতি দিন (Allow)" else "Allow")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.setAnalyticsConsent(false) }) {
                        Text(if (isBn) "না, ধন্যবাদ" else "Decline")
                    }
                }
            )
        }
    }
}

@Composable
private fun StatusChipWithDot(status: EngineStatus, lang: AppLanguage) {
    val (dotColor, label) = when (status) {
        EngineStatus.CALIBRATING -> OntikAmberAccent to AppStrings.statusCalibrating(lang)
        EngineStatus.READY -> Color(0xFF38BDF8) to AppStrings.statusReady(lang)
        EngineStatus.COUNTING -> OntikGreenBright to AppStrings.statusCounting(lang)
        EngineStatus.PAUSED -> OntikAmberDark to AppStrings.statusPaused(lang)
    }
    Surface(
        color = dotColor.copy(alpha = 0.18f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = label,
                color = dotColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun BadgePill(text: String, textColor: Color, bgColor: Color) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun OnboardingOverlayDialog(
    lang: AppLanguage,
    onFinish: () -> Unit
) {
    var slideIndex by remember { mutableIntStateOf(0) }

    val titles = listOf(
        AppStrings.onboardingSlide1Title(lang),
        AppStrings.onboardingSlide2Title(lang),
        AppStrings.onboardingSlide3Title(lang)
    )
    val bodies = listOf(
        AppStrings.onboardingSlide1Body(lang),
        AppStrings.onboardingSlide2Body(lang),
        AppStrings.onboardingSlide3Body(lang)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (lang == AppLanguage.BANGLA) "কীভাবে ব্যবহার করবেন (${slideIndex + 1}/3)" else "Quick Guide (${slideIndex + 1}/3)",
                    color = OntikGreenPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = titles[slideIndex],
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = bodies[slideIndex],
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onFinish) {
                        Text(if (lang == AppLanguage.BANGLA) "স্কিপ করুন" else "Skip")
                    }

                    Button(
                        onClick = {
                            if (slideIndex < 2) {
                                slideIndex++
                            } else {
                                onFinish()
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OntikGreenPrimary)
                    ) {
                        Text(
                            text = if (slideIndex < 2) {
                                if (lang == AppLanguage.BANGLA) "পরবর্তী" else "Next"
                            } else {
                                if (lang == AppLanguage.BANGLA) "শুরু করুন" else "Start Now"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
