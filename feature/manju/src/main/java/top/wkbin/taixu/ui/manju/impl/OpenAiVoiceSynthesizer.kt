package top.wkbin.taixu.ui.manju.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import top.wkbin.taixu.ui.manju.VoiceEndpointConfig
import top.wkbin.taixu.ui.manju.VoiceSynthesizer
import java.io.File

/**
 * 语音合成协议层。
 *
 * 抽成纯函数以便完全离线单测：字段名拼错、格式选错是这类
 * 接口最常见的静默失败点，而它们不需要网络就能验证。
 */
object SpeechProtocol {

    /** 请求体。字段与 OpenAI 兼容的 /audio/speech 对齐。 */
    fun requestBody(
        model: String,
        input: String,
        voice: String,
        responseFormat: String = "mp3",
        speed: Double = 1.0,
    ): String = buildJsonObject {
        put("model", model)
        put("input", input)
        put("voice", voice)
        put("response_format", responseFormat)
        put("speed", speed)
    }.toString()

    /**
     * 解析错误响应。
     *
     * 成功时服务端直接返回音频二进制，因此只有在失败时才有 JSON 可解；
     * 解析不出结构时退回原始文本，保证错误信息不丢。
     */
    fun errorMessage(body: String): String = runCatching {
        val root = Json.parseToJsonElement(body).jsonObject
        val err = root["error"]?.jsonObject
        val msg = err?.get("message")?.jsonPrimitive?.content
            ?: root["message"]?.jsonPrimitive?.content
        msg?.takeIf { it.isNotBlank() } ?: body.take(300)
    }.getOrElse { body.take(300) }

    /**
     * 校验音频载荷是否可信。
     *
     * 有些网关在鉴权失败时仍返回 200 但内容是一段 JSON 文本，
     * 直接落盘会得到「假音频」，FFmpeg 阶段才报错且难以定位。
     */
    fun looksLikeAudio(bytes: ByteArray): Boolean = bytes.size > 128

    /** 由响应格式推导文件扩展名，供调用方决定落盘名。 */
    fun extensionFor(responseFormat: String): String = when (responseFormat.lowercase()) {
        "mp3" -> "mp3"
        "opus" -> "opus"
        "aac" -> "aac"
        "flac" -> "flac"
        "wav" -> "wav"
        "pcm" -> "pcm"
        else -> "mp3"
    }
}

/**
 * OpenAI 兼容的语音合成实现（火山方舟 / 硅基流动 / 智谱等）。
 *
 * 与出图共用一套「配置在界面填、Key 加密存储」的机制，
 * 未配置时给出可操作提示而不是静默失败。
 */
class OpenAiVoiceSynthesizer(
    private val httpClient: OkHttpClient,
    private val endpointProvider: suspend () -> VoiceEndpointConfig,
    private val responseFormat: String = "mp3",
) : VoiceSynthesizer {

    override suspend fun synthesize(
        text: String,
        outputPath: String,
        voiceId: String?,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = endpointProvider()
            if (!endpoint.ready) {
                throw IllegalStateException("尚未配置配音服务，请在漫剧页填写语音 API Key")
            }
            val body = SpeechProtocol.requestBody(
                model = endpoint.model,
                input = text,
                voice = voiceId?.takeIf { it.isNotBlank() } ?: endpoint.voice,
                responseFormat = responseFormat,
            )
            val request = Request.Builder()
                .url("${endpoint.baseUrl.trimEnd('/')}/audio/speech")
                .header("Authorization", "Bearer ${endpoint.apiKey}")
                .header("Content-Type", "application/json")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val bytes = httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.bytes()
                if (!response.isSuccessful) {
                    val text = raw?.decodeToString().orEmpty()
                    throw IllegalStateException(
                        "配音失败 HTTP ${response.code}：${SpeechProtocol.errorMessage(text)}"
                    )
                }
                raw ?: throw IllegalStateException("配音服务返回空响应")
            }
            if (!SpeechProtocol.looksLikeAudio(bytes)) {
                throw IllegalStateException(
                    "配音返回内容不是音频（疑似网关错误）：${bytes.decodeToString().take(200)}"
                )
            }
            File(outputPath).apply { parentFile?.mkdirs() }.writeBytes(bytes)
            outputPath
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
