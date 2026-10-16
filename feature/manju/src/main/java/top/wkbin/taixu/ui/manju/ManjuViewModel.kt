package top.wkbin.taixu.ui.manju

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.wkbin.taixu.ui.manju.impl.FfmpegAvailability
import top.wkbin.taixu.ui.manju.impl.ImageInbox
import top.wkbin.taixu.ui.manju.impl.RuntimeFfmpegProbe

/**
 * 漫剧制作界面的状态与动作。
 *
 * 状态用单个不可变 [UiState] 承载，通过 StateFlow 暴露，
 * UI 侧用 collectAsStateWithLifecycle 订阅。
 */
class ManjuViewModel(
    private val planner: StoryboardPlanner,
    private val engine: ManjuEngine,
    private val paths: ManjuPaths,
    /** 出图配置的读取与写入；null 表示运行在无配置环境（如预览/测试）。 */
    private val imageConfig: ManjuImageConfigStore? = null,
    /** 配音配置；null 同上。 */
    private val voiceConfig: ManjuVoiceConfigStore? = null,
    /** FFmpeg 可用性探测；null 表示不探测（预览/测试）。 */
    private val ffmpegProbe: RuntimeFfmpegProbe? = null,
) : ViewModel() {

    data class UiState(
        val script: String = "",
        val globalStyle: String = "",
        val storyboard: Storyboard? = null,
        val isPlanning: Boolean = false,
        val isGenerating: Boolean = false,
        val exportedPath: String? = null,
        val message: String? = null,
        /** 出图供应商配置（Key 不回显明文，仅给出是否已配置）。 */
        val imageBaseUrl: String = "",
        val imageModel: String = "",
        val imageKeyConfigured: Boolean = false,
        val showImageConfig: Boolean = false,
        /** 出图收件目录（宿主路径），提示用户把图放这里。 */
        val inboxPath: String = "",
        /** 收件箱中已就绪的镜头号。 */
        val inboxShots: Set<Int> = emptySet(),
        /** 配音供应商配置（Key 同样不回显）。 */
        val voiceBaseUrl: String = "",
        val voiceModel: String = "",
        val voiceName: String = "",
        val voiceKeyConfigured: Boolean = false,
        val showVoiceConfig: Boolean = false,
        /** FFmpeg 可用性：null 表示尚未探测。 */
        val ffmpeg: FfmpegAvailability? = null,
    ) {
        /** 整体进度，用于进度条 */
        val progress: Float
            get() {
                val sb = storyboard ?: return 0f
                return if (sb.shots.isEmpty()) 0f else sb.readyCount.toFloat() / sb.shots.size
            }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(inboxPath = paths.hostInboxDir.absolutePath) }
        viewModelScope.launch {
            voiceConfig?.let { store ->
                val cfg = store.current()
                _uiState.update {
                    it.copy(
                        voiceBaseUrl = cfg.baseUrl,
                        voiceModel = cfg.model,
                        voiceName = cfg.voice,
                        voiceKeyConfigured = cfg.apiKey.isNotBlank(),
                    )
                }
            }
        }
        refreshFfmpeg()
        viewModelScope.launch {
            imageConfig?.let { store ->
                store.current().let { cfg ->
                    _uiState.update {
                        it.copy(
                            imageBaseUrl = cfg.baseUrl,
                            imageModel = cfg.model,
                            imageKeyConfigured = cfg.apiKey.isNotBlank(),
                        )
                    }
                }
            }
        }
    }

    fun onToggleImageConfig() = _uiState.update { it.copy(showImageConfig = !it.showImageConfig) }

    /** 重新扫描收件箱：用户手动放图后点一下，即可看到哪些镜头已就绪。 */
    fun refreshInbox() {
        viewModelScope.launch {
            val dir = paths.hostInboxDir
            val found = ImageInbox.availableIndices(dir)
            _uiState.update {
                it.copy(
                    inboxPath = dir.absolutePath,
                    inboxShots = found,
                    message = if (found.isEmpty()) {
                        "收件目录里还没有图"
                    } else {
                        "已就绪 ${found.size} 个镜头：${found.sorted().joinToString(", ") { i -> "shot_$i" }}"
                    },
                )
            }
        }
    }

    /** 保存出图配置。[apiKey] 留空表示沿用已保存的 Key（避免明文回显后再覆盖为空）。 */
    fun onSaveImageConfig(baseUrl: String, model: String, apiKey: String) {
        val store = imageConfig ?: return
        viewModelScope.launch {
            runCatching {
                val existing = store.current()
                store.save(
                    ManjuImageEndpointConfig(
                        baseUrl = baseUrl.ifBlank { existing.baseUrl },
                        model = model.ifBlank { existing.model },
                        apiKey = apiKey.ifBlank { existing.apiKey },
                    )
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        imageBaseUrl = baseUrl.ifBlank { it.imageBaseUrl },
                        imageModel = model.ifBlank { it.imageModel },
                        imageKeyConfigured = true,
                        showImageConfig = false,
                        message = "出图配置已保存",
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(message = "保存失败：${e.message}") }
            }
        }
    }

    fun onToggleVoiceConfig() = _uiState.update { it.copy(showVoiceConfig = !it.showVoiceConfig) }

    /** 重新探测 FFmpeg：用户装完后点一下即可刷新状态。 */
    fun refreshFfmpeg() {
        viewModelScope.launch {
            val probe = ffmpegProbe ?: return@launch
            _uiState.update { it.copy(ffmpeg = probe.probe()) }
        }
    }

    /** 保存配音配置。[apiKey] 留空表示沿用已保存的 Key。 */
    fun onSaveVoiceConfig(baseUrl: String, model: String, voice: String, apiKey: String) {
        val store = voiceConfig ?: return
        viewModelScope.launch {
            runCatching {
                val existing = store.current()
                store.save(
                    ManjuVoiceEndpointConfig(
                        baseUrl = baseUrl.ifBlank { existing.baseUrl },
                        model = model.ifBlank { existing.model },
                        voice = voice.ifBlank { existing.voice },
                        apiKey = apiKey.ifBlank { existing.apiKey },
                    )
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        voiceBaseUrl = baseUrl.ifBlank { it.voiceBaseUrl },
                        voiceModel = model.ifBlank { it.voiceModel },
                        voiceName = voice.ifBlank { it.voiceName },
                        voiceKeyConfigured = true,
                        showVoiceConfig = false,
                        message = "配音配置已保存",
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(message = "保存失败：${e.message}") }
            }
        }
    }

    fun onScriptChange(value: String) = _uiState.update { it.copy(script = value) }

    fun onStyleChange(value: String) = _uiState.update { it.copy(globalStyle = value) }

    /** 拆解剧本为分镜。 */
    fun planStoryboard() {
        val state = _uiState.value
        if (state.script.isBlank()) {
            _uiState.update { it.copy(message = "请先输入剧本") }
            return
        }
        _uiState.update { it.copy(isPlanning = true, message = null, exportedPath = null) }
        viewModelScope.launch {
            planner.plan(state.script, state.globalStyle)
                .onSuccess { sb ->
                    _uiState.update {
                        it.copy(isPlanning = false, storyboard = sb, message = "已拆解 ${sb.shots.size} 个镜头")
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isPlanning = false, message = "拆解失败：${e.message}") }
                }
        }
    }

    /** 生成所有镜头。 */
    fun generateAll() {
        val sb = _uiState.value.storyboard ?: return
        if (_uiState.value.isGenerating) return
        _uiState.update { it.copy(isGenerating = true, message = null) }
        viewModelScope.launch {
            val updated = engine.generateShots(sb) { shot ->
                // 每镜完成即刷新，用户能看到逐镜推进
                _uiState.update { st ->
                    val cur = st.storyboard ?: return@update st
                    st.copy(storyboard = cur.copy(shots = cur.shots.map { if (it.index == shot.index) shot else it }))
                }
            }
            val failed = updated.shots.count { it.status is ShotStatus.Failed }
            _uiState.update {
                it.copy(
                    isGenerating = false,
                    storyboard = updated,
                    message = if (failed > 0) "完成，${failed} 个镜头失败可重试" else "全部镜头生成完成",
                )
            }
        }
    }

    /** 重试单个镜头。 */
    fun retryShot(index: Int) {
        val sb = _uiState.value.storyboard ?: return
        val shot = sb.shots.find { it.index == index } ?: return
        _uiState.update { st ->
            st.copy(storyboard = sb.copy(shots = sb.shots.map { if (it.index == index) it.copy(status = ShotStatus.Pending) else it }))
        }
        viewModelScope.launch {
            engine.generateShots(sb.copy(shots = listOf(shot.copy(status = ShotStatus.Pending)))) { updated ->
                _uiState.update { st ->
                    val cur = st.storyboard ?: return@update st
                    st.copy(storyboard = cur.copy(shots = cur.shots.map { if (it.index == updated.index) updated else it }))
                }
            }
        }
    }

    /** 导出整片。 */
    fun export() {
        val sb = _uiState.value.storyboard ?: return
        viewModelScope.launch {
            val out = paths.hostFile("manju_export.mp4")
            engine.export(sb, out)
                .onSuccess { _uiState.update { st -> st.copy(exportedPath = it, message = "导出成功") } }
                .onFailure { e -> _uiState.update { st -> st.copy(message = "导出失败：${e.message}") } }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
}
