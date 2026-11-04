package com.ontik.countai.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
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

    // Free users see latest 5 sessions; Pro users see unlimited history
    val historySessions: StateFlow<List<CountSessionEntity>> = combine(
        countRepository.activeSessions,
        planManager.planState
    ) { all, plan ->
        if (plan.isEffectivePro) {
            all
        } else {
            all.take(plan.freeHistoryLimit)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _uiState = MutableStateFlow(LiveCountingUiState())
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

    init {
        fetchRemoteConfigAndRefreshAuth()
    }

    private fun fetchRemoteConfigAndRefreshAuth() {
        viewModelScope.launch(Dispatchers.IO) {
            val profile = authRepository.refreshSessionAndSyncFirestore()
            if (!profile.isGuest && profile.uid.isNotBlank()) {
                billingRepository.linkFirebaseUser(profile.uid)
                try {
                    if (FirebaseApp.getApps(getApplication()).isNotEmpty()) {
                        // Crashlytics omitted
                    }
                } catch (_: Throwable) {
                }
            }
            billingRepository.refreshCustomerInfo()

            try {
                if (FirebaseApp.getApps(getApplication()).isNotEmpty()) {
                    val rc = FirebaseRemoteConfig.getInstance()
                    rc.fetchAndActivate().addOnSuccessListener {
                        val dailyLimit = rc.getLong("free_daily_limit").toInt().let { if (it <= 0) 500 else it }
                        val histLimit = rc.getLong("free_history_limit").toInt().let { if (it <= 0) 5 else it }
                        val showAds = if (rc.getKeysByPrefix("show_ads").isNotEmpty()) rc.getBoolean("show_ads") else true
                        val monthlyTxt = rc.getString("pro_monthly_price_text")
                        val yearlyTxt = rc.getString("pro_yearly_price_text")
                        val maintMsg = rc.getString("maintenance_message")
                        val minVer = rc.getLong("min_app_version").toInt()

                        planManager.applyRemoteConfigValues(
                            freeDailyLimit = dailyLimit,
                            freeHistoryLimit = histLimit,
                            showAds = showAds,
                            monthlyPriceText = monthlyTxt,
                            yearlyPriceText = yearlyTxt,
                            maintenanceMessage = maintMsg,
                            minAppVersionCode = minVer,
                            currentAppVersionCode = BuildConfig.VERSION_CODE
                        )
                    }
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
                    billingRepository.linkFirebaseUser(res.profile.uid)
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
                    billingRepository.linkFirebaseUser(res.profile.uid)
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
                    billingRepository.linkFirebaseUser(res.profile.uid)
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
            billingRepository.logOutRevenueCat()
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
                    billingRepository.logOutRevenueCat()
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

    // Billing & Paywall Actions
    fun purchaseProPackage(activity: Activity, pkg: Package?) {
        val user = currentUser.value
        if (!user.isGuest && !user.isEmailVerified && !user.isGoogleAccount) {
            _authErrorBanner.value = "Please verify your email address before purchasing PRO." to
                "PRO কেনার আগে আপনার ইমেইল ভেরিফাই করুন।"
            return
        }
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

    // Cloud Sync Trigger
    fun triggerManualCloudSync() {
        SyncWorker.triggerImmediateSync(getApplication())
    }

    // Live Counting Controls
    fun startCounting() {
        if (!planManager.canUse(GatedFeature.UNLIMITED_DAILY_COUNTING)) {
            _uiState.update {
                it.copy(
                    status = EngineStatus.PAUSED,
                    dailyLimitHitPaywallPromptVisible = true
                )
            }
            return
        }
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
        if (_uiState.value.status == EngineStatus.COUNTING) {
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
                targetGoalReachedDialogVisible = false,
                trackedGrains = emptyList(),
                sessionStartElapsedMs = SystemClock.elapsedRealtime()
            )
        }
    }

    fun saveCurrentBatchAndResetCounter() {
        val current = _uiState.value.totalCount
        if (current <= 0) return
        _uiState.update { state ->
            val nextBatchNum = state.batches.size + 1
            val updatedBatches = state.batches + BatchCountEntry(
                batchNumber = nextBatchNum,
                count = current,
                timestampMs = System.currentTimeMillis()
            )
            state.copy(
                batches = updatedBatches,
                autoCountLineA = 0,
                autoCountLineB = 0,
                manualDelta = 0
            )
        }
        tracker.reset()
    }

    fun clearAllBatches() {
        _uiState.update { it.copy(batches = emptyList()) }
    }

    fun dismissTargetReachedDialog() {
        _uiState.update { it.copy(targetGoalReachedDialogVisible = false) }
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
        if (delta > 0 && !planManager.canUse(GatedFeature.UNLIMITED_DAILY_COUNTING)) {
            _uiState.update { it.copy(dailyLimitHitPaywallPromptVisible = true) }
            return
        }
        _uiState.update { state ->
            val next = state.totalCount + delta
            if (next < 0) state else {
                if (delta > 0) {
                    planManager.recordGrainsCounted(delta)
                    countUndoStack.addLast(delta)
                }
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
                contextualHintEn = if (nextTest) "100-Grain Test Mode Active: Drop 100 grains, then tap Save" else null,
                contextualHintBn = if (nextTest) "১০০ দানা টেস্ট মোড চালু: ১০০টি দানা পার করে সেভ চাপুন" else null
            )
        }
    }

    fun saveSessionToHistory(customLabel: String = "", customNotes: String = ""): CountSessionEntity? {
        val st = _uiState.value
        val cfg = settings.value
        val finalTotal = if (cfg.batchModeEnabled && st.batchGrandTotal > 0) {
            st.batchGrandTotal
        } else if (st.totalCount > 0) {
            st.totalCount
        } else {
            st.inZoneGrainCount
        }
        if (finalTotal <= 0 && !st.isTestMode100Active) return null

        val durationSec = ((SystemClock.elapsedRealtime() - st.sessionStartElapsedMs) / 1000L).coerceAtLeast(1L)
        val errPct = if (st.isTestMode100Active) abs(finalTotal - 100).toFloat() else 0f
        val defaultLabel = when {
            st.isTestMode100Active -> "100-Grain Test (${String.format(java.util.Locale.US, "%.1f%%", 100f - errPct)} acc)"
            cfg.batchModeEnabled && st.batches.isNotEmpty() -> "${cfg.activePreset.nameEn} (${st.batches.size + 1} Batches)"
            else -> cfg.activePreset.nameEn
        }

        val syncFlag = if (planManager.canUse(GatedFeature.CLOUD_BACKUP_SYNC) && !currentUser.value.isGuest) {
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
            if (planManager.canUse(GatedFeature.CLOUD_BACKUP_SYNC)) {
                SyncWorker.triggerImmediateSync(getApplication())
            }
        }
        return entity
    }

    fun updateHistorySession(session: CountSessionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            countRepository.updateSession(session)
            if (planManager.canUse(GatedFeature.CLOUD_BACKUP_SYNC)) {
                SyncWorker.triggerImmediateSync(getApplication())
            }
        }
    }

    fun deleteHistorySession(session: CountSessionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            countRepository.softDeleteSession(session)
            if (planManager.canUse(GatedFeature.CLOUD_BACKUP_SYNC)) {
                SyncWorker.triggerImmediateSync(getApplication())
            }
        }
    }

    fun exportHistoryCsv(onBlockedByFreePlan: () -> Unit) {
        if (!planManager.canUse(GatedFeature.CSV_EXPORT)) {
            onBlockedByFreePlan()
            return
        }
        val list = historySessions.value
        if (list.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val file = SessionExportHelper.exportToCsv(getApplication(), list)
            SessionExportHelper.shareExportedFile(getApplication(), file, "text/csv")
        }
    }

    fun exportHistoryPdfReport(onBlockedByFreePlan: () -> Unit) {
        if (!planManager.canUse(GatedFeature.PDF_EXPORT)) {
            onBlockedByFreePlan()
            return
        }
        val list = historySessions.value
        if (list.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val file = PdfReportGenerator.generateSessionReportPdf(getApplication(), list)
            SessionExportHelper.shareExportedFile(getApplication(), file, "application/pdf")
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

    fun sendFeedbackEmail(context: Context) {
        try {
            val body = buildString {
                appendLine("--- ONTIK Count AI Diagnostic Info ---")
                appendLine("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})")
                appendLine("Plan Tier: ${planState.value.tier.id}")
                appendLine("Active Preset: ${settings.value.activePreset.nameEn}")
                appendLine("--------------------------------------")
                appendLine()
                appendLine("Describe your feedback or issue below:")
            }
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:support@ontikcountai.com")
                putExtra(Intent.EXTRA_SUBJECT, "ONTIK Count AI Feedback (v${BuildConfig.VERSION_NAME})")
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Throwable) {
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

                    val rawAdded = trackerOut.newlyCountedLineA
                    val allowedAdded = if (rawAdded > 0 && countingActive) {
                        planManager.recordGrainsCounted(rawAdded)
                    } else {
                        0
                    }

                    val hitFreeLimitNow = rawAdded > 0 && allowedAdded < rawAdded && !planManager.canUse(GatedFeature.UNLIMITED_DAILY_COUNTING)

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

                    // Check Target Count Goal (Phase 6.1)
                    val targetGoal = cfg.targetCountGoal
                    val reachedTargetNow = targetGoal > 0 && prevTotal < targetGoal && newTotal >= targetGoal
                    val crossedHundredMilestone = allowedAdded > 0 && (newTotal / 100) > (prevTotal / 100)

                    if ((crossedHundredMilestone || reachedTargetNow) && cfg.soundBeepEnabled) {
                        soundManager.playMilestoneChime(cfg.soundVolume)
                    }

                    val finalStatusAfterChecks = if (hitFreeLimitNow || reachedTargetNow) {
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
                            lowFpsWarning = trackerOut.frameDropDetected || measuredFps < 15,
                            confidenceBadge = cvOut.confidenceLevel,
                            learnedSingleGrainAreaPx = cvOut.learnedSingleGrainAreaPx,
                            noiseStd = cvOut.noiseStd,
                            trackedGrains = trackerOut.activeTracks,
                            debugMaskBitmap = cvOut.debugMaskBitmap,
                            targetGoalReachedDialogVisible = state.targetGoalReachedDialogVisible || reachedTargetNow,
                            dailyLimitHitPaywallPromptVisible = state.dailyLimitHitPaywallPromptVisible || hitFreeLimitNow,
                            contextualHintEn = cvOut.contextualHintEn ?: if (trackerOut.frameDropDetected) "Low FPS detected — gating widened automatically" else null,
                            contextualHintBn = cvOut.contextualHintBn ?: if (trackerOut.frameDropDetected) "ফ্রেম রেট কম — ট্র্যাকিং গেট স্বয়ংক্রিয়ভাবে বাড়ানো হয়েছে" else null,
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
    fun applyPreset(preset: GrainPreset, onBlockedCustomPreset: () -> Unit) {
        if (preset == GrainPreset.CUSTOM && !planManager.canUse(GatedFeature.CUSTOM_PRESETS)) {
            onBlockedCustomPreset()
            return
        }
        viewModelScope.launch {
            settingsRepository.applyPreset(preset)
            cvProcessor.setManualSingleGrainArea(preset.defaultSingleGrainAreaPx)
        }
    }

    fun saveCustomPreset(onBlockedByFreePlan: () -> Unit) {
        if (!planManager.canUse(GatedFeature.CUSTOM_PRESETS)) {
            onBlockedByFreePlan()
            return
        }
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
        cvProcessor.release()
        soundManager.release()
    }
}
