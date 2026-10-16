package top.wkbin.taixu.ui.manju

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import top.wkbin.taixu.ui.manju.impl.ImageInbox
import java.io.File

/**
 * 收件箱取图逻辑单测。
 *
 * 命名规则（shot_<序号>.<扩展名>）与扩展名优先级是「手动出图」
 * 整条链路最容易出错的地方，必须能离线验证。
 */
class ImageInboxTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val out = "/work/shot_3.png"

    @Test
    fun `由输出路径取主干`() {
        assertEquals("shot_3", ImageInbox.stemOf(out))
    }

    @Test
    fun `无扩展名时主干为文件名本身`() {
        assertEquals("shot_9", ImageInbox.stemOf("/work/shot_9"))
    }

    @Test
    fun `命中 png`() {
        val dir = tmp.newFolder("inbox")
        File(dir, "shot_3.png").writeBytes(byteArrayOf(1, 2, 3))
        assertNotNull(ImageInbox.find(dir, out))
    }

    @Test
    fun `png 缺失时回退到 jpg`() {
        val dir = tmp.newFolder("inbox")
        File(dir, "shot_3.jpg").writeBytes(byteArrayOf(1, 2, 3))
        assertEquals("shot_3.jpg", ImageInbox.find(dir, out)?.name)
    }

    @Test
    fun `扩展名优先级 png 高于 jpg`() {
        val dir = tmp.newFolder("inbox")
        File(dir, "shot_3.png").writeBytes(byteArrayOf(1))
        File(dir, "shot_3.jpg").writeBytes(byteArrayOf(2))
        assertEquals("shot_3.png", ImageInbox.find(dir, out)?.name)
    }

    @Test
    fun `空文件不算命中`() {
        val dir = tmp.newFolder("inbox")
        File(dir, "shot_3.png").createNewFile()
        assertNull(ImageInbox.find(dir, out))
    }

    @Test
    fun `目录不存在返回 null 而不抛异常`() {
        assertNull(ImageInbox.find(File(tmp.root, "nope"), out))
    }

    @Test
    fun `不误匹配其它镜头号`() {
        val dir = tmp.newFolder("inbox")
        File(dir, "shot_30.png").writeBytes(byteArrayOf(1))
        assertNull(ImageInbox.find(dir, out))
    }

    @Test
    fun `列出可用镜头号`() {
        val dir = tmp.newFolder("inbox")
        File(dir, "shot_0.png").writeBytes(byteArrayOf(1))
        File(dir, "shot_2.webp").writeBytes(byteArrayOf(1))
        File(dir, "notes.txt").writeText("x")
        assertEquals(setOf(0, 2), ImageInbox.availableIndices(dir))
    }

    @Test
    fun `可用镜头号忽略空文件`() {
        val dir = tmp.newFolder("inbox")
        File(dir, "shot_1.png").createNewFile()
        assertEquals(emptySet<Int>(), ImageInbox.availableIndices(dir))
    }

    @Test
    fun `目录不存在时可用镜头号为空集`() {
        assertEquals(emptySet<Int>(), ImageInbox.availableIndices(File(tmp.root, "nope")))
    }
}
