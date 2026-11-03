package com.ontik.countai.cv

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Pure, deterministic area-based & watershed-assisted grain splitter.
 *
 * Rules:
 * - Maintains a robust running median of the first 30 clean isolated grain areas (and keeps updating).
 * - If `blobArea <= 1.5 * singleGrainArea`: counts as 1 grain.
 * - If `blobArea > 1.5 * singleGrainArea`: estimates `N = max(1, round(blobArea / singleGrainArea))`.
 * - If watershed peak count `W` is available and `W >= N`, uses the watershed peaks;
 *   otherwise generates `N` virtual sub-centroids inside the blob so a touching group or clump
 *   is NEVER counted as less than its estimated `N`.
 * - If `blobArea > 6.0 * singleGrainArea`: flags the blob as a `clump` (`isClump = true`).
 */
object AreaBasedGrainSplitter {

    fun estimateGrainCount(
        blobAreaPx: Double,
        singleGrainAreaPx: Float,
        watershedPeakCount: Int = 1
    ): Pair<Int, Boolean> {
        val refArea = singleGrainAreaPx.coerceAtLeast(8f).toDouble()
        val isClump = blobAreaPx > 6.0 * refArea

        if (blobAreaPx <= 1.5 * refArea) {
            return 1 to false
        }

        val estimatedByArea = (blobAreaPx / refArea).roundToInt().coerceAtLeast(2)
        val finalCount = max(estimatedByArea, watershedPeakCount)
        return finalCount to isClump
    }

    /**
     * Generates `count` deterministic sub-centroids around `(centerX, centerY)` within radius `(boxW, boxH)`
     * when watershed finds fewer peaks than the area ratio `N`.
     */
    fun generateVirtualSubCentroids(
        centerX: Float,
        centerY: Float,
        boxW: Float,
        boxH: Float,
        count: Int
    ): List<Pair<Float, Float>> {
        if (count <= 1) return listOf(centerX to centerY)
        val result = ArrayList<Pair<Float, Float>>(count)
        val rx = (boxW * 0.22f).coerceAtLeast(0.004f)
        val ry = (boxH * 0.22f).coerceAtLeast(0.004f)
        for (i in 0 until count) {
            val angle = (2.0 * Math.PI * i) / count
            val px = (centerX + (rx * cos(angle)).toFloat()).coerceIn(0f, 1f)
            val py = (centerY + (ry * sin(angle)).toFloat()).coerceIn(0f, 1f)
            result.add(px to py)
        }
        return result
    }

    /**
     * Computes the median of a list of positive floats.
     */
    fun computeMedian(values: List<Float>): Float {
        if (values.isEmpty()) return 100f
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) * 0.5f
        } else {
            sorted[mid]
        }
    }
}
