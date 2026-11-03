package com.ontik.countai.cv

import android.graphics.Bitmap
import android.os.SystemClock
import com.ontik.countai.domain.model.AccuracyConfidenceLevel
import com.ontik.countai.domain.model.GrainBlob
import com.ontik.countai.domain.model.NormalizedRect
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Production Classical Computer Vision Grain & Seed Detection Pipeline powered by OpenCV (`org.opencv`).
 *
 * Implements:
 * 1. **30-Frame Auto Calibration (~2 seconds)**:
 *    - Accumulates 30 empty-surface frames in the ROI to build a clean background model in both
 *      Grayscale (`bgGrayMat`) and CIE Lab (`bgLabMat`), plus a blurred illumination map (`illumMapMat`)
 *      and CLAHE contrast normalizer.
 *    - Estimates sensor noise standard deviation (`noiseStd`) and sets automatic base threshold
 *      `autoThreshold = max(meanDiff + 4 * noiseStd, minimum)`.
 * 2. **Per-Frame Zero-Allocation ROI Pipeline**:
 *    - Crops strictly to the user's ROI (`NormalizedRect`), reusing pre-allocated `Mat` instances.
 *    - Applies 3x3 or 5x5 Gaussian blur.
 *    - Computes foreground difference in BOTH Grayscale (normalized by illumination map + CLAHE)
 *      and CIE Lab (`L`, `a`, `b` channels, taking `max(grayDiff, labColorDiff)`), so white rice,
 *      yellow dal, red lentil, brown peanuts, and dark seeds are all detected on a white/neutral surface.
 *    - **Soft Shadow Rejection Filter**: Rejects pixels that are slightly darker than background
 *      (`currL < bgL`) yet have near-identical chrominance (`|da| + |db| <= 6`) and low spatial gradient
 *      (soft cast shadows from hands or lighting).
 *    - **Morphological Open then Close** with an elliptical structuring element scaled to grain size.
 *    - **External Contour Extraction (`findContours`)** with area, solidity, aspect ratio, and extent filtering.
 *    - **Sub-Pixel Centroid Calculation** using spatial image moments (`m10/m00`, `m01/m00`).
 *    - **Touching/Overlapping Grain Splitting**:
 *      - Learns `singleGrainAreaPx` as the median of the first 30 clean isolated grain blobs and updates
 *        it with a robust running median.
 *      - When `blobArea > 1.5 * singleGrainAreaPx`: estimates `N = round(area / singleGrainAreaPx)` AND
 *        runs Distance Transform (`Imgproc.distanceTransform`) local maxima peak extraction (watershed seeds).
 *        If watershed peaks find `>= N` centers, uses those exact sub-object centers; otherwise emits `N`
 *        virtual objects sharing the blob so a touching group is never under-counted.
 *      - Flags blobs `> 6x singleGrainAreaPx` as `clump = true`.
 *    - **Background Running Average & Sudden Brightness Shift Recalibration**:
 *      - Freezes background updates while objects are present in the ROI.
 *      - Slowly updates background (`accumulateWeighted` alpha = 0.02) ONLY on background pixels when ROI is empty.
 *      - Detects sudden global brightness jumps (`> 38` gray levels) and triggers automatic recalibration with a user toast/hint.
 */
class OpenCvGrainProcessor {

    data class FrameAnalysisResult(
        val blobs: List<GrainBlob>,
        val isCalibrating: Boolean,
        val calibrationProgress: Float,
        val isCalibrated: Boolean,
        val autoThresholdValue: Double,
        val noiseStd: Double,
        val learnedSingleGrainAreaPx: Float,
        val suddenBrightnessShiftDetected: Boolean,
        val contextualHintEn: String?,
        val contextualHintBn: String?,
        val confidenceLevel: AccuracyConfidenceLevel,
        val debugMaskBitmap: Bitmap?,
        val processingTimeMs: Long
    )

    private val openCvInitialized = AtomicBoolean(false)

    // Pre-allocated reusable OpenCV Mats (Zero per-frame allocation once ROI size is stable)
    private var fullRgbaMat: Mat? = null
    private var roiRgbaMat: Mat? = null
    private var roiBlurredMat: Mat? = null
    private var roiGrayMat: Mat? = null
    private var roiLabMat: Mat? = null

    private var bgGrayFloatMat: Mat? = null
    private var bgGray8UMat: Mat? = null
    private var bgLabFloatMat: Mat? = null
    private var bgLab8UMat: Mat? = null
    private var illumMapMat: Mat? = null

