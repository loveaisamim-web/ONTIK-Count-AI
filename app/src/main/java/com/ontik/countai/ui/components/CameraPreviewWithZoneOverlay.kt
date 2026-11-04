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
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.ontik.countai.domain.model.CountingDirection
import com.ontik.countai.domain.model.NormalizedRect
import com.ontik.countai.domain.model.TrackedGrain
import com.ontik.countai.ui.theme.OntikAmberAccent
import com.ontik.countai.ui.theme.OntikGreenBright
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs

private enum class DragTarget {
    NONE,
    LINE_A,
    LINE_B,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    EDGE_TOP,
    EDGE_BOTTOM,
    EDGE_LEFT,
    EDGE_RIGHT
}

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

    LaunchedEffect(lockCameraExposureFocus, activeCamera) {
        val cam = activeCamera ?: return@LaunchedEffect
        try {
            if (lockCameraExposureFocus) {
                val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
                val centerPt = factory.createPoint(roiRect.centerX, roiRect.centerY)
                val action = FocusMeteringAction.Builder(centerPt, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                    .setAutoCancelDuration(5, TimeUnit.SECONDS)
                    .build()
                cam.cameraControl.startFocusAndMetering(action)
            }
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

    var localRoi by remember(roiRect) {
        mutableStateOf(
            NormalizedRect(
                left = roiRect.left.coerceIn(0.04f, 0.72f),
                top = roiRect.top.coerceIn(0.16f, 0.56f),
                right = roiRect.right.coerceIn(0.28f, 0.96f),
                bottom = roiRect.bottom.coerceIn(0.34f, 0.72f)
            )
        )
    }
    var localLineA by remember(lineAPosition) { mutableFloatStateOf(lineAPosition) }
    var localLineB by remember(lineBPosition) { mutableFloatStateOf(lineBPosition) }
    var activeDragTarget by remember { mutableStateOf(DragTarget.NONE) }

    Box(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(countingDirection, enableVerificationLineB) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            val px = startOffset.x / w
                            val py = startOffset.y / h

                            val isHoriz = countingDirection.isHorizontalLine
                            val distLineA = if (isHoriz) abs(py - localLineA) else abs(px - localLineA)

                            val cornerTol = 0.08f
                            val edgeTol = 0.06f

                            activeDragTarget = when {
                                abs(px - localRoi.left) < cornerTol && abs(py - localRoi.top) < cornerTol -> DragTarget.TOP_LEFT
                                abs(px - localRoi.right) < cornerTol && abs(py - localRoi.top) < cornerTol -> DragTarget.TOP_RIGHT
                                abs(px - localRoi.left) < cornerTol && abs(py - localRoi.bottom) < cornerTol -> DragTarget.BOTTOM_LEFT
                                abs(px - localRoi.right) < cornerTol && abs(py - localRoi.bottom) < cornerTol -> DragTarget.BOTTOM_RIGHT
                                distLineA < 0.045f && px in (localRoi.left + 0.05f)..(localRoi.right - 0.05f) && py in (localRoi.top + 0.05f)..(localRoi.bottom - 0.05f) -> DragTarget.LINE_A
                                abs(py - localRoi.top) < edgeTol && px in localRoi.left..localRoi.right -> DragTarget.EDGE_TOP
                                abs(py - localRoi.bottom) < edgeTol && px in localRoi.left..localRoi.right -> DragTarget.EDGE_BOTTOM
                                abs(px - localRoi.left) < edgeTol && py in localRoi.top..localRoi.bottom -> DragTarget.EDGE_LEFT
                                abs(px - localRoi.right) < edgeTol && py in localRoi.top..localRoi.bottom -> DragTarget.EDGE_RIGHT
                                else -> {
                                    // Fallback to nearest corner inside/near ROI for effortless resizing
                                    val midX = (localRoi.left + localRoi.right) * 0.5f
                                    val midY = (localRoi.top + localRoi.bottom) * 0.5f
                                    when {
                                        px < midX && py < midY -> DragTarget.TOP_LEFT
                                        px >= midX && py < midY -> DragTarget.TOP_RIGHT
                                        px < midX && py >= midY -> DragTarget.BOTTOM_LEFT
                                        else -> DragTarget.BOTTOM_RIGHT
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            activeDragTarget = DragTarget.NONE
                            onRoiAndLinesUpdated(localRoi, localLineA, localLineB)
                        },
                        onDragCancel = {
                            activeDragTarget = DragTarget.NONE
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val dx = dragAmount.x / w
                        val dy = dragAmount.y / h
                        val isHoriz = countingDirection.isHorizontalLine

                        when (activeDragTarget) {
                            DragTarget.LINE_A -> {
                                localLineA = if (isHoriz) {
                                    (localLineA + dy).coerceIn(localRoi.top + 0.05f, localRoi.bottom - 0.05f)
                                } else {
                                    (localLineA + dx).coerceIn(localRoi.left + 0.05f, localRoi.right - 0.05f)
                                }
                            }
                            DragTarget.TOP_LEFT -> {
                                localRoi = localRoi.copy(
                                    left = (localRoi.left + dx).coerceIn(0.03f, localRoi.right - 0.18f),
                                    top = (localRoi.top + dy).coerceIn(0.15f, localRoi.bottom - 0.16f)
                                )
                            }
                            DragTarget.TOP_RIGHT -> {
                                localRoi = localRoi.copy(
                                    right = (localRoi.right + dx).coerceIn(localRoi.left + 0.18f, 0.97f),
                                    top = (localRoi.top + dy).coerceIn(0.15f, localRoi.bottom - 0.16f)
                                )
                            }
                            DragTarget.BOTTOM_LEFT -> {
                                localRoi = localRoi.copy(
                                    left = (localRoi.left + dx).coerceIn(0.03f, localRoi.right - 0.18f),
                                    bottom = (localRoi.bottom + dy).coerceIn(localRoi.top + 0.16f, 0.74f)
                                )
                            }
                            DragTarget.BOTTOM_RIGHT -> {
                                localRoi = localRoi.copy(
                                    right = (localRoi.right + dx).coerceIn(localRoi.left + 0.18f, 0.97f),
                                    bottom = (localRoi.bottom + dy).coerceIn(localRoi.top + 0.16f, 0.74f)
                                )
                            }
                            DragTarget.EDGE_TOP -> {
                                localRoi = localRoi.copy(
                                    top = (localRoi.top + dy).coerceIn(0.15f, localRoi.bottom - 0.16f)
                                )
                            }
                            DragTarget.EDGE_BOTTOM -> {
                                localRoi = localRoi.copy(
                                    bottom = (localRoi.bottom + dy).coerceIn(localRoi.top + 0.16f, 0.74f)
                                )
                            }
                            DragTarget.EDGE_LEFT -> {
                                localRoi = localRoi.copy(
                                    left = (localRoi.left + dx).coerceIn(0.03f, localRoi.right - 0.18f)
                                )
                            }
                            DragTarget.EDGE_RIGHT -> {
                                localRoi = localRoi.copy(
                                    right = (localRoi.right + dx).coerceIn(localRoi.left + 0.18f, 0.97f)
                                )
                            }
                            else -> {}
                        }

                        // Keep Line A safely inside the resized ROI box
                        localLineA = if (isHoriz) {
                            localLineA.coerceIn(localRoi.top + 0.05f, localRoi.bottom - 0.05f)
                        } else {
                            localLineA.coerceIn(localRoi.left + 0.05f, localRoi.right - 0.05f)
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

            if (showDebugMask && debugMaskBitmap != null) {
                drawImage(
                    image = debugMaskBitmap.asImageBitmap(),
                    dstOffset = IntOffset(roiPx.left.toInt(), roiPx.top.toInt()),
                    dstSize = IntSize(roiPx.width.toInt().coerceAtLeast(1), roiPx.height.toInt().coerceAtLeast(1)),
                    alpha = 0.55f
                )
            }

            // Ultra-clear outside area (alpha = 0.14f) so camera feed is clearly visible everywhere
            val dimColor = Color.Black.copy(alpha = 0.14f)
            drawRect(dimColor, Offset(0f, 0f), ComposeSize(cw, roiPx.top))
            drawRect(dimColor, Offset(0f, roiPx.bottom), ComposeSize(cw, ch - roiPx.bottom))
            drawRect(dimColor, Offset(0f, roiPx.top), ComposeSize(roiPx.left, roiPx.height))
            drawRect(dimColor, Offset(roiPx.right, roiPx.top), ComposeSize(cw - roiPx.right, roiPx.height))

            // Scanner Box Rounded Border
            drawRoundRect(
                color = OntikGreenBright.copy(alpha = 0.92f),
                topLeft = roiPx.topLeft,
                size = roiPx.size,
                cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                style = Stroke(width = 2.5.dp.toPx())
            )

            // Large Easy-to-Grab Corner Handles + Mid-Edge Grab Pills for effortless resizing
            val handleRadius = 9.dp.toPx()
            val corners = listOf(
                roiPx.topLeft,
                Offset(roiPx.right, roiPx.top),
                Offset(roiPx.left, roiPx.bottom),
                roiPx.bottomRight
            )
            for (c in corners) {
                drawCircle(color = Color.Black.copy(alpha = 0.45f), radius = handleRadius * 1.6f, center = c)
                drawCircle(color = OntikGreenBright, radius = handleRadius, center = c)
                drawCircle(color = Color.White, radius = handleRadius * 0.45f, center = c)
            }

            // Mid-edge resize bars (Top, Bottom, Left, Right)
            val edgeBarLen = 28.dp.toPx()
            val edgeBarThick = 6.dp.toPx()
            val midX = roiPx.center.x
            val midY = roiPx.center.y
            // Top & Bottom edge handles
            drawLine(
                color = OntikGreenBright,
                start = Offset(midX - edgeBarLen * 0.5f, roiPx.top),
                end = Offset(midX + edgeBarLen * 0.5f, roiPx.top),
                strokeWidth = edgeBarThick,
                cap = StrokeCap.Round
            )
            drawLine(
                color = OntikGreenBright,
                start = Offset(midX - edgeBarLen * 0.5f, roiPx.bottom),
                end = Offset(midX + edgeBarLen * 0.5f, roiPx.bottom),
                strokeWidth = edgeBarThick,
                cap = StrokeCap.Round
            )
            // Left & Right edge handles
            drawLine(
                color = OntikGreenBright,
                start = Offset(roiPx.left, midY - edgeBarLen * 0.5f),
                end = Offset(roiPx.left, midY + edgeBarLen * 0.5f),
                strokeWidth = edgeBarThick,
                cap = StrokeCap.Round
            )
            drawLine(
                color = OntikGreenBright,
                start = Offset(roiPx.right, midY - edgeBarLen * 0.5f),
                end = Offset(roiPx.right, midY + edgeBarLen * 0.5f),
                strokeWidth = edgeBarThick,
                cap = StrokeCap.Round
            )

            // Primary Counting Line A
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 14f), 0f)
            if (countingDirection.isHorizontalLine) {
                val yA = (localLineA * ch).coerceIn(roiPx.top + 14f, roiPx.bottom - 14f)
                drawLine(
                    color = OntikAmberAccent.copy(alpha = 0.28f),
                    start = Offset(roiPx.left, yA),
                    end = Offset(roiPx.right, yA),
                    strokeWidth = 9.dp.toPx()
                )
                drawLine(
                    color = OntikAmberAccent,
                    start = Offset(roiPx.left, yA),
                    end = Offset(roiPx.right, yA),
                    strokeWidth = 3.dp.toPx(),
                    pathEffect = dashEffect
                )

                val pillW = 46.dp.toPx()
                val pillH = 20.dp.toPx()
                drawRoundRect(
                    color = OntikAmberAccent,
                    topLeft = Offset(midX - pillW * 0.5f, yA - pillH * 0.5f),
                    size = ComposeSize(pillW, pillH),
                    cornerRadius = CornerRadius(pillH * 0.5f, pillH * 0.5f)
                )

                val arrowSize = 5.dp.toPx()
                val arrowPath = Path().apply {
                    if (countingDirection == CountingDirection.BOTTOM_TO_TOP) {
                        moveTo(midX, yA - arrowSize)
                        lineTo(midX - arrowSize, yA + arrowSize * 0.7f)
                        lineTo(midX + arrowSize, yA + arrowSize * 0.7f)
                    } else {
                        moveTo(midX, yA + arrowSize)
                        lineTo(midX - arrowSize, yA - arrowSize * 0.7f)
                        lineTo(midX + arrowSize, yA - arrowSize * 0.7f)
                    }
                    close()
                }
                drawPath(arrowPath, color = Color.Black)
            } else {
                val xA = (localLineA * cw).coerceIn(roiPx.left + 14f, roiPx.right - 14f)
                drawLine(
                    color = OntikAmberAccent.copy(alpha = 0.28f),
                    start = Offset(xA, roiPx.top),
                    end = Offset(xA, roiPx.bottom),
                    strokeWidth = 9.dp.toPx()
                )
                drawLine(
                    color = OntikAmberAccent,
                    start = Offset(xA, roiPx.top),
                    end = Offset(xA, roiPx.bottom),
                    strokeWidth = 3.dp.toPx(),
                    pathEffect = dashEffect
                )

                val pillW = 20.dp.toPx()
                val pillH = 46.dp.toPx()
                drawRoundRect(
                    color = OntikAmberAccent,
                    topLeft = Offset(xA - pillW * 0.5f, midY - pillH * 0.5f),
                    size = ComposeSize(pillW, pillH),
                    cornerRadius = CornerRadius(pillW * 0.5f, pillW * 0.5f)
                )
            }

            // Tracked Grain Dots (Strictly 1 grain per dot — NO "x2" or "x3" labels!)
            for (grain in trackedGrains) {
                val gx = grain.currentX * cw
                val gy = grain.currentY * ch
                val recentlyCounted = grain.countedLineA && (nowMs - grain.countedFlashTimestampMs) < 380L

                val dotColor = if (grain.countedLineA) OntikGreenBright else OntikAmberAccent

                if (grain.trailPoints.size >= 2) {
                    val trailPath = Path()
                    grain.trailPoints.forEachIndexed { idx, (tx, ty) ->
                        val px = tx * cw
                        val py = ty * ch
                        if (idx == 0) trailPath.moveTo(px, py) else trailPath.lineTo(px, py)
                    }
                    drawPath(
                        path = trailPath,
                        color = dotColor.copy(alpha = 0.45f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                val baseRadius = if (recentlyCounted) 9.dp.toPx() else 6.dp.toPx()
                if (recentlyCounted) {
                    drawCircle(
                        color = OntikGreenBright.copy(alpha = 0.40f),
                        radius = baseRadius * 1.8f,
                        center = Offset(gx, gy)
                    )
                }
                drawCircle(
                    color = dotColor,
                    radius = baseRadius,
                    center = Offset(gx, gy)
                )

                if (showTrackIds) {
                    val labelStr = "#${grain.trackId}"
                    val measured = textMeasurer.measure(
                        text = labelStr,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    val tagTopLeft = Offset(
                        (gx + 8.dp.toPx()).coerceAtMost(cw - measured.size.width - 12f),
                        (gy - 10.dp.toPx()).coerceAtLeast(12f)
                    )
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.65f),
                        topLeft = Offset(tagTopLeft.x - 4f, tagTopLeft.y - 2f),
                        size = ComposeSize(measured.size.width + 8f, measured.size.height + 4f),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                    drawText(
                        textLayoutResult = measured,
                        topLeft = tagTopLeft
                    )
                }
            }
        }
    }
}
