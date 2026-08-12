package com.dfrobot.rehab.ui.monitor

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dfrobot.rehab.domain.SessionPhase
import com.dfrobot.rehab.domain.SessionStats
import com.dfrobot.rehab.domain.model.ConnectionState
import com.dfrobot.rehab.domain.model.TrainingRatio
import com.dfrobot.rehab.presentation.monitor.MonitorIntent
import com.dfrobot.rehab.presentation.monitor.MonitorUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 仪器测试(真机/模拟器执行):
 * ./gradlew :app:connectedDebugAndroidTest
 *
 * 文案按 v3 设计提案 §6 更新:
 * - 按钮主文案只显示比例数字("25%"),副标另行断言
 * - 状态栏文案:"连接已断开,点击重试"
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
    fun 四个训练按钮与语音测试按钮显示() {
        setContent(
            MonitorUiState(
                connectionState = ConnectionState.Connected,
                phase = SessionPhase.Idle,
            ),
        )
        composeRule.onNodeWithText("25%").assertIsDisplayed()
        composeRule.onNodeWithText("50%").assertIsDisplayed()
        composeRule.onNodeWithText("75%").assertIsDisplayed()
        composeRule.onNodeWithText("100%").assertIsDisplayed()
        composeRule.onNodeWithText("适应期 · 负重较轻").assertIsDisplayed()
        composeRule.onNodeWithText("标准训练").assertIsDisplayed()
        composeRule.onNodeWithText("负重较大").assertIsDisplayed()
        composeRule.onNodeWithText("最大负重").assertIsDisplayed()
        composeRule.onNodeWithText("语音测试").assertIsDisplayed()
    }

    @Test
    fun 训练中按钮禁用并提示() {
        setContent(
            MonitorUiState(
                connectionState = ConnectionState.Connected,
                phase = SessionPhase.Training,
                stats = SessionStats(0, TrainingRatio.T25),
                activeRatio = TrainingRatio.T25,
                repsCompleted = 1,
            ),
        )
        composeRule.onNodeWithText("25%").assertIsNotEnabled()
        composeRule.onNodeWithText("设备训练中…").assertIsDisplayed()
        composeRule.onNodeWithText("第 1").assertIsDisplayed()
        composeRule.onNodeWithText("/3 次").assertIsDisplayed()
    }

    @Test
    fun 点击25训练发出对应意图() {
        var captured: MonitorIntent? = null
        setContent(
            MonitorUiState(
                connectionState = ConnectionState.Connected,
                phase = SessionPhase.Idle,
            ),
            onIntent = { captured = it },
        )
        composeRule.onNodeWithText("25%").performClick()
        assertEquals(MonitorIntent.StartTraining(TrainingRatio.T25), captured)
    }

    @Test
    fun 断开状态显示重连提示() {
        setContent(MonitorUiState(connectionState = ConnectionState.Disconnected))
        composeRule.onNodeWithText("连接已断开,点击重试").assertIsDisplayed()
    }

    @Test
    fun 设备在线合并状态栏文案() {
        setContent(
            MonitorUiState(
                connectionState = ConnectionState.Connected,
                deviceOnline = true,
            ),
        )
        composeRule.onNodeWithText("已连接 · 设备在线").assertIsDisplayed()
    }

    @Test
    fun 等待设备状态显示() {
        setContent(
            MonitorUiState(
                connectionState = ConnectionState.Connected,
                deviceOnline = false,
            ),
        )
        composeRule.onNodeWithText("已连接 · 等待设备").assertIsDisplayed()
    }
}