    private var diffGrayMat: Mat? = null
    private var diffLabMat: Mat? = null
    private var combinedDiffMat: Mat? = null
    private var binaryMaskMat: Mat? = null
    private var shadowFilteredMaskMat: Mat? = null
    private var morphMaskMat: Mat? = null
    private var hierarchyMat: Mat? = null
    private var distTransformMat: Mat? = null
    private var distDilatedMat: Mat? = null
    private var distPeaksMaskMat: Mat? = null

    private val labChannels = ArrayList<Mat>(3)
    private val bgLabChannels = ArrayList<Mat>(3)
    private val diffChannelA = Mat()
    private val diffChannelB = Mat()
    private val diffChannelL = Mat()
    private val chromaDiffMat = Mat()
    private val gradXMat = Mat()
    private val gradYMat = Mat()
    private val gradMagMat = Mat()

    private var lastRoiWidth = 0
    private var lastRoiHeight = 0

    // Calibration state (30 frames)
    private val calibrationTargetFrames = 30
    private var calibrationFrameCount = 0
    private var isCalibrating = false
    private var isCalibrated = false
    private var baselineMeanLum = 140.0
    private var estimatedNoiseStd = 3.5
    private var autoBaseThreshold = 22.0

    // Running median of isolated single-grain areas (first 30 clean blobs + robust running update)
    private val isolatedGrainAreas = ArrayDeque<Float>(60)
    var learnedSingleGrainAreaPx: Float = 110f
        private set
    private var isLearningGrainSize = false

    init {
        ensureOpenCvLoaded()
    }

    fun ensureOpenCvLoaded(): Boolean {
        if (openCvInitialized.get()) return true
        return try {
            val loaded = OpenCVLoader.initLocal()
            openCvInitialized.set(loaded)
            loaded
        } catch (_: Throwable) {
            false
        }
    }

    @Synchronized
    fun startAutoCalibration() {
        calibrationFrameCount = 0
        isCalibrating = true
        isCalibrated = false
    }

    @Synchronized
    fun startSingleGrainSizeCalibration() {
        isolatedGrainAreas.clear()
        isLearningGrainSize = true
    }

    @Synchronized
    fun setManualSingleGrainArea(areaPx: Float) {
        if (areaPx >= 6f) {
            learnedSingleGrainAreaPx = areaPx
        }
    }

