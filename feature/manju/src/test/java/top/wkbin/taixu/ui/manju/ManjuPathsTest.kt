package top.wkbin.taixu.ui.manju

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 宿主/guest 路径转换是漫剧合成的命门：
 * ffmpeg 在 PRoot 沙盒内执行，只认 guest 路径；出图落盘走宿主路径。
 * 两者混用会让真机上「出图成功但合成失败」。
 */
class ManjuPathsTest {

    private val paths = ManjuPaths(
        hostWorkDir = "/data/user/0/top.wkbin.taixu/files/workspace/manju",
        guestWorkDir = "/workspace/manju",
    )

    @Test
    fun `宿主路径转换为 guest 路径`() {
        assertEquals(
            "/workspace/manju/shot_0.png",
            paths.toGuest("/data/user/0/top.wkbin.taixu/files/workspace/manju/shot_0.png"),
        )
    }

    @Test
    fun `嵌套子目录同样正确转换`() {
        assertEquals(
            "/workspace/manju/sub/a.mp4",
            paths.toGuest("/data/user/0/top.wkbin.taixu/files/workspace/manju/sub/a.mp4"),
        )
    }

    @Test
    fun `工作目录之外的路径原样返回`() {
        val outside = "/tmp/other.png"
        assertEquals(outside, paths.toGuest(outside))
    }

    @Test
    fun `hostFile 拼出宿主路径`() {
        assertEquals(
            "/data/user/0/top.wkbin.taixu/files/workspace/manju/shot_3.mp4",
            paths.hostFile("shot_3.mp4"),
        )
    }

    @Test
    fun `hostFile 与 toGuest 可往返`() {
        val host = paths.hostFile("shot_9.m4a")
        assertEquals("/workspace/manju/shot_9.m4a", paths.toGuest(host))
    }

    @Test
    fun `guest 常量与 PRoot 挂载点一致`() {
        // /workspace 是 ProotMountLayout 中的 bind mount，不能随意改动
        assertEquals("/workspace/manju", ManjuPaths.GUEST_MANJU_DIR)
    }
}
