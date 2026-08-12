package com.dfrobot.rehab.domain

import com.dfrobot.rehab.domain.model.ConnectionState
import com.dfrobot.rehab.domain.model.DeviceSettings
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** 连接生命周期门面:供 presentation 层依赖,便于 fake 测试。 */
interface ConnectionGateway {

    val connectionState: StateFlow<ConnectionState>

    /** 中文用户可读错误事件(凭据错/网络不可达/连接被拒绝)。 */
    val errorEvents: SharedFlow<String>

    /** 收到的无效帧计数(调试用)。 */
    val invalidFrameCount: StateFlow<Int>

    suspend fun connect(settings: DeviceSettings)

    suspend fun disconnect()
}
