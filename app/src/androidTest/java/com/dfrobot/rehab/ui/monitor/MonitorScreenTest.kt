package com.dfrobot.rehab.ui.monitor

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dfrobot.rehab.domain.SessionPhase
import com.dfrobot.rehab.domain.SessionStats
import com.dfrobot.rehab.domain.model.ConnectionState
import com.dfrobot.rehab.domain.model.Thresholds
import com.dfrobot.rehab.presentation.monitor.MonitorIntent
import com.dfrobot.rehab.presentation.monitor.MonitorUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 仪器测试(真机/模拟器执行):
 * ./gradlew :app:connectedDebugAndroidTest
 */
class MonitorScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(state: MonitorUiState, onIntent: (MonitorIntent) -> Unit = {}) {
        composeRule.setContent {
            MonitorScreen(
                state = state,
                onIntent = onIntent,
                snackbarHostState = SnackbarHostState(),
            )
        }
    }

    @Test
    fun 无数据显示占位符与等待提示() {
        setContent(MonitorUiState(connectionState = ConnectionState.Connected))
        composeRule.onNodeWithText("-- kg").assertIsDisplayed()
        composeRule.onNodeWithText("等待设备数据…").assertIsDisplayed()
    }

    @Test
    fun 实时压力以一位小数显示() {
        setContent(
            MonitorUiState(
                connectionState = ConnectionState.Connected,
                livePressureKg = 12.5,
            ),
        )
        composeRule.onNodeWithText("12.5 kg").assertIsDisplayed()
    }

    @Test
    fun 会话控件随阶段切换() {
        setContent(MonitorUiState(connectionState = ConnectionState.Connected))
        composeRule.onNodeWithText("开始训练").assertIsDisplayed()

        setContent(
            MonitorUiState(
                connectionState = ConnectionState.Connected,
                phase = SessionPhase.Running,
                stats = SessionStats(0, 0, 0.0, 0.0),
            ),
        )
        composeRule.onNodeWithText("暂停").assertIsDisplayed()
        composeRule.onNodeWithText("结束").assertIsDisplayed()

        setContent(
            MonitorUiState(
                connectionState = ConnectionState.Connected,
                phase = SessionPhase.Paused,
                stats = SessionStats(0, 0, 0.0, 0.0),
            ),
        )
        composeRule.onNodeWithText("继续").assertIsDisplayed()
    }

    @Test
    fun 点击开始训练发出对应意图() {
        var captured: MonitorIntent? = null
        setContent(
            MonitorUiState(connectionState = ConnectionState.Connected),
            onIntent = { captured = it },
        )
        composeRule.onNodeWithText("开始训练").performClick()
        assertEquals(MonitorIntent.StartSession, captured)
    }

    @Test
    fun 断开状态显示重连提示() {
        setContent(MonitorUiState(connectionState = ConnectionState.Disconnected))
        composeRule.onNodeWithText("已断开,点按重连").assertIsDisplayed()
    }

    @Test
    fun 阈值进度条显示三档标签() {
        setContent(
            MonitorUiState(
                connectionState = ConnectionState.Connected,
                thresholds = Thresholds(15.0, 30.0, 45.0),
                livePressureKg = 20.0,
            ),
        )
        composeRule.onNodeWithText("25%  15.0").assertIsDisplayed()
        composeRule.onNodeWithText("50%  30.0").assertIsDisplayed()
        composeRule.onNodeWithText("75%  45.0").assertIsDisplayed()
    }
}
