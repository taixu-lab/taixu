package top.wkbin.taixu.core.datastore

import top.wkbin.taixu.core.security.SecretManager

/**
 * 漫剧配音（TTS）供应商配置。
 *
 * 默认指向火山方舟的语音合成模型——与出图同源，一个 Key 可覆盖
 * 出图与配音两条链路。协议为 OpenAI 兼容的 `/audio/speech`，
 * 因此硅基流动、智谱等兼容端点无需改码即可替换。
 */
data class ManjuVoiceEndpoint(
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = DEFAULT_MODEL,
    val voice: String = DEFAULT_VOICE,
    val apiKey: String = "",
) {
    val ready: Boolean get() = baseUrl.isNotBlank() && model.isNotBlank() && apiKey.isNotBlank()

    companion object {
        const val DEFAULT_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3"
        const val DEFAULT_MODEL = "doubao-seed-tts"
        const val DEFAULT_VOICE = "zh_female_shuangkuaisisi_moon_bigtts"
    }
}

internal fun encodeManjuVoiceConfig(value: ManjuVoiceEndpoint, secretManager: SecretManager): String? {
    val url = value.baseUrl.trim()
    val model = value.model.trim()
    val voice = value.voice.trim()
    val apiKey = value.apiKey.trim()
    if (url.isBlank() && model.isBlank() && voice.isBlank() && apiKey.isBlank()) return null
    return secretManager.encrypt(listOf(url, model, voice, apiKey).joinToString("\u001f"))
}

internal fun decodeManjuVoiceConfig(stored: String?, secretManager: SecretManager): ManjuVoiceEndpoint {
    val plain = stored?.let(secretManager::decrypt).orEmpty()
    if (plain.isBlank()) return ManjuVoiceEndpoint()
    val parts = plain.split('\u001f', limit = 4)
    return ManjuVoiceEndpoint(
        baseUrl = parts.getOrElse(0) { "" }.ifBlank { ManjuVoiceEndpoint.DEFAULT_BASE_URL },
        model = parts.getOrElse(1) { "" }.ifBlank { ManjuVoiceEndpoint.DEFAULT_MODEL },
        voice = parts.getOrElse(2) { "" }.ifBlank { ManjuVoiceEndpoint.DEFAULT_VOICE },
        apiKey = parts.getOrElse(3) { "" },
    )
}
