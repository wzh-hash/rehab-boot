package com.dfrobot.rehab.data

import com.dfrobot.rehab.data.local.TrainingSessionDao
import com.dfrobot.rehab.data.local.toDomain
import com.dfrobot.rehab.data.local.toEntity
import com.dfrobot.rehab.domain.model.TrainingSession
import com.dfrobot.rehab.domain.repository.TrainingSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrainingSessionRepositoryImpl @Inject constructor(
    private val dao: TrainingSessionDao,
) : TrainingSessionRepository {

    override fun observeSessions(): Flow<List<TrainingSession>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun saveSession(session: TrainingSession) {
        dao.insert(session.toEntity())
    }

    override suspend fun deleteSession(id: Long) {
        dao.delete(id)
    }
}
