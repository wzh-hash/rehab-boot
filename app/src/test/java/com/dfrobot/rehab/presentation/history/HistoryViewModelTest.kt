package com.dfrobot.rehab.presentation.history

import com.dfrobot.rehab.domain.model.TrainingRatio
import com.dfrobot.rehab.domain.model.TrainingSession
import com.dfrobot.rehab.domain.repository.TrainingSessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import app.cash.turbine.test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private class FakeRepo : TrainingSessionRepository {
        val sessions = MutableStateFlow<List<TrainingSession>>(emptyList())
        val deleted = mutableListOf<Long>()

        override fun observeSessions(): Flow<List<TrainingSession>> = sessions.asStateFlow()

        override suspend fun saveSession(session: TrainingSession) {
            sessions.value = sessions.value + session
        }

        override suspend fun deleteSession(id: Long) {
            deleted.add(id)
            sessions.value = sessions.value.filterNot { it.id == id }
        }
    }

    private fun session(id: Long) = TrainingSession(
        id = id, startTimeMillis = 1000L, endTimeMillis = 2000L,
        durationMillis = 1000L, ratio = TrainingRatio.T25,
        repsCompleted = 3, completed = true,
    )

    private lateinit var repo: FakeRepo
    private lateinit var viewModel: HistoryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repo = FakeRepo()
        viewModel = HistoryViewModel(repo)
    }

    @Test
    fun `初始为空列表`() = runTest {
        assertEquals(emptyList<TrainingSession>(), viewModel.state.value.sessions)
        assertEquals(false, viewModel.state.value.isLoading)
    }

    @Test
    fun `新会话到达后 UI 更新`() = runTest {
        repo.sessions.value = listOf(session(1L))
        assertEquals(1, viewModel.state.value.sessions.size)
        assertEquals(TrainingRatio.T25, viewModel.state.value.sessions[0].ratio)
    }

    @Test
    fun `删除会话调用仓库并提示`() = runTest {
        repo.sessions.value = listOf(session(5L))
        viewModel.accept(HistoryIntent.DeleteSession(5L))
        assertEquals(listOf(5L), repo.deleted)
        assertEquals(emptyList<TrainingSession>(), viewModel.state.value.sessions)
        viewModel.effects.test {
            assertEquals(HistoryEffect.ShowMessage("已删除"), awaitItem())
        }
    }
}
