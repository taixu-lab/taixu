package top.wkbin.taixu.core.datastore

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 漫剧配音配置的存取。
 *
 * 与出图配置同样以扩展函数放独立文件，避免 SettingsDataStore
 * 突破行数棘轮（该文件只许下调）。
 */
private val manjuVoiceConfigKey = stringPreferencesKey("manju_voice_config")

val SettingsDataStore.manjuVoiceConfig: Flow<ManjuVoiceEndpoint>
    get() = context.settingsDataStore.data.map {
        decodeManjuVoiceConfig(it[manjuVoiceConfigKey], secretManager)
    }

suspend fun SettingsDataStore.setManjuVoiceConfig(value: ManjuVoiceEndpoint) {
    context.settingsDataStore.edit { prefs ->
        val encoded = encodeManjuVoiceConfig(value, secretManager)
        if (encoded == null) prefs.remove(manjuVoiceConfigKey) else prefs[manjuVoiceConfigKey] = encoded
    }
}
