package com.ontik.countai.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ontik.countai.ui.theme.CardBorderGray
import com.ontik.countai.ui.theme.OntikGreenAccent
import com.ontik.countai.ui.theme.OntikGreenDark
import com.ontik.countai.ui.theme.OntikGreenLight
import com.ontik.countai.ui.theme.OntikGreenPrimary
import com.ontik.countai.ui.theme.OntikIcons
import com.ontik.countai.ui.theme.SoftGraySurface
import com.ontik.countai.ui.theme.TextPrimaryDark
import com.ontik.countai.ui.theme.TextSecondarySlate
import com.ontik.countai.ui.theme.WhiteBackground
import com.ontik.countai.ui.viewmodel.CountViewModel

@Composable
fun PhotoCountingScreen(
    viewModel: CountViewModel,
    onBack: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val photoState by viewModel.photoState.collectAsState()

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.analyzePhotoFromUri(uri)
        }
    }

    val takePicturePreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            viewModel.analyzeBitmapForPhotoMode(bitmap)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WhiteBackground)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text(
                        text = "Photo Counting",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimaryDark
                    )
                    Text(
                        text = "Detect, inspect & manually refine counts on still photos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondarySlate
                    )
                }
            }
            IconButton(onClick = onNavigateToSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }

        // Photo Display
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SoftGraySurface),
            border = BorderStroke(1.dp, CardBorderGray)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val bmp = photoState.bitmap
                if (bmp != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(bmp) {
                                detectTapGestures { offset ->
                                    val normX = offset.x / size.width.toFloat().coerceAtLeast(1f)
                                    val normY = offset.y / size.height.toFloat().coerceAtLeast(1f)
                                    viewModel.onPhotoTap(normX, normY)
                                }
                            }
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Selected Photo for Object Counting",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height

                            photoState.aiDetections.forEachIndexed { idx, det ->
                                if (idx in photoState.suppressedDetectionIndices) return@forEachIndexed
                                val rect = det.boundingBox
                                val left = rect.left * w
                                val top = rect.top * h
                                val bw = rect.width * w
                                val bh = rect.height * h

                                drawRoundRect(
                                    color = OntikGreenAccent,
                                    topLeft = Offset(left, top),
                                    size = Size(bw, bh),
                                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                                    style = Stroke(width = 2.5.dp.toPx())
                                )
                                drawCircle(
                                    color = OntikGreenPrimary,
                                    radius = 4.dp.toPx(),
                                    center = Offset(rect.centerX * w, rect.centerY * h)
                                )
                            }

                            photoState.manualAddedPoints.forEach { pt ->
                                val cx = pt.normalizedX * w
                                val cy = pt.normalizedY * h
                                drawCircle(
                                    color = Color(0xFF2563EB).copy(alpha = 0.35f),
                                    radius = 12.dp.toPx(),
                                    center = Offset(cx, cy)
                                )
                                drawCircle(
                                    color = Color(0xFF2563EB),
                                    radius = 5.dp.toPx(),
                                    center = Offset(cx, cy)
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Select or Capture a Photo",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimaryDark
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Spread seeds, grains, beans, or components on a contrasting surface and capture or pick a photo. You can tap any missed object to add it or tap a box to remove false detections.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                if (photoState.isAnalyzing) {
                    CircularProgressIndicator(color = OntikGreenPrimary)
                }
            }
        }

        // Status note
        photoState.statusMessage?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondarySlate,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        // Count Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SoftGraySurface),
            border = BorderStroke(1.dp, CardBorderGray)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "PHOTO TOTAL COUNT",
                        style = MaterialTheme.typography.labelLarge,
                        color = OntikGreenDark
                    )
                    Text(
                        text = "${photoState.totalCount}",
                        style = MaterialTheme.typography.displayLarge,
                        color = TextPrimaryDark
                    )
                    Text(
                        text = "AI Boxes: ${photoState.activeAiCount} | Tap Marks: +${photoState.manualAddedPoints.size}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledIconButton(
                        onClick = { viewModel.adjustPhotoCountManually(-1) },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = WhiteBackground,
                            contentColor = TextPrimaryDark
                        )
                    ) {
                        Icon(OntikIcons.Minus, contentDescription = "Minus 1", tint = TextPrimaryDark)
                    }
                    FilledIconButton(
                        onClick = { viewModel.adjustPhotoCountManually(1) },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = OntikGreenLight,
                            contentColor = OntikGreenDark
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Plus 1")
                    }
                    FilledIconButton(
                        onClick = viewModel::clearPhotoManualAdjustments,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = WhiteBackground,
                            contentColor = TextPrimaryDark
                        )
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset Edits")
                    }
                    FilledIconButton(
                        onClick = { viewModel.savePhotoSession() },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = OntikGreenPrimary,
                            contentColor = WhiteBackground
                        )
                    ) {
                        Icon(OntikIcons.BookmarkCheck, contentDescription = "Save Photo Session")
                    }
                }
            }
        }

        // Bottom Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { takePicturePreviewLauncher.launch(null) },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OntikGreenPrimary)
            ) {
                Icon(OntikIcons.Camera, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Capture Photo", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            OutlinedButton(
                onClick = {
                    galleryPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, OntikGreenPrimary)
            ) {
                Icon(OntikIcons.PhotoLibrary, contentDescription = null, tint = OntikGreenPrimary)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Pick from Gallery", color = OntikGreenPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}
