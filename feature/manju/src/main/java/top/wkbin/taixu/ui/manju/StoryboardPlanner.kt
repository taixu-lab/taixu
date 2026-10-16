package top.wkbin.taixu.ui.manju

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 分镜拆解：把剧本拆成镜头列表，并拼装每个镜头的出图提示词。
 *
 * 这里刻意不依赖 Android 框架，纯 Kotlin 实现，便于单元测试与快速迭代提示词。
 * LLM 调用本身通过 [StoryboardLlm] 抽象注入，便于替换供应商与在测试中打桩。
 */
class StoryboardPlanner(
    private val llm: StoryboardLlm,
) {
    /**
     * 将剧本拆解为分镜表。
     *
     * @param script 用户输入的剧本/小说片段
     * @param globalStyle 全局风格，如「国漫2D，赛璐璐上色」
     * @param characters 已定义的角色，用于把外观描述拼进提示词
     */
    suspend fun plan(
        script: String,
        globalStyle: String = "",
        characters: List<CharacterRef> = emptyList(),
    ): Result<Storyboard> = runCatching {
        require(script.isNotBlank()) { "剧本不能为空" }
        val raw = llm.decompose(buildDecomposePrompt(script))
        val shots = parseShots(raw)
        require(shots.isNotEmpty()) { "分镜拆解结果为空" }
        Storyboard(
            title = llm.suggestTitle(script),
            shots = shots.mapIndexed { i, s ->
                s.copy(
                    index = i,
                    imagePrompt = assembleImagePrompt(s.imagePrompt, globalStyle, characters),
                )
            },
            globalStyle = globalStyle,
        )
    }

    /** 拆解用的系统提示词。 */
    internal fun buildDecomposePrompt(script: String): String = """
        你是漫剧分镜师。把下面的剧本拆成若干镜头，输出严格 JSON 数组，不要任何解释文字。
        每个元素字段：
          - imagePrompt: 画面描述（人物、动作、场景、构图），不要包含画风词
          - camera: 镜头语言（景别 + 运镜），如「中景，缓慢推近」
          - dialogue: 该镜的台词或旁白，没有则留空字符串
          - durationSec: 该镜时长（秒），整数，通常 3-8
        要求：
          1. 一个镜头只表达一个动作或一个情绪点，不要塞多件事
          2. 同一场景连续对话可合并为一个镜头
          3. 镜头数控制在 6-20 之间

        剧本：
        $script
    """.trimIndent()

    /**
     * 解析 LLM 返回的 JSON。
     *
     * LLM 常见问题：包裹 ```json 代码块、前后带解释文字、字段缺失。
     * 这里做容错，而不是直接抛异常。
     */
    internal fun parseShots(raw: String): List<Shot> {
        val json = extractJsonArray(raw) ?: return emptyList()
        val array = runCatching { Json.parseToJsonElement(json).jsonArray }.getOrNull()
            ?: return emptyList()
        return array.mapIndexedNotNull { i, element ->
            val obj = runCatching { element.jsonObject }.getOrNull() ?: return@mapIndexedNotNull null
            val prompt = obj.str("imagePrompt").ifBlank { obj.str("image_prompt") }
            if (prompt.isBlank()) return@mapIndexedNotNull null
            Shot(
                index = i,
                imagePrompt = prompt,
                camera = obj.str("camera"),
                dialogue = obj.str("dialogue"),
                // 顺序很重要：先判断「缺失或非正数」再收敛。
                // 若先 coerceIn(1,30)，durationSec=0 会被抬成 1，默认值 5 永远不可达。
                durationSec = obj.int("durationSec").takeIf { it > 0 }?.coerceIn(1, 30) ?: 5,
            )
        }
    }

    /** 从可能带代码块或解释文字的返回中抠出 JSON 数组。 */
    internal fun extractJsonArray(raw: String): String? {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        return if (start >= 0 && end > start) raw.substring(start, end + 1) else null
    }

    /** 把风格与角色外观拼进出图提示词，保证跨镜头一致性。 */
    internal fun assembleImagePrompt(
        base: String,
        globalStyle: String,
        characters: List<CharacterRef>,
    ): String = buildList {
        add(base.trim())
        if (globalStyle.isNotBlank()) add(globalStyle.trim())
        characters.forEach { c ->
            if (c.appearance.isNotBlank()) add("${c.name}：${c.appearance.trim()}")
        }
    }.filter { it.isNotBlank() }.joinToString("，")

    private fun JsonObject.str(key: String): String =
        runCatching { this[key]?.jsonPrimitive?.content }.getOrNull().orEmpty()

    private fun JsonObject.int(key: String): Int =
        runCatching { this[key]?.jsonPrimitive?.content?.toInt() }.getOrNull() ?: 0
}

/** LLM 能力抽象：便于替换供应商、便于测试打桩。 */
interface StoryboardLlm {
    suspend fun decompose(prompt: String): String
    suspend fun suggestTitle(script: String): String
}
