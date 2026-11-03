package com.ontik.countai.cv

import com.ontik.countai.domain.model.CountingDirection
import com.ontik.countai.domain.model.GrainBlob
import com.ontik.countai.domain.model.NormalizedRect
import com.ontik.countai.domain.model.TrackLifecycleState
import com.ontik.countai.domain.model.TrackedGrain
import kotlin.math.hypot
import kotlin.math.max

/**
 * Multi-Object Kalman + Hungarian Tracker tailored for high-speed falling grains/seeds.
 *
 * Features:
 * - 4-state Constant-Velocity Kalman filter `[x, y, vx, vy]` per track using real frame `dtSeconds`.
 * - Optimal Hungarian assignment (`HungarianAlgorithm`) with speed-scaled gating distance
 *   (automatically widened when a frame drop `dt > 2 * expectedDt` is detected).
 * - Track states:
 *   - `TENTATIVE` (needs 2 consecutive hits to confirm; if it crosses the line while tentative,
 *     records `tentativeCrossedLineA = true` and counts retroactively upon confirmation within 3 frames).
 *   - `CONFIRMED` (active track).
 *   - `LOST` (kept up to 8 frames or 150 ms via Kalman prediction).
 * - Split/merge inheritance: when a counted clump splits into nearby child blobs, children inherit
 *   `countedLineA = true` proportionally so they are never double-counted.
 * - Dual-line support: Primary Counting Line A + optional Verification Line B.
 */
class KalmanHungarianTracker {

    private class InternalKalmanTrack(
        val trackId: Int,
        var x: Float,
        var y: Float,
        var prevX: Float,
        var prevY: Float,
        var vx: Float = 0f,
        var vy: Float = 0f,
        var posVar: Float = 0.01f,
        var velVar: Float = 0.05f,
        var boundingBox: NormalizedRect,
        var estimatedGrainCount: Int,
        var isClump: Boolean,
        var isMotionStreak: Boolean,
        var consecutiveHits: Int = 1,
        var ageFrames: Int = 1,
        var missedFrames: Int = 0,
        var lastSeenTimestampMs: Long,
        var bornPastLineA: Boolean,
        var countedLineA: Boolean = false,
        var countedLineB: Boolean = false,
        var tentativeCrossedLineA: Boolean = false,
        var tentativeCrossSign: Int = 0,
        var lastCrossDirectionSign: Int = 0,
        var countedFlashTimestampMs: Long = 0L,
        val trail: ArrayDeque<Pair<Float, Float>> = ArrayDeque()
    ) {
        init {
            trail.addLast(x to y)
        }

        val lifecycleState: TrackLifecycleState
            get() = when {
                missedFrames > 0 -> TrackLifecycleState.LOST
                consecutiveHits >= 2 -> TrackLifecycleState.CONFIRMED
                else -> TrackLifecycleState.TENTATIVE
            }

        fun predict(dtSeconds: Float) {
            val dt = dtSeconds.coerceIn(0.004f, 0.25f)
            prevX = x
            prevY = y
            x = (x + vx * dt).coerceIn(0f, 1f)
            y = (y + vy * dt).coerceIn(0f, 1f)
            posVar += velVar * dt + 0.002f
            velVar += 0.01f
        }

        fun update(blob: GrainBlob, dtSeconds: Float, timestampMs: Long) {
            val dt = dtSeconds.coerceIn(0.004f, 0.25f)
            val measNoise = 0.005f
            val kalmanGainPos = posVar / (posVar + measNoise)
            val residualX = blob.centerX - x
            val residualY = blob.centerY - y

            val newX = (x + kalmanGainPos * residualX).coerceIn(0f, 1f)
            val newY = (y + kalmanGainPos * residualY).coerceIn(0f, 1f)

            val measuredVx = ( blob.centerX - prevX ) / dt
            val measuredVy = ( blob.centerY - prevY ) / dt
            vx = if (ageFrames == 1) measuredVx else (0.65f * measuredVx + 0.35f * vx)
            vy = if (ageFrames == 1) measuredVy else (0.65f * measuredVy + 0.35f * vy)

            x = newX
            y = newY
            posVar *= (1f - kalmanGainPos)

            boundingBox = blob.boundingBox
            estimatedGrainCount = max(estimatedGrainCount, blob.estimatedGrainCount)
            isClump = blob.isClump
            isMotionStreak = blob.isMotionStreak
            consecutiveHits += 1
            ageFrames += 1
            missedFrames = 0
            lastSeenTimestampMs = timestampMs

            trail.addLast(x to y)
            while (trail.size > 8) {
                trail.removeFirst()
            }
        }

        fun toTrackedGrain(): TrackedGrain = TrackedGrain(
            trackId = trackId,
            state = lifecycleState,
            currentX = x,
            currentY = y,
            previousX = prevX,
            previousY = prevY,
            velocityX = vx,
            velocityY = vy,
            boundingBox = boundingBox,
            estimatedGrainCount = estimatedGrainCount,
            isClump = isClump,
            isMotionStreak = isMotionStreak,
            consecutiveHits = consecutiveHits,
            missedFrames = missedFrames,
            lastSeenTimestampMs = lastSeenTimestampMs,
            bornPastLineA = bornPastLineA,
            countedLineA = countedLineA,
            countedLineB = countedLineB,
            tentativeCrossedLineA = tentativeCrossedLineA,
            lastCrossDirectionSign = lastCrossDirectionSign,
            countedFlashTimestampMs = countedFlashTimestampMs,
            trailPoints = trail.toList()
        )
    }