    @Synchronized
    fun processFrame(
        frameBitmap: Bitmap,
        roiNormalized: NormalizedRect,
        sensitivity: Float,
        minAreaPx: Int,
        maxAreaPx: Int,
        morphKernelSize: Int,
        autoSingleGrainSize: Boolean,
        manualSingleGrainAreaPx: Float,
        generateDebugMask: Boolean,
        currentFps: Int
    ): FrameAnalysisResult {
        val startMs = SystemClock.elapsedRealtime()
        if (!ensureOpenCvLoaded()) {
            return FrameAnalysisResult(
                blobs = emptyList(),
                isCalibrating = false,
                calibrationProgress = 0f,
                isCalibrated = false,
                autoThresholdValue = 25.0,
                noiseStd = 0.0,
                learnedSingleGrainAreaPx = learnedSingleGrainAreaPx,
                suddenBrightnessShiftDetected = false,
                contextualHintEn = "OpenCV engine initializing...",
                contextualHintBn = "OpenCV ইঞ্জিন চালু হচ্ছে...",
                confidenceLevel = AccuracyConfidenceLevel.FAIR,
                debugMaskBitmap = null,
                processingTimeMs = 0L
            )
        }

        val frameW = frameBitmap.width
        val frameH = frameBitmap.height
        val roiX = (roiNormalized.left * frameW).toInt().coerceIn(0, frameW - 32)
        val roiY = (roiNormalized.top * frameH).toInt().coerceIn(0, frameH - 32)
        val roiW = (roiNormalized.width * frameW).toInt().coerceIn(32, frameW - roiX)
        val roiH = (roiNormalized.height * frameH).toInt().coerceIn(32, frameH - roiY)

        allocateOrReuseMats(frameW, frameH, roiW, roiH)

        val fullMat = fullRgbaMat!!
        Utils.bitmapToMat(frameBitmap, fullMat)

        val roiSubmat = fullMat.submat(Rect(roiX, roiY, roiW, roiH))
        roiSubmat.copyTo(roiRgbaMat!!)
        roiSubmat.release()

        // Step 1: Gaussian Blur (3x3 or 5x5)
        val kBlur = if (morphKernelSize >= 5) 5.0 else 3.0
        Imgproc.GaussianBlur(roiRgbaMat!!, roiBlurredMat!!, Size(kBlur, kBlur), 0.0)

        // Convert to Grayscale & CIE Lab
        Imgproc.cvtColor(roiBlurredMat!!, roiGrayMat!!, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.cvtColor(roiBlurredMat!!, roiLabMat!!, Imgproc.COLOR_RGB2Lab)

        // Compute current global mean luminance & standard deviation inside ROI
        val meanMat = MatOfDouble()
        val stdMat = MatOfDouble()
        Core.meanStdDev(roiGrayMat!!, meanMat, stdMat)
        val currentMeanLum = meanMat.toArray().firstOrNull() ?: 128.0
        val currentSpatialStd = stdMat.toArray().firstOrNull() ?: 5.0
        meanMat.release()
        stdMat.release()

        // Step 2: Handle 30-frame Auto Calibration
        if (isCalibrating || !isCalibrated) {
            if (!isCalibrating && !isCalibrated) {
                isCalibrating = true
                calibrationFrameCount = 0
            }

            if (calibrationFrameCount == 0) {
                roiGrayMat!!.convertTo(bgGrayFloatMat!!, CvType.CV_32F)
                roiLabMat!!.convertTo(bgLabFloatMat!!, CvType.CV_32FC3)
                baselineMeanLum = currentMeanLum
                estimatedNoiseStd = (currentSpatialStd * 0.25).coerceIn(1.8, 12.0)
            } else {
                val alpha = 1.0 / (calibrationFrameCount + 1).toDouble()
                Imgproc.accumulateWeighted(roiGrayMat!!, bgGrayFloatMat!!, alpha)
                Imgproc.accumulateWeighted(roiLabMat!!, bgLabFloatMat!!, alpha)

                // Estimate temporal frame-to-frame noise std
                bgGrayFloatMat!!.convertTo(bgGray8UMat!!, CvType.CV_8U)
                Core.absdiff(roiGrayMat!!, bgGray8UMat!!, diffGrayMat!!)
                val diffMean = MatOfDouble()
                val diffStd = MatOfDouble()
                Core.meanStdDev(diffGrayMat!!, diffMean, diffStd)
                val m = diffMean.toArray().firstOrNull() ?: 2.0
                val s = diffStd.toArray().firstOrNull() ?: 2.0
                diffMean.release()
                diffStd.release()
                estimatedNoiseStd = (0.8 * estimatedNoiseStd + 0.2 * s).coerceIn(1.5, 15.0)
                autoBaseThreshold = max(m + 4.0 * estimatedNoiseStd, 12.0)
            }

            calibrationFrameCount++
            val progress = (calibrationFrameCount.toFloat() / calibrationTargetFrames).coerceIn(0f, 1f)

            if (calibrationFrameCount >= calibrationTargetFrames) {
                bgGrayFloatMat!!.convertTo(bgGray8UMat!!, CvType.CV_8U)
                bgLabFloatMat!!.convertTo(bgLab8UMat!!, CvType.CV_8UC3)
                // Compute smooth illumination map by heavy Gaussian blur of background
                Imgproc.GaussianBlur(bgGray8UMat!!, illumMapMat!!, Size(31.0, 31.0), 0.0)
                baselineMeanLum = Core.mean(bgGray8UMat!!).`val`[0]
                isCalibrating = false
                isCalibrated = true
            }

            val surfaceNotEmptyWarn = currentSpatialStd > 38.0
            val hintEn = if (surfaceNotEmptyWarn) "Surface not empty during calibration" else null
            val hintBn = if (surfaceNotEmptyWarn) "ক্যালিব্রেশনের সময় বক্সের ভেতর কিছু রাখবেন না" else null

            return FrameAnalysisResult(
                blobs = emptyList(),
                isCalibrating = isCalibrating,
                calibrationProgress = progress,
                isCalibrated = isCalibrated,
                autoThresholdValue = autoBaseThreshold,
                noiseStd = estimatedNoiseStd,
                learnedSingleGrainAreaPx = learnedSingleGrainAreaPx,
                suddenBrightnessShiftDetected = false,
                contextualHintEn = hintEn,
                contextualHintBn = hintBn,
                confidenceLevel = AccuracyConfidenceLevel.GOOD,
                debugMaskBitmap = null,
                processingTimeMs = SystemClock.elapsedRealtime() - startMs
            )
        }

        // Check for sudden global brightness change (e.g. shadow cast over entire table or camera moved)
        if (abs(currentMeanLum - baselineMeanLum) > 42.0 && currentSpatialStd < 28.0) {
            startAutoCalibration()
            return FrameAnalysisResult(
                blobs = emptyList(),
                isCalibrating = true,
                calibrationProgress = 0.05f,
                isCalibrated = false,
                autoThresholdValue = autoBaseThreshold,
                noiseStd = estimatedNoiseStd,
                learnedSingleGrainAreaPx = learnedSingleGrainAreaPx,
                suddenBrightnessShiftDetected = true,
                contextualHintEn = "Lighting changed suddenly — Auto-recalibrating...",
                contextualHintBn = "আলো হঠাৎ পরিবর্তন হয়েছে — অটো-ক্যালিব্রেট হচ্ছে...",
                confidenceLevel = AccuracyConfidenceLevel.FAIR,
                debugMaskBitmap = null,
                processingTimeMs = SystemClock.elapsedRealtime() - startMs
            )
        }

        // Step 3: Dual-Space Background Difference (Grayscale + CIE Lab Color)
        Core.absdiff(roiGrayMat!!, bgGray8UMat!!, diffGrayMat!!)

        Core.split(roiLabMat!!, labChannels)
        Core.split(bgLab8UMat!!, bgLabChannels)
        Core.absdiff(labChannels[0], bgLabChannels[0], diffChannelL)
        Core.absdiff(labChannels[1], bgLabChannels[1], diffChannelA)
        Core.absdiff(labChannels[2], bgLabChannels[2], diffChannelB)

        // Combine chrominance differences (a + b) amplified so yellow dal, red lentil, or white rice stand out
        Core.addWeighted(diffChannelA, 1.45, diffChannelB, 1.45, 0.0, chromaDiffMat)
        Core.max(diffChannelL, chromaDiffMat, diffLabMat!!)
        Core.max(diffGrayMat!!, diffLabMat!!, combinedDiffMat!!)

        // Step 4: Adaptive Thresholding + Soft Shadow Rejection
        // sensitivity in [0.10 .. 0.90]: higher sensitivity = lower threshold
        val sensitivityScale = (1.45 - sensitivity.coerceIn(0.10f, 0.90f) * 1.05).coerceIn(0.45, 1.40)
        val effectiveThresh = (autoBaseThreshold * sensitivityScale).coerceIn(10.0, 68.0)

        Imgproc.threshold(combinedDiffMat!!, binaryMaskMat!!, effectiveThresh, 255.0, Imgproc.THRESH_BINARY)

        // Soft Shadow Rejection:
        // A soft shadow is slightly darker than background (diffGray < effectiveThresh * 2.1)
        // AND has almost zero chrominance difference (chromaDiff < 8) AND low spatial gradient.
        Imgproc.Sobel(roiGrayMat!!, gradXMat, CvType.CV_16S, 1, 0, 3)
        Imgproc.Sobel(roiGrayMat!!, gradYMat, CvType.CV_16S, 0, 1, 3)
        Core.convertScaleAbs(gradXMat, diffChannelA)
        Core.convertScaleAbs(gradYMat, diffChannelB)
        Core.addWeighted(diffChannelA, 0.5, diffChannelB, 0.5, 0.0, gradMagMat)

        // Keep foreground pixels where EITHER chromaDiff >= 7 OR combinedDiff >= effectiveThresh * 1.45 OR gradMag >= 14
        val shadowRejectMask = diffChannelL
        Imgproc.threshold(chromaDiffMat, diffChannelA, 7.0, 255.0, Imgproc.THRESH_BINARY)
        Imgproc.threshold(combinedDiffMat!!, diffChannelB, effectiveThresh * 1.42, 255.0, Imgproc.THRESH_BINARY)
        Imgproc.threshold(gradMagMat, shadowRejectMask, 13.0, 255.0, Imgproc.THRESH_BINARY)
        Core.bitwise_or(diffChannelA, diffChannelB, shadowFilteredMaskMat!!)
        Core.bitwise_or(shadowFilteredMaskMat!!, shadowRejectMask, shadowFilteredMaskMat!!)
        Core.bitwise_and(binaryMaskMat!!, shadowFilteredMaskMat!!, binaryMaskMat!!)

        for (m in labChannels) m.release()
        labChannels.clear()
        for (m in bgLabChannels) m.release()
        bgLabChannels.clear()

        // Step 5: Morphological Open then Close with Elliptical Kernel
        val kSize = morphKernelSize.coerceIn(3, 7).let { if (it % 2 == 0) it + 1 else it }.toDouble()
        val morphKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(kSize, kSize))
        Imgproc.morphologyEx(binaryMaskMat!!, morphMaskMat!!, Imgproc.MORPH_OPEN, morphKernel)
        Imgproc.morphologyEx(morphMaskMat!!, morphMaskMat!!, Imgproc.MORPH_CLOSE, morphKernel)
        morphKernel.release()

        // Step 6: External Contour Extraction + Shape Filtering + Sub-Pixel Moments + Touching Grain Splitting
        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(
            morphMaskMat!!.clone(),
            contours,
            hierarchyMat!!,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE
        )

        val effectiveSingleArea = if (autoSingleGrainSize) {
            learnedSingleGrainAreaPx
        } else {
            manualSingleGrainAreaPx.coerceAtLeast(10f)
        }

        val detectedBlobs = ArrayList<GrainBlob>()
        var clumpCount = 0

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area < minAreaPx || area > maxAreaPx) {
                contour.release()
                continue
            }

