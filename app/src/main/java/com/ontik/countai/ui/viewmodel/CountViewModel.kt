package com.ontik.countai.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.ontik.countai.BuildConfig
import com.ontik.countai.auth.AuthRepository
import com.ontik.countai.auth.AuthResult
import com.ontik.countai.billing.BillingRepository
import com.ontik.countai.billing.PlanManager
import com.ontik.countai.billing.PurchaseUiState
import com.ontik.countai.billing.StoreProductPricing
import com.ontik.countai.cv.KalmanHungarianTracker
import com.ontik.countai.cv.OpenCvGrainProcessor
import com.ontik.countai.data.local.CountSessionEntity
import com.ontik.countai.data.remote.SyncWorker
import com.ontik.countai.data.repository.AppSettings
import com.ontik.countai.data.repository.CountRepository
import com.ontik.countai.data.repository.SettingsRepository
import com.ontik.countai.domain.model.AccuracyConfidenceLevel
import com.ontik.countai.domain.model.AppLanguage
import com.ontik.countai.domain.model.AppThemeMode
import com.ontik.countai.domain.model.BatchCountEntry
import com.ontik.countai.domain.model.CountingDirection
import com.ontik.countai.domain.model.DebugFrameSnapshot
import com.ontik.countai.domain.model.GatedFeature
import com.ontik.countai.domain.model.GrainPreset
import com.ontik.countai.domain.model.NormalizedRect
import com.ontik.countai.domain.model.PlanState
import com.ontik.countai.domain.model.SyncStatus
import com.ontik.countai.domain.model.TrackedGrain
import com.ontik.countai.domain.model.UserProfile
import com.ontik.countai.util.ImageProxyUtils
import com.ontik.countai.util.PdfReportGenerator
import com.ontik.countai.util.SessionExportHelper
import com.ontik.countai.util.SoundFeedbackManager
import com.revenuecat.purchases.Package
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
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
    val autoThresholdValue: Double = 16.0,
    val effectiveThresholdValue: Double = 16.0,
    val meanLuminance: Double = 140.0,
    val rawContourCount: Int = 0,
    val rejectedByAreaCount: Int = 0,
    val rejectedByShapeCount: Int = 0,
    val processingTimeMs: Long = 8L,
    val openCvReady: Boolean = true,
    val isOnline: Boolean = true,
    val trackedGrains: List<TrackedGrain> = emptyList(),
    val debugMaskBitmap: Bitmap? = null,
    val torchEnabled: Boolean = false,
    val isTestMode100Active: Boolean = false,
    val targetGoalReachedDialogVisible: Boolean = false,
    val dailyLimitHitPaywallPromptVisible: Boolean = false,
    val batches: List<BatchCountEntry> = emptyList(),
    val contextualHintEn: String? = null,
    val contextualHintBn: String? = null,
    val lastCountPulseTimestampMs: Long = 0L,
    val lastMilestoneHundredTimestampMs: Long = 0L,
    val sessionStartElapsedMs: Long = SystemClock.elapsedRealtime()
) {
    val totalCount: Int
        get() = (autoCountLineA + manualDelta).coerceAtLeast(0)

    val batchGrandTotal: Int
        get() = batches.sumOf { it.count } + totalCount

    val verificationMismatchWarning: Boolean
        get() = autoCountLineB > 0 && abs(autoCountLineA - autoCountLineB) >= 3
}

