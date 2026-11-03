package com.ontik.countai.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ontik.countai.cv.KalmanHungarianTracker
import com.ontik.countai.cv.OpenCvGrainProcessor
import com.ontik.countai.data.local.CountSessionEntity
import com.ontik.countai.data.repository.AppSettings
import com.ontik.countai.data.repository.CountRepository
import com.ontik.countai.data.repository.SettingsRepository
import com.ontik.countai.domain.model.AccuracyConfidenceLevel
import com.ontik.countai.domain.model.AppLanguage
import com.ontik.countai.domain.model.AppThemeMode
import com.ontik.countai.domain.model.CountingDirection
import com.ontik.countai.domain.model.DebugFrameSnapshot
import com.ontik.countai.domain.model.GrainPreset
import com.ontik.countai.domain.model.NormalizedRect
import com.ontik.countai.domain.model.TrackedGrain
import com.ontik.countai.util.ImageProxyUtils
import com.ontik.countai.util.SessionExportHelper
import com.ontik.countai.util.SoundFeedbackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

enum class EngineStatus {
    CALIBRATING,
    READY,
    COUNTING,
    PAUSED
}

data class LiveCountingUiState(
    val status: EngineStatus = EngineStatus.CALIBRATING,
    val calibrationProgress: Float = 0f,
    val isCalibrated: Boolean = false,
    val cameraLockedAfterCalibration: Boolean = false,
    val autoCountLineA: Int = 0,
    val autoCountLineB: Int = 0,
    val manualDelta: Int = 0,
    val inZoneGrainCount: Int = 0,
    val countRatePerSec: Float = 0f,
    val currentFps: Int = 30,
    val lowFpsWarning: Boolean = false,
    val confidenceBadge: AccuracyConfidenceLevel = AccuracyConfidenceLevel.GOOD,
    val learnedSingleGrainAreaPx: Float = 110f,
    val noiseStd: Double = 2.5,
    val trackedGrains: List<TrackedGrain> = emptyList(),
    val debugMaskBitmap: Bitmap? = null,
    val torchEnabled: Boolean = false,
    val isTestMode100Active: Boolean = false,
    val contextualHintEn: String? = null,
    val contextualHintBn: String? = null,
    val lastCountPulseTimestampMs: Long = 0L,
    val lastMilestoneHundredTimestampMs: Long = 0L,
    val sessionStartElapsedMs: Long = SystemClock.elapsedRealtime()
) {
    val totalCount: Int
        get() = (autoCountLineA + manualDelta).coerceAtLeast(0)

    val verificationMismatchWarning: Boolean
        get() = autoCountLineB > 0 && abs(autoCountLineA - autoCountLineB) >= 3
}

