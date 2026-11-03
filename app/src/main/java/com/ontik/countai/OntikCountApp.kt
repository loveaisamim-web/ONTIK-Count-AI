package com.ontik.countai

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.ontik.countai.data.remote.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.opencv.android.OpenCVLoader
import javax.inject.Inject

@HiltAndroidApp
class OntikCountApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    private val appInitScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize 100% offline OpenCV native engine immediately for zero-latency camera counting
        try {
            OpenCVLoader.initLocal()
        } catch (_: Throwable) {
        }

        // 2. Initialize Firebase + Play Integrity App Check (safe no-op if offline)
        try {
            if (FirebaseApp.getApps(this).isNotEmpty()) {
                val appCheck = FirebaseAppCheck.getInstance()
                appCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                )
            }
        } catch (_: Throwable) {
        }

        // 3. Initialize Google Mobile Ads (AdMob) strictly on a background IO thread
        // so it never blocks app startup or the live counting camera pipeline.
        appInitScope.launch {
            try {
                MobileAds.initialize(this@OntikCountApp)
            } catch (_: Throwable) {
            }
        }

        // 4. Schedule periodic background SyncWorker for Pro accounts
        try {
            SyncWorker.schedulePeriodicSync(this)
        } catch (_: Throwable) {
        }
    }
}
