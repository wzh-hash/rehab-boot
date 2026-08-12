package com.dfrobot.rehab.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import com.dfrobot.rehab.domain.model.TrainingSession
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "training_sessions")
data class TrainingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val durationMillis: Long,
    val avgPressureKg: Double,
    val peakPressureKg: Double,
)

fun TrainingSessionEntity.toDomain(): TrainingSession = TrainingSession(
    id = id,
    startTimeMillis = startTimeMillis,
    endTimeMillis = endTimeMillis,
    durationMillis = durationMillis,
    avgPressureKg = avgPressureKg,
    peakPressureKg = peakPressureKg,
)

fun TrainingSession.toEntity(): TrainingSessionEntity = TrainingSessionEntity(
    id = id,
    startTimeMillis = startTimeMillis,
    endTimeMillis = endTimeMillis,
    durationMillis = durationMillis,
    avgPressureKg = avgPressureKg,
    peakPressureKg = peakPressureKg,
)

@Dao
interface TrainingSessionDao {

    @Insert
    suspend fun insert(session: TrainingSessionEntity): Long

    @Query("SELECT * FROM training_sessions ORDER BY startTimeMillis DESC")
    fun observeAll(): Flow<List<TrainingSessionEntity>>

    @Query("DELETE FROM training_sessions WHERE id = :id")
    suspend fun delete(id: Long)
}
