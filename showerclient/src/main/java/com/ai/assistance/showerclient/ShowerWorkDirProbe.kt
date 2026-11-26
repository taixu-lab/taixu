package com.ai.assistance.showerclient

internal sealed class ShowerWorkDirStatus {
    data class Ready(val path: String) : ShowerWorkDirStatus()
    data class Unavailable(val message: String) : ShowerWorkDirStatus()
}

internal sealed class ShowerWorkDirStep {
    data class Use(val path: String) : ShowerWorkDirStep()
    data object TryNext : ShowerWorkDirStep()
    data class ChannelUnavailable(val detail: String) : ShowerWorkDirStep()
}

/**
 * 目录探测脚本以 0 退出、且不回显 [SHOWER_WORKDIR_PROBE_MARK] 时，目录才是不可写。
 * 负的 exitCode 表示特权通道根本没跑完命令（UserService 启动超时、未授权、PRoot）。
 */
internal fun interpretShowerWorkDirProbe(dir: String, result: ShellCommandResult): ShowerWorkDirStep {
    if (result.stdout.contains(SHOWER_WORKDIR_PROBE_MARK)) return ShowerWorkDirStep.Use(dir)
    if (result.exitCode < 0) {
        val detail = result.stderr.trim().ifBlank { "exit=${result.exitCode}" }
        return ShowerWorkDirStep.ChannelUnavailable(detail)
    }
    return ShowerWorkDirStep.TryNext
}

internal fun showerWorkDirProbeCommand(dir: String): String {
    val probeFile = "$dir/.shower_probe"
    return "mkdir -p $dir 2>/dev/null; " +
        "if touch $probeFile 2>/dev/null; then rm -f $probeFile; echo $SHOWER_WORKDIR_PROBE_MARK; fi"
}

internal fun showerWorkDirFailureMessage(candidates: List<String>, channelDetail: String?): String {
    if (channelDetail == null) return "Shower 工作目录不可写，已尝试 $candidates"
    val clipped = channelDetail.take(CHANNEL_DETAIL_LIMIT)
    val timedOut = clipped.contains("超时") || clipped.contains("timed out", ignoreCase = true)
    val reason = if (timedOut) "UserService 启动超时或 shell 命令超时" else "shell 通道未执行探测"
    return "Shower 工作目录探测失败（$reason，不是目录不可写）：$clipped"
}

/**
 * 按候选顺序探测。命令回显成功标记即采用；通道没跑完（负 exitCode）则停止，
 * 避免 UserService 超时在每个候选上各等一遍。
 * 至少有一次探测真正跑完时，失败仍报告目录不可写。
 */
internal suspend fun selectShowerWorkDir(
    candidates: List<String>,
    onAttempt: (String, ShellCommandResult, ShowerWorkDirStep) -> Unit = { _, _, _ -> },
    probe: suspend (String) -> ShellCommandResult,
): ShowerWorkDirStatus {
    var finishedProbe = false
    var channelDetail: String? = null
    for (dir in candidates) {
        val result = probe(dir)
        val step = interpretShowerWorkDirProbe(dir, result)
        onAttempt(dir, result, step)
        when (step) {
            is ShowerWorkDirStep.Use -> return ShowerWorkDirStatus.Ready(step.path)
            ShowerWorkDirStep.TryNext -> finishedProbe = true
            is ShowerWorkDirStep.ChannelUnavailable -> {
                channelDetail = step.detail
                break
            }
        }
    }
    val detail = if (finishedProbe) null else channelDetail
    return ShowerWorkDirStatus.Unavailable(showerWorkDirFailureMessage(candidates, detail))
}

private const val CHANNEL_DETAIL_LIMIT = 500
