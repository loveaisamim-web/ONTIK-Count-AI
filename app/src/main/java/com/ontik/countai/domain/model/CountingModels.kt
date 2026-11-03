package com.ontik.countai.domain.model

import android.graphics.RectF

/**
 * Normalized bounding box and coordinates in [0.0f .. 1.0f] relative to the analyzed frame/image.
 */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val centerX: Float get() = left + width * 0.5f
    val centerY: Float get() = top + height * 0.5f

    fun contains(x: Float, y: Float): Boolean {
        return x in left..right && y in top..bottom
    }

    fun toRectF(): RectF = RectF(left, top, right, bottom)
}

/**
 * Single AI object detection output from TensorFlow Lite / LiteRT model.
 */
data class DetectionResult(
    val boundingBox: NormalizedRect,
    val confidence: Float,
    val classIndex: Int,
    val label: String,
    val maskPolygon: List<Pair<Float, Float>>? = null
)

/**
 * Tracked physical object across sequential video frames with unique tracking ID and crossing state.
 */
data class TrackedObject(
    val trackId: Int,
    val boundingBox: NormalizedRect,
    val confidence: Float,
    val label: String,
    val previousCenterX: Float,
    val previousCenterY: Float,
    val currentCenterX: Float,
    val currentCenterY: Float,
    val velocityX: Float,
    val velocityY: Float,
    val ageFrames: Int,
    val missedFrames: Int,
    val hasBeenCounted: Boolean,
    val crossedTimestampMs: Long? = null
)

/**
 * Configurable direction for virtual line crossing.
 */
enum class CountingDirection(val displayName: String, val description: String) {
    TOP_TO_BOTTOM("Top → Bottom", "Count objects dropping downward across the horizontal line"),
    BOTTOM_TO_TOP("Bottom → Top", "Count objects moving upward across the horizontal line"),
    LEFT_TO_RIGHT("Left → Right", "Count objects moving left-to-right across the vertical line"),
    RIGHT_TO_LEFT("Right → Left", "Count objects moving right-to-left across the vertical line"),
    BIDIRECTIONAL("Bidirectional", "Count objects crossing the line in either direction once per ID");

    val isHorizontalLine: Boolean
        get() = this == TOP_TO_BOTTOM || this == BOTTOM_TO_TOP || this == BIDIRECTIONAL
}

/**
 * Configurable AI model architecture profile.
 */
enum class ModelProfile(
    val id: String,
    val title: String,
    val defaultAssetFile: String,
    val labelAssetFile: String,
    val inputResolution: Int,
    val description: String
) {
    DEDICATED_SMALL_OBJECT_YOLO(
        id = "small_object_yolo",
        title = "Small-Object YOLOv8/v11 (Seeds, Grains, Beans)",
        defaultAssetFile = "models/ontik_small_objects_yolov8n.tflite",
        labelAssetFile = "models/labels_small_objects.txt",
        inputResolution = 640,
        description = "Dedicated model profile for small physical objects (seeds, grains, beans, lentils, pellets). Supports high-density NMS and small bounding boxes."
    ),
    GENERAL_YOLO_DETECTOR(
        id = "general_yolo",
        title = "General YOLOv8n / TFLite Object Detector",
        defaultAssetFile = "models/yolov8n_general.tflite",
        labelAssetFile = "models/labels_coco.txt",
        inputResolution = 640,
        description = "Standard pretrained object detection profile for larger everyday items."
    ),
    CUSTOM_EXTERNAL_TFLITE(
        id = "custom_tflite",
        title = "Custom Imported TFLite Model (.tflite)",
        defaultAssetFile = "",
        labelAssetFile = "models/labels_small_objects.txt",
        inputResolution = 640,
        description = "User-imported custom .tflite model stored locally on device for 100% offline operation."
    )
}

/**
 * Status of the on-device AI inference engine.
 */
sealed class ModelEngineState {
    data object Uninitialized : ModelEngineState()
    data class Ready(
        val modelName: String,
        val inputWidth: Int,
        val inputHeight: Int,
        val isQuantized: Boolean,
        val outputShapeDescription: String
    ) : ModelEngineState()

    data class MissingModelFile(
        val expectedPath: String,
        val instructions: String
    ) : ModelEngineState()

    data class Error(val message: String) : ModelEngineState()
}

/**
 * Manual annotation adjustment on a photo in Photo Counting mode.
 */
data class ManualPhotoPoint(
    val id: Long,
    val normalizedX: Float,
    val normalizedY: Float,
    val radius: Float = 0.025f
)
