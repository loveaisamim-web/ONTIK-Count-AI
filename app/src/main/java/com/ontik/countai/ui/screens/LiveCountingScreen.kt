package com.ontik.countai.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
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
import com.ontik.countai.domain.model.AppLanguage
import com.ontik.countai.ui.components.AdMobBannerCard
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
import java.util.Locale

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
    val view = LocalView.current
    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val lang = settings.language
    val isBn = lang == AppLanguage.BANGLA

    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }

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
        viewModel.refreshOnlineStatus()
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

    val isPulsing = System.currentTimeMillis() - uiState.lastCountPulseTimestampMs < 180L
    val numberScale by animateFloatAsState(
        targetValue = if (isPulsing) 1.08f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "countPulse"
    )

    val statusColor = when (uiState.status) {
        EngineStatus.CALIBRATING -> OntikAmberAccent
        EngineStatus.READY -> Color(0xFF38BDF8)
        EngineStatus.COUNTING -> OntikGreenBright
        EngineStatus.PAUSED -> OntikAmberDark
    }

    val statusLabel = when (uiState.status) {
        EngineStatus.CALIBRATING -> AppStrings.statusCalibrating(lang)
        EngineStatus.READY -> AppStrings.statusReady(lang)
        EngineStatus.COUNTING -> AppStrings.statusCounting(lang)
        EngineStatus.PAUSED -> AppStrings.statusPaused(lang)
    }

    // See-through glassmorphic background (alpha ~ 0.42f) so camera is clearly visible through all boxes!
    val clearGlassBg = Color(0x6B0F172A)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F14))
    ) {
        // 1. Full-Screen CameraX + OpenCV Overlay
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
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isBn) "শস্য গণনার জন্য ক্যামেরা পারমিশন প্রয়োজন" else "Camera Permission Required for Counting",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = OntikGreenPrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isBn) "ক্যামেরা চালু করুন (Allow Camera)" else "Allow Camera Access")
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        } catch (_: Throwable) {
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isBn) "অ্যাপ সেটিংস থেকে পারমিশন দিন (Open App Settings)" else "Open App Settings",
                        color = Color.White
                    )
                }
            }
        }

        // 2. Fixed-Size See-Through Top Bar (Never resizes when changing items!)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = clearGlassBg),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
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
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Surface(
                                color = statusColor.copy(alpha = 0.22f),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.75f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(statusColor)
                                    )
                                    Text(
                                        text = statusLabel,
                                        color = statusColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onNavigateToHome, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Home, contentDescription = "Home", tint = Color.White)
                            }
                            IconButton(onClick = viewModel::toggleTorch, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    imageVector = if (uiState.torchEnabled) OntikIcons.FlashOn else OntikIcons.FlashOff,
                                    contentDescription = "Torch",
                                    tint = if (uiState.torchEnabled) OntikAmberAccent else Color.White
                                )
                            }
                            IconButton(
                                onClick = { viewModel.updateShowDebugMask(!settings.showDebugMask) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = OntikIcons.BugMask,
                                    contentDescription = "Debug Mask",
                                    tint = if (settings.showDebugMask) OntikGreenBright else Color.White
                                )
                            }
                            IconButton(onClick = onNavigateToHistory, modifier = Modifier.size(32.dp)) {
                                Icon(OntikIcons.History, contentDescription = "History", tint = Color.White)
                            }
                            IconButton(onClick = onNavigateToProfile, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Person, contentDescription = "Profile", tint = Color.White)
                            }
                            IconButton(onClick = onNavigateToSettings, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Row 2: Fixed single-line short preset title + FPS + Self-Test + 100 Test (Never wraps or resizes!)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            BadgePill(
                                text = settings.activePreset.shortTitle(lang),
                                textColor = OntikGreenBright,
                                bgColor = OntikGreenPrimary.copy(alpha = 0.28f)
                            )
                            BadgePill(
                                text = "${uiState.currentFps} FPS",
                                textColor = Color.White,
                                bgColor = Color.White.copy(alpha = 0.16f)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                onClick = { showDiagnosticsDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = if (isBn) "টেস্ট ইনফো" else "Self-Test",
                                    color = OntikAmberAccent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            Surface(
                                onClick = viewModel::toggleTestMode100,
                                shape = RoundedCornerShape(8.dp),
                                color = if (uiState.isTestMode100Active) OntikAmberAccent else Color.White.copy(alpha = 0.16f)
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
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = if (isBn) "১০০ টেস্ট" else "100 Test",
                                        color = if (uiState.isTestMode100Active) Color.Black else Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.status == EngineStatus.CALIBRATING) {
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { uiState.calibrationProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = OntikAmberAccent,
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )
                    }
                }
            }

            // Helpful single-line hint (See-through)
            val activeHint = if (isBn) uiState.contextualHintBn else uiState.contextualHintEn
            AnimatedVisibility(
                visible = activeHint != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = clearGlassBg),
                    border = BorderStroke(1.dp, OntikAmberAccent.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = activeHint ?: "",
                        color = OntikAmberAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // 3. See-Through Bottom Counter Card + AdMob Banner
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (settings.batchModeEnabled) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = clearGlassBg),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isBn) {
                                "ব্যাচ: ${uiState.batches.size} টি • সর্বমোট: ${uiState.batchGrandTotal}"
                            } else {
                                "Batches: ${uiState.batches.size} • Grand Total: ${uiState.batchGrandTotal}"
                            },
                            color = OntikAmberAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(
                                onClick = viewModel::saveCurrentBatchAndResetCounter,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(if (isBn) "+ ব্যাচ যোগ" else "+ Add Batch", color = OntikGreenBright, fontSize = 12.sp)
                            }
                            if (uiState.batches.isNotEmpty()) {
                                TextButton(
                                    onClick = viewModel::clearAllBatches,
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text(if (isBn) "ক্লিয়ার" else "Clear", color = DangerRed, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = clearGlassBg),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = AppStrings.totalCountHeader(lang, uiState.isTestMode100Active),
                                color = OntikGreenBright,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )

                            AnimatedContent(
                                targetState = uiState.totalCount,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "giantTotal"
                            ) { countVal ->
                                Text(
                                    text = "$countVal",
                                    color = Color.White,
                                    fontSize = 48.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    lineHeight = 50.sp,
                                    modifier = Modifier.scale(numberScale)
                                )
                            }

                            Text(
                                text = AppStrings.subStatsLine(
                                    lang = lang,
                                    inZone = uiState.inZoneGrainCount,
                                    ratePerSec = uiState.countRatePerSec
                                ),
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledIconButton(
                                onClick = viewModel::undoLastCount,
                                modifier = Modifier.size(40.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.18f),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(OntikIcons.Undo, contentDescription = "Undo")
                            }

                            FilledIconButton(
                                onClick = { viewModel.adjustCountManually(-1) },
                                modifier = Modifier.size(42.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.20f),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(OntikIcons.Minus, contentDescription = "-1")
                            }

                            FilledIconButton(
                                onClick = { viewModel.adjustCountManually(+1) },
                                modifier = Modifier.size(42.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = OntikGreenPrimary,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "+1")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isCounting = uiState.status == EngineStatus.COUNTING
                        Button(
                            onClick = viewModel::toggleStartPause,
                            modifier = Modifier
                                .weight(1.25f)
                                .height(44.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isCounting) OntikAmberDark else OntikGreenPrimary,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(
                                imageVector = if (isCounting) OntikIcons.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isCounting) AppStrings.btnPause(lang) else AppStrings.btnStart(lang),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                        }

                        OutlinedButton(
                            onClick = viewModel::triggerAutoCalibrate,
                            modifier = Modifier
                                .weight(1.15f)
                                .height(44.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, OntikAmberAccent),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = OntikIcons.CalibrateTarget,
                                contentDescription = null,
                                tint = OntikAmberAccent,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = AppStrings.btnAutoCalibrate(lang),
                                color = OntikAmberAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1
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
                            modifier = Modifier.size(44.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.White.copy(alpha = 0.18f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = AppStrings.btnReset(lang))
                        }

                        FilledIconButton(
                            onClick = {
                                val saved = viewModel.saveSessionToHistory()
                                val msg = if (saved != null) {
                                    if (isBn) "হিস্ট্রিতে সেভ হয়েছে (${saved.finalCount})" else "Saved to History (${saved.finalCount})"
                                } else {
                                    if (isBn) "সেভ করার জন্য অন্তত ১টি গণনা প্রয়োজন" else "Count at least 1 grain to save"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(44.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = OntikGreenPrimary.copy(alpha = 0.85f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(OntikIcons.BookmarkCheck, contentDescription = AppStrings.btnSave(lang))
                        }
                    }
                }
            }

            // AdMob Banner Ad on Live Counting Screen too as requested ("অ্যাডস সব জায়গায় থাকবে")
            AdMobBannerCard(isVisible = true)
        }

        // 4. 100% Online Requirement Dialog
        if (!uiState.isOnline) {
            AlertDialog(
                onDismissRequest = { viewModel.refreshOnlineStatus() },
                title = {
                    Text(
                        text = if (isBn) "ইন্টারনেট সংযোগ প্রয়োজন" else "Internet Connection Required",
                        color = DangerRed,
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                text = {
                    Text(
                        text = if (isBn) {
                            "ONTIK Count AI সম্পূর্ণ অনলাইনভিত্তিক। গণনা শুরু বা চালিয়ে যেতে অনুগ্রহ করে আপনার ফোনের Wi-Fi অথবা Mobile Data চালু করুন।"
                        } else {
                            "ONTIK Count AI requires an active internet connection. Please turn on Wi-Fi or Mobile Data to continue counting."
                        }
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val ok = viewModel.refreshOnlineStatus()
                            if (!ok) {
                                Toast.makeText(
                                    context,
                                    if (isBn) "এখনো ইন্টারনেট সংযোগ পাওয়া যায়নি" else "Still offline — please enable Wi-Fi or Mobile Data",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OntikGreenPrimary)
                    ) {
                        Text(if (isBn) "পুনরায় চেক করুন (Retry)" else "Retry Connection")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                            } catch (_: Throwable) {
                            }
                        }
                    ) {
                        Text(if (isBn) "ওয়াই-ফাই সেটিংস" else "Wi-Fi Settings")
                    }
                }
            )
        }

        // 5. Self-Test / Diagnostics Dialog
        if (showDiagnosticsDialog) {
            AlertDialog(
                onDismissRequest = { showDiagnosticsDialog = false },
                title = {
                    Text(
                        text = if (isBn) "সেলফ-টেস্ট ও ডায়াগনস্টিকস" else "Self-Test & Live Diagnostics",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val oneGrainStatus = when {
                            !uiState.isCalibrated -> if (isBn) "আগে খালি জায়গায় ক্যালিব্রেট চাপুন" else "Tap Calibrate on empty surface first"
                            uiState.inZoneGrainCount == 1 -> if (isBn) "সফল: বক্সে ১টি দানা স্পষ্টভাবে শনাক্ত হয়েছে!" else "PASS: 1 Grain clearly detected in zone!"
                            uiState.inZoneGrainCount == 0 -> if (isBn) "বক্স খালি (পরীক্ষার জন্য বক্সে ১টি দানা রাখুন)" else "Zone empty (place 1 grain inside box to test)"
                            else -> if (isBn) "বক্সে বর্তমান দানার সংখ্যা: ${uiState.inZoneGrainCount} টি" else "Grains currently in zone: ${uiState.inZoneGrainCount}"
                        }
                        Surface(
                            color = OntikGreenPrimary.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = oneGrainStatus,
                                color = OntikGreenPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        DiagRow("OpenCV Engine", if (uiState.openCvReady) "Loaded (v4.9.0)" else "Failed")
                        DiagRow("Internet Status", if (uiState.isOnline) "Online (Connected)" else "Offline")
                        DiagRow("Camera FPS / Frame Time", "${uiState.currentFps} FPS (${uiState.processingTimeMs} ms)")
                        DiagRow("Calibration State", if (uiState.isCalibrated) "Calibrated (AE/AF Locked)" else "Calibrating...")
                        DiagRow("Surface Brightness / Noise", String.format(Locale.US, "Lum: %.1f | Noise: %.2f", uiState.meanLuminance, uiState.noiseStd))
                        DiagRow("Detection Threshold", String.format(Locale.US, "Auto: %.1f | Effective: %.1f", uiState.autoThresholdValue, uiState.effectiveThresholdValue))
                        DiagRow("Learned Single Grain Size", String.format(Locale.US, "%.0f px²", uiState.learnedSingleGrainAreaPx))
                        DiagRow("Raw Contours -> Valid Blobs", "${uiState.rawContourCount} -> ${uiState.inZoneGrainCount}")
                        DiagRow("Active Kalman Tracks", "${uiState.trackedGrains.size}")
                        DiagRow("Total Counted", "${uiState.totalCount}")
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showDiagnosticsDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = OntikGreenPrimary)
                    ) {
                        Text(if (isBn) "বন্ধ করুন" else "Close")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            viewModel.updateShowDebugMask(!settings.showDebugMask)
                        }
                    ) {
                        Text(if (settings.showDebugMask) (if (isBn) "মাস্ক লুকান" else "Hide Mask") else (if (isBn) "মাস্ক দেখুন" else "Show Mask"))
                    }
                }
            )
        }

        // 6. Reset Confirmation Dialog
        if (showResetConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showResetConfirmDialog = false },
                title = { Text(AppStrings.resetDialogTitle(lang)) },
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

        // 7. Target Count Goal Reached Dialog
        if (uiState.targetGoalReachedDialogVisible && settings.targetCountGoal > 0) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissTargetReachedDialog() },
                title = {
                    Text(
                        text = if (isBn) "টার্গেট পূর্ণ হয়েছে! (${settings.targetCountGoal})" else "Target Reached! (${settings.targetCountGoal})",
                        fontWeight = FontWeight.ExtraBold,
                        color = OntikGreenPrimary
                    )
                },
                text = {
                    Text(
                        if (isBn) "আপনার নির্ধারিত ${settings.targetCountGoal}টি দানা গণনা সম্পন্ন হয়েছে (বর্তমান: ${uiState.totalCount})।"
                        else "You have reached your target goal of ${settings.targetCountGoal} grains (Current: ${uiState.totalCount})."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.dismissTargetReachedDialog()
                            viewModel.startCounting()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OntikGreenPrimary)
                    ) {
                        Text(if (isBn) "গণনা চালিয়ে যান" else "Continue Counting")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            viewModel.dismissTargetReachedDialog()
                            viewModel.resetCount()
                        }
                    ) {
                        Text(if (isBn) "রিসেট" else "Reset")
                    }
                }
            )
        }

        // 8. First-Launch Onboarding
        if (!settings.onboardingCompleted) {
            OnboardingOverlayDialog(
                lang = lang,
                onFinish = {
                    viewModel.completeOnboarding()
                    viewModel.triggerAutoCalibrate()
                }
            )
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BadgePill(
    text: String,
    textColor: Color,
    bgColor: Color
) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun OnboardingOverlayDialog(
    lang: AppLanguage,
    onFinish: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    val title = when (step) {
        0 -> AppStrings.onboardingSlide1Title(lang)
        1 -> AppStrings.onboardingSlide2Title(lang)
        else -> AppStrings.onboardingSlide3Title(lang)
    }
    val body = when (step) {
        0 -> AppStrings.onboardingSlide1Body(lang)
        1 -> AppStrings.onboardingSlide2Body(lang)
        else -> AppStrings.onboardingSlide3Body(lang)
    }

    AlertDialog(
        onDismissRequest = onFinish,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(body, fontSize = 15.sp, lineHeight = 22.sp) },
        confirmButton = {
            Button(
                onClick = {
                    if (step < 2) step++ else onFinish()
                },
                colors = ButtonDefaults.buttonColors(containerColor = OntikGreenPrimary)
            ) {
                Text(
                    if (step < 2) {
                        if (lang == AppLanguage.BANGLA) "পরবর্তী (Next)" else "Next"
                    } else {
                        if (lang == AppLanguage.BANGLA) "শুরু করুন" else "Get Started"
                    }
                )
            }
        },
        dismissButton = {
            if (step < 2) {
                TextButton(onClick = onFinish) {
                    Text(if (lang == AppLanguage.BANGLA) "স্কিপ" else "Skip")
                }
            }
        }
    )
}
