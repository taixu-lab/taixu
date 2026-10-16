package top.wkbin.taixu.ui.manju

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 单个分镜（镜头）。
 *
 * 漫剧流水线的最小单元：一镜 = 一张图 + 一段配音 + 一个时长。
 * [status] 用密封类表达生成状态机，避免用可空字段拼凑「有没有生成好」。
 */
@Serializable
data class Shot(
    val index: Int,
    /** 画面描述（喂给出图模型） */
    val imagePrompt: String,
    /** 镜头语言，如「中景，缓慢推近」 */
    val camera: String = "",
    /** 台词/旁白，用于 TTS 配音 */
    val dialogue: String = "",
    /** 目标时长（秒），合成时用于对齐 */
    val durationSec: Int = 5,
    /** 角色一致性锚定图路径：优先复用首镜的角色图 */
    val referenceImage: String? = null,
    val status: ShotStatus = ShotStatus.Pending,
) {
    val isReady: Boolean get() = status is ShotStatus.Ready
}

/** 分镜生成状态机。 */
@Serializable
sealed interface ShotStatus {
    /** 尚未生成 */
    @Serializable
    @SerialName("pending")
    data object Pending : ShotStatus

    /** 正在生成（出图 / 配音 / 合成任一阶段） */
    @Serializable
    @SerialName("generating")
    data class Generating(val stage: Stage) : ShotStatus

    /** 已完成，产物就绪 */
    @Serializable
    @SerialName("ready")
    data class Ready(
        val imagePath: String,
        val audioPath: String? = null,
        val clipPath: String? = null,
    ) : ShotStatus

    /** 生成失败，保留原因便于重试 */
    @Serializable
    @SerialName("failed")
    data class Failed(val reason: String, val retryable: Boolean = true) : ShotStatus

    enum class Stage { IMAGE, AUDIO, COMPOSE }
}

/** 一部漫剧的分镜表。 */
@Serializable
data class Storyboard(
    val title: String,
    val shots: List<Shot> = emptyList(),
    /** 全局风格提示词，拼进每个分镜的 imagePrompt */
    val globalStyle: String = "",
    /** 字幕样式预设 */
    val subtitleStyle: String = "default",
) {
    /** 总时长（秒） */
    val totalDurationSec: Int get() = shots.sumOf { it.durationSec }

    /** 已完成的镜头数 */
    val readyCount: Int get() = shots.count { it.isReady }

    /** 是否全部生成完毕 */
    val isComplete: Boolean get() = shots.isNotEmpty() && shots.all { it.isReady }
}

/** 角色设定，用于跨镜头保持外观一致。 */
@Serializable
data class CharacterRef(
    val name: String,
    /** 外观描述，拼进每一镜的提示词 */
    val appearance: String,
    /** 锚定图路径（首镜生成后回填） */
    val anchorImage: String? = null,
)
