package com.dfrobot.rehab.data.protocol

import com.dfrobot.rehab.domain.model.DeviceEvent
import com.dfrobot.rehab.domain.model.TrainingRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FirmwareCodecTest {

    private fun decode(s: String) =
        FirmwareCodec.decodeIncoming(s.toByteArray(Charsets.UTF_8))

    @Test
    fun `hello 帧解码为设备上线`() {
        assertEquals(DeviceEvent.Hello, decode("hello"))
    }

    @Test
    fun `WA 帧解码为达到目标重量`() {
        assertEquals(DeviceEvent.RepReached, decode("WA"))
    }

    @Test
    fun `plus 帧解码为完成一次重复`() {
        assertEquals(DeviceEvent.RepCompleted, decode("plus"))
    }

    @Test
    fun `解码容忍首尾空白`() {
        assertEquals(DeviceEvent.RepCompleted, decode("plus\n"))
        assertEquals(DeviceEvent.Hello, decode(" hello "))
    }

    @Test
    fun `大小写敏感_未知载荷返回 null`() {
        assertNull(decode("HELLO"))
        assertNull(decode("wa"))
        assertNull(decode("garbage"))
        assertNull(decode(""))
        assertNull(decode("hello world"))
        assertNull(decode("""{"type":"data","p":12.5}"""))
    }

    @Test
    fun `encodeCommand 输出固件指令码`() {
        assertEquals("A", FirmwareCodec.encodeCommand(TrainingRatio.T25).toString(Charsets.UTF_8))
        assertEquals("B", FirmwareCodec.encodeCommand(TrainingRatio.T50).toString(Charsets.UTF_8))
        assertEquals("C", FirmwareCodec.encodeCommand(TrainingRatio.T75).toString(Charsets.UTF_8))
        assertEquals("D", FirmwareCodec.encodeCommand(TrainingRatio.T100).toString(Charsets.UTF_8))
    }

    @Test
    fun `encodeHelloTest 输出 S`() {
        assertEquals("S", FirmwareCodec.encodeHelloTest().toString(Charsets.UTF_8))
    }

    @Test
    fun `TrainingRatio 比例映射正确`() {
        assertEquals(25, TrainingRatio.T25.percent)
        assertEquals(100, TrainingRatio.T100.percent)
    }
}
