package com.vyllo.music.data.translation

import com.vyllo.music.core.security.SecurePreferenceManager
import com.vyllo.music.data.TranslationEngine
import com.vyllo.music.domain.model.TranslationProvider
import com.vyllo.music.domain.model.TranslationSettings
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores translation provider credentials in EncryptedSharedPreferences.
 * Keys never leave the device and are never logged.
 */
@Singleton
class TranslationSettingsStore @Inject constructor(
    private val secure: SecurePreferenceManager
) {
    fun settings(): TranslationSettings = TranslationSettings(
        provider = TranslationProvider.fromId(secure.getString(KEY_PROVIDER)),
        apiKey = secure.getString(KEY_API_KEY).orEmpty(),
        model = secure.getString(KEY_MODEL).orEmpty(),
        targetLanguage = secure.getString(KEY_TARGET).orEmpty().ifBlank { DEFAULT_TARGET },
        romanize = secure.getBoolean(KEY_ROMANIZE, false)
    )

    fun save(settings: TranslationSettings) {
        secure.saveString(KEY_PROVIDER, settings.provider.id)
        secure.saveString(KEY_API_KEY, settings.apiKey.trim())
        secure.saveString(KEY_MODEL, settings.model.trim())
        secure.saveString(KEY_TARGET, settings.targetLanguage.ifBlank { DEFAULT_TARGET })
        secure.saveBoolean(KEY_ROMANIZE, settings.romanize)
        TranslationEngine.clearCache()
        cacheRevision++
    }

    /** Bumped on save so callers can discard translated/romanized state. */
    @Volatile
    var cacheRevision: Int = 0
        private set

    private companion object {
        const val KEY_PROVIDER = "translation_provider"
        const val KEY_API_KEY = "translation_api_key"
        const val KEY_MODEL = "translation_model"
        const val KEY_TARGET = "translation_target_lang"
        const val KEY_ROMANIZE = "translation_romanize"
        const val DEFAULT_TARGET = "en"
    }
}
