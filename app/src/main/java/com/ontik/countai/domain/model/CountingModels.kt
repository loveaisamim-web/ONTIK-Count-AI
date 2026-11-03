package com.ontik.countai.domain.model

/**
 * Supported App Languages (English & Bengali).
 */
enum class AppLanguage(val code: String, val nativeName: String) {
    BANGLA("bn", "বাংলা (Bengali)"),
    ENGLISH("en", "English")
}

/**
 * Supported App Theme Modes.
 */
enum class AppThemeMode(val id: String, val labelEn: String, val labelBn: String) {
    SYSTEM("system", "Follow System", "সিস্টেম থিম"),
    LIGHT("light", "Light Theme", "লাইট থিম (সাদা)"),
    DARK("dark", "Dark Theme", "ডার্ক থিম (কালো)");

    fun title(lang: AppLanguage): String = if (lang == AppLanguage.BANGLA) labelBn else labelEn
}

/**
 * Built-in Grain & Seed Presets that configure area limits, morphology, sensitivity, and tracking parameters.
 */
enum class GrainPreset(
    val id: String,
    val nameEn: String,
    val nameBn: String,
    val defaultSensitivity: Float,
    val defaultMinAreaPx: Int,
    val defaultMaxAreaPx: Int,
    val defaultSingleGrainAreaPx: Float,
    val defaultMorphKernelSize: Int,
    val defaultTrackingDistanceNormalized: Float,
    val defaultLineHysteresisNormalized: Float
) {
    RICE(
        id = "rice",
        nameEn = "Rice (চাল / ধান)",
        nameBn = "চাল / ধান (Rice)",
        defaultSensitivity = 0.50f,
        defaultMinAreaPx = 18,
        defaultMaxAreaPx = 4500,
        defaultSingleGrainAreaPx = 110f,
        defaultMorphKernelSize = 3,
        defaultTrackingDistanceNormalized = 0.14f,
        defaultLineHysteresisNormalized = 0.015f
    ),
    DAL_LENTIL(
        id = "dal_lentil",
        nameEn = "Dal / Red & Yellow Lentil (ডাল / মসুর)",
        nameBn = "ডাল / মসুর ও মুগ (Dal / Lentil)",
        defaultSensitivity = 0.55f,
        defaultMinAreaPx = 14,
        defaultMaxAreaPx = 4000,
        defaultSingleGrainAreaPx = 85f,
        defaultMorphKernelSize = 3,
        defaultTrackingDistanceNormalized = 0.15f,
        defaultLineHysteresisNormalized = 0.015f
    ),
    SESAME_SMALL_SEEDS(
        id = "sesame_small",
        nameEn = "Sesame / Tiny Seeds (তিল / সরিষা / ছোট বীজ)",
        nameBn = "তিল / সরিষা / ছোট বীজ (Small Seeds)",
        defaultSensitivity = 0.68f,
        defaultMinAreaPx = 6,
        defaultMaxAreaPx = 1800,
        defaultSingleGrainAreaPx = 32f,
        defaultMorphKernelSize = 3,
        defaultTrackingDistanceNormalized = 0.12f,
        defaultLineHysteresisNormalized = 0.012f
    ),
    BEANS_PEANUTS(
        id = "beans_peanuts",
        nameEn = "Beans / Peanuts / Chickpeas (বাদাম / ছোলা / শিমের বিচি)",
        nameBn = "বাদাম / ছোলা / শিমের বিচি (Beans / Peanuts)",
        defaultSensitivity = 0.45f,
        defaultMinAreaPx = 45,
        defaultMaxAreaPx = 18000,
        defaultSingleGrainAreaPx = 340f,
        defaultMorphKernelSize = 5,
        defaultTrackingDistanceNormalized = 0.18f,
        defaultLineHysteresisNormalized = 0.020f
    ),
    TABLETS_CAPSULES(
        id = "tablets",
        nameEn = "Tablets / Capsules / Pills (ট্যাবলেট / ক্যাপসুল)",
        nameBn = "ট্যাবলেট / ক্যাপসুল / পিল (Tablets)",
        defaultSensitivity = 0.45f,
        defaultMinAreaPx = 50,
        defaultMaxAreaPx = 22000,
        defaultSingleGrainAreaPx = 420f,
        defaultMorphKernelSize = 5,
        defaultTrackingDistanceNormalized = 0.16f,
        defaultLineHysteresisNormalized = 0.018f
    ),
    CUSTOM(
        id = "custom",
        nameEn = "My Custom Preset (কাস্টম প্রিসেট)",
        nameBn = "আমার কাস্টম প্রিসেট (Custom Preset)",
        defaultSensitivity = 0.50f,
        defaultMinAreaPx = 16,
        defaultMaxAreaPx = 8000,
        defaultSingleGrainAreaPx = 120f,
        defaultMorphKernelSize = 3,
        defaultTrackingDistanceNormalized = 0.15f,
        defaultLineHysteresisNormalized = 0.015f
    );

    fun title(lang: AppLanguage): String = if (lang == AppLanguage.BANGLA) nameBn else nameEn
}

/**
 * Normalized rectangle in [0.0f .. 1.0f] relative to the full camera frame.
 */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0.05f)
    val height: Float get() = (bottom - top).coerceAtLeast(0.05f)
    val centerX: Float get() = left + width * 0.5f
    val centerY: Float get() = top + height * 0.5f

    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}