    data class TrackerStepOutput(
        val activeTracks: List<TrackedGrain>,
        val newlyCountedLineA: Int,
        val newlyCountedLineB: Int,
        val frameDropDetected: Boolean
    )

    private var nextTrackId = 1
    private val tracks = mutableListOf<InternalKalmanTrack>()
    private var lastFrameTimestampMs: Long = 0L

    @Synchronized
    fun reset() {
        tracks.clear()
        nextTrackId = 1
        lastFrameTimestampMs = 0L
    }

    @Synchronized
    fun step(
        blobs: List<GrainBlob>,
        timestampMs: Long,
        baseGatingDistance: Float,
        lineAPos: Float,
        lineBPos: Float?,
        hysteresis: Float,
        direction: CountingDirection,
        allowBidirectionalReCount: Boolean,
        isCountingActive: Boolean
    ): TrackerStepOutput {
        val dtMs = if (lastFrameTimestampMs > 0L && timestampMs > lastFrameTimestampMs) {
            timestampMs - lastFrameTimestampMs
        } else {
            33L
        }
        lastFrameTimestampMs = timestampMs
        val dtSeconds = (dtMs / 1000f).coerceIn(0.005f, 0.25f)
        val frameDropDetected = dtMs > 68L // > 2x expected 33ms (30fps)

        // 1. Predict all existing tracks using real dt
        for (track in tracks) {
            track.predict(dtSeconds)
        }

        // 2. Build Cost Matrix [numTracks][numBlobs] & Speed-Scaled Gating Distances
        val numTracks = tracks.size
        val numBlobs = blobs.size
        val costMatrix = Array(numTracks) { FloatArray(numBlobs) }
        val gatingArray = FloatArray(numTracks)

        for (tIdx in 0 until numTracks) {
            val tr = tracks[tIdx]
            val speedNormPerSec = hypot(tr.vx.toDouble(), tr.vy.toDouble()).toFloat()
            val speedBonus = (speedNormPerSec * dtSeconds * 0.85f).coerceAtMost(0.22f)
            val dropMultiplier = if (frameDropDetected) 1.65f else 1.0f
            gatingArray[tIdx] = ((baseGatingDistance + speedBonus) * dropMultiplier).coerceIn(0.05f, 0.45f)

            for (bIdx in 0 until numBlobs) {
                val b = blobs[bIdx]
                val distPred = hypot((tr.x - b.centerX).toDouble(), (tr.y - b.centerY).toDouble()).toFloat()
                val distPrev = hypot((tr.prevX - b.centerX).toDouble(), (tr.prevY - b.centerY).toDouble()).toFloat()
                costMatrix[tIdx][bIdx] = minOf(distPred, distPrev)
            }
        }

        // 3. Optimal Assignment via Hungarian Algorithm
        val assignment = HungarianAlgorithm.solveAssignment(costMatrix, gatingArray)
        val matchedBlobIndices = HashSet<Int>()

        var newlyCountedA = 0
        var newlyCountedB = 0

        for (tIdx in 0 until numTracks) {
            val matchedBIdx = assignment[tIdx]
            val track = tracks[tIdx]
            if (matchedBIdx >= 0) {
                matchedBlobIndices.add(matchedBIdx)
                val blob = blobs[matchedBIdx]
                track.update(blob, dtSeconds, timestampMs)

                // Evaluate Line A crossing on segment (prevX, prevY) -> (x, y)
                if (isCountingActive) {
                    val crossSignA = LineCrossingDetector.evaluateSegmentCrossing(
                        prevX = track.prevX,
                        prevY = track.prevY,
                        currX = track.x,
                        currY = track.y,
                        linePos = lineAPos,
                        hysteresis = hysteresis,
                        direction = direction
                    )

                    val canCountOnLineA = if (!track.countedLineA) {
                        !track.bornPastLineA
                    } else {
                        direction == CountingDirection.BIDIRECTIONAL &&
                            allowBidirectionalReCount &&
                            crossSignA != 0 &&
                            crossSignA != track.lastCrossDirectionSign
                    }

                    if (crossSignA != 0 && canCountOnLineA) {
                        if (track.consecutiveHits >= 2 || track.isMotionStreak) {
                            track.countedLineA = true
                            track.lastCrossDirectionSign = crossSignA
                            track.countedFlashTimestampMs = timestampMs
                            newlyCountedA += track.estimatedGrainCount
                        } else {
                            // Tentative track crossed: flag it for retroactive counting once confirmed within 3 frames
                            track.tentativeCrossedLineA = true
                            track.tentativeCrossSign = crossSignA
                        }
                    } else if (track.tentativeCrossedLineA && !track.countedLineA && track.consecutiveHits >= 2 && track.ageFrames <= 4) {
                        // Retroactive confirmation of tentative track that crossed on frame 1->2
                        track.countedLineA = true
                        track.tentativeCrossedLineA = false
                        track.lastCrossDirectionSign = track.tentativeCrossSign
                        track.countedFlashTimestampMs = timestampMs
                        newlyCountedA += track.estimatedGrainCount
                    }

                    // Optional Verification Line B
                    if (lineBPos != null && !track.countedLineB) {
                        val crossSignB = LineCrossingDetector.evaluateSegmentCrossing(
                            prevX = track.prevX,
                            prevY = track.prevY,
                            currX = track.x,
                            currY = track.y,
                            linePos = lineBPos,
                            hysteresis = hysteresis,
                            direction = direction
                        )
                        if (crossSignB != 0 && track.consecutiveHits >= 2) {
                            track.countedLineB = true
                            newlyCountedB += track.estimatedGrainCount
                        }
                    }
                }
            } else {
                track.missedFrames += 1
            }
        }

        // 4. Prune LOST tracks older than 8 frames or 150 ms
        val iterator = tracks.iterator()
        while (iterator.hasNext()) {
            val tr = iterator.next()
            val lostDurationMs = timestampMs - tr.lastSeenTimestampMs
            if (tr.missedFrames > 8 || (tr.missedFrames > 0 && lostDurationMs > 150L)) {
                iterator.remove()
            }
        }

        // 5. Spawn new tracks for unmatched blobs (with Clump-Split inheritance check)
        for (bIdx in 0 until numBlobs) {
            if (bIdx in matchedBlobIndices) continue
            val blob = blobs[bIdx]

            // Check if this unmatched blob split off from a nearby already-counted clump track
            val parentCountedClump = tracks.firstOrNull { tr ->
                tr.countedLineA &&
                    (tr.estimatedGrainCount > 1 || tr.isClump) &&
                    hypot((tr.x - blob.centerX).toDouble(), (tr.y - blob.centerY).toDouble()) < baseGatingDistance * 1.1f
            }

            val inheritCounted = parentCountedClump != null
            if (parentCountedClump != null && parentCountedClump.estimatedGrainCount > 1) {
                parentCountedClump.estimatedGrainCount -= 1
            }

            val bornPast = LineCrossingDetector.isBornPastLine(
                startX = blob.centerX,
                startY = blob.centerY,
                linePos = lineAPos,
                hysteresis = hysteresis,
                direction = direction
            )

            tracks.add(
                InternalKalmanTrack(
                    trackId = nextTrackId++,
                    x = blob.centerX,
                    y = blob.centerY,
                    prevX = blob.centerX,
                    prevY = blob.centerY,
                    boundingBox = blob.boundingBox,
                    estimatedGrainCount = blob.estimatedGrainCount,
                    isClump = blob.isClump,
                    isMotionStreak = blob.isMotionStreak,
                    lastSeenTimestampMs = timestampMs,
                    bornPastLineA = bornPast,
                    countedLineA = inheritCounted
                )
            )
        }

        val visibleTracks = tracks
            .filter { it.missedFrames <= 2 }
            .map { it.toTrackedGrain() }

        return TrackerStepOutput(
            activeTracks = visibleTracks,
            newlyCountedLineA = newlyCountedA,
            newlyCountedLineB = newlyCountedB,
            frameDropDetected = frameDropDetected
        )
    }
}
