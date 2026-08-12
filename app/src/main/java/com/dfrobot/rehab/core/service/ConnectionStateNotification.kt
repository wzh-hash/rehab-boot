package com.dfrobot.rehab.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.dfrobot.rehab.MainActivity
import com.dfrobot.rehab.R
import com.dfrobot.rehab.domain.model.ConnectionState

object ConnectionStateNotification {

    const val CHANNEL_ID = "rehab_connection"
    const val NOTIFICATION_ID = 1

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "连接状态", NotificationManager.IMPORTANCE_LOW),
        )
    }

    fun build(context: Context, state: ConnectionState, topic: String? = null): Notification {
        ensureChannel(context)
        val text = when (state) {
            ConnectionState.Connecting -> "正在连接…"
            ConnectionState.Connected -> "已连接" + (topic?.let { " · $it" } ?: "")
            ConnectionState.Disconnected -> "已断开,等待重连…"
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
