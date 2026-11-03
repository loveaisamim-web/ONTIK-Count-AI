package com.ontik.countai.ai

import android.graphics.Bitmap
import android.os.SystemClock
import com.ontik.countai.domain.model.DetectionResult
import com.ontik.countai.domain.model.NormalizedRect
import kotlin.math.max
import kotlin.math.min

/**
 * Real-time On-Device Small-Object & Physical Item Vision Detector.
 *
 * Uses multi-cue local background estimation (integral image box filtering),
 * luminance + chrominance contrast segmentation, morphological smoothing, and
 * 8-connected component labeling so physical objects (peanuts, seeds, beans, grains, coins, pills)
 * placed on or passing over a background surface are accurately segmented and bounded in real time.
 */
object BuiltInSmallObjectDetector {

    private const val WORK_SIZE = 240

    fun detectObjects(
        bitmap: Bitmap,
        confidenceThreshold: Float,
        iouThreshold: Float,
        countingZone: NormalizedRect?
    ): InferenceOutput {
        val startMs = SystemClock.elapsedRealtime()

        val scaled = if (bitmap.width == WORK_SIZE && bitmap.height == WORK_SIZE) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, WORK_SIZE, WORK_SIZE, true)
        }

        val w = WORK_SIZE
        val h = WORK_SIZE
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled !== bitmap) {
            scaled.recycle()
        }

        val lum = IntArray(w * h)
        val sat = IntArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            lum[i] = (r * 77 + g * 150 + b * 29) shr 8
            val maxC = max(r, max(g, b))
            val minC = min(r, min(g, b))
            sat[i] = maxC - minC
        }

        val intLum = IntArray((w + 1) * (h + 1))
        val intSat = IntArray((w + 1) * (h + 1))
        val stride = w + 1

        for (y in 1..h) {
            var rowSumL = 0
            var rowSumS = 0
            val srcOffset = (y - 1) * w
            val dstOffset = y * stride
            val prevOffset = (y - 1) * stride
            for (x in 1..w) {
                rowSumL += lum[srcOffset + x - 1]
                rowSumS += sat[srcOffset + x - 1]
                intLum[dstOffset + x] = intLum[prevOffset + x] + rowSumL
                intSat[dstOffset + x] = intSat[prevOffset + x] + rowSumS
            }
        }

        val minX = ((countingZone?.left ?: 0.04f) * w).toInt().coerceIn(4, w - 5)
        val maxX = ((countingZone?.right ?: 0.96f) * w).toInt().coerceIn(minX + 1, w - 4)
        val minY = ((countingZone?.top ?: 0.04f) * h).toInt().coerceIn(4, h - 5)
        val maxY = ((countingZone?.bottom ?: 0.96f) * h).toInt().coerceIn(minY + 1, h - 4)

        val contrastThreshold = (14f + (confidenceThreshold.coerceIn(0.10f, 0.90f) * 32f)).toInt()

        val foregroundMask = BooleanArray(w * h)
        val contrastScoreMap = IntArray(w * h)
        val radius = 24

        for (y in minY..maxY) {
            val y0 = (y - radius).coerceAtLeast(0)
            val y1 = (y + radius).coerceAtMost(h)
            val rowOff = y * w
            for (x in minX..maxX) {
                val x0 = (x - radius).coerceAtLeast(0)
                val x1 = (x + radius).coerceAtMost(w)
                val area = (x1 - x0) * (y1 - y0)

                val sumL = intLum[y1 * stride + x1] - intLum[y0 * stride + x1] - intLum[y1 * stride + x0] + intLum[y0 * stride + x0]
                val sumS = intSat[y1 * stride + x1] - intSat[y0 * stride + x1] - intSat[y1 * stride + x0] + intSat[y0 * stride + x0]

                val bgL = sumL / area
                val bgS = sumS / area

                val curL = lum[rowOff + x]
                val curS = sat[rowOff + x]

                val darkDiff = (bgL - curL).coerceAtLeast(0)
                val brightDiff = (curL - bgL).coerceAtLeast(0)
                val satDiff = (curS - bgS).coerceAtLeast(0)

                val combinedScore = max(darkDiff, brightDiff * 8 / 10) + (satDiff * 7 / 10)
                if (combinedScore >= contrastThreshold) {
                    foregroundMask[rowOff + x] = true
                    contrastScoreMap[rowOff + x] = combinedScore
                }
            }
        }

        // Morphological 3x3 majority erosion to ignore thin handwriting lines while keeping solid objects
        val eroded = BooleanArray(w * h)
        for (y in (minY + 1) until maxY) {
            val rowOff = y * w
            for (x in (minX + 1) until maxX) {
                if (!foregroundMask[rowOff + x]) continue
                var neighbors = 0
                for (dy in -1..1) {
                    val nr = (y + dy) * w
                    for (dx in -1..1) {
                        if (foregroundMask[nr + x + dx]) neighbors++
                    }
                }
                if (neighbors >= 6) {
                    eroded[rowOff + x] = true
                }
            }
        }

        val visited = BooleanArray(w * h)
        val queueX = IntArray(w * h)
        val queueY = IntArray(w * h)
        val rawDetections = mutableListOf<DetectionResult>()

        val minComponentPixels = 14
        val maxComponentPixels = (w * h * 0.35f).toInt()

        for (y in (minY + 1) until maxY) {
            for (x in (minX + 1) until maxX) {
                val startIdx = y * w + x
                if (!eroded[startIdx] || visited[startIdx]) continue

                var head = 0
                var tail = 0
                queueX[tail] = x
                queueY[tail] = y
                tail++
                visited[startIdx] = true

                var compMinX = x
                var compMaxX = x
                var compMinY = y
                var compMaxY = y
                var scoreSum = 0L

                while (head < tail) {
                    val cx = queueX[head]
                    val cy = queueY[head]
                    head++

                    if (cx < compMinX) compMinX = cx
                    if (cx > compMaxX) compMaxX = cx
                    if (cy < compMinY) compMinY = cy
                    if (cy > compMaxY) compMaxY = cy
                    scoreSum += contrastScoreMap[cy * w + cx]

                    for (dy in -2..2) {
                        val ny = cy + dy
                        if (ny < minY || ny > maxY) continue
                        val nRow = ny * w
                        for (dx in -2..2) {
                            val nx = cx + dx
                            if (nx < minX || nx > maxX) continue
                            val nIdx = nRow + nx
                            if (eroded[nIdx] && !visited[nIdx]) {
                                visited[nIdx] = true
                                queueX[tail] = nx
                                queueY[tail] = ny
                                tail++
                            }
                        }
                    }
                }

                val pixelCount = tail
                if (pixelCount in minComponentPixels..maxComponentPixels) {
                    val boxW = compMaxX - compMinX + 1
                    val boxH = compMaxY - compMinY + 1
                    val aspect = boxW.toFloat() / boxH.toFloat().coerceAtLeast(1f)
                    val fillRatio = pixelCount.toFloat() / (boxW * boxH).toFloat().coerceAtLeast(1f)

                    if (aspect in 0.16f..6.2f && fillRatio >= 0.22f) {
                        val avgContrast = (scoreSum / pixelCount).toFloat()
                        val normConf = ((avgContrast / 95f).coerceIn(0.35f, 0.99f))

                        val padX = max(2, boxW / 8)
                        val padY = max(2, boxH / 8)
                        val normRect = NormalizedRect(
                            left = ((compMinX - padX).toFloat() / w).coerceIn(0f, 1f),
                            top = ((compMinY - padY).toFloat() / h).coerceIn(0f, 1f),
                            right = ((compMaxX + padX).toFloat() / w).coerceIn(0f, 1f),
                            bottom = ((compMaxY + padY).toFloat() / h).coerceIn(0f, 1f)
                        )

                        rawDetections.add(
                            DetectionResult(
                                boundingBox = normRect,
                                confidence = normConf,
                                classIndex = 0,
                                label = "object"
                            )
                        )
                    }
                }
            }
        }

        val filtered = applyNms(rawDetections, iouThreshold)
        val elapsed = SystemClock.elapsedRealtime() - startMs

        return InferenceOutput(
            detections = filtered,
            lowConfidenceCandidateCount = 0,
            inferenceTimeMs = elapsed,
            statusMessage = null
        )
    }

    private fun applyNms(
        candidates: List<DetectionResult>,
        iouThreshold: Float
    ): List<DetectionResult> {
        if (candidates.isEmpty()) return emptyList()
        val sorted = candidates.sortedByDescending { it.confidence }
        val selected = ArrayList<DetectionResult>()
        val active = BooleanArray(sorted.size) { true }

        for (i in sorted.indices) {
            if (!active[i]) continue
            val a = sorted[i]
            selected.add(a)
            for (j in (i + 1) until sorted.size) {
                if (!active[j]) continue
                val b = sorted[j]
                val interLeft = max(a.boundingBox.left, b.boundingBox.left)
                val interTop = max(a.boundingBox.top, b.boundingBox.top)
                val interRight = min(a.boundingBox.right, b.boundingBox.right)
                val interBottom = min(a.boundingBox.bottom, b.boundingBox.bottom)
                val iw = (interRight - interLeft).coerceAtLeast(0f)
                val ih = (interBottom - interTop).coerceAtLeast(0f)
                val inter = iw * ih
                if (inter > 0f) {
                    val union = a.boundingBox.width * a.boundingBox.height +
                        b.boundingBox.width * b.boundingBox.height - inter
                    if (union > 0f && inter / union > iouThreshold) {
                        active[j] = false
                    }
                }
            }
        }
        return selected
    }
}
