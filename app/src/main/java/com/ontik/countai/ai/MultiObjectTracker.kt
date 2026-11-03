package com.ontik.countai.ai

import com.ontik.countai.domain.model.DetectionResult
import com.ontik.countai.domain.model.NormalizedRect
import com.ontik.countai.domain.model.TrackedObject
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * High-speed Multi-Object Tracker (SORT / ByteTrack inspired velocity-predictive tracker)
 * designed for small falling seeds, grains, beans, and objects moving at variable speeds.
 *
 * Key guarantees:
 * - Assigns monotonically increasing unique `trackId` to each physical object.
 * - Uses constant-velocity state prediction (`predictedX = currentX + velocityX`) so fast-falling
 *   seeds are accurately matched across frames even when displacement exceeds bounding box width.
 * - Combines IoU + normalized Euclidean center distance cost matrix for small objects.
 * - Preserves `hasBeenCounted` state across the entire lifetime of a track ID so the same object
 *   can never be counted twice.
 */
class MultiObjectTracker(
    private val maxMissedFrames: Int = 6,
    private val maxMatchDistanceNormalized: Float = 0.18f
) {

    private class InternalTrack(
        val trackId: Int,
        var boundingBox: NormalizedRect,
        var confidence: Float,
        var label: String,
        var previousCenterX: Float,
        var previousCenterY: Float,
        var currentCenterX: Float,
        var currentCenterY: Float,
        var velocityX: Float = 0f,
        var velocityY: Float = 0f,
        var ageFrames: Int = 1,
        var missedFrames: Int = 0,
        var hasBeenCounted: Boolean = false,
        var crossedTimestampMs: Long? = null
    ) {
        fun predictedCenterX(): Float = currentCenterX + velocityX
        fun predictedCenterY(): Float = currentCenterY + velocityY

        fun toTrackedObject(): TrackedObject = TrackedObject(
            trackId = trackId,
            boundingBox = boundingBox,
            confidence = confidence,
            label = label,
            previousCenterX = previousCenterX,
            previousCenterY = previousCenterY,
            currentCenterX = currentCenterX,
            currentCenterY = currentCenterY,
            velocityX = velocityX,
            velocityY = velocityY,
            ageFrames = ageFrames,
            missedFrames = missedFrames,
            hasBeenCounted = hasBeenCounted,
            crossedTimestampMs = crossedTimestampMs
        )
    }

    private var nextTrackId: Int = 1
    private val activeTracks = mutableListOf<InternalTrack>()

    @Synchronized
    fun reset() {
        activeTracks.clear()
        nextTrackId = 1
    }

    @Synchronized
    fun markTrackAsCounted(trackId: Int, timestampMs: Long) {
        activeTracks.firstOrNull { it.trackId == trackId }?.let { track ->
            track.hasBeenCounted = true
            track.crossedTimestampMs = timestampMs
        }
    }

    /**
     * Updates active tracks with the current frame's [detections] and returns all active [TrackedObject]s.
     */
    @Synchronized
    fun update(detections: List<DetectionResult>): List<TrackedObject> {
        if (activeTracks.isEmpty() && detections.isEmpty()) {
            return emptyList()
        }

        val matchedTrackIndices = HashSet<Int>()
        val matchedDetectionIndices = HashSet<Int>()

        // Build cost pairs (trackIdx, detIdx, cost) using velocity-predicted position + IoU
        data class MatchCandidate(val trackIdx: Int, val detIdx: Int, val cost: Float)
        val candidates = ArrayList<MatchCandidate>()

        for (tIdx in activeTracks.indices) {
            val track = activeTracks[tIdx]
            val predX = track.predictedCenterX()
            val predY = track.predictedCenterY()

            for (dIdx in detections.indices) {
                val det = detections[dIdx]
                val detX = det.boundingBox.centerX
                val detY = det.boundingBox.centerY

                val centerDist = hypot((predX - detX).toDouble(), (predY - detY).toDouble()).toFloat()
                val rawDist = hypot((track.currentCenterX - detX).toDouble(), (track.currentCenterY - detY).toDouble()).toFloat()
                val effectiveDist = min(centerDist, rawDist)

                val iou = computeIoU(track.boundingBox, det.boundingBox)
                if (effectiveDist <= maxMatchDistanceNormalized || iou >= 0.05f) {
                    val cost = effectiveDist - (iou * 0.12f)
                    candidates.add(MatchCandidate(tIdx, dIdx, cost))
                }
            }
        }

        // Greedy optimal assignment sorted by lowest cost
        candidates.sortBy { it.cost }

        for (candidate in candidates) {
            if (candidate.trackIdx in matchedTrackIndices || candidate.detIdx in matchedDetectionIndices) {
                continue
            }
            matchedTrackIndices.add(candidate.trackIdx)
            matchedDetectionIndices.add(candidate.detIdx)

            val track = activeTracks[candidate.trackIdx]
            val det = detections[candidate.detIdx]
            val newCx = det.boundingBox.centerX
            val newCy = det.boundingBox.centerY

            // Smooth exponential moving average velocity update
            val instVx = newCx - track.currentCenterX
            val instVy = newCy - track.currentCenterY
            track.velocityX = if (track.ageFrames == 1) instVx else (0.65f * instVx + 0.35f * track.velocityX)
            track.velocityY = if (track.ageFrames == 1) instVy else (0.65f * instVy + 0.35f * track.velocityY)

            track.previousCenterX = track.currentCenterX
            track.previousCenterY = track.currentCenterY
            track.currentCenterX = newCx
            track.currentCenterY = newCy
            track.boundingBox = det.boundingBox
            track.confidence = det.confidence
            track.label = det.label
            track.ageFrames += 1
            track.missedFrames = 0
        }

        // Handle unmatched existing tracks (coast briefly for occlusion resilience, then prune)
        val iterator = activeTracks.iterator()
        var currentIndex = 0
        while (iterator.hasNext()) {
            val track = iterator.next()
            if (currentIndex !in matchedTrackIndices) {
                track.missedFrames += 1
                if (track.missedFrames > maxMissedFrames) {
                    iterator.remove()
                } else {
                    // Advance coasting position slightly using velocity
                    track.previousCenterX = track.currentCenterX
                    track.previousCenterY = track.currentCenterY
                    track.currentCenterX = (track.currentCenterX + track.velocityX * 0.5f).coerceIn(0f, 1f)
                    track.currentCenterY = (track.currentCenterY + track.velocityY * 0.5f).coerceIn(0f, 1f)
                }
            }
            currentIndex++
        }

        // Spawn new tracks for unmatched detections
        for (dIdx in detections.indices) {
            if (dIdx !in matchedDetectionIndices) {
                val det = detections[dIdx]
                val cx = det.boundingBox.centerX
                val cy = det.boundingBox.centerY
                activeTracks.add(
                    InternalTrack(
                        trackId = nextTrackId++,
                        boundingBox = det.boundingBox,
                        confidence = det.confidence,
                        label = det.label,
                        previousCenterX = cx,
                        previousCenterY = cy,
                        currentCenterX = cx,
                        currentCenterY = cy
                    )
                )
            }
        }

        // Return visible tracks (missedFrames == 0 or 1 for smooth UI rendering)
        return activeTracks
            .filter { it.missedFrames <= 1 }
            .map { it.toTrackedObject() }
    }

    private fun computeIoU(a: NormalizedRect, b: NormalizedRect): Float {
        val interLeft = max(a.left, b.left)
        val interTop = max(a.top, b.top)
        val interRight = min(a.right, b.right)
        val interBottom = min(a.bottom, b.bottom)

        val interW = (interRight - interLeft).coerceAtLeast(0f)
        val interH = (interBottom - interTop).coerceAtLeast(0f)
        val interArea = interW * interH
        if (interArea <= 0f) return 0f

        val union = a.width * a.height + b.width * b.height - interArea
        return if (union > 0f) interArea / union else 0f
    }
}
