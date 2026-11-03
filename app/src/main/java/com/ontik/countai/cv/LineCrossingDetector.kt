package com.ontik.countai.cv

import com.ontik.countai.domain.model.CountingDirection

/**
 * High-speed segment-intersection line-crossing evaluator.
 *
 * Guarantees:
 * - Evaluates whether the 2D displacement segment `(prevX, prevY) -> (currX, currY)` intersects
 *   the counting line `linePos` within the ROI, catching fast-falling grains that jump across the line
 *   between two consecutive camera frames.
 * - Enforces a hysteresis band (`hysteresis`) around the line so jittery stationary objects on the line
 *   do not trigger false counts.
 * - Returns the crossing direction sign (`+1` for Top->Bottom or Left->Right, `-1` for Bottom->Top or Right->Left, `0` if no valid crossing).
 */
object LineCrossingDetector {

    /**
     * Checks if a movement from `(prevX, prevY)` to `(currX, currY)` crosses `linePos` in `direction`.
     * Returns `+1` or `-1` if a valid crossing occurred, or `0` otherwise.
     */
    fun evaluateSegmentCrossing(
        prevX: Float,
        prevY: Float,
        currX: Float,
        currY: Float,
        linePos: Float,
        hysteresis: Float,
        direction: CountingDirection
    ): Int {
        val h = hysteresis.coerceIn(0.002f, 0.05f)

        return if (direction.isHorizontalLine) {
            val crossedDown = prevY < linePos && currY >= linePos && (currY - prevY) > h * 0.2f
            val crossedUp = prevY > linePos && currY <= linePos && (prevY - currY) > h * 0.2f

            when (direction) {
                CountingDirection.TOP_TO_BOTTOM -> if (crossedDown) +1 else 0
                CountingDirection.BOTTOM_TO_TOP -> if (crossedUp) -1 else 0
                CountingDirection.BIDIRECTIONAL -> when {
                    crossedDown -> +1
                    crossedUp -> -1
                    else -> 0
                }
                else -> 0
            }
        } else {
            val crossedRight = prevX < linePos && currX >= linePos && (currX - prevX) > h * 0.2f
            val crossedLeft = prevX > linePos && currX <= linePos && (prevX - currX) > h * 0.2f

            when (direction) {
                CountingDirection.LEFT_TO_RIGHT -> if (crossedRight) +1 else 0
                CountingDirection.RIGHT_TO_LEFT -> if (crossedLeft) -1 else 0
                CountingDirection.BIDIRECTIONAL -> when {
                    crossedRight -> +1
                    crossedLeft -> -1
                    else -> 0
                }
                else -> 0
            }
        }
    }

    /**
     * Determines whether a newly spawned track at `(startX, startY)` is born already past the counting line
     * in the target direction (so it must not be counted).
     */
    fun isBornPastLine(
        startX: Float,
        startY: Float,
        linePos: Float,
        hysteresis: Float,
        direction: CountingDirection
    ): Boolean {
        val margin = hysteresis.coerceIn(0.005f, 0.04f)
        return when (direction) {
            CountingDirection.TOP_TO_BOTTOM -> startY > (linePos + margin)
            CountingDirection.BOTTOM_TO_TOP -> startY < (linePos - margin)
            CountingDirection.LEFT_TO_RIGHT -> startX > (linePos + margin)
            CountingDirection.RIGHT_TO_LEFT -> startX < (linePos - margin)
            CountingDirection.BIDIRECTIONAL -> kotlin.math.abs(( if (direction.isHorizontalLine) startY else startX ) - linePos) < margin * 0.25f
        }
    }
}
