package com.ontik.countai.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ontik.countai.ai.LineCrossingEvaluator
import com.ontik.countai.ai.MultiObjectTracker
import com.ontik.countai.ai.ObjectDetectorContract
import com.ontik.countai.ai.TFLiteYoloDetector
import com.ontik.countai.data.local.CountSessionEntity
import com.ontik.countai.data.repository.AppSettings
import com.ontik.countai.data.repository.CountRepository
import com.ontik.countai.data.repository.SettingsRepository
import com.ontik.countai.domain.model.CountingDirection
import com.ontik.countai.domain.model.DetectionResult
import com.ontik.countai.domain.model.ManualPhotoPoint
import com.ontik.countai.domain.model.ModelEngineState
import com.ontik.countai.domain.model.ModelProfile
import com.ontik.countai.domain.model.NormalizedRect
import com.ontik.countai.domain.model.TrackedObject
import com.ontik.countai.util.ImageProxyUtils
import com.ontik.countai.util.SessionExportHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.hypot

enum class LiveCountingState {
    IDLE,
    RUNNING,
    PAUSED
}

data class LiveUiState(
    val countingState: LiveCountingState = LiveCountingState.IDLE,
    val aiCrossedCount: Int = 0,
    val manualDelta: Int = 0,
    val currentlyDetectedInZone: Int = 0,
    val trackedObjects: List<TrackedObject> = emptyList(),
    val inferenceTimeMs: Long = 0L,
    val torchEnabled: Boolean = false,
    val isAdjustingZone: Boolean = false,
    val statusBannerMessage: String? = null,
    val modelEngineState: ModelEngineState = ModelEngineState.Uninitialized,
    val sessionStartElapsedMs: Long = 0L,
    val lastCountPulseTimestamp: Long = 0L
) {
    val totalCount: Int
        get() = (aiCrossedCount + manualDelta).coerceAtLeast(0)
}

