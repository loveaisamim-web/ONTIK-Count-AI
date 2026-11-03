package com.ontik.countai.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.hardware.camera2.CaptureRequest
import android.os.SystemClock
import android.util.Range
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import com.ontik.countai.domain.model.TrackedGrain
import com.ontik.countai.ui.theme.DangerRed
import com.ontik.countai.ui.theme.OntikAmberAccent
import com.ontik.countai.ui.theme.OntikGreenBright
import com.ontik.countai.ui.theme.OntikGreenPrimary
import java.util.concurrent.Executors
import kotlin.math.abs

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun CameraPreviewWithZoneOverlay(
    modifier: Modifier = Modifier,
    useBackCamera: Boolean,
    targetFps: Int,
    lockCameraExposureFocus: Boolean,
    torchEnabled: Boolean,
    roiRect: NormalizedRect,
    lineAPosition: Float,
    enableVerificationLineB: Boolean,
    lineBPosition: Float,
    countingDirection: CountingDirection,
    trackedGrains: List<TrackedGrain>,
    showTrackIds: Boolean,
    showDebugMask: Boolean,
    debugMaskBitmap: Bitmap?,
    onFrameAvailable: (ImageProxy) -> Unit,
    onRoiAndLinesUpdated: (NormalizedRect, Float, Float) -> Unit
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

    // Bind CameraX 1280x720 ImageAnalysis with Camera2Interop FPS target
    LaunchedEffect(useBackCamera, targetFps) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analysisBuilder = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)

            try {
                val ext = Camera2Interop.Extender(analysisBuilder)
                val fpsMin = 30
                val fpsMax = if (targetFps >= 60) 60 else 30
                ext.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                    Range(fpsMin, fpsMax)
                )
            } catch (_: Throwable) {
            }

            val imageAnalysis = analysisBuilder.build().also { analysis ->
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
            } catch (_: Throwable) {
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Lock AE, AWB, and AF after calibration via Camera2CameraControl
    LaunchedEffect(lockCameraExposureFocus, activeCamera) {
        val cam = activeCamera ?: return@LaunchedEffect
        try {
            val c2Control = Camera2CameraControl.from(cam.cameraControl)
            val options = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, lockCameraExposureFocus)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, lockCameraExposureFocus)
                .build()
            c2Control.setCaptureRequestOptions(options)
        } catch (_: Throwable) {
        }
    }

    LaunchedEffect(torchEnabled, activeCamera) {
        val cam = activeCamera ?: return@LaunchedEffect
        if (cam.cameraInfo.hasFlashUnit()) {
            cam.cameraControl.enableTorch(torchEnabled)
        }
    }

    var localRoi by remember(roiRect) { mutableStateOf(roiRect) }
    var localLineA by remember(lineAPosition) { mutableFloatStateOf(lineAPosition) }
    var localLineB by remember(lineBPosition) { mutableFloatStateOf(lineBPosition) }

    Box(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Optional Semi-Transparent Binary Debug Mask Overlay inside the screen
        if (showDebugMask && debugMaskBitmap != null) {
            Image(
                bitmap = debugMaskBitmap.asImageBitmap(),
                contentDescription = "OpenCV Binary Mask Overlay",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.45f)
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(countingDirection, enableVerificationLineB) {
                    detectDragGestures(
                        onDragEnd = {
                            onRoiAndLinesUpdated(localRoi, localLineA, localLineB)
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val px = change.position.x / w
                        val py = change.position.y / h
                        val dx = dragAmount.x / w
                        val dy = dragAmount.y / h

                        val isHoriz = countingDirection.isHorizontalLine
                        val distLineA = if (isHoriz) abs(py - localLineA) else abs(px - localLineA)
                        val distLineB = if (enableVerificationLineB) {
                            if (isHoriz) abs(py - localLineB) else abs(px - localLineB)
                        } else {
                            1f
                        }

                        // Check if dragging Line A pill handle
                        if (distLineA < 0.045f && px in localRoi.left..localRoi.right && py in localRoi.top..localRoi.bottom) {
                            localLineA = if (isHoriz) {
                                (localLineA + dy).coerceIn(localRoi.top + 0.06f, localRoi.bottom - 0.06f)
                            } else {
                                (localLineA + dx).coerceIn(localRoi.left + 0.06f, localRoi.right - 0.06f)
                            }
                        } else if (enableVerificationLineB && distLineB < 0.045f) {
                            localLineB = if (isHoriz) {
                                (localLineB + dy).coerceIn(localRoi.top + 0.06f, localRoi.bottom - 0.06f)
                            } else {
                                (localLineB + dx).coerceIn(localRoi.left + 0.06f, localRoi.right - 0.06f)
                            }
                        } else {
                            // Dragging ROI corner handles
                            val midX = (localRoi.left + localRoi.right) * 0.5f
                            val midY = (localRoi.top + localRoi.bottom) * 0.5f
                            if (px < midX && py < midY) {
                                localRoi = localRoi.copy(
                                    left = (localRoi.left + dx).coerceIn(0.04f, localRoi.right - 0.20f),
                                    top = (localRoi.top + dy).coerceIn(0.08f, localRoi.bottom - 0.20f)
                                )
                            } else if (px >= midX && py < midY) {
                                localRoi = localRoi.copy(
                                    right = (localRoi.right + dx).coerceIn(localRoi.left + 0.20f, 0.96f),
                                    top = (localRoi.top + dy).coerceIn(0.08f, localRoi.bottom - 0.20f)
                                )
                            } else if (px < midX && py >= midY) {
                                localRoi = localRoi.copy(
                                    left = (localRoi.left + dx).coerceIn(0.04f, localRoi.right - 0.20f),
                                    bottom = (localRoi.bottom + dy).coerceIn(localRoi.top + 0.20f, 0.92f)
                                )
                            } else {
                                localRoi = localRoi.copy(
                                    right = (localRoi.right + dx).coerceIn(localRoi.left + 0.20f, 0.96f),
                                    bottom = (localRoi.bottom + dy).coerceIn(localRoi.top + 0.20f, 0.92f)
                                )
                            }
                        }
                    }
                }
        ) {
            val cw = size.width
            val ch = size.height
            val nowMs = SystemClock.elapsedRealtime()

            val roiPx = Rect(
                left = localRoi.left * cw,
                top = localRoi.top * ch,
                right = localRoi.right * cw,
                bottom = localRoi.bottom * ch
            )

            // 1. Dim Area Outside ROI
            val dimColor = Color.Black.copy(alpha = 0.48f)
            drawRect(dimColor, Offset(0f, 0f), ComposeSize(cw, roiPx.top))
            drawRect(dimColor, Offset(0f, roiPx.bottom), ComposeSize(cw, ch - roiPx.bottom))
            drawRect(dimColor, Offset(0f, roiPx.top), ComposeSize(roiPx.left, roiPx.height))
            drawRect(dimColor, Offset(roiPx.right, roiPx.top), ComposeSize(cw - roiPx.right, roiPx.height))

            // 2. ROI Rounded Border + Glowing Corner Handles
            drawRoundRect(
                color = OntikGreenBright.copy(alpha = 0.85f),
                topLeft = roiPx.topLeft,
                size = roiPx.size,
                cornerRadius = CornerRadius(22.dp.toPx(), 22.dp.toPx()),
                style = Stroke(width = 2.2.dp.toPx())
            )

            val handleRadius = 7.dp.toPx()
            val corners = listOf(
                roiPx.topLeft,
                Offset(roiPx.right, roiPx.top),
                Offset(roiPx.left, roiPx.bottom),
                roiPx.bottomRight
            )
            for (c in corners) {
                drawCircle(color = OntikGreenBright.copy(alpha = 0.32f), radius = handleRadius * 1.9f, center = c)
                drawCircle(color = OntikGreenBright, radius = handleRadius, center = c)
            }

            // 3. Primary Counting Line A (Dashed, Glowing, with Draggable Pill Handle & Direction Arrow)
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 14f), 0f)
            if (countingDirection.isHorizontalLine) {
                val yA = (localLineA * ch).coerceIn(roiPx.top + 12f, roiPx.bottom - 12f)
                drawLine(
                    color = OntikAmberAccent.copy(alpha = 0.30f),
                    start = Offset(roiPx.left, yA),
                    end = Offset(roiPx.right, yA),
                    strokeWidth = 10.dp.toPx()
                )
                drawLine(
                    color = OntikAmberAccent,
                    start = Offset(roiPx.left, yA),
                    end = Offset(roiPx.right, yA),
                    strokeWidth = 3.dp.toPx(),
                    pathEffect = dashEffect
                )

                // Draggable Pill Handle on Line A
                val pillW = 46.dp.toPx()
                val pillH = 18.dp.toPx()
                val pillCenter = Offset(roiPx.center.x, yA)
                drawRoundRect(
                    color = OntikAmberAccent,
                    topLeft = Offset(pillCenter.x - pillW * 0.5f, pillCenter.y - pillH * 0.5f),
                    size = ComposeSize(pillW, pillH),
                    cornerRadius = CornerRadius(9.dp.toPx(), 9.dp.toPx())
                )

                // Direction Arrow inside Pill
                val arrowDir = if (countingDirection == CountingDirection.BOTTOM_TO_TOP) -1f else 1f
                val arrowPath = Path().apply {
                    moveTo(pillCenter.x - 6.dp.toPx(), pillCenter.y - 3.dp.toPx() * arrowDir)
                    lineTo(pillCenter.x + 6.dp.toPx(), pillCenter.y - 3.dp.toPx() * arrowDir)
                    lineTo(pillCenter.x, pillCenter.y + 4.dp.toPx() * arrowDir)
                    close()
                }
                drawPath(arrowPath, color = Color.Black)

                // Optional Verification Line B
                if (enableVerificationLineB) {
                    val yB = (localLineB * ch).coerceIn(roiPx.top + 12f, roiPx.bottom - 12f)
                    drawLine(
                        color = Color(0xFF38BDF8),
                        start = Offset(roiPx.left, yB),
                        end = Offset(roiPx.right, yB),
                        strokeWidth = 2.2.dp.toPx(),
                        pathEffect = dashEffect
                    )
                }
            } else {
                val xA = (localLineA * cw).coerceIn(roiPx.left + 12f, roiPx.right - 12f)
                drawLine(
                    color = OntikAmberAccent.copy(alpha = 0.30f),
                    start = Offset(xA, roiPx.top),
                    end = Offset(xA, roiPx.bottom),
                    strokeWidth = 10.dp.toPx()
                )
                drawLine(
                    color = OntikAmberAccent,
                    start = Offset(xA, roiPx.top),
                    end = Offset(xA, roiPx.bottom),
                    strokeWidth = 3.dp.toPx(),
                    pathEffect = dashEffect
                )

                val pillW = 18.dp.toPx()
                val pillH = 46.dp.toPx()
                val pillCenter = Offset(xA, roiPx.center.y)
                drawRoundRect(
                    color = OntikAmberAccent,
                    topLeft = Offset(pillCenter.x - pillW * 0.5f, pillCenter.y - pillH * 0.5f),
                    size = ComposeSize(pillW, pillH),
                    cornerRadius = CornerRadius(9.dp.toPx(), 9.dp.toPx())
                )
            }

            // 4. Draw Tracked Grains: Fading Motion Trail + Colored Dot + 200ms Counted Flash Ring + Clump Badge
            for (grain in trackedGrains) {
                val gx = grain.currentX * cw
                val gy = grain.currentY * ch
                val timeSinceCounted = nowMs - grain.countedFlashTimestampMs
                val isFlashing = grain.countedLineA && timeSinceCounted in 0..220L

                val dotColor = when {
                    grain.isClump -> DangerRed
                    grain.countedLineA -> OntikGreenBright
                    else -> OntikAmberAccent
                }

                // Short fading trail
                val pts = grain.trailPoints
                if (pts.size >= 2) {
                    for (i in 0 until pts.size - 1) {
                        val alpha = ((i + 1).toFloat() / pts.size) * 0.65f
                        drawLine(
                            color = dotColor.copy(alpha = alpha),
                            start = Offset(pts[i].first * cw, pts[i].second * ch),
                            end = Offset(pts[i + 1].first * cw, pts[i + 1].second * ch),
                            strokeWidth = 2.5.dp.toPx()
                        )
                    }
                }

                // 200 ms Green Flash Ring when counted
                if (isFlashing) {
                    val progress = (timeSinceCounted / 220f).coerceIn(0f, 1f)
                    val ringRadius = (10.dp.toPx() + progress * 18.dp.toPx())
                    drawCircle(
                        color = OntikGreenBright.copy(alpha = 1f - progress),
                        radius = ringRadius,
                        center = Offset(gx, gy),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }

                // Center grain dot
                drawCircle(
                    color = dotColor,
                    radius = if (grain.estimatedGrainCount > 1) 6.5.dp.toPx() else 4.5.dp.toPx(),
                    center = Offset(gx, gy)
                )

                // Optional ID or multi-grain split badge (e.g. "x2", "CLUMP x7")
                if (showTrackIds || grain.estimatedGrainCount > 1 || grain.isClump) {
                    val badgeText = buildString {
                        if (showTrackIds) append("#${grain.trackId} ")
                        if (grain.isClump) append("CLUMP ")
                        if (grain.estimatedGrainCount > 1) append("x${grain.estimatedGrainCount}")
                    }.trim()

                    if (badgeText.isNotEmpty()) {
                        val layout = textMeasurer.measure(
                            text = badgeText,
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        drawRoundRect(
                            color = Color.Black.copy(alpha = 0.72f),
                            topLeft = Offset(gx + 6.dp.toPx(), gy - layout.size.height * 0.5f),
                            size = ComposeSize(layout.size.width + 10f, layout.size.height + 4f),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                        drawText(
                            textLayoutResult = layout,
                            topLeft = Offset(gx + 6.dp.toPx() + 5f, gy - layout.size.height * 0.5f + 2f)
                        )
                    }
                }
            }
        }
    }
}
