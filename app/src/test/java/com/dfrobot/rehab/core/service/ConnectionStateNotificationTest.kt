package com.dfrobot.rehab.core.service

import android.app.Notification
import com.dfrobot.rehab.domain.model.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ConnectionStateNotificationTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `三种状态的文案映射`() {
        val texts = listOf(
            ConnectionState.Connecting to "正在连接…",
            ConnectionState.Connected to "已连接",
            ConnectionState.Disconnected to "已断开,等待重连…",
        )
        for ((state, expected) in texts) {
            val notification = ConnectionStateNotification.build(context, state)
            assertTrue(
                "state=$state 应包含「$expected」",
                notification.extras.getString(Notification.EXTRA_TEXT)!!.contains(expected),
            )
        }
    }

    @Test
    fun `已连接时显示设备 topic`() {
        val notification = ConnectionStateNotification.build(
            context, ConnectionState.Connected, "BJpHJt1VW",
        )
        val text = notification.extras.getString(Notification.EXTRA_TEXT)!!
        assertTrue(text.contains("BJpHJt1VW"))
    }

    @Test
    fun `通知使用固定 channel 且常驻`() {
        val notification = ConnectionStateNotification.build(context, ConnectionState.Disconnected)
        assertEquals(ConnectionStateNotification.CHANNEL_ID, notification.channelId)
        assertEquals(true, notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }
}
