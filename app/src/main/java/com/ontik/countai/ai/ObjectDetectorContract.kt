package com.ontik.countai.ai

import android.graphics.Bitmap
import com.ontik.countai.domain.model.DetectionResult
import com.ontik.countai.domain.model.ModelEngineState
import com.ontik.countai.domain.model.NormalizedRect

/**
 * Replaceable on-device AI Object Detector interface.
 * Supports TensorFlow Lite / LiteRT YOLO detection, SSD MobileNet, or instance segmentation models.
 */
interface ObjectDetectorContract {

    val engineState: ModelEngineState

    /**
     * Runs inference on the provided upright [bitmap].
     * If [countingZone] is provided, detections outside the zone can be filtered or cropped.
     */
    fun detect(
        bitmap: Bitmap,
        confidenceThreshold: Float,
        iouThreshold: Float,
        countingZone: NormalizedRect? = null
    ): InferenceOutput

    fun close()
}

data class InferenceOutput(
    val detections: List<DetectionResult>,
    val lowConfidenceCandidateCount: Int,
    val inferenceTimeMs: Long,
    val statusMessage: String? = null
)
