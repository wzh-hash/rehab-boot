package com.dfrobot.rehab.domain.model

/**
 * 平台连接配置(全部可在设置页编辑,不硬编码)。
 *
 * @param host  MQTT broker 主机,默认 DFRobot Easy IoT 中国区
 * @param port  MQTT 端口,平台仅支持明文 1883
 * @param iotId 平台账号 Iot_id
 * @param iotPwd 平台账号 Iot_pwd
 * @param topic 设备 Topic(扁平随机字符串,即设备 ID),发布与订阅同一 topic
 */
data class DeviceSettings(
    val host: String = "iot.dfrobot.com.cn",
    val port: Int = 1883,
    val iotId: String = "",
    val iotPwd: String = "",
    val topic: String = "",
) {
    fun isComplete(): Boolean =
        host.isNotBlank() && port in 1..65535 &&
            iotId.isNotBlank() && iotPwd.isNotBlank() && topic.isNotBlank()
}
