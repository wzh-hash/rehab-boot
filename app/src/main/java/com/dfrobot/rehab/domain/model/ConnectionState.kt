package com.dfrobot.rehab.domain.model

/** MQTT 连接状态。 */
sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
}
