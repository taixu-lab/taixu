package top.wkbin.taixu.ui.manju

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.wkbin.taixu.ui.manju.impl.SpeechProtocol

/**
 * 语音合成协议层单测。
 *
 * 字段名拼错、格式选错是 TTS 接口最常见的静默失败点，
 * 这些都能离线验证，不必消耗真实配额。
 */
class SpeechProtocolTest {

    @Test
    fun `请求体包含模型 文本 音色`() {
        val obj = Json.parseToJsonElement(
            SpeechProtocol.requestBody("doubao-seed-tts", "你好世界", "zh_female"),
        ).jsonObject
        assertEquals("doubao-seed-tts", obj["model"]?.jsonPrimitive?.content)
        assertEquals("你好世界", obj["input"]?.jsonPrimitive?.content)
        assertEquals("zh_female", obj["voice"]?.jsonPrimitive?.content)
    }

    @Test
    fun `默认返回 mp3`() {
        val obj = Json.parseToJsonElement(
            SpeechProtocol.requestBody("m", "t", "v"),
        ).jsonObject
        assertEquals("mp3", obj["response_format"]?.jsonPrimitive?.content)
    }

    @Test
    fun `语速可调`() {
        val obj = Json.parseToJsonElement(
            SpeechProtocol.requestBody("m", "t", "v", speed = 1.5),
        ).jsonObject
        assertEquals("1.5", obj["speed"]?.jsonPrimitive?.content)
    }

    @Test
    fun `解析 OpenAI 风格错误`() {
        val msg = SpeechProtocol.errorMessage("""{"error":{"message":"invalid voice"}}""")
        assertEquals("invalid voice", msg)
    }

    @Test
    fun `解析顶层 message 错误`() {
        val msg = SpeechProtocol.errorMessage("""{"message":"quota exceeded"}""")
        assertEquals("quota exceeded", msg)
    }

    @Test
    fun `错误体非 JSON 时退回原文`() {
        assertEquals("Bad Gateway", SpeechProtocol.errorMessage("Bad Gateway"))
    }

    @Test
    fun `错误体为空时不抛异常`() {
        assertEquals("", SpeechProtocol.errorMessage(""))
    }

    @Test
    fun `短响应不算音频`() {
        assertFalse(SpeechProtocol.looksLikeAudio("""{"error":"x"}""".toByteArray()))
    }

    @Test
    fun `足够长的载荷算音频`() {
        assertTrue(SpeechProtocol.looksLikeAudio(ByteArray(200) { 1 }))
    }

    @Test
    fun `扩展名映射`() {
        assertEquals("mp3", SpeechProtocol.extensionFor("mp3"))
        assertEquals("wav", SpeechProtocol.extensionFor("WAV"))
        assertEquals("opus", SpeechProtocol.extensionFor("opus"))
    }

    @Test
    fun `未知格式回退 mp3`() {
        assertEquals("mp3", SpeechProtocol.extensionFor("bogus"))
    }
}
