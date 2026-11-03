package com.ontik.countai.data.repository

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ontik.countai.domain.model.CountingDirection
import com.ontik.countai.domain.model.ModelProfile
import com.ontik.countai.domain.model.NormalizedRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private val Context.dataStore by preferencesDataStore(name = "ontik_settings")

data class AppSettings(
    val confidenceThreshold: Float = 0.35f,
    val iouThreshold: Float = 0.45f,
    val countingDirection: CountingDirection = CountingDirection.TOP_TO_BOTTOM,
    val modelProfile: ModelProfile = ModelProfile.DEDICATED_SMALL_OBJECT_YOLO,
    val customModelPath: String? = null,
    val useBackCamera: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val countingZone: NormalizedRect = NormalizedRect(0.12f, 0.20f, 0.88f, 0.80f),
    val countingLinePosition: Float = 0.50f,
    val cpuThreads: Int = 4
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val CONFIDENCE = floatPreferencesKey("confidence_threshold")
        val IOU = floatPreferencesKey("iou_threshold")
        val DIRECTION = stringPreferencesKey("counting_direction")
        val MODEL_PROFILE = stringPreferencesKey("model_profile")
        val CUSTOM_MODEL_PATH = stringPreferencesKey("custom_model_path")
        val USE_BACK_CAMERA = booleanPreferencesKey("use_back_camera")
        val HAPTIC = booleanPreferencesKey("haptic_feedback")
        val ZONE_LEFT = floatPreferencesKey("zone_left")
        val ZONE_TOP = floatPreferencesKey("zone_top")
        val ZONE_RIGHT = floatPreferencesKey("zone_right")
        val ZONE_BOTTOM = floatPreferencesKey("zone_bottom")
        val LINE_POS = floatPreferencesKey("line_position")
        val CPU_THREADS = intPreferencesKey("cpu_threads")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val dirName = prefs[Keys.DIRECTION] ?: CountingDirection.TOP_TO_BOTTOM.name
        val direction = CountingDirection.entries.firstOrNull { it.name == dirName }
            ?: CountingDirection.TOP_TO_BOTTOM

        val profileId = prefs[Keys.MODEL_PROFILE] ?: ModelProfile.DEDICATED_SMALL_OBJECT_YOLO.id
        val profile = ModelProfile.entries.firstOrNull { it.id == profileId }
            ?: ModelProfile.DEDICATED_SMALL_OBJECT_YOLO

        val left = (prefs[Keys.ZONE_LEFT] ?: 0.12f).coerceIn(0.02f, 0.80f)
        val top = (prefs[Keys.ZONE_TOP] ?: 0.20f).coerceIn(0.02f, 0.80f)
        val right = (prefs[Keys.ZONE_RIGHT] ?: 0.88f).coerceIn(left + 0.15f, 0.98f)
        val bottom = (prefs[Keys.ZONE_BOTTOM] ?: 0.80f).coerceIn(top + 0.15f, 0.98f)

        AppSettings(
            confidenceThreshold = (prefs[Keys.CONFIDENCE] ?: 0.35f).coerceIn(0.10f, 0.95f),
            iouThreshold = (prefs[Keys.IOU] ?: 0.45f).coerceIn(0.15f, 0.85f),
            countingDirection = direction,
            modelProfile = profile,
            customModelPath = prefs[Keys.CUSTOM_MODEL_PATH],
            useBackCamera = prefs[Keys.USE_BACK_CAMERA] ?: true,
            hapticFeedbackEnabled = prefs[Keys.HAPTIC] ?: true,
            countingZone = NormalizedRect(left, top, right, bottom),
            countingLinePosition = (prefs[Keys.LINE_POS] ?: 0.50f).coerceIn(0.10f, 0.90f),
            cpuThreads = (prefs[Keys.CPU_THREADS] ?: 4).coerceIn(1, 8)
        )
    }

    suspend fun updateConfidence(value: Float) {
        context.dataStore.edit { it[Keys.CONFIDENCE] = value.coerceIn(0.10f, 0.95f) }
    }

    suspend fun updateIouThreshold(value: Float) {
        context.dataStore.edit { it[Keys.IOU] = value.coerceIn(0.15f, 0.85f) }
    }

    suspend fun updateDirection(direction: CountingDirection) {
        context.dataStore.edit { it[Keys.DIRECTION] = direction.name }
    }

    suspend fun updateModelProfile(profile: ModelProfile) {
        context.dataStore.edit { it[Keys.MODEL_PROFILE] = profile.id }
    }

    suspend fun updateCameraSelection(useBack: Boolean) {
        context.dataStore.edit { it[Keys.USE_BACK_CAMERA] = useBack }
    }

    suspend fun updateHapticFeedback(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTIC] = enabled }
    }

    suspend fun updateCpuThreads(threads: Int) {
        context.dataStore.edit { it[Keys.CPU_THREADS] = threads.coerceIn(1, 8) }
    }

    suspend fun updateCountingZoneAndLine(zone: NormalizedRect, linePos: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ZONE_LEFT] = zone.left.coerceIn(0.02f, 0.80f)
            prefs[Keys.ZONE_TOP] = zone.top.coerceIn(0.02f, 0.80f)
            prefs[Keys.ZONE_RIGHT] = zone.right.coerceIn(0.20f, 0.98f)
            prefs[Keys.ZONE_BOTTOM] = zone.bottom.coerceIn(0.20f, 0.98f)
            prefs[Keys.LINE_POS] = linePos.coerceIn(0.10f, 0.90f)
        }
    }

    /**
     * Copies a user-selected `.tflite` file from storage URI into the app's private offline storage
     * (`filesDir/models/custom_imported_model.tflite`) and activates it immediately.
     */
    suspend fun importCustomTfliteModel(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
            val targetFile = File(modelsDir, "custom_imported_model.tflite")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(IllegalStateException("Unable to read selected file URI"))

            if (targetFile.length() < 128) {
                targetFile.delete()
                return@withContext Result.failure(IllegalArgumentException("Selected file is too small to be a valid .tflite model"))
            }

            context.dataStore.edit { prefs ->
                prefs[Keys.CUSTOM_MODEL_PATH] = targetFile.absolutePath
                prefs[Keys.MODEL_PROFILE] = ModelProfile.CUSTOM_EXTERNAL_TFLITE.id
            }

            Result.success(targetFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
