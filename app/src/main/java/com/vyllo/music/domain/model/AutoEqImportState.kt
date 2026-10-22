package com.vyllo.music.domain.model

import androidx.annotation.StringRes
import com.vyllo.music.R

/** Why an AutoEQ import did not produce a usable profile. */
enum class AutoEqImportError(@StringRes val messageRes: Int) {
    INVALID_SOURCE(R.string.eq_autoeq_error_source),
    FETCH_FAILED(R.string.eq_autoeq_error_fetch),
    UNPARSABLE(R.string.eq_autoeq_error_parse)
}

/** Wizard state for importing an AutoEQ profile. */
data class AutoEqImportState(
    val isLoading: Boolean = false,
    val error: AutoEqImportError? = null,
    val profileName: String = "",
    val bandLevels: List<Int> = emptyList()
) {
    val hasPreview: Boolean get() = bandLevels.isNotEmpty()
}
