package top.wkbin.taixu.core.datastore

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 漫剧出图配置的存取。
 *
 * 以扩展函数形式放在独立文件中，而不是继续往 SettingsDataStore 里塞：
 * 那个文件已触及架构棘轮上限（只许下调），新功能应另立文件。
 */
private val manjuImageConfigKey = stringPreferencesKey("manju_image_config")

val SettingsDataStore.manjuImageConfig: Flow<ManjuImageEndpoint>
    get() = context.settingsDataStore.data.map {
        decodeManjuImageConfig(it[manjuImageConfigKey], secretManager)
    }

suspend fun SettingsDataStore.setManjuImageConfig(value: ManjuImageEndpoint) {
    context.settingsDataStore.edit { prefs ->
        val encoded = encodeManjuImageConfig(value, secretManager)
        if (encoded == null) prefs.remove(manjuImageConfigKey) else prefs[manjuImageConfigKey] = encoded
    }
}
