package com.ontik.countai.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ontik.countai.domain.model.AppLanguage
import com.ontik.countai.ui.components.CameraPreviewWithZoneOverlay
import com.ontik.countai.ui.theme.AppStrings
import com.ontik.countai.ui.theme.CardBorderGray
import com.ontik.countai.ui.theme.OntikGreenDark
import com.ontik.countai.ui.theme.OntikGreenLight
import com.ontik.countai.ui.theme.OntikGreenPrimary
import com.ontik.countai.ui.theme.OntikIcons
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
    val lang = settings.language

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
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Top Header Bar with Quick Language Toggle, Photo, History & Settings
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "ONTIK Count AI",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimaryDark
                    )
                    StatusBadge(countingState = liveState.countingState, lang = lang)
                }
                Text(
                    text = "${settings.countingDirection.title(lang)} • Sens ${(settings.confidenceThreshold * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondarySlate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val nextLang = if (lang == AppLanguage.BANGLA) AppLanguage.ENGLISH else AppLanguage.BANGLA
                        viewModel.updateLanguage(nextLang)
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, OntikGreenPrimary)
                ) {
                    Text(
                        text = if (lang == AppLanguage.BANGLA) "EN" else "বাং",
                        color = OntikGreenPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onNavigateToPhoto) {
                    Icon(
                        imageVector = OntikIcons.Camera,
                        contentDescription = "Photo Counting Mode",
                        tint = OntikGreenPrimary
                    )
                }
                IconButton(onClick = onNavigateToHistory) {
                    Icon(
                        imageVector = OntikIcons.History,
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

        // Camera Preview with Zone Overlay
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(18.dp),
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
                            text = AppStrings.cameraPermissionTitle(lang),
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimaryDark
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = AppStrings.cameraPermissionDesc(lang),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(containerColor = OntikGreenPrimary)
                        ) {
                            Text(AppStrings.grantPermissionBtn(lang))
                        }
                    }
                }

                // Floating Camera Controls
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
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
                            imageVector = OntikIcons.Crop,
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
                            imageVector = if (liveState.torchEnabled) OntikIcons.FlashOn else OntikIcons.FlashOff,
                            contentDescription = "Toggle Camera Flash"
                        )
                    }
                }

                // Telemetry Badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (liveState.isAdjustingZone) {
                            AppStrings.dragZoneHint(lang)
                        } else {
                            AppStrings.liveTelemetry(lang, liveState.currentlyDetectedInZone, liveState.inferenceTimeMs)
                        },
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // Total Count Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SoftGraySurface),
            border = BorderStroke(1.dp, CardBorderGray)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = AppStrings.totalCountLabel(lang),
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
                        text = AppStrings.inZoneSummary(lang, liveState.currentlyDetectedInZone, liveState.manualDelta),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondarySlate
                    )
                }

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
                        Icon(OntikIcons.Minus, contentDescription = "Decrease Count by 1", tint = TextPrimaryDark)
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
                        onClick = {
                            val savedTotal = viewModel.saveCurrentLiveSession()
                            if (savedTotal > 0) {
                                Toast.makeText(
                                    context,
                                    AppStrings.sessionSavedToast(lang, savedTotal),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = OntikGreenPrimary,
                            contentColor = WhiteBackground
                        ),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(OntikIcons.BookmarkCheck, contentDescription = "Save Session to History")
                    }
                }
            }
        }

        // Clean Single-Line Primary Action Controls (Protected from System Navigation Bar overlap)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (liveState.countingState) {
                LiveCountingState.IDLE -> {
                    Button(
                        onClick = viewModel::startLiveCounting,
                        modifier = Modifier
                            .weight(1.25f)
                            .height(48.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OntikGreenPrimary)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = AppStrings.startCounting(lang),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                LiveCountingState.RUNNING -> {
                    Button(
                        onClick = viewModel::pauseLiveCounting,
                        modifier = Modifier
                            .weight(1.25f)
                            .height(48.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OntikGreenDark)
                    ) {
                        Icon(OntikIcons.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = AppStrings.pauseCounting(lang),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                LiveCountingState.PAUSED -> {
                    Button(
                        onClick = viewModel::resumeLiveCounting,
                        modifier = Modifier
                            .weight(1.25f)
                            .height(48.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OntikGreenPrimary)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = AppStrings.resumeCounting(lang),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = viewModel::resetLiveCounting,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CardBorderGray)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = TextPrimaryDark, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = AppStrings.resetCounting(lang),
                    color = TextPrimaryDark,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(
                onClick = onNavigateToPhoto,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, OntikGreenPrimary)
            ) {
                Icon(OntikIcons.Camera, contentDescription = null, tint = OntikGreenPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = AppStrings.photoModeBtn(lang),
                    color = OntikGreenPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(countingState: LiveCountingState, lang: AppLanguage) {
    val (bg, fg, label) = when (countingState) {
        LiveCountingState.IDLE -> Triple(SoftGraySurface, TextSecondarySlate, AppStrings.statusReady(lang))
        LiveCountingState.RUNNING -> Triple(OntikGreenLight, OntikGreenDark, AppStrings.statusLive(lang))
        LiveCountingState.PAUSED -> Triple(WarningAmberBg, WarningAmber, AppStrings.statusPaused(lang))
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
