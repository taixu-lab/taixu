package top.wkbin.taixu.ui.manju

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.wkbin.taixu.ui.manju.impl.FfmpegAvailability
import top.wkbin.taixu.ui.manju.impl.FfmpegProbe

/**
 * FFmpeg 探测结果解析单测。
 *
 * 不同发行版的 `command -v` 输出形态各异（路径/空/多行），
 * 误判会让用户去装一个已经装好的东西，必须覆盖验证。
 */
class FfmpegProbeTest {

    @Test
    fun `退出码非零视为缺失`() {
        assertEquals(FfmpegAvailability.Missing, FfmpegProbe.parse(1, ""))
    }

    @Test
    fun `标准路径输出视为可用`() {
        val r = FfmpegProbe.parse(0, "/usr/bin/ffmpeg\n")
        assertEquals(FfmpegAvailability.Available("/usr/bin/ffmpeg"), r)
    }

    @Test
    fun `去除首尾空白`() {
        val r = FfmpegProbe.parse(0, "  /usr/local/bin/ffmpeg  \n")
        assertEquals(FfmpegAvailability.Available("/usr/local/bin/ffmpeg"), r)
    }

    @Test
    fun `多行输出取第一行非空`() {
        val r = FfmpegProbe.parse(0, "\n/usr/bin/ffmpeg\n/usr/bin/ffplay\n")
        assertEquals(FfmpegAvailability.Available("/usr/bin/ffmpeg"), r)
    }

    @Test
    fun `退出码为零但输出为空仍视为缺失`() {
        assertEquals(FfmpegAvailability.Missing, FfmpegProbe.parse(0, ""))
        assertEquals(FfmpegAvailability.Missing, FfmpegProbe.parse(0, "   \n"))
    }

    @Test
    fun `安装提示包含可执行命令`() {
        assertTrue(FfmpegProbe.INSTALL_HINT.contains("apt install"))
        assertTrue(FfmpegProbe.INSTALL_HINT.contains("ffmpeg"))
    }

    @Test
    fun `探测命令为静默判断`() {
        assertTrue(FfmpegProbe.PROBE_COMMAND.startsWith("command -v"))
    }
}
