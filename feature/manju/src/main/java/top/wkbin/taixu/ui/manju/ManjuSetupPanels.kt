package top.wkbin.taixu.ui.manju

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.wkbin.taixu.ui.components.RuntimeButton
import top.wkbin.taixu.ui.components.RuntimeCard
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.components.RuntimeOutlinedTextField
import top.wkbin.taixu.ui.manju.impl.FfmpegAvailability

/**
 * 漫剧页的准备区：出图收件箱、出图配置、配音配置、FFmpeg 状态。
 *
 * 从 ManjuScreen 拆出，一是该文件已接近 400 行上限，
 * 二是这四块都是「一次性配置」，与主流程（剧本 → 分镜 → 导出）职责不同。
 */
@Composable
internal fun ManjuSetupPanels(
    state: ManjuViewModel.UiState,
    viewModel: ManjuViewModel?,
) {
    ImageInboxPanel(state = state, viewModel = viewModel)
    ImageConfigPanel(state = state, viewModel = viewModel)
    VoiceConfigPanel(state = state, viewModel = viewModel)
    FfmpegStatusPanel(state = state, viewModel = viewModel)
}

@Composable
private fun ImageInboxPanel(state: ManjuViewModel.UiState, viewModel: ManjuViewModel?) {
    RuntimeCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("出图收件箱", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "把图按 shot_0.png、shot_1.png… 命名放进该目录，生成时优先取用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.inboxPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (state.inboxShots.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "已就绪：${state.inboxShots.sorted().joinToString(", ") { "shot_$it" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            RuntimeButton(onClick = { viewModel?.refreshInbox() }, tonal = true) {
                RuntimeIcon(RuntimeIconName.Refresh)
            }
        }
    }
}

@Composable
private fun ImageConfigPanel(state: ManjuViewModel.UiState, viewModel: ManjuViewModel?) {
    // Key 不回显已保存值，留空即沿用原值。
    var baseUrl by remember(state.showImageConfig) { mutableStateOf(state.imageBaseUrl) }
    var model by remember(state.showImageConfig) { mutableStateOf(state.imageModel) }
    var apiKey by remember(state.showImageConfig) { mutableStateOf("") }

    ConfigToggle(
        label = "出图配置",
        configured = state.imageKeyConfigured,
        expanded = state.showImageConfig,
        onToggle = { viewModel?.onToggleImageConfig() },
    )
    if (state.showImageConfig) {
        RuntimeCard(modifier = Modifier.fillMaxWidth()) {
            ConfigField(baseUrl, { baseUrl = it }, "接口地址", "https://ark.cn-beijing.volces.com/api/v3")
            ConfigField(model, { model = it }, "模型", "doubao-seedream-4-0-250828")
            ConfigField(
                apiKey,
                { apiKey = it },
                "API Key",
                if (state.imageKeyConfigured) "留空则沿用已保存的 Key" else "火山方舟 API Key",
            )
            Spacer(Modifier.height(12.dp))
            RuntimeButton(onClick = {
                viewModel?.onSaveImageConfig(baseUrl, model, apiKey)
                apiKey = ""
            }) {
                RuntimeIcon(RuntimeIconName.Check)
                Spacer(Modifier.width(8.dp))
                Text("保存")
            }
        }
    }
}

@Composable
private fun VoiceConfigPanel(state: ManjuViewModel.UiState, viewModel: ManjuViewModel?) {
    var baseUrl by remember(state.showVoiceConfig) { mutableStateOf(state.voiceBaseUrl) }
    var model by remember(state.showVoiceConfig) { mutableStateOf(state.voiceModel) }
    var voice by remember(state.showVoiceConfig) { mutableStateOf(state.voiceName) }
    var apiKey by remember(state.showVoiceConfig) { mutableStateOf("") }

    ConfigToggle(
        label = "配音配置",
        configured = state.voiceKeyConfigured,
        expanded = state.showVoiceConfig,
        onToggle = { viewModel?.onToggleVoiceConfig() },
    )
    if (state.showVoiceConfig) {
        RuntimeCard(modifier = Modifier.fillMaxWidth()) {
            ConfigField(baseUrl, { baseUrl = it }, "接口地址", "https://ark.cn-beijing.volces.com/api/v3")
            ConfigField(model, { model = it }, "模型", "doubao-seed-tts")
            ConfigField(voice, { voice = it }, "音色", "zh_female_shuangkuaisisi_moon_bigtts")
            ConfigField(
                apiKey,
                { apiKey = it },
                "API Key",
                if (state.voiceKeyConfigured) "留空则沿用已保存的 Key" else "与出图可共用同一个 Key",
            )
            Spacer(Modifier.height(12.dp))
            RuntimeButton(onClick = {
                viewModel?.onSaveVoiceConfig(baseUrl, model, voice, apiKey)
                apiKey = ""
            }) {
                RuntimeIcon(RuntimeIconName.Check)
                Spacer(Modifier.width(8.dp))
                Text("保存")
            }
        }
    }
}

@Composable
private fun FfmpegStatusPanel(state: ManjuViewModel.UiState, viewModel: ManjuViewModel?) {
    val availability = state.ffmpeg ?: return
    val (text, color) = when (availability) {
        is FfmpegAvailability.Available ->
            "合成环境就绪（${availability.path}）" to MaterialTheme.colorScheme.primary

        FfmpegAvailability.Missing ->
            "缺少 ffmpeg，导出会失败。请在太墟终端执行：apt update && apt install -y ffmpeg" to
                MaterialTheme.colorScheme.error

        is FfmpegAvailability.Unknown ->
            "无法确认 ffmpeg 状态（${availability.reason}）" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    RuntimeCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = color,
                modifier = Modifier.weight(1f),
            )
            RuntimeButton(onClick = { viewModel?.refreshFfmpeg() }, tonal = true) {
                RuntimeIcon(RuntimeIconName.Refresh)
            }
        }
    }
}

@Composable
private fun ConfigToggle(
    label: String,
    configured: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RuntimeButton(onClick = onToggle, tonal = true) {
            Text(if (expanded) "收起$label" else label)
        }
        Text(
            text = if (configured) "已配置" else "未配置",
            style = MaterialTheme.typography.bodySmall,
            color = if (configured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun ConfigField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
) {
    RuntimeOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
    )
    Spacer(Modifier.height(8.dp))
}
