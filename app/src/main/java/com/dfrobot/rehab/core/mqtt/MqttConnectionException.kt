package com.dfrobot.rehab.core.mqtt

/** 连接失败异常,message 为中文用户可读文案。 */
class MqttConnectionException(message: String) : Exception(message)
