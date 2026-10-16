package top.wkbin.taixu.ui.manju.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import top.wkbin.taixu.core.datastore.ManjuImageEndpoint
import top.wkbin.taixu.ui.manju.ImageGenerator
import java.io.File
import java.util.Base64

/**
 * 出图协议层：请求体构造与响应解析。
 *
 * 抽成纯函数是为了能完全离线单测——这类「拼 JSON + 解 JSON」的代码
 * 是最容易静默出错的部分（字段名写错、base64 前缀漏掉），
 * 而它们不需要网络就能验证。
 */
object ArkImageProtocol {

    /** 请求体。字段与火山方舟 /images/generations（OpenAI 兼容）对齐。 */
    fun requestBody(
        model: String,
        prompt: String,
        size: String = "1024x1024",
        referenceImage: String? = null,
    ): String = buildJsonObject {
        put("model", model)
        put("prompt", prompt)
        put("size", size)
        // 显式要 base64，避免产物 URL 过期导致后续 FFmpeg 取不到图
        put("response_format", "b64_json")
        if (referenceImage != null) {
            put("image", referenceImage)
        }
    }.toString()

    /**
     * 从响应中取出图片字节。
     *
     * 兼容两种返回：b64_json（优先）与 url（仅返回 URL，由调用方再取）。
     */
    fun extractImage(payload: String): Result<ImagePayload> = runCatching {
        val root = Json.parseToJsonElement(payload).jsonObject
        root["error"]?.let { err ->
            val msg = err.jsonObject["message"]?.jsonPrimitive?.contentOrNullSafe()
            throw IllegalStateException("出图服务返回错误：${msg ?: err.toString()}")
        }
        val data = root["data"]?.jsonArray
            ?: throw IllegalStateException("出图响应缺少 data 字段")
        val first = data.firstOrNull()?.jsonObject
            ?: throw IllegalStateException("出图响应 data 为空")
        first["b64_json"]?.jsonPrimitive?.contentOrNullSafe()?.let { b64 ->
            return@runCatching ImagePayload.Bytes(Base64.getDecoder().decode(b64))
        }
        first["url"]?.jsonPrimitive?.contentOrNullSafe()?.let { url ->
            return@runCatching ImagePayload.RemoteUrl(url)
        }
        throw IllegalStateException("出图响应既无 b64_json 也无 url")
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
        if (this is kotlinx.serialization.json.JsonNull) null else content
}

/** 出图结果载荷。 */
sealed interface ImagePayload {
    data class Bytes(val value: ByteArray) : ImagePayload {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Bytes && value.contentEquals(other.value))

        override fun hashCode(): Int = value.contentHashCode()
    }

    data class RemoteUrl(val url: String) : ImagePayload
}

/**
 * 火山方舟（Ark）出图实现。
 *
 * 即梦 App 本身不提供公开 API，其能力的官方程序化入口是方舟的
 * doubao-seedream 模型（竞品「量子探险」反编译中出现的也是它）。
 *
 * [endpointProvider] 每次调用时读取配置，用户在界面上改 Key 后无需重启应用。
 */
class ArkImageGenerator(
    private val httpClient: OkHttpClient,
    private val endpointProvider: suspend () -> ManjuImageEndpoint,
    private val size: String = "1024x1024",
) : ImageGenerator {

    override suspend fun generate(
        prompt: String,
        referenceImage: String?,
        outputPath: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = endpointProvider()
            if (!endpoint.ready) {
                throw IllegalStateException("尚未配置出图服务，请在漫剧页填写 API Key")
            }
            val body = ArkImageProtocol.requestBody(
                model = endpoint.model,
                prompt = prompt,
                size = size,
                referenceImage = referenceImage?.let { readAsDataUri(it) },
            )
            val request = Request.Builder()
                .url("${endpoint.baseUrl.trimEnd('/')}/images/generations")
                .header("Authorization", "Bearer ${endpoint.apiKey}")
                .header("Content-Type", "application/json")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val payload = httpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    // 把服务端错误原文带出去，便于用户自助排查（欠费/模型未开通/Key 错）
                    throw IllegalStateException("出图失败 HTTP ${response.code}：${text.take(300)}")
                }
                text
            }

            when (val image = ArkImageProtocol.extractImage(payload).getOrThrow()) {
                is ImagePayload.Bytes -> {
                    File(outputPath).apply { parentFile?.mkdirs() }.writeBytes(image.value)
                }
                is ImagePayload.RemoteUrl -> downloadTo(image.url, outputPath)
            }
            outputPath
        }
    }

    /** 把参考图读成 data URI，用于角色一致性（Seedream 支持 image 字段）。 */
    private fun readAsDataUri(path: String): String? = runCatching {
        val file = File(path)
        if (!file.exists()) return null
        val mime = when (file.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "image/png"
        }
        "data:$mime;base64," + Base64.getEncoder().encodeToString(file.readBytes())
    }.getOrNull()

    private fun downloadTo(url: String, outputPath: String) {
        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("下载出图结果失败 HTTP ${response.code}")
            val stream = response.body?.byteStream() ?: throw IllegalStateException("出图结果为空")
            File(outputPath).apply { parentFile?.mkdirs() }.outputStream().use { stream.copyTo(it) }
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
