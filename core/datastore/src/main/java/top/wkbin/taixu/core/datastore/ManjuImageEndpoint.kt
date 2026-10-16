package top.wkbin.taixu.core.datastore

import top.wkbin.taixu.core.security.SecretManager

/**
 * 漫剧出图供应商配置。
 *
 * 默认指向火山方舟（Ark）的 doubao-seedream 图像生成模型——
 * 即「即梦」能力的官方 API 形态（即梦 App 本身不提供公开 API）。
 * baseUrl / model 均可改，任何 OpenAI 兼容的 `/images/generations` 端点都能用。
 */
data class ManjuImageEndpoint(
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = DEFAULT_MODEL,
    val apiKey: String = "",
) {
    val ready: Boolean get() = baseUrl.isNotBlank() && model.isNotBlank() && apiKey.isNotBlank()

    companion object {
        const val DEFAULT_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3"
        const val DEFAULT_MODEL = "doubao-seedream-4-0-250828"
    }
}

internal fun encodeManjuImageConfig(value: ManjuImageEndpoint, secretManager: SecretManager): String? {
    val url = value.baseUrl.trim()
    val model = value.model.trim()
    val apiKey = value.apiKey.trim()
    if (url.isBlank() && model.isBlank() && apiKey.isBlank()) return null
    return secretManager.encrypt(listOf(url, model, apiKey).joinToString("\u001f"))
}

internal fun decodeManjuImageConfig(stored: String?, secretManager: SecretManager): ManjuImageEndpoint {
    val plain = stored?.let(secretManager::decrypt).orEmpty()
    if (plain.isBlank()) return ManjuImageEndpoint()
    val parts = plain.split('\u001f', limit = 3)
    return ManjuImageEndpoint(
        baseUrl = parts.getOrElse(0) { "" }.ifBlank { ManjuImageEndpoint.DEFAULT_BASE_URL },
        model = parts.getOrElse(1) { "" }.ifBlank { ManjuImageEndpoint.DEFAULT_MODEL },
        apiKey = parts.getOrElse(2) { "" },
    )
}
