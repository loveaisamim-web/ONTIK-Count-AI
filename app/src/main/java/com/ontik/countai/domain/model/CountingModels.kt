package com.ontik.countai.domain.model

import android.graphics.RectF

/**
 * Supported App Languages (English & Bangla) selectable in Settings.
 */
enum class AppLanguage(val code: String, val nativeName: String) {
    BANGLA("bn", "বাংলা (Bangla)"),
    ENGLISH("en", "English")
}

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
 * Single object detection output from either Built-In Small Object Vision Engine or TFLite YOLO model.
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
 * Configurable direction for virtual line crossing (Pure ASCII arrows to prevent encoding glitches).
 */
enum class CountingDirection(
    val displayNameEn: String,
    val displayNameBn: String,
    val descriptionEn: String,
    val descriptionBn: String
) {
    TOP_TO_BOTTOM(
        displayNameEn = "Top -> Bottom",
        displayNameBn = "উপর -> নিচ (Top -> Bottom)",
        descriptionEn = "Count objects dropping downward across the horizontal line",
        descriptionBn = "উপর থেকে নিচে দাগের ওপারে গেলে গণনা হবে"
    ),
    BOTTOM_TO_TOP(
        displayNameEn = "Bottom -> Top",
        displayNameBn = "নিচ -> উপর (Bottom -> Top)",
        descriptionEn = "Count objects moving upward across the horizontal line",
        descriptionBn = "নিচ থেকে উপরে দাগের ওপারে গেলে গণনা হবে"
    ),
    LEFT_TO_RIGHT(
        displayNameEn = "Left -> Right",
        displayNameBn = "বাম -> ডান (Left -> Right)",
        descriptionEn = "Count objects moving left-to-right across the vertical line",
        descriptionBn = "বাম থেকে ডানে দাগের ওপারে গেলে গণনা হবে"
    ),
    RIGHT_TO_LEFT(
        displayNameEn = "Right -> Left",
        displayNameBn = "ডান -> বাম (Right -> Left)",
        descriptionEn = "Count objects moving right-to-left across the vertical line",
        descriptionBn = "ডান থেকে বামে দাগের ওপারে গেলে গণনা হবে"
    ),
    BIDIRECTIONAL(
        displayNameEn = "Both Ways (<->)",
        displayNameBn = "উভয় দিকে (Both Ways <->)",
        descriptionEn = "Count objects crossing the line in either direction once per ID",
        descriptionBn = "যেদিক থেকেই দাগ পার হোক প্রতি বস্তু একবার গণনা হবে"
    );

    val isHorizontalLine: Boolean
        get() = this == TOP_TO_BOTTOM || this == BOTTOM_TO_TOP || this == BIDIRECTIONAL

    fun title(lang: AppLanguage): String =
        if (lang == AppLanguage.BANGLA) displayNameBn else displayNameEn

    fun subtitle(lang: AppLanguage): String =
        if (lang == AppLanguage.BANGLA) descriptionBn else descriptionEn
}

/**
 * Configurable AI & Computer Vision model architecture profile.
 */
enum class ModelProfile(
    val id: String,
    val titleEn: String,
    val titleBn: String,
    val defaultAssetFile: String,
    val labelAssetFile: String,
    val inputResolution: Int,
    val descriptionEn: String,
    val descriptionBn: String
) {
    BUILTIN_SMALL_OBJECT_VISION(
        id = "builtin_vision",
        titleEn = "Built-In Smart Object & Grain Detector (Instant Offline)",
        titleBn = "বিল্ট-ইন স্মার্ট অবজেক্ট ও দানা ডিটেক্টর (ফাইল ছাড়াই সরাসরি কাজ করে)",
        defaultAssetFile = "",
        labelAssetFile = "models/labels_small_objects.txt",
        inputResolution = 320,
        descriptionEn = "Works out-of-the-box with zero external files! Uses adaptive local contrast & connected-component segmentation to detect seeds, beans, nuts, coins, and objects on paper/table.",
        descriptionBn = "কোনো আলাদা মডেল ফাইল ছাড়াই সাথে সাথে কাজ করে! সাদা কাগজ বা টেবিলের ওপর রাখা বাদাম, বীজ, শস্যদানা, কয়েন বা যেকোনো বস্তু শনাক্ত ও গণনা করে।"
    ),
    DEDICATED_SMALL_OBJECT_YOLO(
        id = "small_object_yolo",
        titleEn = "Small-Object YOLOv8/v11 TFLite (Seeds, Grains, Beans)",
        titleBn = "Small-Object YOLOv8/v11 TFLite (বীজ ও শস্যদানার কাস্টম মডেল)",
        defaultAssetFile = "models/ontik_small_objects_yolov8n.tflite",
        labelAssetFile = "models/labels_small_objects.txt",
        inputResolution = 640,
        descriptionEn = "Uses a dedicated YOLOv8/v11 .tflite model if imported, and automatically falls back to Built-In Vision Engine if no .tflite file is loaded.",
        descriptionBn = "কাস্টম ট্রেইন করা .tflite মডেল ব্যবহার করে (মডেল ফাইল না থাকলে স্বয়ংক্রিয়ভাবে বিল্ট-ইন ইঞ্জিনে কাজ করবে)।"
    ),
    CUSTOM_EXTERNAL_TFLITE(
        id = "custom_tflite",
        titleEn = "Custom Imported TFLite Model (.tflite)",
        titleBn = "ফোন থেকে ইমপোর্ট করা কাস্টম মডেল (.tflite)",
        defaultAssetFile = "",
        labelAssetFile = "models/labels_small_objects.txt",
        inputResolution = 640,
        descriptionEn = "User-imported custom .tflite model stored locally on device for 100% offline operation.",
        descriptionBn = "ফোনের মেমোরি থেকে সিলেক্ট করা কাস্টম .tflite মডেল (সম্পূর্ণ অফলাইনে চলে)।"
    );

    fun title(lang: AppLanguage): String =
        if (lang == AppLanguage.BANGLA) titleBn else titleEn

    fun subtitle(lang: AppLanguage): String =
        if (lang == AppLanguage.BANGLA) descriptionBn else descriptionEn
}

/**
 * Status of the on-device AI / Vision inference engine.
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

    data class FallbackBuiltInReady(
        val reasonEn: String,
        val reasonBn: String
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
