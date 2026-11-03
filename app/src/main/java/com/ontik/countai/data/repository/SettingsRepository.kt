package com.ontik.countai.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ontik.countai.domain.model.AppLanguage
import com.ontik.countai.domain.model.AppThemeMode
import com.ontik.countai.domain.model.CountingDirection
import com.ontik.countai.domain.model.GrainPreset
import com.ontik.countai.domain.model.NormalizedRect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "ontik_opencv_settings")

data class AppSettings(
    val language: AppLanguage = AppLanguage.BANGLA,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val activePreset: GrainPreset = GrainPreset.RICE,
    val sensitivity: Float = 0.50f,
    val minAreaPx: Int = 18,
    val maxAreaPx: Int = 4500,
    val autoSingleGrainSize: Boolean = true,
    val manualSingleGrainAreaPx: Float = 110f,
    val trackingDistanceNormalized: Float = 0.14f,
    val lineHysteresisNormalized: Float = 0.015f,
    val morphKernelSize: Int = 3,
    val countingDirection: CountingDirection = CountingDirection.TOP_TO_BOTTOM,
    val enableVerificationLineB: Boolean = false,
    val useBackCamera: Boolean = true,
    val targetFps: Int = 30,
    val lockCameraExposureFocus: Boolean = true,
    val hapticEnabled: Boolean = true,
    val soundBeepEnabled: Boolean = true,
    val soundVolume: Float = 0.65f,
    val keepScreenOn: Boolean = true,
    val showTrackIds: Boolean = false,
    val showDebugMask: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val analyticsConsentAsked: Boolean = false,
    val analyticsConsentGranted: Boolean = false,
    val targetCountGoal: Int = 0,
    val batchModeEnabled: Boolean = false,
    val completedSessionsCountForReview: Int = 0,
    val roiRect: NormalizedRect = NormalizedRect(0.10f, 0.16f, 0.90f, 0.82f),
    val lineAPosition: Float = 0.50f,
    val lineBPosition: Float = 0.68f
)

