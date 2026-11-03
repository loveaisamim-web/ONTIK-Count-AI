package com.ontik.countai.data.repository

import com.ontik.countai.data.local.CountSessionDao
import com.ontik.countai.data.local.CountSessionEntity
import kotlinx.coroutines.flow.Flow

class CountRepository(private val dao: CountSessionDao) {

    val allSessions: Flow<List<CountSessionEntity>> = dao.observeAllSessions()

    suspend fun saveSession(session: CountSessionEntity): Long {
        return dao.insertSession(session)
    }

    suspend fun deleteSession(session: CountSessionEntity) {
        dao.deleteSession(session)
    }

    suspend fun clearAllHistory() {
        dao.clearAllSessions()
    }
}
