package top.wkbin.taixu.ui.manju.impl

import top.wkbin.taixu.harness.ApiMessage
import top.wkbin.taixu.harness.ProviderClient
import top.wkbin.taixu.ui.manju.StoryboardLlm

/**
 * 走太墟既有 ProviderClient 的分镜拆解 LLM 实现。
 *
 * 复用用户在太墟里已配置的模型与供应商，无需额外填 API Key，
 * 也天然继承其重试、超时与协议兼容处理。
 */
class ProviderStoryboardLlm(
    private val providerClient: ProviderClient,
) : StoryboardLlm {

    override suspend fun decompose(prompt: String): String {
        val model = providerClient.resolveModel()
        val result = providerClient.chat(
            model,
            listOf(ApiMessage(role = "user", content = prompt)),
        )
        return result.content.orEmpty()
    }

    override suspend fun suggestTitle(script: String): String {
        val model = providerClient.resolveModel()
        val result = runCatching {
            providerClient.chat(
                model,
                listOf(
                    ApiMessage(
                        role = "user",
                        content = "用不超过 12 个字给下面这段剧本起一个标题，只输出标题本身，不要引号和标点。\n\n$script",
                    ),
                ),
            )
        }.getOrNull()
        return result?.content?.trim()?.take(24)?.takeIf { it.isNotBlank() } ?: "未命名漫剧"
    }
}