/**
 * Single or split grain blob extracted by the classical computer vision pipeline.
 * Coordinates are normalized [0..1] in full-frame space for seamless overlay and tracking.
 */
data class GrainBlob(
    val centerX: Float,
    val centerY: Float,
    val boundingBox: NormalizedRect,
    val areaPx: Double,
    val solidity: Double,
    val aspectRatio: Double,
    val extent: Double,
    val estimatedGrainCount: Int,
    val isClump: Boolean,
    val isMotionStreak: Boolean
)

/**
 * Track lifecycle state in the Kalman + Hungarian multi-object tracker.
 */
enum class TrackLifecycleState {
    TENTATIVE, // < 2 consecutive hits
    CONFIRMED, // >= 2 consecutive hits
    LOST,      // Coasting on Kalman prediction (up to 8 frames or 150ms)
    DELETED
}

/**
 * Tracked grain or clump with 4D Kalman filter state (x, y, vx, vy), trail history, and crossing flags.
 */
data class TrackedGrain(
    val trackId: Int,
    val state: TrackLifecycleState,
    val currentX: Float,
    val currentY: Float,
    val previousX: Float,
    val previousY: Float,
    val velocityX: Float,
    val velocityY: Float,
    val boundingBox: NormalizedRect,
    val estimatedGrainCount: Int,
    val isClump: Boolean,
    val isMotionStreak: Boolean,
    val consecutiveHits: Int,
    val missedFrames: Int,
    val lastSeenTimestampMs: Long,
    val bornPastLineA: Boolean,
    val countedLineA: Boolean,
    val countedLineB: Boolean,
    val tentativeCrossedLineA: Boolean,
    val lastCrossDirectionSign: Int,
    val countedFlashTimestampMs: Long,
    val trailPoints: List<Pair<Float, Float>>
)

/**
 * Configurable direction for virtual counting line crossing.
 */
enum class CountingDirection(
    val displayNameEn: String,
    val displayNameBn: String,
    val shortChipEn: String,
    val shortChipBn: String,
    val descriptionEn: String,
    val descriptionBn: String
) {
    TOP_TO_BOTTOM(
        displayNameEn = "Top -> Bottom",
        displayNameBn = "উপর -> নিচ",
        shortChipEn = "Top->Bot",
        shortChipBn = "উপর->নিচ",
        descriptionEn = "Count grains dropping downward across the horizontal line",
        descriptionBn = "উপর থেকে নিচে দাগের ওপারে পড়লে গণনা হবে"
    ),
    BOTTOM_TO_TOP(
        displayNameEn = "Bottom -> Top",
        displayNameBn = "নিচ -> উপর",
        shortChipEn = "Bot->Top",
        shortChipBn = "নিচ->উপর",
        descriptionEn = "Count grains moving upward across the horizontal line",
        descriptionBn = "নিচ থেকে উপরে দাগের ওপারে গেলে গণনা হবে"
    ),
    LEFT_TO_RIGHT(
        displayNameEn = "Left -> Right",
        displayNameBn = "বাম -> ডান",
        shortChipEn = "L -> R",
        shortChipBn = "বাম->ডান",
        descriptionEn = "Count grains moving left-to-right across the vertical line",
        descriptionBn = "বাম থেকে ডানে দাগের ওপারে গেলে গণনা হবে"
    ),
    RIGHT_TO_LEFT(
        displayNameEn = "Right -> Left",
        displayNameBn = "ডান -> বাম",
        shortChipEn = "R -> L",
        shortChipBn = "ডান->বাম",
        descriptionEn = "Count grains moving right-to-left across the vertical line",
        descriptionBn = "ডান থেকে বামে দাগের ওপারে গেলে গণনা হবে"
    ),
    BIDIRECTIONAL(
        displayNameEn = "Bidirectional (<->)",
        displayNameBn = "উভয় দিকে (<->)",
        shortChipEn = "Both <->",
        shortChipBn = "উভয় <->",
        descriptionEn = "Count grains crossing the line in either direction",
        descriptionBn = "যেদিক থেকেই দাগ পার হোক গণনা হবে"
    );

    val isHorizontalLine: Boolean
        get() = this == TOP_TO_BOTTOM || this == BOTTOM_TO_TOP || this == BIDIRECTIONAL

    fun title(lang: AppLanguage): String = if (lang == AppLanguage.BANGLA) displayNameBn else displayNameEn
    fun chip(lang: AppLanguage): String = if (lang == AppLanguage.BANGLA) shortChipBn else shortChipEn
    fun subtitle(lang: AppLanguage): String = if (lang == AppLanguage.BANGLA) descriptionBn else descriptionEn
}

/**
 * Running self-check confidence badge level based on FPS, lighting stability, noise level, and clump ratio.
 */
enum class AccuracyConfidenceLevel(val labelEn: String, val labelBn: String) {
    GOOD("Good", "উত্তম (Good)"),
    FAIR("Fair", "মাঝারি (Fair)"),
    POOR("Poor", "কম (Poor)");

    fun label(lang: AppLanguage): String = if (lang == AppLanguage.BANGLA) labelBn else labelEn
}

/**
 * Debug recorder snapshot for the last 10 seconds of counting.
 */
data class DebugFrameSnapshot(
    val timestampMs: Long,
    val totalCount: Int,
    val inZoneCount: Int,
    val fps: Int,
    val noiseStd: Double,
    val singleGrainAreaPx: Float
)
