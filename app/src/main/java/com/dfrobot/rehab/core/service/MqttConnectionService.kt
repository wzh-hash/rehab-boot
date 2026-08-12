package com.dfrobot.rehab.core.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.dfrobot.rehab.core.mqtt.MqttConnectionManager
import com.dfrobot.rehab.domain.model.ConnectionState
import com.dfrobot.rehab.domain.model.DeviceSettings
import com.dfrobot.rehab.domain.repository.DeviceSettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 前台服务(dataSync):保活载体,持有 [MqttConnectionManager] 常驻连接。
 * 用户从设置页"启用连接"启动、设置页"断开"停止(onDestroy → manager.disconnect)。
 */
@AndroidEntryPoint
class MqttConnectionService : Service() {

    @Inject lateinit var manager: MqttConnectionManager
    @Inject lateinit var settingsRepository: DeviceSettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var connectionStateCollector: kotlinx.coroutines.Job? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground(ConnectionState.Connecting, null)
        scope.launch {
            runCatching {
                val settings = settingsRepository.settings.first()
                if (settings.isComplete()) {
                    manager.connect(settings)
                }
            }
        }
        connectionStateCollector = scope.launch {
            manager.connectionState.collect { state ->
                val topic = runCatching {
                    settingsRepository.settings.first().topic
                }.getOrNull()
                notifyState(state, topic)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY:进程被杀后系统重建服务,连接随 manager 单例恢复重连
        return START_STICKY
    }

    override fun onDestroy() {
        connectionStateCollector?.cancel()
        scope.launch { manager.disconnect() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground(state: ConnectionState, topic: String?) {
        val notification = ConnectionStateNotification.build(this, state, topic)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(
                ConnectionStateNotification.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(ConnectionStateNotification.NOTIFICATION_ID, notification)
        }
    }

    private fun notifyState(state: ConnectionState, topic: String?) {
        val notification = ConnectionStateNotification.build(this, state, topic)
        val managerService = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        managerService.notify(ConnectionStateNotification.NOTIFICATION_ID, notification)
    }

    /** 供设置页调用:以新配置重启连接(先 stop 后 start 由调用方保证时序)。 */
    companion object {
        fun start(context: android.content.Context) {
            val intent = Intent(context, MqttConnectionService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, MqttConnectionService::class.java))
        }
    }
}
