package com.dfrobot.rehab.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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

    @Test
    fun `insert 后 observeAll 按开始时间倒序`() = runTest {
        dao.insert(
            TrainingSessionEntity(
                startTimeMillis = 1_000L, endTimeMillis = 2_000L,
                durationMillis = 1_000L, avgPressureKg = 10.0, peakPressureKg = 15.0,
            ),
        )
        dao.insert(
            TrainingSessionEntity(
                startTimeMillis = 5_000L, endTimeMillis = 6_000L,
                durationMillis = 1_000L, avgPressureKg = 20.0, peakPressureKg = 25.0,
            ),
        )
        val sessions = dao.observeAll().first()
        assertEquals(2, sessions.size)
        assertEquals(5_000L, sessions[0].startTimeMillis)
        assertEquals(1_000L, sessions[1].startTimeMillis)
    }

    @Test
    fun `delete 后列表移除`() = runTest {
        val id = dao.insert(
            TrainingSessionEntity(
                startTimeMillis = 1_000L, endTimeMillis = 2_000L,
                durationMillis = 1_000L, avgPressureKg = 10.0, peakPressureKg = 15.0,
            ),
        )
        assertEquals(1, dao.observeAll().first().size)
        dao.delete(id)
        assertEquals(0, dao.observeAll().first().size)
    }

    @Test
    fun `映射到领域模型字段一致`() {
        val entity = TrainingSessionEntity(
            id = 7L, startTimeMillis = 1_000L, endTimeMillis = 2_000L,
            durationMillis = 1_000L, avgPressureKg = 10.0, peakPressureKg = 15.0,
        )
        val domain = entity.toDomain()
        assertEquals(7L, domain.id)
        assertEquals(1_000L, domain.startTimeMillis)
        assertEquals(15.0, domain.peakPressureKg, 0.0)
        assertEquals(domain, domain.toEntity().toDomain())
    }
}
