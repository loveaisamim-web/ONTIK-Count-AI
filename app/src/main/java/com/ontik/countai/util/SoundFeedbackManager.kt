package com.ontik.countai.util

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Generates ultra-low-latency acoustic ticks when a grain is counted and a double-chime on 100-grain milestones.
 */
class SoundFeedbackManager {

    private var toneGenerator: ToneGenerator? = null
    private var currentVolumeInt: Int = 65

    private fun getOrInitGenerator(volumeFloat: Float): ToneGenerator? {
        val targetVol = (volumeFloat.coerceIn(0.1f, 1.0f) * 100).toInt()
        if (toneGenerator == null || targetVol != currentVolumeInt) {
            try {
                toneGenerator?.release()
                currentVolumeInt = targetVol
                toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, currentVolumeInt)
            } catch (_: Throwable) {
            }
        }
        return toneGenerator
    }

    fun playCountTick(volume: Float) {
        try {
            getOrInitGenerator(volume)?.startTone(ToneGenerator.TONE_PROP_BEEP, 22)
        } catch (_: Throwable) {
        }
    }

    fun playMilestoneChime(volume: Float) {
        try {
            getOrInitGenerator(volume)?.startTone(ToneGenerator.TONE_PROP_ACK, 120)
        } catch (_: Throwable) {
        }
    }

    fun release() {
        try {
            toneGenerator?.release()
        } catch (_: Throwable) {
        } finally {
            toneGenerator = null
        }
    }
}