data class PhotoCountingUiState(
    val bitmap: Bitmap? = null,
    val isAnalyzing: Boolean = false,
    val aiDetections: List<DetectionResult> = emptyList(),
    val suppressedDetectionIndices: Set<Int> = emptySet(),
    val manualAddedPoints: List<ManualPhotoPoint> = emptyList(),
    val manualDelta: Int = 0,
    val inferenceTimeMs: Long = 0L,
    val statusMessage: String? = null
) {
    val activeAiCount: Int
        get() = (aiDetections.size - suppressedDetectionIndices.size).coerceAtLeast(0)

    val totalCount: Int
        get() = (activeAiCount + manualAddedPoints.size + manualDelta).coerceAtLeast(0)
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

    private val _liveState = MutableStateFlow(LiveUiState())
    val liveState: StateFlow<LiveUiState> = _liveState.asStateFlow()

    private val _photoState = MutableStateFlow(PhotoCountingUiState())
    val photoState: StateFlow<PhotoCountingUiState> = _photoState.asStateFlow()

    private var detector: ObjectDetectorContract? = null
    private val tracker = MultiObjectTracker()
    private val isProcessingFrame = AtomicBoolean(false)

    private var activeProfileId: String = ""
    private var activeCustomPath: String? = null
    private var activeThreads: Int = 4

    init {
        viewModelScope.launch(Dispatchers.Default) {
            settingsRepository.settingsFlow.collect { cfg ->
                if (detector == null ||
                    cfg.modelProfile.id != activeProfileId ||
                    cfg.customModelPath != activeCustomPath ||
                    cfg.cpuThreads != activeThreads
                ) {
                    rebuildDetector(cfg)
                }
            }
        }
    }

    @Synchronized
    private fun rebuildDetector(cfg: AppSettings) {
        detector?.close()
        activeProfileId = cfg.modelProfile.id
        activeCustomPath = cfg.customModelPath
        activeThreads = cfg.cpuThreads

        val newDetector = TFLiteYoloDetector(
            context = getApplication(),
            profile = cfg.modelProfile,
            customModelFilePath = cfg.customModelPath,
            numThreads = cfg.cpuThreads
        )
        detector = newDetector
        _liveState.update {
            it.copy(
                modelEngineState = newDetector.engineState,
                statusBannerMessage = when (val st = newDetector.engineState) {
                    is ModelEngineState.MissingModelFile -> st.instructions
                    is ModelEngineState.Error -> st.message
                    is ModelEngineState.Ready -> "AI Model Ready: ${st.modelName} (${st.inputWidth}x${st.inputHeight})"
                    ModelEngineState.Uninitialized -> "Initializing AI engine..."
                }
            )
        }
    }

    // Live Counting Controls
    fun startLiveCounting() {
        _liveState.update { state ->
            val startMs = if (state.sessionStartElapsedMs == 0L) {
                SystemClock.elapsedRealtime()
            } else {
                state.sessionStartElapsedMs
            }
            state.copy(
                countingState = LiveCountingState.RUNNING,
                sessionStartElapsedMs = startMs
            )
        }
    }

    fun pauseLiveCounting() {
        _liveState.update { it.copy(countingState = LiveCountingState.PAUSED) }
    }

    fun resumeLiveCounting() {
        _liveState.update { it.copy(countingState = LiveCountingState.RUNNING) }
    }

    fun resetLiveCounting() {
        tracker.reset()
        _liveState.update {
            it.copy(
                countingState = LiveCountingState.IDLE,
                aiCrossedCount = 0,
                manualDelta = 0,
                currentlyDetectedInZone = 0,
                trackedObjects = emptyList(),
                sessionStartElapsedMs = 0L
            )
        }
    }

    fun adjustLiveCountManually(delta: Int) {
        _liveState.update { state ->
            val nextTotal = state.totalCount + delta
            if (nextTotal < 0) state else state.copy(manualDelta = state.manualDelta + delta)
        }
    }

    fun toggleTorch() {
        _liveState.update { it.copy(torchEnabled = !it.torchEnabled) }
    }

    fun setTorchEnabled(enabled: Boolean) {
        _liveState.update { it.copy(torchEnabled = enabled) }
    }

    fun toggleZoneAdjustmentMode() {
        _liveState.update { it.copy(isAdjustingZone = !it.isAdjustingZone) }
    }

    fun saveCurrentLiveSession(notes: String = "") {
        val current = _liveState.value
        val cfg = settings.value
        if (current.totalCount <= 0 && current.aiCrossedCount <= 0) return

        val durationSec = if (current.sessionStartElapsedMs > 0L) {
            ((SystemClock.elapsedRealtime() - current.sessionStartElapsedMs) / 1000L).coerceAtLeast(1L)
        } else {
            0L
        }
        val modelLabel = when (val st = current.modelEngineState) {
            is ModelEngineState.Ready -> st.modelName
            else -> cfg.modelProfile.title
        }

        viewModelScope.launch(Dispatchers.IO) {
            countRepository.saveSession(
                CountSessionEntity(
                    timestampMs = System.currentTimeMillis(),
                    mode = "LIVE",
                    finalCount = current.totalCount,
                    aiDetectedCount = current.aiCrossedCount,
                    manualDelta = current.manualDelta,
                    direction = cfg.countingDirection.displayName,
                    confidenceThreshold = cfg.confidenceThreshold,
                    durationSeconds = durationSec,
                    modelName = modelLabel,
                    notes = notes
                )
            )
        }
    }

    /**
     * Processes a real-time CameraX [ImageProxy] frame on a background analyzer thread.
     */
    fun processCameraFrame(imageProxy: ImageProxy) {
        if (!isProcessingFrame.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val cfg = settings.value
                val bitmap = ImageProxyUtils.imageProxyToUprightBitmap(
                    imageProxy = imageProxy,
                    isFrontCamera = !cfg.useBackCamera
                )
                if (bitmap != null) {
                    val activeDetector = detector
                    if (activeDetector != null) {
                        val output = activeDetector.detect(
                            bitmap = bitmap,
                            confidenceThreshold = cfg.confidenceThreshold,
                            iouThreshold = cfg.iouThreshold,
                            countingZone = cfg.countingZone
                        )

                        val tracked = tracker.update(output.detections)
                        val nowMs = SystemClock.elapsedRealtime()

                        var newlyCounted = 0
                        if (_liveState.value.countingState == LiveCountingState.RUNNING) {
                            val crossedList = LineCrossingEvaluator.findNewlyCrossedObjects(
                                trackedObjects = tracked,
                                countingZone = cfg.countingZone,
                                linePositionNormalized = cfg.countingLinePosition,
                                direction = cfg.countingDirection
                            )
                            for (crossedObj in crossedList) {
                                tracker.markTrackAsCounted(crossedObj.trackId, nowMs)
                                newlyCounted++
                            }
                        }

                        val refreshedTracked = if (newlyCounted > 0) {
                            tracked.map { t ->
                                if (t.hasBeenCounted || LineCrossingEvaluator.findNewlyCrossedObjects(
                                        listOf(t),
                                        cfg.countingZone,
                                        cfg.countingLinePosition,
                                        cfg.countingDirection
                                    ).isNotEmpty()
                                ) {
                                    t.copy(hasBeenCounted = true, crossedTimestampMs = nowMs)
                                } else {
                                    t
                                }
                            }
                        } else {
                            tracked
                        }

                        _liveState.update { prev ->
                            prev.copy(
                                aiCrossedCount = prev.aiCrossedCount + newlyCounted,
                                currentlyDetectedInZone = output.detections.size,
                                trackedObjects = refreshedTracked,
                                inferenceTimeMs = output.inferenceTimeMs,
                                statusBannerMessage = output.statusMessage ?: when (val st = activeDetector.engineState) {
                                    is ModelEngineState.MissingModelFile -> st.instructions
                                    is ModelEngineState.Error -> st.message
                                    is ModelEngineState.Ready -> null
                                    ModelEngineState.Uninitialized -> null
                                },
                                lastCountPulseTimestamp = if (newlyCounted > 0) nowMs else prev.lastCountPulseTimestamp
                            )
                        }
                    }
                    bitmap.recycle()
                }
            } finally {
                imageProxy.close()
                isProcessingFrame.set(false)
            }
        }
    }

    // Photo Counting Mode
    fun analyzePhotoFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.Default) {
            _photoState.update { it.copy(isAnalyzing = true, statusMessage = "Loading and analyzing photo...") }
            val bmp = ImageProxyUtils.loadUprightBitmapFromUri(getApplication(), uri, maxDimension = 1280)
            if (bmp == null) {
                _photoState.update {
                    it.copy(
                        isAnalyzing = false,
                        statusMessage = "Could not decode the selected image. Please try another photo."
                    )
                }
                return@launch
            }
            analyzeBitmapForPhotoMode(bmp)
        }
    }

    fun analyzeBitmapForPhotoMode(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.Default) {
            _photoState.update {
                it.copy(
                    bitmap = bitmap,
                    isAnalyzing = true,
                    suppressedDetectionIndices = emptySet(),
                    manualAddedPoints = emptyList(),
                    manualDelta = 0
                )
            }
            val cfg = settings.value
            val activeDetector = detector
            if (activeDetector == null) {
                _photoState.update {
                    it.copy(
                        isAnalyzing = false,
                        statusMessage = "AI detector is not initialized."
                    )
                }
                return@launch
            }

            val output = activeDetector.detect(
                bitmap = bitmap,
                confidenceThreshold = cfg.confidenceThreshold,
                iouThreshold = cfg.iouThreshold,
                countingZone = null // Full photo analysis
            )

            val message = output.statusMessage ?: if (output.detections.isEmpty()) {
                "0 objects confidently detected at ${(cfg.confidenceThreshold * 100).toInt()}% threshold. Tap anywhere on the photo to mark objects manually, or lower sensitivity in Settings."
            } else {
                "Detected ${output.detections.size} objects in ${output.inferenceTimeMs} ms. Tap a box to remove false positives, or tap empty space to add missed objects."
            }

            _photoState.update {
                it.copy(
                    isAnalyzing = false,
                    aiDetections = output.detections,
                    inferenceTimeMs = output.inferenceTimeMs,
                    statusMessage = message
                )
            }
        }
    }

    /**
     * Interactive tap on the photo in Photo Counting mode:
     * - If the tap hits an existing manual point, removes that manual point.
     * - Else if the tap hits an active AI bounding box, toggles suppression of that AI detection.
     * - Otherwise adds a new manual object marker at `(normalizedX, normalizedY)`.
     */
    fun onPhotoTap(normalizedX: Float, normalizedY: Float) {
        val x = normalizedX.coerceIn(0f, 1f)
        val y = normalizedY.coerceIn(0f, 1f)

        _photoState.update { state ->
            // 1. Check if user tapped an existing manual point
            val hitManual = state.manualAddedPoints.firstOrNull { pt ->
                hypot((pt.normalizedX - x).toDouble(), (pt.normalizedY - y).toDouble()) <= 0.038
            }
            if (hitManual != null) {
                return@update state.copy(
                    manualAddedPoints = state.manualAddedPoints.filterNot { it.id == hitManual.id }
                )
            }

            // 2. Check if user tapped inside an AI detection bounding box
            val hitAiIndex = state.aiDetections.indexOfFirst { det ->
                det.boundingBox.contains(x, y)
            }
            if (hitAiIndex >= 0) {
                val updatedSuppressed = state.suppressedDetectionIndices.toMutableSet()
                if (hitAiIndex in updatedSuppressed) {
                    updatedSuppressed.remove(hitAiIndex)
                } else {
                    updatedSuppressed.add(hitAiIndex)
                }
                return@update state.copy(suppressedDetectionIndices = updatedSuppressed)
            }

            // 3. Otherwise add a manual marker
            val newPoint = ManualPhotoPoint(
                id = SystemClock.elapsedRealtimeNanos(),
                normalizedX = x,
                normalizedY = y
            )
            state.copy(manualAddedPoints = state.manualAddedPoints + newPoint)
        }
    }

    fun adjustPhotoCountManually(delta: Int) {
        _photoState.update { state ->
            if (state.totalCount + delta < 0) state else state.copy(manualDelta = state.manualDelta + delta)
        }
    }

    fun clearPhotoManualAdjustments() {
        _photoState.update {
            it.copy(
                suppressedDetectionIndices = emptySet(),
                manualAddedPoints = emptyList(),
                manualDelta = 0
            )
        }
    }

    fun savePhotoSession(notes: String = "") {
        val photo = _photoState.value
        val cfg = settings.value
        if (photo.bitmap == null) return

        val modelLabel = when (val st = _liveState.value.modelEngineState) {
            is ModelEngineState.Ready -> st.modelName
            else -> cfg.modelProfile.title
        }

        val netManual = photo.manualAddedPoints.size - photo.suppressedDetectionIndices.size + photo.manualDelta

        viewModelScope.launch(Dispatchers.IO) {
            countRepository.saveSession(
                CountSessionEntity(
                    timestampMs = System.currentTimeMillis(),
                    mode = "PHOTO",
                    finalCount = photo.totalCount,
                    aiDetectedCount = photo.aiDetections.size,
                    manualDelta = netManual,
                    direction = "Static Photo",
                    confidenceThreshold = cfg.confidenceThreshold,
                    durationSeconds = (photo.inferenceTimeMs / 1000L).coerceAtLeast(1L),
                    modelName = modelLabel,
                    notes = notes
                )
            )
        }
    }

    // History & Export Actions
    fun deleteSession(session: CountSessionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            countRepository.deleteSession(session)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            countRepository.clearAllHistory()
        }
    }

    fun exportHistoryAsCsv() {
        val list = historySessions.value
        if (list.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val file = SessionExportHelper.exportToCsv(getApplication(), list)
            SessionExportHelper.shareExportedFile(getApplication(), file, "text/csv")
        }
    }

    fun exportHistoryAsTxt() {
        val list = historySessions.value
        if (list.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val file = SessionExportHelper.exportToTxt(getApplication(), list)
            SessionExportHelper.shareExportedFile(getApplication(), file, "text/plain")
        }
    }

    // Settings Updates
    fun updateConfidenceThreshold(value: Float) {
        viewModelScope.launch { settingsRepository.updateConfidence(value) }
    }

    fun updateIouThreshold(value: Float) {
        viewModelScope.launch { settingsRepository.updateIouThreshold(value) }
    }

    fun updateCountingDirection(direction: CountingDirection) {
        viewModelScope.launch { settingsRepository.updateDirection(direction) }
    }

    fun updateModelProfile(profile: ModelProfile) {
        viewModelScope.launch { settingsRepository.updateModelProfile(profile) }
    }

    fun updateCameraSelection(useBackCamera: Boolean) {
        viewModelScope.launch { settingsRepository.updateCameraSelection(useBackCamera) }
    }

    fun updateHapticFeedback(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateHapticFeedback(enabled) }
    }

    fun updateCpuThreads(threads: Int) {
        viewModelScope.launch { settingsRepository.updateCpuThreads(threads) }
    }

    fun updateZoneAndLine(zone: NormalizedRect, linePos: Float) {
        viewModelScope.launch { settingsRepository.updateCountingZoneAndLine(zone, linePos) }
    }

    fun importCustomTfliteModel(uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = settingsRepository.importCustomTfliteModel(uri)
            result.fold(
                onSuccess = { path ->
                    onResult(true, "Imported offline model: ${path.substringAfterLast('/')}")
                },
                onFailure = { err ->
                    onResult(false, err.localizedMessage ?: "Failed to import model")
                }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        detector?.close()
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