@Singleton
class SettingsRepository @Inject constructor(
    private val context: Context
) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PRESET = stringPreferencesKey("active_preset")
        val SENSITIVITY = floatPreferencesKey("sensitivity")
        val MIN_AREA = intPreferencesKey("min_area_px")
        val MAX_AREA = intPreferencesKey("max_area_px")
        val AUTO_GRAIN_SIZE = booleanPreferencesKey("auto_grain_size")
        val MANUAL_GRAIN_AREA = floatPreferencesKey("manual_grain_area_px")
        val TRACKING_DIST = floatPreferencesKey("tracking_dist")
        val LINE_HYSTERESIS = floatPreferencesKey("line_hysteresis")
        val MORPH_KERNEL = intPreferencesKey("morph_kernel")
        val DIRECTION = stringPreferencesKey("counting_direction")
        val VERIFY_LINE_B = booleanPreferencesKey("verify_line_b")
        val USE_BACK_CAM = booleanPreferencesKey("use_back_cam")
        val TARGET_FPS = intPreferencesKey("target_fps")
        val LOCK_CAMERA = booleanPreferencesKey("lock_camera")
        val HAPTIC = booleanPreferencesKey("haptic_enabled")
        val SOUND_BEEP = booleanPreferencesKey("sound_beep")
        val SOUND_VOLUME = floatPreferencesKey("sound_volume")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val SHOW_TRACK_IDS = booleanPreferencesKey("show_track_ids")
        val SHOW_DEBUG_MASK = booleanPreferencesKey("show_debug_mask")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val ANALYTICS_ASKED = booleanPreferencesKey("analytics_asked")
        val ANALYTICS_GRANTED = booleanPreferencesKey("analytics_granted")
        val TARGET_COUNT_GOAL = intPreferencesKey("target_count_goal")
        val BATCH_MODE = booleanPreferencesKey("batch_mode_enabled")
        val COMPLETED_SESSIONS_COUNT = intPreferencesKey("completed_sessions_count")
        val ROI_LEFT = floatPreferencesKey("roi_left")
        val ROI_TOP = floatPreferencesKey("roi_top")
        val ROI_RIGHT = floatPreferencesKey("roi_right")
        val ROI_BOTTOM = floatPreferencesKey("roi_bottom")
        val LINE_A_POS = floatPreferencesKey("line_a_pos")
        val LINE_B_POS = floatPreferencesKey("line_b_pos")

        val CUSTOM_SENS = floatPreferencesKey("custom_sens")
        val CUSTOM_MIN_AREA = intPreferencesKey("custom_min_area")
        val CUSTOM_MAX_AREA = intPreferencesKey("custom_max_area")
        val CUSTOM_SINGLE_AREA = floatPreferencesKey("custom_single_area")
        val CUSTOM_MORPH = intPreferencesKey("custom_morph")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val langCode = prefs[Keys.LANGUAGE] ?: AppLanguage.BANGLA.code
        val lang = AppLanguage.entries.firstOrNull { it.code == langCode } ?: AppLanguage.BANGLA

        val themeId = prefs[Keys.THEME_MODE] ?: AppThemeMode.SYSTEM.id
        val theme = AppThemeMode.entries.firstOrNull { it.id == themeId } ?: AppThemeMode.SYSTEM

        val presetId = prefs[Keys.PRESET] ?: GrainPreset.RICE.id
        val preset = GrainPreset.entries.firstOrNull { it.id == presetId } ?: GrainPreset.RICE

        val dirName = prefs[Keys.DIRECTION] ?: CountingDirection.TOP_TO_BOTTOM.name
        val dir = CountingDirection.entries.firstOrNull { it.name == dirName } ?: CountingDirection.TOP_TO_BOTTOM

        val left = (prefs[Keys.ROI_LEFT] ?: 0.10f).coerceIn(0.04f, 0.75f)
        val top = (prefs[Keys.ROI_TOP] ?: 0.16f).coerceIn(0.04f, 0.75f)
        val right = (prefs[Keys.ROI_RIGHT] ?: 0.90f).coerceIn(left + 0.18f, 0.96f)
        val bottom = (prefs[Keys.ROI_BOTTOM] ?: 0.82f).coerceIn(top + 0.18f, 0.96f)

        AppSettings(
            language = lang,
            themeMode = theme,
            activePreset = preset,
            sensitivity = (prefs[Keys.SENSITIVITY] ?: preset.defaultSensitivity).coerceIn(0.10f, 0.90f),
            minAreaPx = (prefs[Keys.MIN_AREA] ?: preset.defaultMinAreaPx).coerceIn(4, 2000),
            maxAreaPx = (prefs[Keys.MAX_AREA] ?: preset.defaultMaxAreaPx).coerceIn(200, 50000),
            autoSingleGrainSize = prefs[Keys.AUTO_GRAIN_SIZE] ?: true,
            manualSingleGrainAreaPx = (prefs[Keys.MANUAL_GRAIN_AREA] ?: preset.defaultSingleGrainAreaPx).coerceIn(8f, 5000f),
            trackingDistanceNormalized = (prefs[Keys.TRACKING_DIST] ?: preset.defaultTrackingDistanceNormalized).coerceIn(0.05f, 0.35f),
            lineHysteresisNormalized = (prefs[Keys.LINE_HYSTERESIS] ?: preset.defaultLineHysteresisNormalized).coerceIn(0.004f, 0.05f),
            morphKernelSize = (prefs[Keys.MORPH_KERNEL] ?: preset.defaultMorphKernelSize).coerceIn(3, 7),
            countingDirection = dir,
            enableVerificationLineB = prefs[Keys.VERIFY_LINE_B] ?: false,
            useBackCamera = prefs[Keys.USE_BACK_CAM] ?: true,
            targetFps = prefs[Keys.TARGET_FPS] ?: 30,
            lockCameraExposureFocus = prefs[Keys.LOCK_CAMERA] ?: true,
            hapticEnabled = prefs[Keys.HAPTIC] ?: true,
            soundBeepEnabled = prefs[Keys.SOUND_BEEP] ?: true,
            soundVolume = (prefs[Keys.SOUND_VOLUME] ?: 0.65f).coerceIn(0.1f, 1.0f),
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true,
            showTrackIds = prefs[Keys.SHOW_TRACK_IDS] ?: false,
            showDebugMask = prefs[Keys.SHOW_DEBUG_MASK] ?: false,
            onboardingCompleted = prefs[Keys.ONBOARDING_DONE] ?: false,
            analyticsConsentAsked = prefs[Keys.ANALYTICS_ASKED] ?: false,
            analyticsConsentGranted = prefs[Keys.ANALYTICS_GRANTED] ?: false,
            targetCountGoal = (prefs[Keys.TARGET_COUNT_GOAL] ?: 0).coerceAtLeast(0),
            batchModeEnabled = prefs[Keys.BATCH_MODE] ?: false,
            completedSessionsCountForReview = prefs[Keys.COMPLETED_SESSIONS_COUNT] ?: 0,
            roiRect = NormalizedRect(left, top, right, bottom),
            lineAPosition = (prefs[Keys.LINE_A_POS] ?: 0.50f).coerceIn(0.10f, 0.90f),
            lineBPosition = (prefs[Keys.LINE_B_POS] ?: 0.68f).coerceIn(0.15f, 0.92f)
        )
    }

    suspend fun applyPreset(preset: GrainPreset) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PRESET] = preset.id
            if (preset == GrainPreset.CUSTOM) {
                prefs[Keys.SENSITIVITY] = prefs[Keys.CUSTOM_SENS] ?: preset.defaultSensitivity
                prefs[Keys.MIN_AREA] = prefs[Keys.CUSTOM_MIN_AREA] ?: preset.defaultMinAreaPx
                prefs[Keys.MAX_AREA] = prefs[Keys.CUSTOM_MAX_AREA] ?: preset.defaultMaxAreaPx
                prefs[Keys.MANUAL_GRAIN_AREA] = prefs[Keys.CUSTOM_SINGLE_AREA] ?: preset.defaultSingleGrainAreaPx
                prefs[Keys.MORPH_KERNEL] = prefs[Keys.CUSTOM_MORPH] ?: preset.defaultMorphKernelSize
            } else {
                prefs[Keys.SENSITIVITY] = preset.defaultSensitivity
                prefs[Keys.MIN_AREA] = preset.defaultMinAreaPx
                prefs[Keys.MAX_AREA] = preset.defaultMaxAreaPx
                prefs[Keys.MANUAL_GRAIN_AREA] = preset.defaultSingleGrainAreaPx
                prefs[Keys.MORPH_KERNEL] = preset.defaultMorphKernelSize
                prefs[Keys.TRACKING_DIST] = preset.defaultTrackingDistanceNormalized
                prefs[Keys.LINE_HYSTERESIS] = preset.defaultLineHysteresisNormalized
            }
        }
    }

    suspend fun saveCurrentAsCustomPreset() {
        context.dataStore.edit { prefs ->
            prefs[Keys.CUSTOM_SENS] = prefs[Keys.SENSITIVITY] ?: 0.50f
            prefs[Keys.CUSTOM_MIN_AREA] = prefs[Keys.MIN_AREA] ?: 18
            prefs[Keys.CUSTOM_MAX_AREA] = prefs[Keys.MAX_AREA] ?: 4500
            prefs[Keys.CUSTOM_SINGLE_AREA] = prefs[Keys.MANUAL_GRAIN_AREA] ?: 110f
            prefs[Keys.CUSTOM_MORPH] = prefs[Keys.MORPH_KERNEL] ?: 3
            prefs[Keys.PRESET] = GrainPreset.CUSTOM.id
        }
    }

    suspend fun resetAllToDefaults() {
        applyPreset(GrainPreset.RICE)
    }

    suspend fun updateLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun updateThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.id }
    }

    suspend fun updateSensitivity(value: Float) {
        context.dataStore.edit { it[Keys.SENSITIVITY] = value.coerceIn(0.10f, 0.90f) }
    }

    suspend fun updateMinArea(value: Int) {
        context.dataStore.edit { it[Keys.MIN_AREA] = value.coerceIn(4, 2000) }
    }

    suspend fun updateMaxArea(value: Int) {
        context.dataStore.edit { it[Keys.MAX_AREA] = value.coerceIn(200, 50000) }
    }

    suspend fun updateAutoSingleGrainSize(auto: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_GRAIN_SIZE] = auto }
    }

    suspend fun updateManualSingleGrainArea(areaPx: Float) {
        context.dataStore.edit { it[Keys.MANUAL_GRAIN_AREA] = areaPx.coerceIn(8f, 5000f) }
    }

    suspend fun updateTrackingDistance(dist: Float) {
        context.dataStore.edit { it[Keys.TRACKING_DIST] = dist.coerceIn(0.05f, 0.35f) }
    }

    suspend fun updateLineHysteresis(hyst: Float) {
        context.dataStore.edit { it[Keys.LINE_HYSTERESIS] = hyst.coerceIn(0.004f, 0.05f) }
    }

    suspend fun updateMorphKernel(size: Int) {
        val odd = if (size <= 3) 3 else if (size <= 5) 5 else 7
        context.dataStore.edit { it[Keys.MORPH_KERNEL] = odd }
    }

    suspend fun updateDirection(direction: CountingDirection) {
        context.dataStore.edit { it[Keys.DIRECTION] = direction.name }
    }

    suspend fun updateVerificationLineB(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VERIFY_LINE_B] = enabled }
    }

    suspend fun updateCameraFacing(useBack: Boolean) {
        context.dataStore.edit { it[Keys.USE_BACK_CAM] = useBack }
    }

    suspend fun updateTargetFps(fps: Int) {
        context.dataStore.edit { it[Keys.TARGET_FPS] = if (fps >= 60) 60 else 30 }
    }

    suspend fun updateLockCamera(locked: Boolean) {
        context.dataStore.edit { it[Keys.LOCK_CAMERA] = locked }
    }

    suspend fun updateHaptic(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTIC] = enabled }
    }

    suspend fun updateSoundBeep(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SOUND_BEEP] = enabled }
    }

    suspend fun updateSoundVolume(volume: Float) {
        context.dataStore.edit { it[Keys.SOUND_VOLUME] = volume.coerceIn(0.1f, 1.0f) }
    }

    suspend fun updateKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { it[Keys.KEEP_SCREEN_ON] = enabled }
    }

    suspend fun updateShowTrackIds(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_TRACK_IDS] = show }
    }

    suspend fun updateShowDebugMask(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_DEBUG_MASK] = show }
    }

    suspend fun setOnboardingCompleted() {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = true }
    }

    suspend fun setAnalyticsConsent(granted: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ANALYTICS_ASKED] = true
            prefs[Keys.ANALYTICS_GRANTED] = granted
        }
    }

    suspend fun updateTargetCountGoal(goal: Int) {
        context.dataStore.edit { it[Keys.TARGET_COUNT_GOAL] = goal.coerceIn(0, 100000) }
    }

    suspend fun updateBatchMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BATCH_MODE] = enabled }
    }

    suspend fun incrementCompletedSessions(): Int {
        var updated = 1
        context.dataStore.edit { prefs ->
            updated = (prefs[Keys.COMPLETED_SESSIONS_COUNT] ?: 0) + 1
            prefs[Keys.COMPLETED_SESSIONS_COUNT] = updated
        }
        return updated
    }

    suspend fun updateRoiAndLines(roi: NormalizedRect, lineA: Float, lineB: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ROI_LEFT] = roi.left.coerceIn(0.04f, 0.75f)
            prefs[Keys.ROI_TOP] = roi.top.coerceIn(0.04f, 0.75f)
            prefs[Keys.ROI_RIGHT] = roi.right.coerceIn(0.20f, 0.96f)
            prefs[Keys.ROI_BOTTOM] = roi.bottom.coerceIn(0.20f, 0.96f)
            prefs[Keys.LINE_A_POS] = lineA.coerceIn(0.10f, 0.90f)
            prefs[Keys.LINE_B_POS] = lineB.coerceIn(0.15f, 0.92f)
        }
    }
}
