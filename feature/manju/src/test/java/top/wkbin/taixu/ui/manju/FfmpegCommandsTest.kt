package top.wkbin.taixu.ui.manju

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegCommandsTest {

    @Test
    fun `带音频时包含音频输入与 aac 编码`() {
        val args = FfmpegCommands.composeShot(
            imagePath = "/tmp/a.png",
            audioPath = "/tmp/a.m4a",
            durationSec = 5,
            outputPath = "/tmp/a.mp4",
        )
        assertTrue(args.contains("-loop"))
        assertTrue(args.contains("/tmp/a.m4a"))
        assertTrue(args.contains("aac"))
        assertTrue(args.contains("-shortest"))
        assertTrue(args.last() == "/tmp/a.mp4")
    }

    @Test
    fun `无音频时禁用音频流`() {
        val args = FfmpegCommands.composeShot(
            imagePath = "/tmp/a.png",
            audioPath = null,
            durationSec = 5,
            outputPath = "/tmp/a.mp4",
        )
        assertTrue(args.contains("-an"))
        assertTrue(!args.contains("-shortest"))
    }

    @Test
    fun `zoompan 帧数按时长与帧率计算`() {
        val args = FfmpegCommands.composeShot(
            imagePath = "/tmp/a.png",
            audioPath = null,
            durationSec = 4,
            outputPath = "/tmp/a.mp4",
            fps = 25,
        )
        val vf = args[args.indexOf("-vf") + 1]
        // 4 秒 * 25fps = 100 帧
        assertTrue(vf.contains("d=100"))
        assertTrue(vf.contains("fps=25"))
    }

    @Test
    fun `时长参数被正确写入`() {
        val args = FfmpegCommands.composeShot("/i.png", null, 7, "/o.mp4")
        assertEquals("7", args[args.indexOf("-t") + 1])
    }

    @Test
    fun `输出像素格式固定 yuv420p 保证兼容性`() {
        val args = FfmpegCommands.composeShot("/i.png", null, 5, "/o.mp4")
        assertTrue(args.contains("yuv420p"))
    }

    @Test
    fun `拼接命令使用 concat demuxer`() {
        val args = FfmpegCommands.concatShots("/tmp/list.txt", "/tmp/out.mp4")
        assertTrue(args.contains("-f"))
        assertTrue(args.contains("concat"))
        assertTrue(args.contains("-safe"))
        assertEquals("/tmp/list.txt", args[args.indexOf("-i") + 1])
    }

    @Test
    fun `拼接清单文件格式正确`() {
        val content = FfmpegCommands.concatListFile(listOf("/tmp/1.mp4", "/tmp/2.mp4"))
        assertEquals("file '/tmp/1.mp4'\nfile '/tmp/2.mp4'", content)
    }

    @Test
    fun `拼接清单转义单引号防止注入`() {
        val content = FfmpegCommands.concatListFile(listOf("/tmp/it's.mp4"))
        assertTrue(content.contains("'\\''"))
    }
}
