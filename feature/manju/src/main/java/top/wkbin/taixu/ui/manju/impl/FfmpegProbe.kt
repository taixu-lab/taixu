package top.wkbin.taixu.ui.manju.impl

import top.wkbin.taixu.runtime.LinuxRuntime
import top.wkbin.taixu.runtime.shell.ShellCommand

/**
 * FFmpeg 可用性探测。
 *
 * 合成环节完全依赖沙盒内的系统 ffmpeg，缺失时用户会在「导出」时
 * 才看到一条晦涩的执行失败。提前探测并给出安装命令，能把
 * 「莫名其妙失败」变成「照着做一步即可」。
 */
object FfmpegProbe {

    /** 探测命令：静默判断可执行文件是否存在。 */
    const val PROBE_COMMAND = "command -v ffmpeg"

    /** 面向用户的安装提示。 */
    const val INSTALL_HINT = "沙盒内缺少 ffmpeg，请在太墟终端执行：apt update && apt install -y ffmpeg"

    /**
     * 解析探测结果。
     *
     * 抽成纯函数：不同发行版的输出格式、以及 `command -v` 的
     * 各种返回形态（路径 / 空 / 报错）都需要被覆盖验证。
     */
    fun parse(exitCode: Int, stdout: String): FfmpegAvailability {
        if (exitCode != 0) return FfmpegAvailability.Missing
        val path = stdout.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return if (path.isBlank()) FfmpegAvailability.Missing else FfmpegAvailability.Available(path)
    }
}

/** ffmpeg 探测结果。 */
sealed interface FfmpegAvailability {
    data class Available(val path: String) : FfmpegAvailability
    data object Missing : FfmpegAvailability
    /** 探测本身失败（沙盒未就绪等），与「已确认缺失」区分开。 */
    data class Unknown(val reason: String) : FfmpegAvailability
}

/**
 * 通过沙盒执行探测。
 *
 * 探测失败（如沙盒未启动）归为 [FfmpegAvailability.Unknown]，
 * 而不是谎报「缺失」——否则会误导用户去装一个可能已经装好的东西。
 */
class RuntimeFfmpegProbe(private val linuxRuntime: Lazy<LinuxRuntime>) {

    suspend fun probe(): FfmpegAvailability = runCatching {
        val result = linuxRuntime.value.execute(ShellCommand(commandLine = FfmpegProbe.PROBE_COMMAND))
        FfmpegProbe.parse(result.exitCode, result.stdout)
    }.getOrElse { e ->
        FfmpegAvailability.Unknown(e.message ?: "沙盒不可用")
    }
}
