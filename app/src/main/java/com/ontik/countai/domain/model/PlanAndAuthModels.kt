package com.ontik.countai.domain.model

/**
 * All gated features managed strictly through [com.ontik.countai.billing.PlanManager].
 */
enum class GatedFeature {
    UNLIMITED_DAILY_COUNTING,
    UNLIMITED_HISTORY,
    CSV_EXPORT,
    PDF_EXPORT,
    CLOUD_BACKUP_SYNC,
    CUSTOM_PRESETS,
    TARGET_COUNT_ALARM,
    AD_FREE,
    MULTI_DEVICE_SYNC
}

/**
 * Subscription plan tier.
 */
enum class PlanTier(val id: String, val badgeEn: String, val badgeBn: String) {
    FREE("free", "FREE", "ফ্রি (FREE)"),
    PRO("pro", "PRO", "প্রো (PRO)");

    fun badge(lang: AppLanguage): String = if (lang == AppLanguage.BANGLA) badgeBn else badgeEn
}

/**
 * Current state of the user's plan, daily counting usage, and 7-day offline verification grace period.
 */
data class PlanState(
    val tier: PlanTier = PlanTier.FREE,
    val proExpiresAtMs: Long? = null,
    val lastVerifiedOnlineMs: Long = 0L,
    val isWithinOfflineGracePeriod: Boolean = true,
    val requiresOnlineVerification: Boolean = false,
    val todayDateIso: String = "",
    val todayUsedCount: Int = 0,
    val freeDailyLimit: Int = 500,
    val freeHistoryLimit: Int = 5,
    val showAdsOnSecondaryScreens: Boolean = true,
    val proMonthlyPriceText: String = "$2.99 / month",
    val proYearlyPriceText: String = "$19.99 / year (Save 40%)",
    val proLifetimePriceText: String = "$49.99 lifetime",
    val maintenanceMessage: String? = null,
    val forceUpdateRequired: Boolean = false
) {
    val isEffectivePro: Boolean
        get() {
            if (tier != PlanTier.PRO) return false
            val now = System.currentTimeMillis()
            val notExpired = proExpiresAtMs == null || proExpiresAtMs > now
            return notExpired && isWithinOfflineGracePeriod
        }

    val remainingToday: Int
        get() = if (isEffectivePro) Int.MAX_VALUE else (freeDailyLimit - todayUsedCount).coerceAtLeast(0)

    val isDailyLimitReached: Boolean
        get() = !isEffectivePro && todayUsedCount >= freeDailyLimit
}

/**
 * Authenticated or Guest User Profile.
 */
data class UserProfile(
    val uid: String = "",
    val name: String = "Guest User",
    val email: String = "",
    val photoUrl: String? = null,
    val isGuest: Boolean = true,
    val isEmailVerified: Boolean = false,
    val isGoogleAccount: Boolean = false,
    val planTier: PlanTier = PlanTier.FREE,
    val proExpiresAtMs: Long? = null,
    val proSource: String? = null,
    val createdAtMs: Long = System.currentTimeMillis(),
    val lastLoginAtMs: Long = System.currentTimeMillis(),
    val deviceIds: List<String> = emptyList()
)

/**
 * Cloud synchronization status for each session row in Room.
 */
enum class SyncStatus {
    LOCAL_ONLY,
    PENDING_UPLOAD,
    SYNCED,
    FAILED
}

/**
 * Batch subtotal item for Batch Counting Mode (Phase 6.4).
 */
data class BatchCountEntry(
    val batchNumber: Int,
    val count: Int,
    val timestampMs: Long
)
