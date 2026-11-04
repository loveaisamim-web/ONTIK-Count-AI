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
 * Built-in Grain & Seed Presets with both full titles (for Settings) and short fixed-width titles (for Camera HUD).
 */
enum class GrainPreset(
    val id: String,
    val nameEn: String,
    val nameBn: String,
    val shortEn: String,
    val shortBn: String,
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
        shortEn = "Rice",
        shortBn = "চাল / ধান",
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
        nameEn = "Dal / Lentil (ডাল / মসুর)",
        nameBn = "ডাল / মসুর (Lentil)",
        shortEn = "Dal / Lentil",
        shortBn = "ডাল / মসুর",
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
        nameEn = "Small Seeds (তিল / সরিষা / বীজ)",
        nameBn = "তিল / ছোট বীজ (Seeds)",
        shortEn = "Small Seeds",
        shortBn = "তিল / বীজ",
        defaultSensitivity = 0.65f,
        defaultMinAreaPx = 8,
        defaultMaxAreaPx = 1800,
        defaultSingleGrainAreaPx = 36f,
        defaultMorphKernelSize = 3,
        defaultTrackingDistanceNormalized = 0.12f,
        defaultLineHysteresisNormalized = 0.012f
    ),
    BEANS_PEANUTS(
        id = "beans_peanuts",
        nameEn = "Beans / Chickpeas (বাদাম / ছোলা / বিচি)",
        nameBn = "বাদাম / ছোলা (Beans)",
        shortEn = "Beans",
        shortBn = "বাদাম / ছোলা",
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
        nameEn = "Tablets / Pills (ট্যাবলেট / ক্যাপসুল)",
        nameBn = "ট্যাবলেট / ওষুধ (Tablets)",
        shortEn = "Tablets",
        shortBn = "ট্যাবলেট / ওষুধ",
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
        nameEn = "Custom Preset (কাস্টম)",
        nameBn = "কাস্টম প্রিসেট (Custom)",
        shortEn = "Custom",
        shortBn = "কাস্টম",
        defaultSensitivity = 0.50f,
        defaultMinAreaPx = 16,
        defaultMaxAreaPx = 8000,
        defaultSingleGrainAreaPx = 120f,
        defaultMorphKernelSize = 3,
        defaultTrackingDistanceNormalized = 0.15f,
        defaultLineHysteresisNormalized = 0.015f
    );

    fun title(lang: AppLanguage): String = if (lang == AppLanguage.BANGLA) nameBn else nameEn
    fun shortTitle(lang: AppLanguage): String = if (lang == AppLanguage.BANGLA) shortBn else shortEn
}

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

enum class TrackLifecycleState {
    TENTATIVE,
    CONFIRMED,
    LOST,
    DELETED
}

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

enum class AccuracyConfidenceLevel(val labelEn: String, val labelBn: String) {
    GOOD("Good", "উত্তম (Good)"),
    FAIR("Fair", "মাঝারি (Fair)"),
    POOR("Poor", "কম (Poor)");

    fun label(lang: AppLanguage): String = if (lang == AppLanguage.BANGLA) labelBn else labelEn
}

data class DebugFrameSnapshot(
    val timestampMs: Long,
    val totalCount: Int,
    val inZoneCount: Int,
    val fps: Int,
    val noiseStd: Double,
    val singleGrainAreaPx: Float
)
