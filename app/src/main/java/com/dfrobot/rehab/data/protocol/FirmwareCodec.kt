package com.dfrobot.rehab.data.protocol

import com.dfrobot.rehab.domain.model.DeviceEvent
import com.dfrobot.rehab.domain.model.TrainingRatio

/**
 * 与掌控板 Mind+ 固件的通信协议(短码,单 topic 双向):
 * - App→设备:`S` 语音问候测试;`A`/`B`/`C`/`D` 开始训练(25%/50%/75%/100%)
 * - 设备→App:`hello`(上线)/ `WA`(达到目标重量)/ `plus`(完成一次重复)
 *
 * 解码零容忍:未知载荷一律返回 null(由调用方计数)。
 */
object FirmwareCodec {

    fun decodeIncoming(payload: ByteArray): DeviceEvent? {
        val text = payload.toString(Charsets.UTF_8).trim()
        return when (text) {
            "hello" -> DeviceEvent.Hello
            "WA" -> DeviceEvent.RepReached
            "plus" -> DeviceEvent.RepCompleted
            else -> null
        }
    }

    fun encodeCommand(ratio: TrainingRatio): ByteArray =
        ratio.code.toByteArray(Charsets.UTF_8)

    fun encodeHelloTest(): ByteArray =
        "S".toByteArray(Charsets.UTF_8)
}
