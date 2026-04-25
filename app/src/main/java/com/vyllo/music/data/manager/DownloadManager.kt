package com.vyllo.music.data.manager

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    private val context: Context
) {
    val downloadProgress = mutableStateMapOf<String, Int>()
    private var observeJob: Job? = null

    fun startObserving(scope: CoroutineScope) {
        if (observeJob?.isActive == true) return

        observeJob?.cancel() // Clean up dead job if present
        observeJob = scope.launch(Dispatchers.Default) {
            WorkManager.getInstance(context)
                .getWorkInfosByTagFlow("download_song")
                .collectLatest { workInfos ->
                    workInfos.forEach { workInfo ->
                        val url = workInfo.tags.find { it.startsWith("download_") && it != "download_song" }
                            ?.removePrefix("download_")

                        if (url != null) {
                            if (workInfo.state.isFinished) {
                                // Delay removing progress to allow Room database to emit the completed download
                                // This prevents the download button from flashing back to download icon
                                launch {
                                    kotlinx.coroutines.delay(500)
                                    downloadProgress.remove(url)
                                }
                            } else {
                                val progress = workInfo.progress.getInt("progress", 0)
                                if (progress > 0) {
                                    downloadProgress[url] = progress
                                }
                            }
                        }
                    }
                }
        }
        observeJob?.invokeOnCompletion { observeJob = null }
    }

    fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
    }
}
