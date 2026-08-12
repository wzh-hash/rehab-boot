package com.dfrobot.rehab.domain.repository

import com.dfrobot.rehab.domain.model.TrainingSession
import kotlinx.coroutines.flow.Flow

interface TrainingSessionRepository {
    fun observeSessions(): Flow<List<TrainingSession>>
    suspend fun saveSession(session: TrainingSession)
    suspend fun deleteSession(id: Long)
}
