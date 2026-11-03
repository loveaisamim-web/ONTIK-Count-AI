package com.ontik.countai.data.repository

import com.ontik.countai.data.local.CountSessionDao
import com.ontik.countai.data.local.CountSessionEntity
import com.ontik.countai.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CountRepository @Inject constructor(
    private val dao: CountSessionDao
) {
    val activeSessions: Flow<List<CountSessionEntity>> = dao.observeActiveSessions()

    suspend fun saveSession(session: CountSessionEntity): Long {
        return dao.insertSession(session)
    }

    suspend fun updateSession(session: CountSessionEntity) {
        dao.updateSession(
            session.copy(
                updatedAtMs = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING_UPLOAD.name
            )
        )
    }

    suspend fun softDeleteSession(session: CountSessionEntity) {
        dao.softDeleteSession(session.id, System.currentTimeMillis())
    }

    suspend fun getUnsyncedSessions(): List<CountSessionEntity> {
        return dao.getUnsyncedSessions()
    }

    suspend fun markSessionSynced(session: CountSessionEntity) {
        dao.updateSession(session.copy(syncStatus = SyncStatus.SYNCED.name))
    }

    suspend fun markSessionSyncFailed(session: CountSessionEntity) {
        dao.updateSession(session.copy(syncStatus = SyncStatus.FAILED.name))
    }

    /**
     * Last-Write-Wins (LWW) conflict resolution by `updatedAtMs` when merging remote Firestore session rows.
     */
    suspend fun mergeRemoteSession(remote: CountSessionEntity) {
        val local = dao.getSessionByRemoteId(remote.remoteId)
        if (local == null) {
            dao.insertSession(remote.copy(syncStatus = SyncStatus.SYNCED.name))
        } else if (remote.updatedAtMs > local.updatedAtMs) {
            dao.updateSession(
                remote.copy(
                    id = local.id,
                    syncStatus = SyncStatus.SYNCED.name
                )
            )
        }
    }

    suspend fun purgeExpiredSoftDeletes() {
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24L * 3600L * 1000L)
        dao.purgeDeletedOlderThan(thirtyDaysAgo)
    }

    suspend fun clearAllLocalData() {
        dao.clearAllSessionsHard()
    }
}
