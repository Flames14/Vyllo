package com.vyllo.music.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.vyllo.music.data.translation.TranslationSettingsStore
import com.vyllo.music.domain.model.TranslationProvider
import com.vyllo.music.domain.model.TranslationSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Edits the lyrics translation provider, credentials, target language, and
 * romanization preference. Saving persists to encrypted preferences.
 */
@HiltViewModel
class TranslationViewModel @Inject constructor(
    private val store: TranslationSettingsStore
) : ViewModel() {

    private var initial = store.settings()

    var provider by mutableStateOf(initial.provider)
        private set
    var apiKey by mutableStateOf(initial.apiKey)
        private set
    var model by mutableStateOf(initial.model)
        private set
    var targetLanguage by mutableStateOf(initial.targetLanguage)
        private set
    var romanize by mutableStateOf(initial.romanize)
        private set

    fun updateProvider(value: TranslationProvider) {
        provider = value
        if (value.defaultModel.isNotBlank() && model.isBlank()) model = value.defaultModel
    }

    fun updateApiKey(value: String) {
        apiKey = value
    }

    fun updateModel(value: String) {
        model = value
    }

    fun updateTargetLanguage(value: String) {
        targetLanguage = value
    }

    fun updateRomanize(value: Boolean) {
        romanize = value
    }

    fun clearKey() {
        apiKey = ""
    }

    /** Discard edits made in the dialog and restore the last saved values. */
    fun discardChanges() {
        initial = store.settings()
        provider = initial.provider
        apiKey = initial.apiKey
        model = initial.model
        targetLanguage = initial.targetLanguage
        romanize = initial.romanize
    }

    fun save() {
        store.save(
            TranslationSettings(
                provider = provider,
                apiKey = apiKey.trim(),
                model = model.trim(),
                targetLanguage = targetLanguage,
                romanize = romanize
            )
        )
    }
}
