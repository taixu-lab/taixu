package com.ai.assistance.showerclient

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowerWorkDirProbeTest {
    private val candidates = listOf("/data/local/tmp", "/data/data/com.android.shell/files")

    @Test fun usesTheFirstDirectoryThatPrintsTheProbeMark() = runBlocking {
        var calls = 0
        val status = selectShowerWorkDir(candidates) {
            calls++
            ShellCommandResult(true, "prefix $SHOWER_WORKDIR_PROBE_MARK\n", "", 0)
        }
        assertEquals(1, calls)
        assertEquals("/data/local/tmp", (status as ShowerWorkDirStatus.Ready).path)
    }

    @Test fun fallsThroughAFinishedUnwritableProbe() = runBlocking {
        val status = selectShowerWorkDir(candidates) { dir ->
            if (dir == candidates[0]) ShellCommandResult(true, "", "", 0)
            else ShellCommandResult(true, SHOWER_WORKDIR_PROBE_MARK, "", 0)
        }
        assertEquals(candidates[1], (status as ShowerWorkDirStatus.Ready).path)
    }

    @Test fun reportsUnwritableOnlyAfterProbesActuallyFinish() = runBlocking {
        var calls = 0
        val status = selectShowerWorkDir(candidates) {
            calls++
            ShellCommandResult(false, "", "touch: permission denied", 1)
        }
        assertEquals(candidates.size, calls)
        val message = (status as ShowerWorkDirStatus.Unavailable).message
        assertEquals("Shower 工作目录不可写，已尝试 $candidates", message)
        assertFalse(message.contains("不是目录不可写"))
    }

    @Test fun userServiceStartTimeoutIsNotReportedAsUnwritable() = runBlocking {
        var calls = 0
        val stderr = "Shizuku UserService 执行失败: ${shizukuStyleTimeout()}"
        val status = selectShowerWorkDir(candidates) {
            calls++
            ShellCommandResult(false, "", stderr, -1)
        }
        assertEquals(1, calls)
        val message = (status as ShowerWorkDirStatus.Unavailable).message
        assertTrue(message.contains("UserService 启动超时"))
        assertTrue(message.contains("不是目录不可写"))
        assertTrue(message.contains(shizukuStyleTimeout()))
        assertFalse(message.contains("工作目录不可写"))
    }

    @Test fun englishBinderTimeoutIsNotReportedAsUnwritable() = runBlocking {
        val status = selectShowerWorkDir(candidates) {
            ShellCommandResult(false, "", "Timed out waiting for 20000 ms", -1)
        }
        val message = (status as ShowerWorkDirStatus.Unavailable).message
        assertTrue(message.contains("UserService 启动超时或 shell 命令超时"))
        assertFalse(message.contains("工作目录不可写"))
    }

    @Test fun channelFailureWithoutTimeoutStaysDistinctFromUnwritable() = runBlocking {
        val status = selectShowerWorkDir(candidates) {
            ShellCommandResult(false, "", "Shizuku 未授权。请在 Shizuku App 中授予太墟访问权限。", -1)
        }
        val message = (status as ShowerWorkDirStatus.Unavailable).message
        assertTrue(message.contains("shell 通道未执行探测"))
        assertTrue(message.contains("Shizuku 未授权"))
        assertFalse(message.contains("工作目录不可写"))
    }

    @Test fun blankChannelStderrStillNamesTheExitCode() = runBlocking {
        val status = selectShowerWorkDir(candidates) {
            ShellCommandResult(false, "", "  ", -1)
        }
        val message = (status as ShowerWorkDirStatus.Unavailable).message
        assertTrue(message.contains("exit=-1"))
        assertFalse(message.contains("工作目录不可写"))
    }

    @Test fun finishedUnwritableProbeWinsOverALaterChannelFailure() = runBlocking {
        var calls = 0
        val status = selectShowerWorkDir(candidates + "/unused") { dir ->
            calls++
            when (dir) {
                candidates[0] -> ShellCommandResult(true, "", "", 0)
                candidates[1] -> ShellCommandResult(false, "", "Shizuku UserService 启动超时（20000ms）", -1)
                else -> error("通道失败后不应继续探测 $dir")
            }
        }
        assertEquals(2, calls)
        assertTrue((status as ShowerWorkDirStatus.Unavailable).message.contains("工作目录不可写"))
    }

    @Test fun clipsChannelDetail() = runBlocking {
        val detail = "超时" + "x".repeat(600)
        val status = selectShowerWorkDir(listOf("/data/local/tmp")) {
            ShellCommandResult(false, "", detail, -1)
        }
        val message = (status as ShowerWorkDirStatus.Unavailable).message
        val clipped = detail.take(500)
        assertTrue(message.contains("UserService 启动超时"))
        assertTrue(message.endsWith(clipped))
        assertFalse(message.contains(detail))
    }

    @Test fun probeCommandWritesAndClearsAMarkerInsideTheCandidate() {
        val command = showerWorkDirProbeCommand("/data/local/tmp")
        assertTrue(command.contains("touch /data/local/tmp/.shower_probe"))
        assertTrue(command.contains("rm -f /data/local/tmp/.shower_probe"))
        assertTrue(command.contains("echo $SHOWER_WORKDIR_PROBE_MARK"))
        assertFalse(command.contains("com.android.shell"))
    }

    private fun shizukuStyleTimeout(): String = "Shizuku UserService 启动超时（20000ms）"
}