class CountViewModel(
    application: Application,
    private val countRepository: CountRepository,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettings()
    )

    val historySessions: StateFlow<List<CountSessionEntity>> = countRepository.allSessions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _uiState = MutableStateFlow(LiveCountingUiState())
    val uiState: StateFlow<LiveCountingUiState> = _uiState.asStateFlow()

    private val cvProcessor = OpenCvGrainProcessor()
    private val tracker = KalmanHungarianTracker()
    private val soundManager = SoundFeedbackManager()
    private val isProcessingFrame = AtomicBoolean(false)

    // Undo history stack (stores incremental count additions so user can undo the last count)
    private val countUndoStack = ArrayDeque<Int>(30)

    // Rolling timestamps of counted grains in the last 3 seconds to compute Rate/sec
    private val recentCountTimestamps = ArrayDeque<Pair<Long, Int>>(60)

    // FPS measurement window
    private val frameTimestamps = ArrayDeque<Long>(35)

    // 10-second Debug Recorder buffer
    private val debugSnapshots = ArrayDeque<DebugFrameSnapshot>(320)

    fun startCounting() {
        _uiState.update { state ->
            val startMs = if (state.sessionStartElapsedMs == 0L) SystemClock.elapsedRealtime() else state.sessionStartElapsedMs
            state.copy(
                status = EngineStatus.COUNTING,
                sessionStartElapsedMs = startMs
            )
        }
    }

    fun pauseCounting() {
        _uiState.update { it.copy(status = EngineStatus.PAUSED) }
    }

    fun toggleStartPause() {
        val current = _uiState.value.status
        if (current == EngineStatus.COUNTING) {
            pauseCounting()
        } else {
            startCounting()
        }
    }

    fun resetCount() {
        tracker.reset()
        countUndoStack.clear()
        recentCountTimestamps.clear()
        _uiState.update { state ->
            state.copy(
                status = if (state.isCalibrated) EngineStatus.READY else EngineStatus.CALIBRATING,
                autoCountLineA = 0,
                autoCountLineB = 0,
                manualDelta = 0,
                inZoneGrainCount = 0,
                countRatePerSec = 0f,
                trackedGrains = emptyList(),
                sessionStartElapsedMs = SystemClock.elapsedRealtime()
            )
        }
    }

    fun triggerAutoCalibrate() {
        tracker.reset()
        cvProcessor.startAutoCalibration()
        _uiState.update {
            it.copy(
                status = EngineStatus.CALIBRATING,
                calibrationProgress = 0f,
                isCalibrated = false,
                cameraLockedAfterCalibration = false,
                contextualHintEn = "Calibrating empty surface (30 frames)...",
                contextualHintBn = "খালি পৃষ্ঠ ক্যালিব্রেট হচ্ছে (৩০ ফ্রেম)..."
            )
        }
    }

    fun triggerSingleGrainSizeCalibration() {
        cvProcessor.startSingleGrainSizeCalibration()
        _uiState.update {
            it.copy(
                contextualHintEn = "Drop a few isolated grains to learn single-grain size",
                contextualHintBn = "একক দানার আকার শেখার জন্য কয়েকটি আলাদা দানা বক্সে রাখুন"
            )
        }
    }

    fun adjustCountManually(delta: Int) {
        _uiState.update { state ->
            val next = state.totalCount + delta
            if (next < 0) state else {
                if (delta > 0) countUndoStack.addLast(delta)
                state.copy(manualDelta = state.manualDelta + delta)
            }
        }
    }

    fun undoLastCount() {
        _uiState.update { state ->
            if (state.totalCount <= 0) return@update state
            val lastIncrement = if (countUndoStack.isNotEmpty()) countUndoStack.removeLast() else 1
            val deduct = lastIncrement.coerceAtMost(state.totalCount)
            state.copy(manualDelta = state.manualDelta - deduct)
        }
    }

    fun toggleTorch() {
        _uiState.update { it.copy(torchEnabled = !it.torchEnabled) }
    }

    fun toggleTestMode100() {
        resetCount()
        _uiState.update { state ->
            val nextTest = !state.isTestMode100Active
            state.copy(
                isTestMode100Active = nextTest,
                status = EngineStatus.COUNTING,
                contextualHintEn = if (nextTest) "100-Grain Test Mode Active: Drop exactly 100 grains, then tap Save" else null,
                contextualHintBn = if (nextTest) "১০০ দানা টেস্ট মোড চালু: ঠিক ১০০টি দানা পার করে সেভ চাপুন" else null
            )
        }
    }

    fun saveSessionToHistory(customLabel: String = "", customNotes: String = ""): CountSessionEntity? {
        val st = _uiState.value
        val cfg = settings.value
        val finalTotal = if (st.totalCount > 0) st.totalCount else st.inZoneGrainCount
        if (finalTotal <= 0 && !st.isTestMode100Active) return null

        val durationSec = ((SystemClock.elapsedRealtime() - st.sessionStartElapsedMs) / 1000L).coerceAtLeast(1L)
        val errPct = if (st.isTestMode100Active) {
            abs(finalTotal - 100).toFloat()
        } else {
            0f
        }
        val defaultLabel = if (st.isTestMode100Active) {
            "100-Grain Accuracy Test (${String.format(java.util.Locale.US, "%.1f%%", 100f - errPct)} acc)"
        } else {
            cfg.activePreset.nameEn
        }

        val entity = CountSessionEntity(
            timestampMs = System.currentTimeMillis(),
            label = customLabel.ifBlank { defaultLabel },
            presetName = cfg.activePreset.nameEn,
            finalCount = finalTotal,
            autoCount = st.autoCountLineA,
            manualDelta = st.manualDelta,
            direction = cfg.countingDirection.displayNameEn,
            durationSeconds = durationSec,
            isTestModeSession = st.isTestMode100Active,
            expectedTestCount = 100,
            accuracyErrorPercent = errPct,
            notes = customNotes
        )

        viewModelScope.launch(Dispatchers.IO) {
            countRepository.saveSession(entity)
        }
        return entity
    }

    fun updateHistorySession(session: CountSessionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            countRepository.updateSession(session)
        }
    }

    fun deleteHistorySession(session: CountSessionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            countRepository.deleteSession(session)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            countRepository.clearAllHistory()
        }
    }

    fun exportHistoryCsv() {
        val list = historySessions.value
        if (list.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val file = SessionExportHelper.exportToCsv(getApplication(), list)
            SessionExportHelper.shareExportedFile(getApplication(), file, "text/csv")
        }
    }

    fun exportLast10SecondsDebugLog() {
        val snaps = synchronized(debugSnapshots) { debugSnapshots.toList() }
        if (snaps.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val file: File = SessionExportHelper.exportDebugLog(getApplication(), snaps)
            SessionExportHelper.shareExportedFile(getApplication(), file, "text/csv")
        }
    }

    fun processCameraFrame(imageProxy: ImageProxy) {
        if (!isProcessingFrame.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val nowMs = SystemClock.elapsedRealtime()
                val cfg = settings.value

                // Update rolling FPS
                frameTimestamps.addLast(nowMs)
                while (frameTimestamps.size > 1 && nowMs - frameTimestamps.first() > 1000L) {
                    frameTimestamps.removeFirst()
                }
                val measuredFps = frameTimestamps.size.coerceIn(1, 60)

                val bmp = ImageProxyUtils.imageProxyToUprightBitmap(
                    imageProxy = imageProxy,
                    isFrontCamera = !cfg.useBackCamera
                )
                if (bmp != null) {
                    val cvOut = cvProcessor.processFrame(
                        frameBitmap = bmp,
                        roiNormalized = cfg.roiRect,
                        sensitivity = cfg.sensitivity,
                        minAreaPx = cfg.minAreaPx,
                        maxAreaPx = cfg.maxAreaPx,
                        morphKernelSize = cfg.morphKernelSize,
                        autoSingleGrainSize = cfg.autoSingleGrainSize,
                        manualSingleGrainAreaPx = cfg.manualSingleGrainAreaPx,
                        generateDebugMask = cfg.showDebugMask,
                        currentFps = measuredFps
                    )

                    val prevStatus = _uiState.value.status
                    val nextStatus = when {
                        cvOut.isCalibrating -> EngineStatus.CALIBRATING
                        prevStatus == EngineStatus.CALIBRATING && cvOut.isCalibrated -> EngineStatus.COUNTING
                        else -> prevStatus
                    }

                    val countingActive = nextStatus == EngineStatus.COUNTING && cvOut.isCalibrated

                    val trackerOut = tracker.step(
                        blobs = cvOut.blobs,
                        timestampMs = nowMs,
                        baseGatingDistance = cfg.trackingDistanceNormalized,
                        lineAPos = cfg.lineAPosition,
                        lineBPos = if (cfg.enableVerificationLineB) cfg.lineBPosition else null,
                        hysteresis = cfg.lineHysteresisNormalized,
                        direction = cfg.countingDirection,
                        allowBidirectionalReCount = true,
                        isCountingActive = countingActive
                    )

                    val addedCount = trackerOut.newlyCountedLineA
                    if (addedCount > 0) {
                        countUndoStack.addLast(addedCount)
                        while (countUndoStack.size > 30) countUndoStack.removeFirst()
                        recentCountTimestamps.addLast(nowMs to addedCount)

                        if (cfg.soundBeepEnabled) {
                            soundManager.playCountTick(cfg.soundVolume)
                        }
                    }

                    while (recentCountTimestamps.isNotEmpty() && nowMs - recentCountTimestamps.first().first > 2500L) {
                        recentCountTimestamps.removeFirst()
                    }
                    val sumRecent = recentCountTimestamps.sumOf { it.second }
                    val ratePerSec = sumRecent / 2.5f

                    val inZoneTotalGrains = cvOut.blobs.sumOf { it.estimatedGrainCount }

                    val prevTotal = _uiState.value.totalCount
                    val newAutoA = _uiState.value.autoCountLineA + addedCount
                    val newTotal = (newAutoA + _uiState.value.manualDelta).coerceAtLeast(0)
                    val crossedHundredMilestone = addedCount > 0 && (newTotal / 100) > (prevTotal / 100)
                    if (crossedHundredMilestone && cfg.soundBeepEnabled) {
                        soundManager.playMilestoneChime(cfg.soundVolume)
                    }

                    // Record into 10-second debug recorder
                    synchronized(debugSnapshots) {
                        debugSnapshots.addLast(
                            DebugFrameSnapshot(
                                timestampMs = nowMs,
                                totalCount = newTotal,
                                inZoneCount = inZoneTotalGrains,
                                fps = measuredFps,
                                noiseStd = cvOut.noiseStd,
                                singleGrainAreaPx = cvOut.learnedSingleGrainAreaPx
                            )
                        )
                        while (debugSnapshots.isNotEmpty() && nowMs - debugSnapshots.first().timestampMs > 10_000L) {
                            debugSnapshots.removeFirst()
                        }
                    }

                    _uiState.update { state ->
                        state.copy(
                            status = nextStatus,
                            calibrationProgress = cvOut.calibrationProgress,
                            isCalibrated = cvOut.isCalibrated,
                            cameraLockedAfterCalibration = cvOut.isCalibrated && cfg.lockCameraExposureFocus,
                            autoCountLineA = newAutoA,
                            autoCountLineB = state.autoCountLineB + trackerOut.newlyCountedLineB,
                            inZoneGrainCount = inZoneTotalGrains,
                            countRatePerSec = ratePerSec,
                            currentFps = measuredFps,
                            lowFpsWarning = trackerOut.frameDropDetected || measuredFps < 15,
                            confidenceBadge = cvOut.confidenceLevel,
                            learnedSingleGrainAreaPx = cvOut.learnedSingleGrainAreaPx,
                            noiseStd = cvOut.noiseStd,
                            trackedGrains = trackerOut.activeTracks,
                            debugMaskBitmap = cvOut.debugMaskBitmap,
                            contextualHintEn = cvOut.contextualHintEn ?: if (trackerOut.frameDropDetected) "Low FPS detected — gating widened automatically" else null,
                            contextualHintBn = cvOut.contextualHintBn ?: if (trackerOut.frameDropDetected) "ফ্রেম রেট কম — ট্র্যাকিং গেট স্বয়ংক্রিয়ভাবে বাড়ানো হয়েছে" else null,
                            lastCountPulseTimestampMs = if (addedCount > 0) nowMs else state.lastCountPulseTimestampMs,
                            lastMilestoneHundredTimestampMs = if (crossedHundredMilestone) nowMs else state.lastMilestoneHundredTimestampMs
                        )
                    }

                    bmp.recycle()
                }
            } finally {
                imageProxy.close()
                isProcessingFrame.set(false)
            }
        }
    }

    // Settings Delegations
    fun applyPreset(preset: GrainPreset) {
        viewModelScope.launch {
            settingsRepository.applyPreset(preset)
            cvProcessor.setManualSingleGrainArea(preset.defaultSingleGrainAreaPx)
        }
    }

    fun saveCustomPreset() {
        viewModelScope.launch { settingsRepository.saveCurrentAsCustomPreset() }
    }

    fun resetSettingsToDefaults() {
        viewModelScope.launch { settingsRepository.resetAllToDefaults() }
    }

    fun updateLanguage(language: AppLanguage) {
        viewModelScope.launch { settingsRepository.updateLanguage(language) }
    }

    fun updateThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { settingsRepository.updateThemeMode(mode) }
    }

    fun updateSensitivity(value: Float) {
        viewModelScope.launch { settingsRepository.updateSensitivity(value) }
    }

    fun updateMinArea(value: Int) {
        viewModelScope.launch { settingsRepository.updateMinArea(value) }
    }

    fun updateMaxArea(value: Int) {
        viewModelScope.launch { settingsRepository.updateMaxArea(value) }
    }

    fun updateAutoSingleGrainSize(auto: Boolean) {
        viewModelScope.launch { settingsRepository.updateAutoSingleGrainSize(auto) }
    }

    fun updateManualSingleGrainArea(areaPx: Float) {
        cvProcessor.setManualSingleGrainArea(areaPx)
        viewModelScope.launch { settingsRepository.updateManualSingleGrainArea(areaPx) }
    }

    fun updateTrackingDistance(dist: Float) {
        viewModelScope.launch { settingsRepository.updateTrackingDistance(dist) }
    }

    fun updateLineHysteresis(hyst: Float) {
        viewModelScope.launch { settingsRepository.updateLineHysteresis(hyst) }
    }

    fun updateMorphKernel(size: Int) {
        viewModelScope.launch { settingsRepository.updateMorphKernel(size) }
    }

    fun updateDirection(direction: CountingDirection) {
        viewModelScope.launch { settingsRepository.updateDirection(direction) }
    }

    fun updateVerificationLineB(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateVerificationLineB(enabled) }
    }

    fun updateCameraFacing(useBack: Boolean) {
        viewModelScope.launch { settingsRepository.updateCameraFacing(useBack) }
    }

    fun updateTargetFps(fps: Int) {
        viewModelScope.launch { settingsRepository.updateTargetFps(fps) }
    }

    fun updateLockCamera(locked: Boolean) {
        viewModelScope.launch { settingsRepository.updateLockCamera(locked) }
    }

    fun updateHaptic(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateHaptic(enabled) }
    }

    fun updateSoundBeep(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateSoundBeep(enabled) }
    }

    fun updateSoundVolume(volume: Float) {
        viewModelScope.launch { settingsRepository.updateSoundVolume(volume) }
    }

    fun updateKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateKeepScreenOn(enabled) }
    }

    fun updateShowTrackIds(show: Boolean) {
        viewModelScope.launch { settingsRepository.updateShowTrackIds(show) }
    }

    fun updateShowDebugMask(show: Boolean) {
        viewModelScope.launch { settingsRepository.updateShowDebugMask(show) }
    }

    fun completeOnboarding() {
        viewModelScope.launch { settingsRepository.setOnboardingCompleted() }
    }

    fun updateRoiAndLines(roi: NormalizedRect, lineA: Float, lineB: Float) {
        viewModelScope.launch { settingsRepository.updateRoiAndLines(roi, lineA, lineB) }
    }

    override fun onCleared() {
        super.onCleared()
        cvProcessor.release()
        soundManager.release()
    }
}

class CountViewModelFactory(
    private val application: Application,
    private val countRepository: CountRepository,
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CountViewModel::class.java)) {
            return CountViewModel(application, countRepository, settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
