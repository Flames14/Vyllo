package com.vyllo.music.presentation.components

import androidx.compose.runtime.Composable
import com.vyllo.music.domain.model.MusicItem

typealias ShareCardTemplate = com.vyllo.music.presentation.components.share.ShareCardTemplate

@Deprecated("Moved to presentation.components.share", ReplaceWith("com.vyllo.music.presentation.components.share.StoryShareBottomSheet(item, onDismiss)"))
@Composable
fun StoryShareBottomSheet(
    item: MusicItem,
    onDismiss: () -> Unit
) {
    com.vyllo.music.presentation.components.share.StoryShareBottomSheet(item, onDismiss)
}
