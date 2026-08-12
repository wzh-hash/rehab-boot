package com.dfrobot.rehab.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import com.dfrobot.rehab.domain.model.TrainingRatio
import com.dfrobot.rehab.domain.model.TrainingSession
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "training_sessions")
data class TrainingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val durationMillis: Long,
    /** 目标比例指令码(A/B/C/D)。 */
    val ratioCode: String,
    val repsCompleted: Int,
    val completed: Boolean,
)

fun TrainingSessionEntity.toDomain(): TrainingSession = TrainingSession(
    id = id,
    startTimeMillis = startTimeMillis,
    endTimeMillis = endTimeMillis,
    durationMillis = durationMillis,
    ratio = TrainingRatio.entries.firstOrNull { it.code == ratioCode } ?: TrainingRatio.T25,
    repsCompleted = repsCompleted,
    completed = completed,
)

fun TrainingSession.toEntity(): TrainingSessionEntity = TrainingSessionEntity(
    id = id,
    startTimeMillis = startTimeMillis,
    endTimeMillis = endTimeMillis,
    durationMillis = durationMillis,
    ratioCode = ratio.code,
    repsCompleted = repsCompleted,
    completed = completed,
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
