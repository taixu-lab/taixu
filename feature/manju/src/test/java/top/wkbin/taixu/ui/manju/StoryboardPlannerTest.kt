package top.wkbin.taixu.ui.manju

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryboardPlannerTest {

    private class FakeLlm(
        private val decomposeReply: String,
        private val title: String = "测试漫剧",
    ) : StoryboardLlm {
        var lastPrompt: String? = null
        override suspend fun decompose(prompt: String): String {
            lastPrompt = prompt
            return decomposeReply
        }
        override suspend fun suggestTitle(script: String): String = title
    }

    @Test
    fun `解析标准 JSON 数组`() = runTest {
        val planner = StoryboardPlanner(FakeLlm("""[{"imagePrompt":"少年推门","camera":"中景","dialogue":"有人吗","durationSec":5}]"""))
        val sb = planner.plan("剧本").getOrThrow()
        assertEquals(1, sb.shots.size)
        assertEquals("少年推门", sb.shots[0].imagePrompt)
        assertEquals(5, sb.shots[0].durationSec)
        assertEquals(0, sb.shots[0].index)
    }

    @Test
    fun `容错 markdown 代码块包裹`() = runTest {
        val reply = "好的，结果如下：\n```json\n[{\"imagePrompt\":\"雨夜街道\",\"durationSec\":4}]\n```\n以上。"
        val planner = StoryboardPlanner(FakeLlm(reply))
        val sb = planner.plan("剧本").getOrThrow()
        assertEquals(1, sb.shots.size)
        assertEquals("雨夜街道", sb.shots[0].imagePrompt)
    }

    @Test
    fun `容错 snake_case 字段名`() = runTest {
        val planner = StoryboardPlanner(FakeLlm("""[{"image_prompt":"街角","duration_sec":3}]"""))
        val sb = planner.plan("剧本").getOrThrow()
        assertEquals("街角", sb.shots[0].imagePrompt)
    }

    @Test
    fun `时长越界被收敛到合法区间`() = runTest {
        val planner = StoryboardPlanner(FakeLlm("""[{"imagePrompt":"A","durationSec":999},{"imagePrompt":"B","durationSec":0}]"""))
        val sb = planner.plan("剧本").getOrThrow()
        assertEquals(30, sb.shots[0].durationSec)
        assertEquals(5, sb.shots[1].durationSec, )
    }

    @Test
    fun `缺少 imagePrompt 的条目被丢弃`() = runTest {
        val planner = StoryboardPlanner(FakeLlm("""[{"camera":"远景"},{"imagePrompt":"有效镜头"}]"""))
        val sb = planner.plan("剧本").getOrThrow()
        assertEquals(1, sb.shots.size)
        assertEquals("有效镜头", sb.shots[0].imagePrompt)
    }

    @Test
    fun `空剧本直接失败`() = runTest {
        val planner = StoryboardPlanner(FakeLlm("[]"))
        assertTrue(planner.plan("   ").isFailure)
    }

    @Test
    fun `拆解结果为空时报错`() = runTest {
        val planner = StoryboardPlanner(FakeLlm("没有任何镜头"))
        assertTrue(planner.plan("剧本").isFailure)
    }

    @Test
    fun `风格与角色外观拼进提示词`() = runTest {
        val planner = StoryboardPlanner(FakeLlm("""[{"imagePrompt":"少年推门"}]"""))
        val sb = planner.plan(
            script = "剧本",
            globalStyle = "国漫2D",
            characters = listOf(CharacterRef(name = "少年", appearance = "银发，黑风衣")),
        ).getOrThrow()
        val prompt = sb.shots[0].imagePrompt
        assertTrue(prompt.contains("少年推门"))
        assertTrue(prompt.contains("国漫2D"))
        assertTrue(prompt.contains("银发，黑风衣"))
    }

    @Test
    fun `分镜索引按顺序重排`() = runTest {
        val planner = StoryboardPlanner(FakeLlm("""[{"imagePrompt":"A"},{"imagePrompt":"B"},{"imagePrompt":"C"}]"""))
        val sb = planner.plan("剧本").getOrThrow()
        assertEquals(listOf(0, 1, 2), sb.shots.map { it.index })
    }

    @Test
    fun `总时长与完成度统计正确`() = runTest {
        val planner = StoryboardPlanner(FakeLlm("""[{"imagePrompt":"A","durationSec":3},{"imagePrompt":"B","durationSec":4}]"""))
        val sb = planner.plan("剧本").getOrThrow()
        assertEquals(7, sb.totalDurationSec)
        assertEquals(0, sb.readyCount)
        assertFalse(sb.isComplete)
    }
}