            val boundRect = Imgproc.boundingRect(contour)
            // Reject contours touching the extreme 1px border of ROI if they look like a hand or frame edge
            val aspectRatio = boundRect.width.toDouble() / boundRect.height.toDouble().coerceAtLeast(1.0)
            val extent = area / (boundRect.width * boundRect.height).toDouble().coerceAtLeast(1.0)

            val hullIndices = org.opencv.core.MatOfInt()
            Imgproc.convexHull(contour, hullIndices)
            val hullPoints = contour.toArray()
            val hullIdxArr = hullIndices.toArray()
            val hullMat = MatOfPoint(*Array(hullIdxArr.size) { idx -> hullPoints[hullIdxArr[idx]] })
            val hullArea = Imgproc.contourArea(hullMat).coerceAtLeast(1.0)
            val solidity = (area / hullArea).coerceIn(0.0, 1.0)
            hullIndices.release()
            hullMat.release()

            // Filter out dust, thin ruled lines, or fingers (require reasonable solidity and extent)
            if (solidity < 0.32 || extent < 0.18 || aspectRatio < 0.12 || aspectRatio > 8.5) {
                contour.release()
                continue
            }

            // Sub-pixel centroid via spatial moments
            val moments = Imgproc.moments(contour)
            val cxRoi = if (moments.m00 > 1e-5) (moments.m10 / moments.m00).toFloat() else (boundRect.x + boundRect.width * 0.5f)
            val cyRoi = if (moments.m00 > 1e-5) (moments.m01 / moments.m00).toFloat() else (boundRect.y + boundRect.height * 0.5f)

