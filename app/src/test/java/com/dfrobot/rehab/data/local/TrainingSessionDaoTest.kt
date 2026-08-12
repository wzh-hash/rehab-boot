package com.dfrobot.rehab.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dfrobot.rehab.domain.model.TrainingSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrainingSessionDaoTest {

    private lateinit var db: RehabDatabase
    private lateinit var dao: TrainingSessionDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, RehabDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.trainingSessionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun entity(ratioCode: String = "A", start: Long = 1_000L, completed: Boolean = true) =
        TrainingSessionEntity(
            startTimeMillis = start, endTimeMillis = 2_000L,
            durationMillis = 1_000L, ratioCode = ratioCode,
            repsCompleted = 3, completed = completed,
        )

    @Test
    fun `insert 后 observeAll 按开始时间倒序`() = runTest {
        dao.insert(entity(start = 1_000L))
        dao.insert(entity(start = 5_000L))
        val sessions = dao.observeAll().first()
        assertEquals(2, sessions.size)
        assertEquals(5_000L, sessions[0].startTimeMillis)
        assertEquals(1_000L, sessions[1].startTimeMillis)
    }

    @Test
    fun `delete 后列表移除`() = runTest {
        val id = dao.insert(entity())
        assertEquals(1, dao.observeAll().first().size)
        dao.delete(id)
        assertEquals(0, dao.observeAll().first().size)
    }

    @Test
    fun `映射到领域模型字段一致`() {
        val entity = entity(ratioCode = "C", start = 1_000L)
        val domain = entity.toDomain()
        assertEquals(com.dfrobot.rehab.domain.model.TrainingRatio.T75, domain.ratio)
        assertEquals(3, domain.repsCompleted)
        assertEquals(true, domain.completed)
        assertEquals(domain, domain.toEntity().toDomain())
    }
}
