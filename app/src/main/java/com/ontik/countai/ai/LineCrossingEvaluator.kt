package com.ontik.countai.ai

import com.ontik.countai.domain.model.CountingDirection
import com.ontik.countai.domain.model.NormalizedRect
import com.ontik.countai.domain.model.TrackedObject

/**
 * Evaluates whether tracked objects have crossed the virtual counting line inside the active counting zone
 * in the configured [CountingDirection], preventing duplicate counts via unique `trackId` + `hasBeenCounted`
 * state and spatial hysteresis.
 */
object LineCrossingEvaluator {

    /**
     * Returns the list of [TrackedObject]s that newly crossed the virtual counting line on this frame.
     *
     * @param trackedObjects Active tracked objects from [MultiObjectTracker].
     * @param countingZone Active normalized counting zone [0..1].
     * @param linePositionNormalized Normalized position [0..1] of the counting line within the frame
     *        (Y coordinate for horizontal lines, X coordinate for vertical lines).
     * @param direction Configured [CountingDirection].
     */
    fun findNewlyCrossedObjects(
        trackedObjects: List<TrackedObject>,
        countingZone: NormalizedRect,
        linePositionNormalized: Float,
        direction: CountingDirection
    ): List<TrackedObject> {
        val newlyCrossed = mutableListOf<TrackedObject>()

        for (obj in trackedObjects) {
            // Never count the same tracked object twice
            if (obj.hasBeenCounted) continue

            // Require at least 2 observations (previous != current) so a newly spawned object right on the line doesn't false-trigger
            if (obj.ageFrames < 2) continue

            // Ensure object center is within the active counting zone
            if (!countingZone.contains(obj.currentCenterX, obj.currentCenterY) &&
                !countingZone.contains(obj.previousCenterX, obj.previousCenterY)
            ) {
                continue
            }

            val crossed = when (direction) {
                CountingDirection.TOP_TO_BOTTOM -> {
                    obj.previousCenterY < linePositionNormalized && obj.currentCenterY >= linePositionNormalized
                }
                CountingDirection.BOTTOM_TO_TOP -> {
                    obj.previousCenterY > linePositionNormalized && obj.currentCenterY <= linePositionNormalized
                }
                CountingDirection.LEFT_TO_RIGHT -> {
                    obj.previousCenterX < linePositionNormalized && obj.currentCenterX >= linePositionNormalized
                }
                CountingDirection.RIGHT_TO_LEFT -> {
                    obj.previousCenterX > linePositionNormalized && obj.currentCenterX <= linePositionNormalized
                }
                CountingDirection.BIDIRECTIONAL -> {
                    val crossedDown = obj.previousCenterY < linePositionNormalized && obj.currentCenterY >= linePositionNormalized
                    val crossedUp = obj.previousCenterY > linePositionNormalized && obj.currentCenterY <= linePositionNormalized
                    crossedDown || crossedUp
                }
            }

            if (crossed) {
                newlyCrossed.add(obj)
            }
        }

        return newlyCrossed
    }
}
