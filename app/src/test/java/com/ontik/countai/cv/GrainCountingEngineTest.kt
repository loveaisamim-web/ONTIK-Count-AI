package com.ontik.countai.cv

import com.ontik.countai.domain.model.CountingDirection
import com.ontik.countai.domain.model.GrainBlob
import com.ontik.countai.domain.model.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Synthetic unit tests verifying:
 * 1. Area-based & watershed grain splitting (1.5x touching grains and >6x clumps).
 * 2. Hungarian optimal assignment with gating distance.
 * 3. Segment-intersection line crossing (including high-speed jump over the line between two frames).
 * 4. Multi-object Kalman + Hungarian tracker lifecycle (tentative -> confirmed, retroactive crossing, zero double-counts).
 */
class GrainCountingEngineTest {

    @Test
    fun testAreaBasedGrainSplittingAndClumpDetection() {
        val singleArea = 100f

        // Isolated single grain (1.1x single area) -> 1 grain, not a clump
        val (countSingle, clumpSingle) = AreaBasedGrainSplitter.estimateGrainCount(
            blobAreaPx = 110.0,
            singleGrainAreaPx = singleArea,
            watershedPeakCount = 1
        )
        assertEquals(1, countSingle)
        assertFalse(clumpSingle)

        // Touching pair of grains (2.1x single area) -> 2 grains, not a clump
        val (countPair, clumpPair) = AreaBasedGrainSplitter.estimateGrainCount(
            blobAreaPx = 210.0,
            singleGrainAreaPx = singleArea,
            watershedPeakCount = 1
        )
        assertEquals(2, countPair)
        assertFalse(clumpPair)

        // Watershed finds 3 peaks on a 2.6x blob -> never count less than max(areaEstimate, watershedPeaks)
        val (countTriplet, _) = AreaBasedGrainSplitter.estimateGrainCount(
            blobAreaPx = 260.0,
            singleGrainAreaPx = singleArea,
            watershedPeakCount = 3
        )
        assertEquals(3, countTriplet)

        // Large clump (7.2x single area) -> 7 grains, flagged as clump
        val (countClump, isClump) = AreaBasedGrainSplitter.estimateGrainCount(
            blobAreaPx = 720.0,
            singleGrainAreaPx = singleArea,
            watershedPeakCount = 2
        )
        assertEquals(7, countClump)
        assertTrue(isClump)
    }

    @Test
    fun testHungarianOptimalAssignment() {
        val costMatrix = arrayOf(
            floatArrayOf(0.02f, 0.25f),
            floatArrayOf(0.20f, 0.03f)
        )
        val gates = floatArrayOf(0.15f, 0.15f)
        val assignment = HungarianAlgorithm.solveAssignment(costMatrix, gates)
        assertEquals(0, assignment[0])
        assertEquals(1, assignment[1])
    }

    @Test
    fun testFastJumpingGrainSegmentLineIntersection() {
        // Grain jumps from Y=0.32 to Y=0.74 across Line A at Y=0.50 in a single frame step
        val crossSign = LineCrossingDetector.evaluateSegmentCrossing(
            prevX = 0.50f,
            prevY = 0.32f,
            currX = 0.50f,
            currY = 0.74f,
            linePos = 0.50f,
            hysteresis = 0.015f,
            direction = CountingDirection.TOP_TO_BOTTOM
        )
        assertEquals(1, crossSign)

        // Moving in opposite direction (Bottom -> Top) should NOT trigger when configured for Top -> Bottom
        val wrongWay = LineCrossingDetector.evaluateSegmentCrossing(
            prevX = 0.50f,
            prevY = 0.74f,
            currX = 0.50f,
            currY = 0.32f,
            linePos = 0.50f,
            hysteresis = 0.015f,
            direction = CountingDirection.TOP_TO_BOTTOM
        )
        assertEquals(0, wrongWay)
    }

    @Test
    fun testKalmanTrackerCountsFastGrainOnceAndRejectsBornPastLine() {
        val tracker = KalmanHungarianTracker()
        val lineA = 0.50f

        fun makeBlob(x: Float, y: Float, count: Int = 1): GrainBlob {
            return GrainBlob(
                centerX = x,
                centerY = y,
                boundingBox = NormalizedRect(x - 0.02f, y - 0.02f, x + 0.02f, y + 0.02f),
                areaPx = 100.0 * count,
                solidity = 0.9,
                aspectRatio = 1.0,
                extent = 0.75,
                estimatedGrainCount = count,
                isClump = count > 6,
                isMotionStreak = false
            )
        }

        // Frame 1 (t=100ms): Grain 1 spawns above line at Y=0.30, Grain 2 spawns already past line at Y=0.65
        val out1 = tracker.step(
            blobs = listOf(makeBlob(0.4f, 0.30f), makeBlob(0.7f, 0.65f)),
            timestampMs = 100L,
            baseGatingDistance = 0.18f,
            lineAPos = lineA,
            lineBPos = null,
            hysteresis = 0.015f,
            direction = CountingDirection.TOP_TO_BOTTOM,
            allowBidirectionalReCount = false,
            isCountingActive = true
        )
        assertEquals(0, out1.newlyCountedLineA)

        // Frame 2 (t=133ms): Grain 1 jumps across line to Y=0.58 (confirmed hit #2 -> counted +1)
        // Grain 2 moves further down to Y=0.78 (born past line -> NOT counted)
        val out2 = tracker.step(
            blobs = listOf(makeBlob(0.4f, 0.58f), makeBlob(0.7f, 0.78f)),
            timestampMs = 133L,
            baseGatingDistance = 0.18f,
            lineAPos = lineA,
            lineBPos = null,
            hysteresis = 0.015f,
            direction = CountingDirection.TOP_TO_BOTTOM,
            allowBidirectionalReCount = false,
            isCountingActive = true
        )
        assertEquals(1, out2.newlyCountedLineA)

        // Frame 3 (t=166ms): Grain 1 continues to Y=0.72 -> must NOT be double counted
        val out3 = tracker.step(
            blobs = listOf(makeBlob(0.4f, 0.72f)),
            timestampMs = 166L,
            baseGatingDistance = 0.18f,
            lineAPos = lineA,
            lineBPos = null,
            hysteresis = 0.015f,
            direction = CountingDirection.TOP_TO_BOTTOM,
            allowBidirectionalReCount = false,
            isCountingActive = true
        )
        assertEquals(0, out3.newlyCountedLineA)
    }
}
