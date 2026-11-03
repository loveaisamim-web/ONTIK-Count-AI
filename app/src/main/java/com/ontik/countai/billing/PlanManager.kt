package com.ontik.countai.billing

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ontik.countai.domain.model.GatedFeature
import com.ontik.countai.domain.model.PlanState
import com.ontik.countai.domain.model.PlanTier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Single centralized [PlanManager] governing Free vs Pro feature gating, daily limit tracking,
 * midnight reset, anti-tamper reconciliation with Firestore, and 7-day offline Pro verification grace period.
 *
 * Security & Offline rules:
 * - Caches `plan`, `proExpiresAtMs`, `lastVerifiedOnlineMs`, `dailyDate`, and `dailyCount` inside
 *   AES-256 [EncryptedSharedPreferences] (with safe fallback for unit tests).
 * - Pro keeps working 100% offline for up to 7 days (`SEVEN_DAYS_MS`) after the last successful online verification.
 * - After 7 days offline, `isWithinOfflineGracePeriod` becomes `false` and `requiresOnlineVerification` becomes `true`.
 * - Anti-tamper daily count: when syncing with Firestore (`reconcileRemoteDailyCount`), always takes
 *   `max(localTodayCount, remoteTodayCount)` so clearing app data never resets the Free daily limit.
 */
