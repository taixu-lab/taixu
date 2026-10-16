package top.wkbin.taixu.ui.manju

/**
 * 漫剧产物的路径契约。
 *
 * 这里必须区分两套路径，混用会导致真机必然失败：
 *  - [hostWorkDir]：宿主（Android 应用）侧路径，出图/配音写文件走它；
 *  - [guestWorkDir]：PRoot 沙盒内路径，ffmpeg 在沙盒里执行，只认它。
 *
 * 二者指向同一份数据（`/workspace` 是 PRoot bind mount，宿主与 guest 共享），
 * 但字符串不同：宿主是 `<filesDir>/workspace/...`，guest 是 `/workspace/...`。
 */
data class ManjuPaths(
    val hostWorkDir: String,
    val guestWorkDir: String,
) {
    /** 宿主路径 → guest 路径；不在工作目录内则原样返回。 */
    fun toGuest(hostPath: String): String =
        if (hostPath.startsWith(hostWorkDir)) {
            guestWorkDir + hostPath.removePrefix(hostWorkDir)
        } else {
            hostPath
        }

    fun hostFile(name: String): String = "$hostWorkDir/$name"

    /**
     * 出图收件箱（宿主路径）。
     *
     * 手动出图的工作方式：用任意工具生成图后，按 `shot_<序号>.png`
     * 命名放进这里，漫剧流程会直接取用，不再调 API。
     */
    val hostInboxDir: java.io.File get() = java.io.File(hostWorkDir, RELATIVE_INBOX_DIR)

    companion object {
        /** guest 侧漫剧产物目录（位于沙盒 /workspace 下）。 */
        const val GUEST_MANJU_DIR = "/workspace/manju"

        /** 宿主侧相对 workspace 的子目录名。 */
        const val RELATIVE_DIR = "manju"

        /** 出图收件箱子目录名。 */
        const val RELATIVE_INBOX_DIR = "inbox"
    }
}

/**
 * 出图配置的数据载体（与 core:datastore 的持久化模型解耦）。
 */
data class ManjuImageEndpointConfig(
    val baseUrl: String,
    val model: String,
    val apiKey: String,
)

/**
 * 出图配置的读写抽象。
 *
 * 让 ViewModel 与 Engine 都不直接依赖 core:datastore，
 * 测试里可以用内存实现替换，不需要 Android 环境。
 */
interface ManjuImageConfigStore {
    suspend fun current(): ManjuImageEndpointConfig
    suspend fun save(value: ManjuImageEndpointConfig)
}

/** 配音配置的数据载体（与持久化模型解耦）。 */
data class ManjuVoiceEndpointConfig(
    val baseUrl: String,
    val model: String,
    val voice: String,
    val apiKey: String,
)

/** 配音配置的读写抽象。 */
interface ManjuVoiceConfigStore {
    suspend fun current(): ManjuVoiceEndpointConfig
    suspend fun save(value: ManjuVoiceEndpointConfig)
}

/** 传给 TTS 实现的最小配置视图。 */
data class VoiceEndpointConfig(
    val baseUrl: String,
    val model: String,
    val voice: String,
    val apiKey: String,
) {
    val ready: Boolean get() = baseUrl.isNotBlank() && model.isNotBlank() && apiKey.isNotBlank()
}
