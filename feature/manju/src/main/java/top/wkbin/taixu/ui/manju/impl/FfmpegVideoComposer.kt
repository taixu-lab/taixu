package top.wkbin.taixu.ui.manju.impl

import top.wkbin.taixu.runtime.LinuxRuntime
import top.wkbin.taixu.runtime.shell.ShellCommand
import top.wkbin.taixu.ui.manju.FfmpegCommands
import top.wkbin.taixu.ui.manju.Shot
import top.wkbin.taixu.ui.manju.VideoComposer

/**
 * 基于太墟 Linux 沙盒内系统 ffmpeg 的合成实现。
 *
 * 走 [LinuxRuntime] 标准契约执行命令，不额外引入 Android 端 FFmpeg 依赖——
 * 沙盒里已有 Linux 环境，复用它可以显著减小 APK 体积。
 */
class FfmpegVideoComposer(
    private val linuxRuntime: Lazy<LinuxRuntime>,
) : VideoComposer {

    override suspend fun composeShot(
        shot: Shot,
        imagePath: String,
        audioPath: String?,
        outputPath: String,
    ): Result<String> = runCatching {
        val args = FfmpegCommands.composeShot(
            imagePath = imagePath,
            audioPath = audioPath,
            durationSec = shot.durationSec,
            outputPath = outputPath,
        )
        runFfmpeg(args)
        outputPath
    }

    override suspend fun concatShots(clipPaths: List<String>, outputPath: String): Result<String> =
        runCatching {
            require(clipPaths.isNotEmpty()) { "没有可拼接的片段" }
            val listPath = outputPath.substringBeforeLast('/') + "/concat_list.txt"
            val listFile = java.io.File(listPath)
            listFile.writeText(FfmpegCommands.concatListFile(clipPaths))
            runFfmpeg(FfmpegCommands.concatShots(listPath, outputPath))
            outputPath
        }

    /** 执行 ffmpeg，非零退出即抛异常并带上 stderr，便于定位滤镜/编码错误。 */
    private suspend fun runFfmpeg(args: List<String>) {
        val cmd = buildString {
            append("ffmpeg")
            args.forEach { append(' ').append(shellQuote(it)) }
        }
        val result = linuxRuntime.value.execute(ShellCommand(commandLine = cmd))
        check(result.isSuccess) {
            "ffmpeg 执行失败(exit=${result.exitCode})：${result.stderr.takeLast(500)}"
        }
    }

    /** 单引号包裹并转义，避免路径含空格或特殊字符时被 shell 拆词。 */
    private fun shellQuote(raw: String): String = "'" + raw.replace("'", "'\\''") + "'"
}