@Singleton
class PlanManager @Inject constructor(
    context: Context
) {
    companion object {
        const val SEVEN_DAYS_MS = 7L * 24L * 3600L * 1000L
        const val DEFAULT_FREE_DAILY_LIMIT = 500
        const val DEFAULT_FREE_HISTORY_LIMIT = 5

        private const val PREF_PLAN_TIER = "plan_tier"
        private const val PREF_PRO_EXPIRES_MS = "pro_expires_ms"
        private const val PREF_LAST_VERIFIED_MS = "last_verified_ms"
        private const val PREF_DAILY_DATE = "daily_date_iso"
        private const val PREF_DAILY_COUNT = "daily_count_used"

        fun todayIsoString(nowMs: Long = System.currentTimeMillis()): String {
            return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMs))
        }

        /**
         * Pure testable helper for evaluating 7-day offline grace period.
         */
        fun isWithinGracePeriod(lastVerifiedMs: Long, nowMs: Long): Boolean {
            if (lastVerifiedMs <= 0L) return false
            return (nowMs - lastVerifiedMs) <= SEVEN_DAYS_MS
        }

        /**
         * Pure testable helper for anti-tamper daily limit reconciliation.
         */
        fun reconcileDailyCountValues(
            localDate: String,
            localCount: Int,
            remoteDate: String,
            remoteCount: Int,
            todayIso: String
        ): Int {
            val effectiveLocal = if (localDate == todayIso) localCount else 0
            val effectiveRemote = if (remoteDate == todayIso) remoteCount else 0
            return max(effectiveLocal, effectiveRemote).coerceAtLeast(0)
        }
    }

    private val encryptedPrefs: SharedPreferences? = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "ontik_encrypted_plan_store",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Throwable) {
        try {
            context.getSharedPreferences("ontik_fallback_plan_store", Context.MODE_PRIVATE)
        } catch (_: Throwable) {
            null
        }
    }

    private val _planState = MutableStateFlow(loadInitialState())
    val planState: StateFlow<PlanState> = _planState.asStateFlow()

    private fun loadInitialState(): PlanState {
        val nowMs = System.currentTimeMillis()
        val today = todayIsoString(nowMs)
        val prefs = encryptedPrefs ?: return PlanState(todayDateIso = today)

        val tierId = prefs.getString(PREF_PLAN_TIER, PlanTier.FREE.id) ?: PlanTier.FREE.id
        val tier = if (tierId == PlanTier.PRO.id) PlanTier.PRO else PlanTier.FREE
        val expiresMs = prefs.getLong(PREF_PRO_EXPIRES_MS, -1L).let { if (it <= 0L) null else it }
        val lastVerified = prefs.getLong(PREF_LAST_VERIFIED_MS, 0L)

        val storedDate = prefs.getString(PREF_DAILY_DATE, "") ?: ""
        val storedCount = if (storedDate == today) prefs.getInt(PREF_DAILY_COUNT, 0) else 0

        if (storedDate != today) {
            prefs.edit()
                .putString(PREF_DAILY_DATE, today)
                .putInt(PREF_DAILY_COUNT, 0)
                .apply()
        }

        val withinGrace = if (tier == PlanTier.PRO) isWithinGracePeriod(lastVerified, nowMs) else true
        val needsVerify = tier == PlanTier.PRO && !withinGrace

        return PlanState(
            tier = tier,
            proExpiresAtMs = expiresMs,
            lastVerifiedOnlineMs = lastVerified,
            isWithinOfflineGracePeriod = withinGrace,
            requiresOnlineVerification = needsVerify,
            todayDateIso = today,
            todayUsedCount = storedCount
        )
    }

    /**
     * Central gating check for all app features.
     */
    fun canUse(feature: GatedFeature): Boolean {
        checkMidnightResetIfNeeded()
        val s = _planState.value
        val isPro = s.isEffectivePro

        return when (feature) {
            GatedFeature.UNLIMITED_DAILY_COUNTING -> isPro || !s.isDailyLimitReached
            GatedFeature.UNLIMITED_HISTORY -> isPro
            GatedFeature.CSV_EXPORT -> isPro
            GatedFeature.PDF_EXPORT -> isPro
            GatedFeature.CLOUD_BACKUP_SYNC -> isPro
            GatedFeature.CUSTOM_PRESETS -> isPro
            GatedFeature.TARGET_COUNT_ALARM -> isPro
            GatedFeature.AD_FREE -> isPro || !s.showAdsOnSecondaryScreens
            GatedFeature.MULTI_DEVICE_SYNC -> isPro
        }
    }

    fun remainingToday(): Int {
        checkMidnightResetIfNeeded()
        return _planState.value.remainingToday
    }

    /**
     * Records newly counted grains against today's usage and returns how many grains are allowed
     * before hitting the Free daily limit.
     */
    @Synchronized
    fun recordGrainsCounted(addedCount: Int): Int {
        if (addedCount <= 0) return 0
        checkMidnightResetIfNeeded()
        val current = _planState.value

        if (current.isEffectivePro) {
            val nextUsed = current.todayUsedCount + addedCount
            persistDailyUsage(current.todayDateIso, nextUsed)
            _planState.update { it.copy(todayUsedCount = nextUsed) }
            return addedCount
        }

        val allowed = addedCount.coerceAtMost(current.remainingToday)
        val nextUsed = current.todayUsedCount + allowed
        persistDailyUsage(current.todayDateIso, nextUsed)
        _planState.update { it.copy(todayUsedCount = nextUsed) }
        return allowed
    }

    /**
     * Updates plan truth from server (Firestore / RevenueCat CustomerInfo) and refreshes the 7-day offline grace timer.
     */
    @Synchronized
    fun updateVerifiedPlanFromServer(
        tier: PlanTier,
        proExpiresAtMs: Long?,
        nowMs: Long = System.currentTimeMillis()
    ) {
        encryptedPrefs?.edit()?.apply {
            putString(PREF_PLAN_TIER, tier.id)
            putLong(PREF_PRO_EXPIRES_MS, proExpiresAtMs ?: -1L)
            putLong(PREF_LAST_VERIFIED_MS, nowMs)
            apply()
        }
        _planState.update {
            it.copy(
                tier = tier,
                proExpiresAtMs = proExpiresAtMs,
                lastVerifiedOnlineMs = nowMs,
                isWithinOfflineGracePeriod = true,
                requiresOnlineVerification = false
            )
        }
    }

    /**
     * Reconciles local daily count with Firestore daily count using `max(local, remote)` anti-tamper rule.
     */
    @Synchronized
    fun reconcileRemoteDailyCount(remoteDateIso: String, remoteCount: Int) {
        val today = todayIsoString()
        val current = _planState.value
        val merged = reconcileDailyCountValues(
            localDate = current.todayDateIso,
            localCount = current.todayUsedCount,
            remoteDate = remoteDateIso,
            remoteCount = remoteCount,
            todayIso = today
        )
        persistDailyUsage(today, merged)
        _planState.update { it.copy(todayDateIso = today, todayUsedCount = merged) }
    }

    fun applyRemoteConfigValues(
        freeDailyLimit: Int,
        freeHistoryLimit: Int,
        showAds: Boolean,
        monthlyPriceText: String,
        yearlyPriceText: String,
        maintenanceMessage: String?,
        minAppVersionCode: Int,
        currentAppVersionCode: Int
    ) {
        _planState.update {
            it.copy(
                freeDailyLimit = freeDailyLimit.coerceAtLeast(50),
                freeHistoryLimit = freeHistoryLimit.coerceAtLeast(3),
                showAdsOnSecondaryScreens = showAds,
                proMonthlyPriceText = monthlyPriceText.ifBlank { it.proMonthlyPriceText },
                proYearlyPriceText = yearlyPriceText.ifBlank { it.proYearlyPriceText },
                maintenanceMessage = maintenanceMessage?.takeIf { m -> m.isNotBlank() },
                forceUpdateRequired = currentAppVersionCode < minAppVersionCode
            )
        }
    }

    private fun checkMidnightResetIfNeeded(nowMs: Long = System.currentTimeMillis()) {
        val today = todayIsoString(nowMs)
        val current = _planState.value
        val withinGrace = if (current.tier == PlanTier.PRO) {
            isWithinGracePeriod(current.lastVerifiedOnlineMs, nowMs)
        } else {
            true
        }

        if (current.todayDateIso != today) {
            persistDailyUsage(today, 0)
            _planState.update {
                it.copy(
                    todayDateIso = today,
                    todayUsedCount = 0,
                    isWithinOfflineGracePeriod = withinGrace,
                    requiresOnlineVerification = it.tier == PlanTier.PRO && !withinGrace
                )
            }
        } else if (current.isWithinOfflineGracePeriod != withinGrace) {
            _planState.update {
                it.copy(
                    isWithinOfflineGracePeriod = withinGrace,
                    requiresOnlineVerification = it.tier == PlanTier.PRO && !withinGrace
                )
            }
        }
    }

    private fun persistDailyUsage(dateIso: String, count: Int) {
        encryptedPrefs?.edit()?.apply {
            putString(PREF_DAILY_DATE, dateIso)
            putInt(PREF_DAILY_COUNT, count)
            apply()
        }
    }
}
