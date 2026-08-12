package com.dfrobot.rehab.data.protocol

import com.dfrobot.rehab.domain.model.PressureSample
import com.dfrobot.rehab.domain.model.Thresholds
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 与康复靴固件的通信协议(JSON 信封,单 topic 双向,方向由 type 区分):
 * - 设备→App:`{"type":"data","p":12.5,"ts":1723456789}`(p=负重 kg)
 * - App→设备:`{"type":"config","p25":15.0,"p50":30.0,"p75":45.0}`(QoS 1 + retained)
 *
 * 解码规则:任何非法 JSON / 缺字段 / 未知 type / 数值非法一律返回 null(零容忍乱帧)。
 */
object ProtocolCodec {

    @Serializable
    private data class DataFrame(val type: String, val p: Double, val ts: Long? = null)

    @Serializable
    private data class ConfigFrame(
        val type: String,
        val p25: Double,
        val p50: Double,
        val p75: Double,
    )

    private val json = Json { explicitNulls = false }

    sealed interface MqttMessage {
        data class Data(val sample: PressureSample) : MqttMessage
        data class Config(val thresholds: Thresholds) : MqttMessage
    }

    fun decodeIncoming(
        payload: ByteArray,
        nowMillis: () -> Long = System::currentTimeMillis,
    ): MqttMessage? {
        val text = runCatching { payload.toString(Charsets.UTF_8) }.getOrNull() ?: return null
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return null
        val type = runCatching { root.jsonObject["type"]?.jsonPrimitive?.contentOrNull }
            .getOrNull() ?: return null
        return when (type) {
            "data" -> runCatching {
                // 零容忍:数字字段必须是 JSON 数字(字符串数字视为乱帧)
                val obj = root.jsonObject
                if (obj["p"]?.jsonPrimitive?.isString == true) return@runCatching null
                val frame = json.decodeFromJsonElement<DataFrame>(root)
                require(frame.p.isFinite() && frame.p >= 0.0)
                MqttMessage.Data(
                    PressureSample(
                        valueKg = frame.p,
                        timestampMillis = frame.ts ?: nowMillis(),
                    ),
                )
            }.getOrNull()

            "config" -> runCatching {
                val obj = root.jsonObject
                if (obj["p25"]?.jsonPrimitive?.isString == true ||
                    obj["p50"]?.jsonPrimitive?.isString == true ||
                    obj["p75"]?.jsonPrimitive?.isString == true
                ) {
                    return@runCatching null
                }
                val frame = json.decodeFromJsonElement<ConfigFrame>(root)
                require(frame.p25.isFinite() && frame.p25 >= 0.0)
                require(frame.p50.isFinite() && frame.p50 >= 0.0)
                require(frame.p75.isFinite() && frame.p75 >= 0.0)
                require(frame.p25 <= frame.p50 && frame.p50 <= frame.p75)
                MqttMessage.Config(
                    Thresholds(
                        p25Kg = frame.p25,
                        p50Kg = frame.p50,
                        p75Kg = frame.p75,
                    ),
                )
            }.getOrNull()

            else -> null
        }
    }

    fun encodeConfig(thresholds: Thresholds): ByteArray =
        json.encodeToString(
            ConfigFrame.serializer(),
            ConfigFrame("config", thresholds.p25Kg, thresholds.p50Kg, thresholds.p75Kg),
        ).toByteArray(Charsets.UTF_8)
}