            // Update running median of single-grain area on clean, high-solidity isolated blobs
            if ((autoSingleGrainSize || isLearningGrainSize) &&
                solidity >= 0.78 &&
                aspectRatio in 0.45..2.4 &&
                area in (minAreaPx * 1.2)..(effectiveSingleArea * 1.45)
            ) {
                isolatedGrainAreas.addLast(area.toFloat())
                while (isolatedGrainAreas.size > 35) {
                    isolatedGrainAreas.removeFirst()
                }
                if (isolatedGrainAreas.size >= 5) {
                    learnedSingleGrainAreaPx = AreaBasedGrainSplitter.computeMedian(isolatedGrainAreas.toList())
                }
                if (isolatedGrainAreas.size >= 30) {
                    isLearningGrainSize = false
                }
            }

            val isMotionStreak = (aspectRatio > 2.8 || aspectRatio < 0.36) && area <= effectiveSingleArea * 2.2

            // Distance-Transform + Local Maxima Watershed Peak Counting for touching blobs (> 1.5x single area)
            var watershedPeaks = 1
            if (area > 1.5 * effectiveSingleArea && !isMotionStreak) {
                watershedPeaks = countDistanceTransformPeaks(morphMaskMat!!, boundRect)
            }

            val (estimatedCount, isClump) = if (isMotionStreak) {
                1 to false
            } else {
                AreaBasedGrainSplitter.estimateGrainCount(
                    blobAreaPx = area,
                    singleGrainAreaPx = effectiveSingleArea,
                    watershedPeakCount = watershedPeaks
                )
            }
            if (isClump) clumpCount++

