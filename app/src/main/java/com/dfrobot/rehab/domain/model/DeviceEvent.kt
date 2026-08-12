package com.dfrobot.rehab.domain.model

/** 设备(Mind+ 固件)通过 MQTT 上报的状态事件。 */
sealed interface DeviceEvent {
    /** 设备上电 / MQTT 重连成功(固件发布 "hello")。 */
    data object Hello : DeviceEvent

    /** 某次重复达到目标重量(固件发布 "WA")。 */
    data object RepReached : DeviceEvent

    /** 完成一次重复(固件语音+LED 后发布 "plus")。 */
    data object RepCompleted : DeviceEvent
}
