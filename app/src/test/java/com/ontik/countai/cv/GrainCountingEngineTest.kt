package com.ontik.countai.cv

import com.ontik.countai.billing.PlanManager
import com.ontik.countai.domain.model.CountingDirection
import com.ontik.countai.domain.model.GrainBlob
import com.ontik.countai.domain.model.NormalizedRect
import com.ontik.countai.domain.model.PlanState
import com.ontik.countai.domain.model.PlanTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive unit test suite verifying:
 * 1. Area-based & watershed grain splitting (1.5x touching grains and >6x clumps).
 * 2. Hungarian optimal assignment with gating distance.
 * 3. Segment-intersection line crossing (including high-speed jump over the line between two frames).
 * 4. Multi-object Kalman + Hungarian tracker lifecycle (tentative -> confirmed, retroactive crossing, zero double-counts).
 * 5. PlanManager 7-day offline grace period & Free daily limit gating.
 * 6. Daily limit anti-tamper reconciliation (`max(local, remote)`).
 */
class GrainCountingEngineTest {

    @Test
    fun testAreaBasedGrainSplittingAndClumpDetection() {
        val singleArea = 100f

        val (countSingle, clumpSingle) = AreaBasedGrainSplitter.estimateGrainCount(
            blobAreaPx = 110.0,
            singleGrainAreaPx = singleArea,
            watershedPeakCount = 1
        )
        assertEquals(1, countSingle)
        assertFalse(clumpSingle)

        val (countPair, clumpPair) = AreaBasedGrainSplitter.estimateGrainCount(
            blobAreaPx = 210.0,
            singleGrainAreaPx = singleArea,
            watershedPeakCount = 1
        )
        assertEquals(2, countPair)
        assertFalse(clumpPair)

        val (countTriplet, _) = AreaBasedGrainSplitter.estimateGrainCount(
            blobAreaPx = 260.0,
            singleGrainAreaPx = singleArea,
            watershedPeakCount = 3
        )
        assertEquals(3, countTriplet)

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

    @Test
    fun testPlanManagerOfflineGracePeriodAndDailyLimitAntiTamper() {
        val nowMs = 1_700_000_000_000L
        val threeDaysAgo = nowMs - (3L * 24L * 3600L * 1000L)
        val eightDaysAgo = nowMs - (8L * 24L * 3600L * 1000L)

        // Within 7-day offline grace period -> valid
        assertTrue(PlanManager.isWithinGracePeriod(threeDaysAgo, nowMs))

        // 8 days offline without verification -> grace period expired
        assertFalse(PlanManager.isWithinGracePeriod(eightDaysAgo, nowMs))

        // Anti-tamper daily limit reconciliation: clearing local data (local=0) while remote=420 keeps 420
        val reconciled = PlanManager.reconcileDailyCountValues(
            localDate = "2026-11-04",
            localCount = 0,
            remoteDate = "2026-11-04",
            remoteCount = 420,
            todayIso = "2026-11-04"
        )
        assertEquals(420, reconciled)

        // New day resets both
        val newDayReconciled = PlanManager.reconcileDailyCountValues(
            localDate = "2026-11-03",
            localCount = 500,
            remoteDate = "2026-11-03",
            remoteCount = 500,
            todayIso = "2026-11-04"
        )
        assertEquals(0, newDayReconciled)

        // Free PlanState daily limit check
        val freeAtLimit = PlanState(
            tier = PlanTier.FREE,
            todayUsedCount = 500,
            freeDailyLimit = 500
        )
        assertTrue(freeAtLimit.isDailyLimitReached)
        assertEquals(0, freeAtLimit.remainingToday)
    }
}
