package com.ontik.countai.data.remote

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.ontik.countai.billing.PlanManager
import com.ontik.countai.data.local.CountSessionEntity
import com.ontik.countai.data.repository.CountRepository
import com.ontik.countai.domain.model.GatedFeature
import com.ontik.countai.domain.model.SyncStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Offline-first Cloud Sync Worker (Phase 5):
 * - Runs only when network is connected AND user is signed in with PRO plan (`GatedFeature.CLOUD_BACKUP_SYNC`).
 * - Uploads all unsynced local Room rows (`PENDING_UPLOAD` / `FAILED`) to `users/{uid}/sessions/{remoteId}`.
 * - Downloads remote changes from Firestore and resolves conflicts via Last-Write-Wins (`updatedAtMs`).
 * - Purges soft-deleted rows (`deleted = true`) older than 30 days.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val countRepository: CountRepository,
    private val planManager: PlanManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!planManager.canUse(GatedFeature.CLOUD_BACKUP_SYNC)) {
            return Result.success()
        }

        val uid = try {
            if (FirebaseApp.getApps(applicationContext).isNotEmpty()) {
                FirebaseAuth.getInstance().currentUser?.uid
            } else null
        } catch (_: Throwable) {
            null
        } ?: return Result.success()

        val db = try {
            FirebaseFirestore.getInstance()
        } catch (_: Throwable) {
            return Result.retry()
        }

        return try {
            val sessionsCollection = db.collection("users").doc(uid).collection("sessions")

            // 1. Upload unsynced local Room rows
            val unsynced = countRepository.getUnsyncedSessions()
            for (local in unsynced) {
                try {
                    val payload = mapOf(
                        "count" to local.finalCount,
                        "autoCount" to local.autoCount,
                        "manualDelta" to local.manualDelta,
                        "label" to local.label.take(120),
                        "preset" to local.presetName.take(60),
                        "direction" to local.direction,
                        "createdAt" to local.timestampMs,
                        "durationSec" to local.durationSeconds,
                        "isTestModeSession" to local.isTestModeSession,
                        "accuracyTestPercent" to local.accuracyErrorPercent,
                        "notes" to local.notes,
                        "deleted" to local.deleted,
                        "updatedAt" to local.updatedAtMs
                    )
                    sessionsCollection.document(local.remoteId).set(payload, SetOptions.merge()).await()
                    countRepository.markSessionSynced(local)
                } catch (_: Throwable) {
                    countRepository.markSessionSyncFailed(local)
                }
            }

            // 2. Download remote sessions & apply Last-Write-Wins (LWW) by updatedAt
            val remoteSnap = sessionsCollection.get().await()
            for (doc in remoteSnap.documents) {
                val remoteId = doc.id
                val count = (doc.getLong("count") ?: 0L).toInt()
                val autoCount = (doc.getLong("autoCount") ?: count.toLong()).toInt()
                val manualDelta = (doc.getLong("manualDelta") ?: 0L).toInt()
                val label = doc.getString("label") ?: "Synced Session"
                val preset = doc.getString("preset") ?: "Rice"
                val direction = doc.getString("direction") ?: "Top -> Bottom"
                val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                val durationSec = doc.getLong("durationSec") ?: 1L
                val isTest = doc.getBoolean("isTestModeSession") ?: false
                val errPct = (doc.getDouble("accuracyTestPercent") ?: 0.0).toFloat()
                val notes = doc.getString("notes") ?: ""
                val deleted = doc.getBoolean("deleted") ?: false
                val updatedAt = doc.getLong("updatedAt") ?: createdAt

                countRepository.mergeRemoteSession(
                    CountSessionEntity(
                        remoteId = remoteId,
                        timestampMs = createdAt,
                        updatedAtMs = updatedAt,
                        label = label,
                        presetName = preset,
                        finalCount = count,
                        autoCount = autoCount,
                        manualDelta = manualDelta,
                        direction = direction,
                        durationSeconds = durationSec,
                        isTestModeSession = isTest,
                        accuracyErrorPercent = errPct,
                        notes = notes,
                        deleted = deleted,
                        syncStatus = SyncStatus.SYNCED.name
                    )
                )
            }

            // 3. Purge soft-deleted rows older than 30 days
            countRepository.purgeExpiredSoftDeletes()

            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_PERIODIC_SYNC = "ontik_periodic_cloud_sync"
        private const val UNIQUE_ONETIME_SYNC = "ontik_immediate_cloud_sync"

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_SYNC,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun triggerImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ONETIME_SYNC,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