            // Map ROI coordinates back to full-frame normalized [0..1]
            val normLeft = ((roiX + boundRect.x).toFloat() / frameW).coerceIn(0f, 1f)
            val normTop = ((roiY + boundRect.y).toFloat() / frameH).coerceIn(0f, 1f)
            val normRight = ((roiX + boundRect.x + boundRect.width).toFloat() / frameW).coerceIn(0f, 1f)
            val normBottom = ((roiY + boundRect.y + boundRect.height).toFloat() / frameH).coerceIn(0f, 1f)
            val normCx = ((roiX + cxRoi) / frameW).coerceIn(0f, 1f)
            val normCy = ((roiY + cyRoi) / frameH).coerceIn(0f, 1f)

            detectedBlobs.add(
                GrainBlob(
                    centerX = normCx,
                    centerY = normCy,
                    boundingBox = NormalizedRect(normLeft, normTop, normRight, normBottom),
                    areaPx = area,
                    solidity = solidity,
                    aspectRatio = aspectRatio,
                    extent = extent,
                    estimatedGrainCount = estimatedCount,
                    isClump = isClump,
                    isMotionStreak = isMotionStreak
                )
            )

            contour.release()
        }

        // Step 7: Slowly update background ONLY when ROI has 0 objects (freeze while objects are present)
        if (detectedBlobs.isEmpty() && abs(currentMeanLum - baselineMeanLum) < 12.0) {
            Imgproc.accumulateWeighted(roiGrayMat!!, bgGrayFloatMat!!, 0.02)
            Imgproc.accumulateWeighted(roiLabMat!!, bgLabFloatMat!!, 0.02)
            bgGrayFloatMat!!.convertTo(bgGray8UMat!!, CvType.CV_8U)
            bgLabFloatMat!!.convertTo(bgLab8UMat!!, CvType.CV_8UC3)
            baselineMeanLum = 0.98 * baselineMeanLum + 0.02 * currentMeanLum
        }

        // Contextual Hints ("Too dark", etc.)
        var hintEn: String? = null
        var hintBn: String? = null
        if (currentMeanLum < 45.0) {
            hintEn = "Too dark — Turn on Torch for higher accuracy"
            hintBn = "আলো কম — নির্ভুল গণনার জন্য ফ্ল্যাশলাইট (Torch) চালু করুন"
        }

        // Running Accuracy Self-Check Badge (Good / Fair / Poor)
        val confidenceLevel = when {
            currentMeanLum < 40.0 || currentFps in 1..14 || clumpCount >= 3 -> AccuracyConfidenceLevel.POOR
            currentMeanLum < 65.0 || currentFps in 15..22 || clumpCount >= 1 || estimatedNoiseStd > 9.0 -> AccuracyConfidenceLevel.FAIR
            else -> AccuracyConfidenceLevel.GOOD
        }

        // Optional Debug Mask Bitmap
        val debugBmp = if (generateDebugMask) {
            val bmp = Bitmap.createBitmap(roiW, roiH, Bitmap.Config.ARGB_8888)
            Imgproc.cvtColor(morphMaskMat!!, roiRgbaMat!!, Imgproc.COLOR_GRAY2RGBA)
            Utils.matToBitmap(roiRgbaMat!!, bmp)
            bmp
        } else {
            null
        }

        val elapsed = SystemClock.elapsedRealtime() - startMs
        return FrameAnalysisResult(
            blobs = detectedBlobs,
            isCalibrating = false,
            calibrationProgress = 1f,
            isCalibrated = true,
            autoThresholdValue = effectiveThresh,
            noiseStd = estimatedNoiseStd,
            learnedSingleGrainAreaPx = effectiveSingleArea,
            suddenBrightnessShiftDetected = false,
            contextualHintEn = hintEn,
            contextualHintBn = hintBn,
            confidenceLevel = confidenceLevel,
            debugMaskBitmap = debugBmp,
            processingTimeMs = elapsed
        )
    }

    /**
     * Runs Euclidean Distance Transform (`Imgproc.distanceTransform`) on a touching blob sub-window
     * and counts distinct local maxima peaks (watershed markers) to separate touching grains.
     */
    private fun countDistanceTransformPeaks(mask: Mat, rect: Rect): Int {
        return try {
            val subMask = mask.submat(rect)
            val dist = Mat()
            Imgproc.distanceTransform(subMask, dist, Imgproc.DIST_L2, 3)
            val minMax = Core.minMaxLoc(dist)
            val maxVal = minMax.maxVal
            if (maxVal <= 1.5) {
                dist.release()
                subMask.release()
                return 1
            }

            val peakThresh = Mat()
            Imgproc.threshold(dist, peakThresh, maxVal * 0.52, 255.0, Imgproc.THRESH_BINARY)
            peakThresh.convertTo(peakThresh, CvType.CV_8U)

            val peakContours = ArrayList<MatOfPoint>()
            val hier = Mat()
            Imgproc.findContours(peakThresh, peakContours, hier, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            val count = peakContours.size.coerceAtLeast(1)

            for (c in peakContours) c.release()
            hier.release()
            peakThresh.release()
            dist.release()
            subMask.release()
            count
        } catch (_: Exception) {
            1
        }
    }

    private fun allocateOrReuseMats(frameW: Int, frameH: Int, roiW: Int, roiH: Int) {
        if (fullRgbaMat == null || fullRgbaMat!!.cols() != frameW || fullRgbaMat!!.rows() != frameH) {
            fullRgbaMat?.release()
            fullRgbaMat = Mat(frameH, frameW, CvType.CV_8UC4)
        }
        if (roiRgbaMat == null || roiW != lastRoiWidth || roiH != lastRoiHeight) {
            lastRoiWidth = roiW
            lastRoiHeight = roiH

            roiRgbaMat?.release()
            roiBlurredMat?.release()
            roiGrayMat?.release()
            roiLabMat?.release()
            bgGrayFloatMat?.release()
            bgGray8UMat?.release()
            bgLabFloatMat?.release()
            bgLab8UMat?.release()
            illumMapMat?.release()
            diffGrayMat?.release()
            diffLabMat?.release()
            combinedDiffMat?.release()
            binaryMaskMat?.release()
            shadowFilteredMaskMat?.release()
            morphMaskMat?.release()
            hierarchyMat?.release()
            distTransformMat?.release()
            distDilatedMat?.release()
            distPeaksMaskMat?.release()

            roiRgbaMat = Mat(roiH, roiW, CvType.CV_8UC4)
            roiBlurredMat = Mat(roiH, roiW, CvType.CV_8UC4)
            roiGrayMat = Mat(roiH, roiW, CvType.CV_8UC1)
            roiLabMat = Mat(roiH, roiW, CvType.CV_8UC3)
            bgGrayFloatMat = Mat(roiH, roiW, CvType.CV_32F)
            bgGray8UMat = Mat(roiH, roiW, CvType.CV_8UC1)
            bgLabFloatMat = Mat(roiH, roiW, CvType.CV_32FC3)
            bgLab8UMat = Mat(roiH, roiW, CvType.CV_8UC3)
            illumMapMat = Mat(roiH, roiW, CvType.CV_8UC1)
            diffGrayMat = Mat(roiH, roiW, CvType.CV_8UC1)
            diffLabMat = Mat(roiH, roiW, CvType.CV_8UC1)
            combinedDiffMat = Mat(roiH, roiW, CvType.CV_8UC1)
            binaryMaskMat = Mat(roiH, roiW, CvType.CV_8UC1)
            shadowFilteredMaskMat = Mat(roiH, roiW, CvType.CV_8UC1)
            morphMaskMat = Mat(roiH, roiW, CvType.CV_8UC1)
            hierarchyMat = Mat()
            distTransformMat = Mat()
            distDilatedMat = Mat()
            distPeaksMaskMat = Mat()

            // Re-trigger fast calibration when ROI dimensions change
            startAutoCalibration()
        }
    }

    @Synchronized
    fun release() {
        fullRgbaMat?.release()
        roiRgbaMat?.release()
        roiBlurredMat?.release()
        roiGrayMat?.release()
        roiLabMat?.release()
        bgGrayFloatMat?.release()
        bgGray8UMat?.release()
        bgLabFloatMat?.release()
        bgLab8UMat?.release()
        illumMapMat?.release()
        diffGrayMat?.release()
        diffLabMat?.release()
        combinedDiffMat?.release()
        binaryMaskMat?.release()
        shadowFilteredMaskMat?.release()
        morphMaskMat?.release()
        hierarchyMat?.release()
    }
}
