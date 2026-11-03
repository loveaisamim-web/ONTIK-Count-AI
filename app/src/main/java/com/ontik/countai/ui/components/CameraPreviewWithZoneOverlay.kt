package com.ontik.countai.ui.components

import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.ontik.countai.domain.model.CountingDirection
import com.ontik.countai.domain.model.NormalizedRect
import com.ontik.countai.domain.model.TrackedObject
import com.ontik.countai.ui.theme.OntikGreenAccent
import com.ontik.countai.ui.theme.OntikGreenPrimary
import java.util.concurrent.Executors

@Composable
fun CameraPreviewWithZoneOverlay(
    modifier: Modifier = Modifier,
    useBackCamera: Boolean,
    torchEnabled: Boolean,
    countingZone: NormalizedRect,
    countingLinePosition: Float,
    countingDirection: CountingDirection,
    isAdjustingZone: Boolean,
    trackedObjects: List<TrackedObject>,
    onFrameAvailable: (androidx.camera.core.ImageProxy) -> Unit,
    onZoneAndLineUpdated: (NormalizedRect, Float) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val textMeasurer = rememberTextMeasurer()

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var activeCamera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }

    // Bind CameraX lifecycle whenever camera facing changes
    LaunchedEffect(useBackCamera) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 640))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        onFrameAvailable(imageProxy)
                    }
                }

            val selector = if (useBackCamera) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }

            try {
                cameraProvider.unbindAll()
                val cam = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    imageAnalysis
                )
                activeCamera = cam
                if (cam.cameraInfo.hasFlashUnit()) {
                    cam.cameraControl.enableTorch(torchEnabled)
                }
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Synchronize Torch state
    LaunchedEffect(torchEnabled, activeCamera) {
        val cam = activeCamera ?: return@LaunchedEffect
        if (cam.cameraInfo.hasFlashUnit()) {
            cam.cameraControl.enableTorch(torchEnabled)
        }
    }

    // Interactive local state for smooth dragging of zone corners or counting line
    var localZone by remember(countingZone) { mutableStateOf(countingZone) }
    var localLine by remember(countingLinePosition) { mutableFloatStateOf(countingLinePosition) }

    Box(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isAdjustingZone, countingDirection) {
                    if (!isAdjustingZone) return@pointerInput
                    detectDragGestures(
                        onDragEnd = {
                            onZoneAndLineUpdated(localZone, localLine)
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val posX = change.position.x / w
                        val posY = change.position.y / h
                        val dx = dragAmount.x / w
                        val dy = dragAmount.y / h

                        val isHoriz = countingDirection.isHorizontalLine
                        val distToLine = if (isHoriz) {
                            kotlin.math.abs(posY - localLine)
                        } else {
                            kotlin.math.abs(posX - localLine)
                        }

                        if (distToLine < 0.06f) {
                            // Dragging the virtual counting line
                            localLine = if (isHoriz) {
                                (localLine + dy).coerceIn(localZone.top + 0.05f, localZone.bottom - 0.05f)
                            } else {
                                (localLine + dx).coerceIn(localZone.left + 0.05f, localZone.right - 0.05f)
                            }
                        } else if (posX < (localZone.left + localZone.right) * 0.5f &&
                            posY < (localZone.top + localZone.bottom) * 0.5f
                        ) {
                            // Top-left corner adjustment
                            localZone = localZone.copy(
                                left = (localZone.left + dx).coerceIn(0.04f, localZone.right - 0.20f),
                                top = (localZone.top + dy).coerceIn(0.04f, localZone.bottom - 0.20f)
                            )
                        } else {
                            // Bottom-right corner adjustment
                            localZone = localZone.copy(
                                right = (localZone.right + dx).coerceIn(localZone.left + 0.20f, 0.96f),
                                bottom = (localZone.bottom + dy).coerceIn(localZone.top + 0.20f, 0.96f)
                            )
                        }
                    }
                }
        ) {
            val canvasW = size.width
            val canvasH = size.height

            val zoneRect = Rect(
                left = localZone.left * canvasW,
                top = localZone.top * canvasH,
                right = localZone.right * canvasW,
                bottom = localZone.bottom * canvasH
            )

            // Subtle dark scrim outside the active counting zone
            drawRect(
                color = Color.Black.copy(alpha = 0.32f),
                topLeft = Offset(0f, 0f),
                size = ComposeSize(canvasW, zoneRect.top)
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.32f),
                topLeft = Offset(0f, zoneRect.bottom),
                size = ComposeSize(canvasW, canvasH - zoneRect.bottom)
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.32f),
                topLeft = Offset(0f, zoneRect.top),
                size = ComposeSize(zoneRect.left, zoneRect.height)
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.32f),
                topLeft = Offset(zoneRect.right, zoneRect.top),
                size = ComposeSize(canvasW - zoneRect.right, zoneRect.height)
            )

            // Counting Zone rounded border
            drawRoundRect(
                color = if (isAdjustingZone) Color(0xFFFACC15) else OntikGreenAccent,
                topLeft = zoneRect.topLeft,
                size = zoneRect.size,
                cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                style = Stroke(width = if (isAdjustingZone) 3.dp.toPx() else 2.dp.toPx())
            )

            // Virtual Counting Line inside the zone
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f), 0f)
            if (countingDirection.isHorizontalLine) {
                val lineY = (localLine * canvasH).coerceIn(zoneRect.top, zoneRect.bottom)
                drawLine(
                    color = OntikGreenAccent,
                    start = Offset(zoneRect.left, lineY),
                    end = Offset(zoneRect.right, lineY),
                    strokeWidth = 3.5.dp.toPx(),
                    pathEffect = dashEffect
                )
                // Endpoints
                drawCircle(
                    color = OntikGreenPrimary,
                    radius = 6.dp.toPx(),
                    center = Offset(zoneRect.left, lineY)
                )
                drawCircle(
                    color = OntikGreenPrimary,
                    radius = 6.dp.toPx(),
                    center = Offset(zoneRect.right, lineY)
                )
            } else {
                val lineX = (localLine * canvasW).coerceIn(zoneRect.left, zoneRect.right)
                drawLine(
                    color = OntikGreenAccent,
                    start = Offset(lineX, zoneRect.top),
                    end = Offset(lineX, zoneRect.bottom),
                    strokeWidth = 3.5.dp.toPx(),
                    pathEffect = dashEffect
                )
                drawCircle(
                    color = OntikGreenPrimary,
                    radius = 6.dp.toPx(),
                    center = Offset(lineX, zoneRect.top)
                )
                drawCircle(
                    color = OntikGreenPrimary,
                    radius = 6.dp.toPx(),
                    center = Offset(lineX, zoneRect.bottom)
                )
            }

            // Draw Tracked Objects with Unique Tracking IDs and Crossing State
            for (obj in trackedObjects) {
                val boxLeft = obj.boundingBox.left * canvasW
                val boxTop = obj.boundingBox.top * canvasH
                val boxW = obj.boundingBox.width * canvasW
                val boxH = obj.boundingBox.height * canvasH
                val cx = obj.currentCenterX * canvasW
                val cy = obj.currentCenterY * canvasH

                val boxColor = if (obj.hasBeenCounted) {
                    OntikGreenAccent
                } else {
                    Color(0xFF38BDF8)
                }

                drawRoundRect(
                    color = boxColor,
                    topLeft = Offset(boxLeft, boxTop),
                    size = ComposeSize(boxW, boxH),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx())
                )

                // Center tracking dot + motion vector trail
                drawLine(
                    color = boxColor.copy(alpha = 0.75f),
                    start = Offset(obj.previousCenterX * canvasW, obj.previousCenterY * canvasH),
                    end = Offset(cx, cy),
                    strokeWidth = 2.dp.toPx()
                )
                drawCircle(
                    color = boxColor,
                    radius = 4.dp.toPx(),
                    center = Offset(cx, cy)
                )

                // Track ID badge
                val idTag = "#${obj.trackId} ${(obj.confidence * 100).toInt()}%"
                val textLayout = textMeasurer.measure(
                    text = idTag,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                val labelTop = (boxTop - textLayout.size.height - 4f).coerceAtLeast(4f)
                drawRoundRect(
                    color = boxColor.copy(alpha = 0.88f),
                    topLeft = Offset(boxLeft, labelTop),
                    size = ComposeSize(
                        textLayout.size.width + 12f,
                        textLayout.size.height + 4f
                    ),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(boxLeft + 6f, labelTop + 2f)
                )
            }
        }
    }
}
