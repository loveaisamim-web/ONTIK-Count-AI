package com.ontik.countai

import android.app.Application
import com.ontik.countai.data.local.AppDatabase
import com.ontik.countai.data.repository.CountRepository
import com.ontik.countai.data.repository.SettingsRepository
import org.opencv.android.OpenCVLoader

class OntikCountApp : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            OpenCVLoader.initLocal()
        } catch (_: Throwable) {
        }
    }

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    val countRepository: CountRepository by lazy {
        CountRepository(database.countSessionDao())
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(this)
    }
}
