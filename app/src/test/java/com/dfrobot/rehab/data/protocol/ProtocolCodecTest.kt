package com.dfrobot.rehab.data.protocol

import com.dfrobot.rehab.domain.model.Thresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProtocolCodecTest {

    private fun decode(s: String) =
        ProtocolCodec.decodeIncoming(s.toByteArray(Charsets.UTF_8), nowMillis = { 999L })

    @Test
    fun `合法 data 帧解码`() {
        val msg = decode("""{"type":"data","p":12.5,"ts":1723456789}""")
        val data = msg as ProtocolCodec.MqttMessage.Data
        assertEquals(12.5, data.sample.valueKg, 0.0)
        assertEquals(1723456789L, data.sample.timestampMillis)
    }

    @Test
    fun `data 帧缺 ts 时回退当前时钟`() {
        val msg = decode("""{"type":"data","p":8.0}""")
        val data = msg as ProtocolCodec.MqttMessage.Data
        assertEquals(8.0, data.sample.valueKg, 0.0)
        assertEquals(999L, data.sample.timestampMillis)
    }

    @Test
    fun `合法 config 帧解码`() {
        val msg = decode("""{"type":"config","p25":15.0,"p50":30.0,"p75":45.0}""")
        val config = msg as ProtocolCodec.MqttMessage.Config
        assertEquals(15.0, config.thresholds.p25Kg, 0.0)
        assertEquals(30.0, config.thresholds.p50Kg, 0.0)
        assertEquals(45.0, config.thresholds.p75Kg, 0.0)
    }

    @Test
    fun `缺 type 返回 null`() {
        assertNull(decode("""{"p":12.5}"""))
    }

    @Test
    fun `未知 type 返回 null`() {
        assertNull(decode("""{"type":"hello","p":12.5}"""))
    }

    @Test
    fun `非 JSON 文本返回 null`() {
        assertNull(decode("garbage"))
        assertNull(decode("12.5"))
        assertNull(decode(""))
    }

    @Test
    fun `p 为字符串返回 null`() {
        assertNull(decode("""{"type":"data","p":"12.5"}"""))
    }

    @Test
    fun `缺 p 返回 null`() {
        assertNull(decode("""{"type":"data","ts":1}"""))
    }

    @Test
    fun `负压力返回 null`() {
        assertNull(decode("""{"type":"data","p":-1.0}"""))
    }

    @Test
    fun `config 阈值非单调返回 null`() {
        assertNull(decode("""{"type":"config","p25":30.0,"p50":15.0,"p75":45.0}"""))
    }

    @Test
    fun `encodeConfig 输出精确匹配`() {
        val out = ProtocolCodec.encodeConfig(Thresholds(15.0, 30.0, 45.0))
            .toString(Charsets.UTF_8)
        assertEquals("""{"type":"config","p25":15.0,"p50":30.0,"p75":45.0}""", out)
    }
}
