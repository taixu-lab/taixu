package top.wkbin.taixu.ui.manju

/**
 * 出图能力抽象。
 *
 * 实现可接任意文生图 API（即梦 / Seedream / gpt-image 等）。
 * [referenceImage] 用于角色一致性：把上一镜或角色锚定图作为参考。
 */
interface ImageGenerator {
    suspend fun generate(
        prompt: String,
        referenceImage: String? = null,
        outputPath: String,
    ): Result<String>
}

/** 配音能力抽象（TTS / 音色克隆）。 */
interface VoiceSynthesizer {
    suspend fun synthesize(
        text: String,
        outputPath: String,
        voiceId: String? = null,
    ): Result<String>
}

/**
 * 视频合成能力抽象。
 *
 * 关键点：这一步**不需要任何 AI 服务**，纯本地 FFmpeg 即可完成
 * 图片 + 音频 + 字幕 → 视频片段 → 拼接成片。
 * 在 TaiXu 沙盒里甚至可以直接调用 PRoot 内的系统 ffmpeg。
 */
interface VideoComposer {
    /** 单镜合成：静图 + 配音 → 带运镜与字幕的片段 */
    suspend fun composeShot(shot: Shot, imagePath: String, audioPath: String?, outputPath: String): Result<String>

    /** 多镜拼接：把片段合成完整漫剧 */
    suspend fun concatShots(clipPaths: List<String>, outputPath: String): Result<String>
}

/**
 * FFmpeg 命令构造器。
 *
 * 单独抽出便于单元测试：命令拼错是这类项目最常见的翻车点，
 * 而命令构造是纯函数，可以完全离线验证。
 */
object FfmpegCommands {

    /**
     * 构造单镜合成命令。
     *
     * - 静图 → 视频：`-loop 1` + `-t 时长`
     * - 运镜：`zoompan` 滤镜做缓慢推近（Ken Burns 效果）
     * - 音频：`-shortest` 保证以音频长度为准
     * - 无音频时：`-an`
     */
    fun composeShot(
        imagePath: String,
        audioPath: String?,
        durationSec: Int,
        outputPath: String,
        fps: Int = 30,
        width: Int = 1080,
        height: Int = 1920,
    ): List<String> = buildList {
        add("-y")
        add("-loop"); add("1")
        add("-i"); add(imagePath)
        if (audioPath != null) {
            add("-i"); add(audioPath)
        }
        // zoompan 需要先把图放大，避免推近时出现边缘抖动
        val frames = durationSec * fps
        val vf = "scale=$width:$height:force_original_aspect_ratio=increase," +
            "crop=$width:$height," +
            "zoompan=z='min(zoom+0.0005,1.15)':d=$frames:s=${width}x$height:fps=$fps," +
            "format=yuv420p"
        add("-vf"); add(vf)
        add("-t"); add(durationSec.toString())
        add("-r"); add(fps.toString())
        add("-c:v"); add("libx264")
        add("-preset"); add("medium")
        add("-pix_fmt"); add("yuv420p")
        if (audioPath != null) {
            add("-c:a"); add("aac")
            add("-b:a"); add("192k")
            add("-shortest")
        } else {
            add("-an")
        }
        add(outputPath)
    }

    /**
     * 构造多镜拼接命令。
     *
     * 用 concat demuxer + 重编码，避免 stream copy 在参数不一致时报错。
     */
    fun concatShots(listFilePath: String, outputPath: String): List<String> = listOf(
        "-y",
        "-f", "concat",
        "-safe", "0",
        "-i", listFilePath,
        "-c:v", "libx264",
        "-preset", "medium",
        "-c:a", "aac",
        "-pix_fmt", "yuv420p",
        outputPath,
    )

    /** 生成 concat demuxer 所需的清单文件内容。 */
    fun concatListFile(clipPaths: List<String>): String =
        clipPaths.joinToString("\n") { "file '${it.replace("'", "'\\''")}'" }
}
