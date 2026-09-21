package com.vyllo.music.ui.library

import androidx.compose.runtime.Composable
import com.vyllo.music.LibraryViewModel

@Deprecated("Moved to ui.library.sync", ReplaceWith("com.vyllo.music.ui.library.sync.YouTubeSyncBottomSheet(viewModel, onDismiss)"))
@Composable
fun YouTubeSyncBottomSheet(
    viewModel: LibraryViewModel,
    onDismiss: () -> Unit
) {
    com.vyllo.music.ui.library.sync.YouTubeSyncBottomSheet(viewModel, onDismiss)
}
