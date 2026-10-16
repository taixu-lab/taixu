package top.wkbin.taixu.ui.manju

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.wkbin.taixu.ui.manju.impl.ArkImageProtocol
import top.wkbin.taixu.ui.manju.impl.ImagePayload
import java.util.Base64

/**
 * 出图协议层单测。
 *
 * 这类「拼 JSON / 解 JSON」的代码最容易静默出错（字段名写错、
 * base64 前缀漏掉、错误分支没覆盖），而它们不需要网络即可验证。
 */
class ArkImageProtocolTest {

    // ---------- 请求体 ----------

    @Test
    fun `请求体包含模型与提示词`() {
        val body = ArkImageProtocol.requestBody("doubao-seedream-4-0", "一只白猫")
        val obj = Json.parseToJsonElement(body).jsonObject
        assertEquals("doubao-seedream-4-0", obj["model"]?.jsonPrimitive?.content)
        assertEquals("一只白猫", obj["prompt"]?.jsonPrimitive?.content)
    }

    @Test
    fun `默认请求 base64 返回，避免 URL 过期`() {
        val obj = Json.parseToJsonElement(ArkImageProtocol.requestBody("m", "p")).jsonObject
        assertEquals("b64_json", obj["response_format"]?.jsonPrimitive?.content)
    }

    @Test
    fun `无参考图时不带 image 字段`() {
        val obj = Json.parseToJsonElement(ArkImageProtocol.requestBody("m", "p")).jsonObject
        assertNull(obj["image"])
    }

    @Test
    fun `有参考图时带上 image 字段（角色一致性）`() {
        val ref = "data:image/png;base64,AAAA"
        val obj = Json.parseToJsonElement(ArkImageProtocol.requestBody("m", "p", referenceImage = ref)).jsonObject
        assertEquals(ref, obj["image"]?.jsonPrimitive?.content)
    }

    @Test
    fun `自定义尺寸生效`() {
        val obj = Json.parseToJsonElement(ArkImageProtocol.requestBody("m", "p", size = "1024x1536")).jsonObject
        assertEquals("1024x1536", obj["size"]?.jsonPrimitive?.content)
    }

    // ---------- 响应解析 ----------

    @Test
    fun `解析 b64_json 返回字节`() {
        val raw = byteArrayOf(1, 2, 3, 4, 5)
        val b64 = Base64.getEncoder().encodeToString(raw)
        val payload = """{"data":[{"b64_json":"$b64"}]}"""
        val result = ArkImageProtocol.extractImage(payload).getOrThrow()
        assertTrue(result is ImagePayload.Bytes)
        assertEquals(raw.toList(), (result as ImagePayload.Bytes).value.toList())
    }

    @Test
    fun `解析 url 返回远程地址`() {
        val payload = """{"data":[{"url":"https://example.com/a.png"}]}"""
        val result = ArkImageProtocol.extractImage(payload).getOrThrow()
        assertEquals(ImagePayload.RemoteUrl("https://example.com/a.png"), result)
    }

    @Test
    fun `b64_json 优先于 url`() {
        val b64 = Base64.getEncoder().encodeToString(byteArrayOf(9))
        val payload = """{"data":[{"url":"https://example.com/a.png","b64_json":"$b64"}]}"""
        assertTrue(ArkImageProtocol.extractImage(payload).getOrThrow() is ImagePayload.Bytes)
    }

    @Test
    fun `服务端 error 字段被转成失败并保留原文`() {
        val payload = """{"error":{"message":"model not found","type":"NotFound"}}"""
        val r = ArkImageProtocol.extractImage(payload)
        assertTrue(r.isFailure)
        assertTrue(r.exceptionOrNull()!!.message!!.contains("model not found"))
    }

    @Test
    fun `缺少 data 字段时失败`() {
        assertTrue(ArkImageProtocol.extractImage("""{"foo":1}""").isFailure)
    }

    @Test
    fun `data 为空数组时失败`() {
        assertTrue(ArkImageProtocol.extractImage("""{"data":[]}""").isFailure)
    }

    @Test
    fun `既无 b64 也无 url 时失败`() {
        assertTrue(ArkImageProtocol.extractImage("""{"data":[{}]}""").isFailure)
    }

    @Test
    fun `非法 JSON 不抛异常而是返回失败`() {
        assertTrue(ArkImageProtocol.extractImage("not json at all").isFailure)
    }
}
