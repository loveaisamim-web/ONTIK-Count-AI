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
 * Single centralized [PlanManager].
 * All counting, history, export, presets, alarm, and cloud backup features are 100% unlocked for all users.
 * Monetization is 100% online AdMob ads on secondary screens (`GatedFeature.AD_FREE` is always `false`).
 */
@Singleton
class PlanManager @Inject constructor(
    context: Context
) {
    companion object {
        const val SEVEN_DAYS_MS = 7L * 24L * 3600L * 1000L
        const val DEFAULT_FREE_DAILY_LIMIT = Int.MAX_VALUE
        const val DEFAULT_FREE_HISTORY_LIMIT = Int.MAX_VALUE

        private const val PREF_PLAN_TIER = "plan_tier"
        private const val PREF_PRO_EXPIRES_MS = "pro_expires_ms"
        private const val PREF_LAST_VERIFIED_MS = "last_verified_ms"
        private const val PREF_DAILY_DATE = "daily_date_iso"
        private const val PREF_DAILY_COUNT = "daily_count_used"

        fun todayIsoString(nowMs: Long = System.currentTimeMillis()): String {
            return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMs))
        }

        fun isWithinGracePeriod(lastVerifiedMs: Long, nowMs: Long): Boolean {
            if (lastVerifiedMs <= 0L) return false
            return (nowMs - lastVerifiedMs) <= SEVEN_DAYS_MS
        }

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
        val prefs = encryptedPrefs ?: return PlanState(
            tier = PlanTier.FREE,
            todayDateIso = today,
            freeDailyLimit = Int.MAX_VALUE,
            freeHistoryLimit = Int.MAX_VALUE,
            showAdsOnSecondaryScreens = true
        )

        val storedDate = prefs.getString(PREF_DAILY_DATE, "") ?: ""
        val storedCount = if (storedDate == today) prefs.getInt(PREF_DAILY_COUNT, 0) else 0

        if (storedDate != today) {
            prefs.edit()
                .putString(PREF_DAILY_DATE, today)
                .putInt(PREF_DAILY_COUNT, 0)
                .apply()
        }

        return PlanState(
            tier = PlanTier.FREE,
            proExpiresAtMs = null,
            lastVerifiedOnlineMs = nowMs,
            isWithinOfflineGracePeriod = true,
            requiresOnlineVerification = false,
            todayDateIso = today,
            todayUsedCount = storedCount,
            freeDailyLimit = Int.MAX_VALUE,
            freeHistoryLimit = Int.MAX_VALUE,
            showAdsOnSecondaryScreens = true
        )
    }

    /**
     * All app features are unlocked for free; AD_FREE is false so AdMob banner ads always show on History & Settings.
     */
    fun canUse(feature: GatedFeature): Boolean {
        checkMidnightResetIfNeeded()
        return when (feature) {
            GatedFeature.AD_FREE -> false
            else -> true
        }
    }

    fun remainingToday(): Int = Int.MAX_VALUE

    @Synchronized
    fun recordGrainsCounted(addedCount: Int): Int {
        if (addedCount <= 0) return 0
        checkMidnightResetIfNeeded()
        val current = _planState.value
        val nextUsed = current.todayUsedCount + addedCount
        persistDailyUsage(current.todayDateIso, nextUsed)
        _planState.update { it.copy(todayUsedCount = nextUsed) }
        return addedCount
    }

    @Synchronized
    fun updateVerifiedPlanFromServer(
        tier: PlanTier,
        proExpiresAtMs: Long?,
        nowMs: Long = System.currentTimeMillis()
    ) {
        // Keep ad-supported mode active for all users
        _planState.update {
            it.copy(
                tier = PlanTier.FREE,
                proExpiresAtMs = null,
                lastVerifiedOnlineMs = nowMs,
                isWithinOfflineGracePeriod = true,
                requiresOnlineVerification = false,
                freeDailyLimit = Int.MAX_VALUE,
                freeHistoryLimit = Int.MAX_VALUE,
                showAdsOnSecondaryScreens = true
            )
        }
    }

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
                freeDailyLimit = Int.MAX_VALUE,
                freeHistoryLimit = Int.MAX_VALUE,
                showAdsOnSecondaryScreens = true,
                maintenanceMessage = maintenanceMessage?.takeIf { m -> m.isNotBlank() },
                forceUpdateRequired = currentAppVersionCode < minAppVersionCode
            )
        }
    }

    private fun checkMidnightResetIfNeeded(nowMs: Long = System.currentTimeMillis()) {
        val today = todayIsoString(nowMs)
        val current = _planState.value
        if (current.todayDateIso != today) {
            persistDailyUsage(today, 0)
            _planState.update {
                it.copy(
                    todayDateIso = today,
                    todayUsedCount = 0
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
