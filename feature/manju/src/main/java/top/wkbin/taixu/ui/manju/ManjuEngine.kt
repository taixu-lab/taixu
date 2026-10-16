package top.wkbin.taixu.ui.manju

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 漫剧生成编排引擎。
 *
 * 负责把「分镜 → 出图 → 配音 → 单镜合成 → 整片拼接」串成流水线，
 * 并处理并发限流与单镜失败隔离。
 *
 * 并发限流很重要：AI 出图/视频接口普遍有 QPS 限制，
 * 一次把 20 个镜头全发出去必然大面积 429。
 */
class ManjuEngine(
    private val imageGenerator: ImageGenerator,
    private val voiceSynthesizer: VoiceSynthesizer,
    private val videoComposer: VideoComposer,
    private val paths: ManjuPaths,
    private val maxConcurrency: Int = 3,
) {
    /**
     * 逐镜生成：出图 → 配音 → 合成。
     *
     * 单镜失败不阻断整批，失败原因写回 [ShotStatus.Failed]，便于用户针对性重试。
     */
    suspend fun generateShots(
        storyboard: Storyboard,
        onShotUpdate: (Shot) -> Unit = {},
    ): Storyboard = coroutineScope {
        val semaphore = Semaphore(maxConcurrency.coerceAtLeast(1))
        val results = storyboard.shots.map { shot ->
            async {
                semaphore.withPermit {
                    val updated = generateSingleShot(shot)
                    onShotUpdate(updated)
                    updated
                }
            }
        }.awaitAll()
        storyboard.copy(shots = results)
    }

    /** 生成单个镜头。 */
    private suspend fun generateSingleShot(shot: Shot): Shot {
        // 1. 出图（带角色一致性参考）。
        //    出图由宿主侧 HTTP/文件 IO 完成，落盘用宿主路径。
        val imagePath = paths.hostFile("shot_${shot.index}.png")
        imageGenerator.generate(shot.imagePrompt, shot.referenceImage, imagePath)
            .onFailure { return shot.copy(status = ShotStatus.Failed("出图失败：${it.message}")) }

        // 2. 配音（无台词则跳过）。
        //    配音失败不阻断整镜：降级为无音频片段，用户仍能看到画面推进。
        val audioPath = if (shot.dialogue.isNotBlank()) {
            voiceSynthesizer.synthesize(shot.dialogue, paths.hostFile("shot_${shot.index}.m4a")).getOrNull()
        } else null

        // 3. 单镜合成：ffmpeg 在沙盒内执行，所有入参必须是 guest 路径。
        val clipPath = paths.hostFile("shot_${shot.index}.mp4")
        videoComposer.composeShot(
            shot = shot,
            imagePath = paths.toGuest(imagePath),
            audioPath = audioPath?.let { paths.toGuest(it) },
            outputPath = paths.toGuest(clipPath),
        ).onFailure { return shot.copy(status = ShotStatus.Failed("合成失败：${it.message}")) }

        return shot.copy(
            status = ShotStatus.Ready(imagePath = imagePath, audioPath = audioPath, clipPath = clipPath),
            referenceImage = shot.referenceImage ?: imagePath,
        )
    }

    /**
     * 整片拼接：只拼接已就绪的镜头。
     *
     * 未就绪的镜头直接跳过，而不是让整次导出失败——
     * 用户往往想先看看已完成部分的效果。
     */
    suspend fun export(storyboard: Storyboard, outputPath: String): Result<String> {
        val clips = storyboard.shots
            .mapNotNull { (it.status as? ShotStatus.Ready)?.clipPath }
        if (clips.isEmpty()) {
            return Result.failure(IllegalStateException("没有已完成的镜头，无法导出"))
        }
        // 片段路径是宿主路径，ffmpeg 只认 guest 路径，这里统一转换。
        return videoComposer.concatShots(
            clipPaths = clips.map { paths.toGuest(it) },
            outputPath = paths.toGuest(outputPath),
        )
    }
}
