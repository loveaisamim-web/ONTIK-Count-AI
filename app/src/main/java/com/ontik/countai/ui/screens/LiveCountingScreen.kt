package com.ontik.countai.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ontik.countai.domain.model.ModelEngineState
import com.ontik.countai.ui.components.CameraPreviewWithZoneOverlay
import com.ontik.countai.ui.theme.CardBorderGray
import com.ontik.countai.ui.theme.OntikGreenDark
import com.ontik.countai.ui.theme.OntikGreenLight
import com.ontik.countai.ui.theme.OntikGreenPrimary
import com.ontik.countai.ui.theme.SoftGraySurface
import com.ontik.countai.ui.theme.TextPrimaryDark
import com.ontik.countai.ui.theme.TextSecondarySlate
import com.ontik.countai.ui.theme.WarningAmber
import com.ontik.countai.ui.theme.WarningAmberBg
import com.ontik.countai.ui.theme.WhiteBackground
import com.ontik.countai.ui.viewmodel.CountViewModel
import com.ontik.countai.ui.viewmodel.LiveCountingState

@Composable
fun LiveCountingScreen(
    viewModel: CountViewModel,
    onNavigateToPhoto: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val liveState by viewModel.liveState.collectAsState()
    val settings by viewModel.settings.collectAsState()

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

    // Haptic pulse when a new object crosses the virtual counting line
    LaunchedEffect(liveState.lastCountPulseTimestamp) {
        if (liveState.lastCountPulseTimestamp > 0L && settings.hapticFeedbackEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    val countScale by animateFloatAsState(
        targetValue = if (System.currentTimeMillis() - liveState.lastCountPulseTimestamp < 180L) 1.08f else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "countScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WhiteBackground)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Header Bar: App Name, Live Status Pill, Shortcuts
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "ONTIK Count AI",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimaryDark
                    )
                    StatusBadge(countingState = liveState.countingState)
                }
                Text(
                    text = "${settings.countingDirection.displayName} • Conf ${(settings.confidenceThreshold * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondarySlate
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onNavigateToPhoto) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Photo Counting Mode",
                        tint = OntikGreenPrimary
                    )
                }
                IconButton(onClick = onNavigateToHistory) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Counting History",
                        tint = TextPrimaryDark
                    )
                }
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings & Model",
                        tint = TextPrimaryDark
                    )
                }
            }
        }

        // Model / Confidence Diagnostic Banner
        AnimatedVisibility(visible = liveState.statusBannerMessage != null) {
            val isMissingModel = liveState.modelEngineState is ModelEngineState.MissingModelFile
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isMissingModel) WarningAmberBg else OntikGreenLight
                ),
                border = BorderStroke(
                    1.dp,
                    if (isMissingModel) WarningAmber.copy(alpha = 0.4f) else OntikGreenPrimary.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = liveState.statusBannerMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isMissingModel) WarningAmber else OntikGreenDark,
                        modifier = Modifier.weight(1f)
                    )
                    if (isMissingModel) {
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onNavigateToSettings,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Import Model", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Live CameraX Preview with Adjustable Zone & Virtual Counting Line
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, CardBorderGray)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (hasCameraPermission) {
                    CameraPreviewWithZoneOverlay(
                        modifier = Modifier.fillMaxSize(),
                        useBackCamera = settings.useBackCamera,
                        torchEnabled = liveState.torchEnabled,
                        countingZone = settings.countingZone,
                        countingLinePosition = settings.countingLinePosition,
                        countingDirection = settings.countingDirection,
                        isAdjustingZone = liveState.isAdjustingZone,
                        trackedObjects = liveState.trackedObjects,
                        onFrameAvailable = viewModel::processCameraFrame,
                        onZoneAndLineUpdated = viewModel::updateZoneAndLine
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SoftGraySurface)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Camera Permission Required",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimaryDark
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "ONTIK Count AI needs camera access to detect and track physical objects passing through the counting zone in real time.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(containerColor = OntikGreenPrimary)
                        ) {
                            Text("Grant Camera Permission")
                        }
                    }
                }

                // Floating Camera Controls (Torch + Zone Edit Toggle)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledIconButton(
                        onClick = viewModel::toggleZoneAdjustmentMode,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (liveState.isAdjustingZone) OntikGreenPrimary else Color.Black.copy(alpha = 0.55f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.CropFree,
                            contentDescription = "Adjust Counting Zone & Line"
                        )
                    }

                    FilledIconButton(
                        onClick = viewModel::toggleTorch,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (liveState.torchEnabled) OntikGreenPrimary else Color.Black.copy(alpha = 0.55f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = if (liveState.torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Toggle Camera Flash"
                        )
                    }
                }

                // Bottom-left real-time telemetry badge inside preview
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                    color = Color.Black.copy(alpha = 0.60f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (liveState.isAdjustingZone) {
                            "Drag zone corners or dashed line to adjust"
                        } else {
                            "In Zone: ${liveState.currentlyDetectedInZone} • ${liveState.inferenceTimeMs} ms"
                        },
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // Prominent Total Count & Manual Correction Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SoftGraySurface),
            border = BorderStroke(1.dp, CardBorderGray)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "TOTAL COUNT",
                        style = MaterialTheme.typography.labelLarge,
                        color = OntikGreenDark
                    )
                    Text(
                        text = "${liveState.totalCount}",
                        style = MaterialTheme.typography.displayLarge,
                        color = TextPrimaryDark,
                        modifier = Modifier.scale(countScale)
                    )
                    Text(
                        text = "In Zone Now: ${liveState.currentlyDetectedInZone}  |  Manual: ${if (liveState.manualDelta >= 0) "+${liveState.manualDelta}" else "${liveState.manualDelta}"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondarySlate
                    )
                }

                // Manual Correction (-1 / +1) & Save Session Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledIconButton(
                        onClick = { viewModel.adjustLiveCountManually(-1) },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = WhiteBackground,
                            contentColor = TextPrimaryDark
                        ),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease Count by 1")
                    }

                    FilledIconButton(
                        onClick = { viewModel.adjustLiveCountManually(1) },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = OntikGreenLight,
                            contentColor = OntikGreenDark
                        ),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase Count by 1")
                    }

                    FilledIconButton(
                        onClick = { viewModel.saveCurrentLiveSession() },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = OntikGreenPrimary,
                            contentColor = WhiteBackground
                        ),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(Icons.Default.BookmarkAdded, contentDescription = "Save Session to History")
                    }
                }
            }
        }

        // Primary Action Bar: Start / Pause / Resume / Reset / Photo Mode
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (liveState.countingState) {
                LiveCountingState.IDLE -> {
                    Button(
                        onClick = viewModel::startLiveCounting,
                        modifier = Modifier
                            .weight(1.4f)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OntikGreenPrimary)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Start Counting", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                LiveCountingState.RUNNING -> {
                    Button(
                        onClick = viewModel::pauseLiveCounting,
                        modifier = Modifier
                            .weight(1.4f)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OntikGreenDark)
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pause", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                LiveCountingState.PAUSED -> {
                    Button(
                        onClick = viewModel::resumeLiveCounting,
                        modifier = Modifier
                            .weight(1.4f)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OntikGreenPrimary)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Resume", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }

            OutlinedButton(
                onClick = viewModel::resetLiveCounting,
                modifier = Modifier
                    .weight(0.9f)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, CardBorderGray)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = TextPrimaryDark)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reset", color = TextPrimaryDark, fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = onNavigateToPhoto,
                modifier = Modifier
                    .weight(0.9f)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, OntikGreenPrimary)
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = OntikGreenPrimary)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Photo", color = OntikGreenPrimary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun StatusBadge(countingState: LiveCountingState) {
    val (bg, fg, label) = when (countingState) {
        LiveCountingState.IDLE -> Triple(SoftGraySurface, TextSecondarySlate, "READY")
        LiveCountingState.RUNNING -> Triple(OntikGreenLight, OntikGreenDark, "LIVE")
        LiveCountingState.PAUSED -> Triple(WarningAmberBg, WarningAmber, "PAUSED")
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
