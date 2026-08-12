package com.dfrobot.rehab.core.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.dfrobot.rehab.core.mqtt.MqttConnectionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 监听网络恢复,触发 [MqttConnectionManager.setNetworkRetryFlag] 提前重连。
 * 仅持 manager 软引用,不延长生命周期。
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext context: Context,
    manager: MqttConnectionManager,
) {

    private val managerRef = WeakReference(manager)
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            managerRef.get()?.setNetworkRetryFlag()
        }
    }

    fun register() {
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(callback)
        }
    }
}
