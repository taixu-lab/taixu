package top.wkbin.taixu.ui.manju.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.wkbin.taixu.ui.manju.ImageGenerator
import java.io.File

/**
 * 出图收件箱：按镜头号从目录里找现成的图。
 *
 * 用途：不接任何 API，用其它工具（如手动用 App 生成）出图后，
 * 按 `shot_<序号>.<扩展名>` 命名丢进收件目录，漫剧流程直接取用。
 *
 * 之所以把「找文件」抽成纯函数：命名规则和扩展名优先级是最容易
 * 出错、又最容易验证的部分，不该依赖网络或 Android 环境。
 */
object ImageInbox {

    /** 支持的图片扩展名，按优先级排列（先找到先用）。 */
    val SUPPORTED_EXTENSIONS = listOf("png", "jpg", "jpeg", "webp")

    /**
     * 由输出路径推导镜头文件名主干。
     *
     * 出图输出固定为 `shot_<index>.<ext>`（见 ManjuEngine），
     * 这里只取主干（去掉扩展名）用于在收件箱中匹配任意扩展名。
     */
    fun stemOf(outputPath: String): String =
        File(outputPath).name.substringBeforeLast('.', missingDelimiterValue = File(outputPath).name)

    /**
     * 在 [inboxDir] 中为 [outputPath] 找到对应的图。
     *
     * @return 命中的文件；未命中返回 null。
     */
    fun find(inboxDir: File, outputPath: String): File? {
        if (!inboxDir.isDirectory) return null
        val stem = stemOf(outputPath)
        return SUPPORTED_EXTENSIONS
            .asSequence()
            .map { File(inboxDir, "$stem.$it") }
            .firstOrNull { it.isFile && it.length() > 0 }
    }

    /** 列出收件箱中可用的镜头号，用于给用户提示还缺哪些。 */
    fun availableIndices(inboxDir: File): Set<Int> {
        if (!inboxDir.isDirectory) return emptySet()
        // 与 find() 保持同一判据：空文件不算可用，
        // 否则 UI 会提示「已就绪」而生成时又找不到图。
        return inboxDir.listFiles().orEmpty()
            .filter { it.isFile && it.length() > 0 && it.extension.lowercase() in SUPPORTED_EXTENSIONS }
            .mapNotNull { it.nameWithoutExtension.removePrefix("shot_").toIntOrNull() }
            .toSet()
    }
}

/**
 * 从本地目录取图，不联网。
 *
 * 适用于「出图与漫剧流程解耦」的用法：用任意工具出好图后按
 * `shot_0.png`、`shot_1.png`… 命名放进收件目录即可。
 */
class LocalFileImageGenerator(
    private val inboxDir: () -> File,
) : ImageGenerator {

    override suspend fun generate(
        prompt: String,
        referenceImage: String?,
        outputPath: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = inboxDir()
            val source = ImageInbox.find(dir, outputPath)
                ?: throw IllegalStateException(
                    "收件目录里没有 ${ImageInbox.stemOf(outputPath)} 对应的图" +
                        "（支持 ${ImageInbox.SUPPORTED_EXTENSIONS.joinToString("/")}）。" +
                        "请把图放进：${dir.absolutePath}"
                )
            val target = File(outputPath).apply { parentFile?.mkdirs() }
            source.copyTo(target, overwrite = true)
            outputPath
        }
    }
}

/**
 * 出图调度：优先用收件箱里现成的图，没有则回退到 API，都没有就给出可操作的提示。
 *
 * 这样「手动出图」与「接 API」两条路可以共存：想省钱就手动补图，
 * 配了 Key 就自动出图，不必改代码或换构建。
 */
class CompositeImageGenerator(
    private val inbox: LocalFileImageGenerator,
    private val api: ImageGenerator?,
) : ImageGenerator {

    override suspend fun generate(
        prompt: String,
        referenceImage: String?,
        outputPath: String,
    ): Result<String> {
        inbox.generate(prompt, referenceImage, outputPath).onSuccess { return Result.success(it) }
        if (api != null) {
            api.generate(prompt, referenceImage, outputPath).onSuccess { return Result.success(it) }
        }
        return Result.failure(
            IllegalStateException("出图失败：收件目录无对应图，且未配置出图服务（见漫剧页「出图配置」）"),
        )
    }
}