@HiltViewModel
class CountViewModel @Inject constructor(
    application: Application,
    private val countRepository: CountRepository,
    private val settingsRepository: SettingsRepository,
    private val planManager: PlanManager,
    private val authRepository: AuthRepository,
    private val billingRepository: BillingRepository
) : AndroidViewModel(application) {

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettings()
    )

    val planState: StateFlow<PlanState> = planManager.planState
    val currentUser: StateFlow<UserProfile> = authRepository.currentUser
    val storePricing: StateFlow<StoreProductPricing> = billingRepository.pricingState
    val purchaseUiState: StateFlow<PurchaseUiState> = billingRepository.purchaseState

    // Unlimited history sessions for all users
    val historySessions: StateFlow<List<CountSessionEntity>> = countRepository.activeSessions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _uiState = MutableStateFlow(LiveCountingUiState(isOnline = checkInternetConnected(application)))
    val uiState: StateFlow<LiveCountingUiState> = _uiState.asStateFlow()

    private val _authLoading = MutableStateFlow(false)
    val authLoading: StateFlow<Boolean> = _authLoading.asStateFlow()

    private val _authErrorBanner = MutableStateFlow<Pair<String, String>?>(null)
    val authErrorBanner: StateFlow<Pair<String, String>?> = _authErrorBanner.asStateFlow()

    private val cvProcessor = OpenCvGrainProcessor()
    private val tracker = KalmanHungarianTracker()
    private val soundManager = SoundFeedbackManager()
    private val isProcessingFrame = AtomicBoolean(false)

    private val countUndoStack = ArrayDeque<Int>(30)
    private val recentCountTimestamps = ArrayDeque<Pair<Long, Int>>(60)
    private val frameTimestamps = ArrayDeque<Long>(35)
    private val debugSnapshots = ArrayDeque<DebugFrameSnapshot>(320)

    private val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            refreshOnlineStatus()
        }

        override fun onLost(network: Network) {
            refreshOnlineStatus()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            refreshOnlineStatus()
        }
    }

    init {
        registerNetworkMonitor()
        fetchRemoteConfigAndRefreshAuth()
    }

    private fun registerNetworkMonitor() {
        try {
            val req = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager?.registerNetworkCallback(req, networkCallback)
        } catch (_: Throwable) {
        }
        refreshOnlineStatus()
    }

    fun refreshOnlineStatus(): Boolean {
        val online = checkInternetConnected(getApplication())
        _uiState.update { state ->
            val nextStatus = if (!online && state.status == EngineStatus.COUNTING) {
                EngineStatus.PAUSED
            } else {
                state.status
            }
            state.copy(isOnline = online, status = nextStatus)
        }
        return online
    }

    private fun checkInternetConnected(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val activeNet = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNet) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
        } catch (_: Throwable) {
            false
        }
    }

    private fun fetchRemoteConfigAndRefreshAuth() {
        viewModelScope.launch(Dispatchers.IO) {
            val profile = authRepository.refreshSessionAndSyncFirestore()
            if (!profile.isGuest && profile.uid.isNotBlank()) {
                billingRepository.linkFirebaseUser(profile.uid)
            }
            try {
                if (FirebaseApp.getApps(getApplication()).isNotEmpty()) {
                    val rc = FirebaseRemoteConfig.getInstance()
                    rc.fetchAndActivate()
                }
            } catch (_: Throwable) {
            }
        }
    }

    fun canUseFeature(feature: GatedFeature): Boolean = planManager.canUse(feature)

    // Authentication Actions
    fun signInWithEmail(email: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _authLoading.value = true
            _authErrorBanner.value = null
            when (val res = authRepository.signInWithEmail(email, password)) {
                is AuthResult.Success -> {
                    _authLoading.value = false
                    SyncWorker.triggerImmediateSync(getApplication())
                    onSuccess()
                }
                is AuthResult.Error -> {
                    _authLoading.value = false
                    _authErrorBanner.value = res.messageEn to res.messageBn
                }
            }
        }
    }

    fun signUpWithEmail(name: String, email: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _authLoading.value = true
            _authErrorBanner.value = null
            when (val res = authRepository.signUpWithEmail(name, email, password)) {
                is AuthResult.Success -> {
                    _authLoading.value = false
                    SyncWorker.triggerImmediateSync(getApplication())
                    onSuccess()
                }
                is AuthResult.Error -> {
                    _authLoading.value = false
                    _authErrorBanner.value = res.messageEn to res.messageBn
                }
            }
        }
    }

    fun signInWithGoogleToken(idToken: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _authLoading.value = true
            _authErrorBanner.value = null
            when (val res = authRepository.signInWithGoogleIdToken(idToken)) {
                is AuthResult.Success -> {
                    _authLoading.value = false
                    SyncWorker.triggerImmediateSync(getApplication())
                    onSuccess()
                }
                is AuthResult.Error -> {
                    _authLoading.value = false
                    _authErrorBanner.value = res.messageEn to res.messageBn
                }
            }
        }
    }

    fun sendPasswordResetEmail(email: String, onSent: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val lang = settings.value.language
            when (val res = authRepository.sendPasswordReset(email)) {
                is AuthResult.Success -> {
                    onSent(
                        true,
                        if (lang == AppLanguage.BANGLA) "পাসওয়ার্ড রিসেট লিংক ইমেইলে পাঠানো হয়েছে।" else "Password reset email sent."
                    )
                }
                is AuthResult.Error -> {
                    onSent(false, if (lang == AppLanguage.BANGLA) res.messageBn else res.messageEn)
                }
            }
        }
    }

    fun updateProfileName(newName: String) {
        viewModelScope.launch {
            authRepository.updateDisplayName(newName)
        }
    }

    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            onSignedOut()
        }
    }

    fun deleteAccountAndData(
        passwordIfEmail: String?,
        googleIdTokenIfGoogle: String? = null,
        onDeleted: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _authLoading.value = true
            val lang = settings.value.language
            when (val res = authRepository.deleteAccountAndAllData(passwordIfEmail, googleIdTokenIfGoogle)) {
                is AuthResult.Success -> {
                    _authLoading.value = false
                    onDeleted(
                        true,
                        if (lang == AppLanguage.BANGLA) "অ্যাকাউন্ট ও সমস্ত ডেটা মুছে ফেলা হয়েছে।" else "Account and all data permanently deleted."
                    )
                }
                is AuthResult.Error -> {
                    _authLoading.value = false
                    onDeleted(false, if (lang == AppLanguage.BANGLA) res.messageBn else res.messageEn)
                }
            }
        }
    }

    fun clearAuthError() {
        _authErrorBanner.value = null
    }

    fun purchaseProPackage(activity: Activity, pkg: Package?) {
        billingRepository.purchasePackage(activity, pkg)
    }

    fun restorePurchases() {
        billingRepository.restorePurchases()
    }

    fun openManageSubscription(context: Context) {
        billingRepository.openPlayStoreSubscriptionsPage(context)
    }

    fun dismissDailyLimitPaywallPrompt() {
        _uiState.update { it.copy(dailyLimitHitPaywallPromptVisible = false) }
    }

    fun triggerManualCloudSync() {
        SyncWorker.triggerImmediateSync(getApplication())
    }

    // Live Counting Controls (Requires Online Connection per P4)
    fun startCounting() {
        if (!refreshOnlineStatus()) return
        _uiState.update {
            if (it.isCalibrated) {
                it.copy(status = EngineStatus.COUNTING)
            } else {
                cvProcessor.startAutoCalibration()
                it.copy(status = EngineStatus.CALIBRATING, calibrationProgress = 0f)
            }
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

    fun triggerAutoCalibrate() {
        if (!refreshOnlineStatus()) return
        tracker.reset()
        cvProcessor.startAutoCalibration()
        _uiState.update {
            it.copy(
                status = EngineStatus.CALIBRATING,
                calibrationProgress = 0f,
                isCalibrated = false,
                cameraLockedAfterCalibration = false,
                trackedGrains = emptyList()
            )
        }
    }

    fun triggerSingleGrainSizeCalibration() {
        cvProcessor.startSingleGrainSizeCalibration()
    }

    fun resetCount() {
        tracker.reset()
        countUndoStack.clear()
        recentCountTimestamps.clear()
        _uiState.update {
            it.copy(
                autoCountLineA = 0,
                autoCountLineB = 0,
                manualDelta = 0,
                inZoneGrainCount = 0,
                countRatePerSec = 0f,
                targetGoalReachedDialogVisible = false,
                sessionStartElapsedMs = SystemClock.elapsedRealtime()
            )
        }
    }

    fun saveCurrentBatchAndResetCounter() {
        val currentTotal = _uiState.value.totalCount
        if (currentTotal <= 0) return
        val nextBatchNum = _uiState.value.batches.size + 1
        val updatedBatches = _uiState.value.batches + BatchCountEntry(
            batchNumber = nextBatchNum,
            count = currentTotal,
            timestampMs = System.currentTimeMillis()
        )
        tracker.reset()
        countUndoStack.clear()
        _uiState.update {
            it.copy(
                batches = updatedBatches,
                autoCountLineA = 0,
                autoCountLineB = 0,
                manualDelta = 0
            )
        }
    }

    fun clearAllBatches() {
        _uiState.update { it.copy(batches = emptyList()) }
    }

    fun undoLastCount() {
        val lastIncrement = if (countUndoStack.isNotEmpty()) countUndoStack.removeLast() else 1
        _uiState.update { state ->
            val newAuto = (state.autoCountLineA - lastIncrement).coerceAtLeast(0)
            state.copy(autoCountLineA = newAuto)
        }
    }

    fun adjustCountManually(delta: Int) {
        if (delta > 0 && !refreshOnlineStatus()) return
        if (delta > 0) {
            planManager.recordGrainsCounted(delta)
        }
        _uiState.update { state ->
            val nextManual = state.manualDelta + delta
            val nextTotal = (state.autoCountLineA + nextManual).coerceAtLeast(0)
            val effectiveManual = nextTotal - state.autoCountLineA
            state.copy(
                manualDelta = effectiveManual,
                lastCountPulseTimestampMs = SystemClock.elapsedRealtime()
            )
        }
    }

    fun toggleTorch() {
        _uiState.update { it.copy(torchEnabled = !it.torchEnabled) }
    }

    fun toggleTestMode100() {
        _uiState.update { state ->
            val nextTest = !state.isTestMode100Active
            tracker.reset()
            state.copy(
                isTestMode100Active = nextTest,
                autoCountLineA = 0,
                autoCountLineB = 0,
                manualDelta = 0,
                sessionStartElapsedMs = SystemClock.elapsedRealtime()
            )
        }
    }

    fun dismissTargetReachedDialog() {
        _uiState.update { it.copy(targetGoalReachedDialogVisible = false) }
    }

    fun saveSessionToHistory(customLabel: String = "", customNotes: String = ""): CountSessionEntity? {
        val st = _uiState.value
        val cfg = settings.value
        val finalTotal = if (cfg.batchModeEnabled && st.batches.isNotEmpty()) st.batchGrandTotal else st.totalCount
        if (finalTotal <= 0) return null

        val durationSec = ((SystemClock.elapsedRealtime() - st.sessionStartElapsedMs) / 1000L).coerceAtLeast(1L)
        val errPct = if (st.isTestMode100Active) abs(finalTotal - 100).toFloat() else 0f
        val defaultLabel = when {
            st.isTestMode100Active -> "100-Grain Test (${String.format(java.util.Locale.US, "%.1f%%", 100f - errPct)} acc)"
            cfg.batchModeEnabled && st.batches.isNotEmpty() -> "${cfg.activePreset.nameEn} (${st.batches.size + 1} Batches)"
            else -> cfg.activePreset.nameEn
        }

        val syncFlag = if (!currentUser.value.isGuest) {
            SyncStatus.PENDING_UPLOAD.name
        } else {
            SyncStatus.LOCAL_ONLY.name
        }

        val nowMs = System.currentTimeMillis()
        val entity = CountSessionEntity(
            timestampMs = nowMs,
            updatedAtMs = nowMs,
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
            notes = customNotes,
            deleted = false,
            syncStatus = syncFlag
        )

        viewModelScope.launch(Dispatchers.IO) {
            countRepository.saveSession(entity)
            settingsRepository.incrementCompletedSessions()
            val pState = planManager.planState.value
            authRepository.syncDailyCountToFirestore(pState.todayDateIso, pState.todayUsedCount)
            if (!currentUser.value.isGuest) {
                SyncWorker.triggerImmediateSync(getApplication())
            }
        }
        return entity
    }

    fun updateHistorySession(session: CountSessionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            countRepository.updateSession(session)
            if (!currentUser.value.isGuest) {
                SyncWorker.triggerImmediateSync(getApplication())
            }
        }
    }

    fun deleteHistorySession(session: CountSessionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            countRepository.softDeleteSession(session)
            if (!currentUser.value.isGuest) {
                SyncWorker.triggerImmediateSync(getApplication())
            }
        }
    }

    // P13 Fix: Export on IO thread and share on Main thread
    fun exportHistoryCsv(onBlockedByFreePlan: () -> Unit = {}) {
        val list = historySessions.value
        if (list.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val file = SessionExportHelper.exportToCsv(getApplication(), list)
            withContext(Dispatchers.Main) {
                SessionExportHelper.shareExportedFile(getApplication(), file, "text/csv")
            }
        }
    }

    fun exportHistoryTxt() {
        val list = historySessions.value
        if (list.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val file = SessionExportHelper.exportToTxt(getApplication(), list)
            withContext(Dispatchers.Main) {
                SessionExportHelper.shareExportedFile(getApplication(), file, "text/plain")
            }
        }
    }

    fun exportHistoryPdfReport(onBlockedByFreePlan: () -> Unit = {}) {
        val list = historySessions.value
        if (list.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val file = PdfReportGenerator.generateBrandedPdfReport(
                context = getApplication(),
                sessions = list,
                userProfile = currentUser.value
            )
            withContext(Dispatchers.Main) {
                SessionExportHelper.shareExportedFile(getApplication(), file, "application/pdf")
            }
        }
    }

    fun exportLast10SecondsDebugLog() {
        val snaps = synchronized(debugSnapshots) { debugSnapshots.toList() }
        if (snaps.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val file = SessionExportHelper.exportDebugLog(getApplication(), snaps)
            withContext(Dispatchers.Main) {
                SessionExportHelper.shareExportedFile(getApplication(), file, "text/csv")
            }
        }
    }

    fun sendFeedbackEmail(context: Context) {
        val st = _uiState.value
        val cfg = settings.value
        val body = buildString {
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})")
            appendLine("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Preset: ${cfg.activePreset.nameEn}")
            appendLine("Learned Grain Area: ${st.learnedSingleGrainAreaPx} px")
            appendLine("Noise Std: ${st.noiseStd}")
            appendLine("Measured FPS: ${st.currentFps}")
            appendLine("\n--- Write your feedback below ---")
        }
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:support@ontik.com")
            putExtra(Intent.EXTRA_SUBJECT, "ONTIK Count AI Feedback (${Build.MODEL})")
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Throwable) {
        }
    }

    // Per-Frame CameraX Analysis Pipeline
    fun processCameraFrame(imageProxy: ImageProxy) {
        if (!isProcessingFrame.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val nowMs = SystemClock.elapsedRealtime()
                val cfg = settings.value
                val online = checkInternetConnected(getApplication())

                frameTimestamps.addLast(nowMs)
                while (frameTimestamps.isNotEmpty() && nowMs - frameTimestamps.first() > 1000L) {
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
                        !online -> EngineStatus.PAUSED
                        cvOut.isCalibrating -> EngineStatus.CALIBRATING
                        prevStatus == EngineStatus.CALIBRATING && cvOut.isCalibrated -> EngineStatus.READY
                        else -> prevStatus
                    }

                    val countingActive = online && nextStatus == EngineStatus.COUNTING && cvOut.isCalibrated

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

                    val rawAdded = trackerOut.newlyCountedLineA
                    val allowedAdded = if (rawAdded > 0 && countingActive) {
                        planManager.recordGrainsCounted(rawAdded)
                    } else {
                        0
                    }

                    if (allowedAdded > 0) {
                        countUndoStack.addLast(allowedAdded)
                        while (countUndoStack.size > 30) countUndoStack.removeFirst()
                        recentCountTimestamps.addLast(nowMs to allowedAdded)

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
                    val newAutoA = _uiState.value.autoCountLineA + allowedAdded
                    val newTotal = (newAutoA + _uiState.value.manualDelta).coerceAtLeast(0)

                    val targetGoal = cfg.targetCountGoal
                    val reachedTargetNow = targetGoal > 0 && prevTotal < targetGoal && newTotal >= targetGoal
                    val crossedHundredMilestone = allowedAdded > 0 && (newTotal / 100) > (prevTotal / 100)

                    if ((crossedHundredMilestone || reachedTargetNow) && cfg.soundBeepEnabled) {
                        soundManager.playMilestoneChime(cfg.soundVolume)
                    }

                    val finalStatusAfterChecks = if (reachedTargetNow || !online) {
                        EngineStatus.PAUSED
                    } else {
                        nextStatus
                    }

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
                            status = finalStatusAfterChecks,
                            calibrationProgress = cvOut.calibrationProgress,
                            isCalibrated = cvOut.isCalibrated,
                            cameraLockedAfterCalibration = cvOut.isCalibrated && cfg.lockCameraExposureFocus,
                            autoCountLineA = newAutoA,
                            autoCountLineB = state.autoCountLineB + trackerOut.newlyCountedLineB,
                            inZoneGrainCount = inZoneTotalGrains,
                            countRatePerSec = ratePerSec,
                            currentFps = measuredFps,
                            lowFpsWarning = measuredFps < 14,
                            confidenceBadge = cvOut.confidenceLevel,
                            learnedSingleGrainAreaPx = cvOut.learnedSingleGrainAreaPx,
                            noiseStd = cvOut.noiseStd,
                            autoThresholdValue = cvOut.autoThresholdValue,
                            effectiveThresholdValue = cvOut.effectiveThresholdValue,
                            meanLuminance = cvOut.meanLuminance,
                            rawContourCount = cvOut.rawContourCount,
                            rejectedByAreaCount = cvOut.rejectedByAreaCount,
                            rejectedByShapeCount = cvOut.rejectedByShapeCount,
                            processingTimeMs = cvOut.processingTimeMs,
                            openCvReady = cvOut.openCvReady,
                            isOnline = online,
                            trackedGrains = trackerOut.activeTracks,
                            debugMaskBitmap = cvOut.debugMaskBitmap,
                            targetGoalReachedDialogVisible = state.targetGoalReachedDialogVisible || reachedTargetNow,
                            dailyLimitHitPaywallPromptVisible = false,
                            contextualHintEn = cvOut.contextualHintEn,
                            contextualHintBn = cvOut.contextualHintBn,
                            lastCountPulseTimestampMs = if (allowedAdded > 0) nowMs else state.lastCountPulseTimestampMs,
                            lastMilestoneHundredTimestampMs = if (crossedHundredMilestone || reachedTargetNow) nowMs else state.lastMilestoneHundredTimestampMs
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
    fun applyPreset(preset: GrainPreset, onBlockedCustomPreset: () -> Unit = {}) {
        viewModelScope.launch {
            settingsRepository.applyPreset(preset)
            cvProcessor.setManualSingleGrainArea(preset.defaultSingleGrainAreaPx)
        }
    }

    fun saveCustomPreset(onBlockedByFreePlan: () -> Unit = {}) {
        viewModelScope.launch { settingsRepository.saveCurrentAsCustomPreset() }
    }

    // P12 Fix: Reset both DataStore and in-memory OpenCvGrainProcessor state
    fun resetSettingsToDefaults() {
        viewModelScope.launch {
            settingsRepository.resetAllToDefaults()
            cvProcessor.setManualSingleGrainArea(GrainPreset.RICE.defaultSingleGrainAreaPx)
            cvProcessor.startAutoCalibration()
        }
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

    fun setAnalyticsConsent(granted: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAnalyticsConsent(granted)
            try {
                if (FirebaseApp.getApps(getApplication()).isNotEmpty()) {
                    FirebaseAnalytics.getInstance(getApplication()).setAnalyticsCollectionEnabled(granted)
                }
            } catch (_: Throwable) {
            }
        }
    }

    fun updateTargetCountGoal(goal: Int) {
        viewModelScope.launch { settingsRepository.updateTargetCountGoal(goal) }
    }

    fun updateBatchMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateBatchMode(enabled) }
    }

    fun updateRoiAndLines(roi: NormalizedRect, lineA: Float, lineB: Float) {
        viewModelScope.launch { settingsRepository.updateRoiAndLines(roi, lineA, lineB) }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (_: Throwable) {
        }
        cvProcessor.release()
        soundManager.release()
    }
}
