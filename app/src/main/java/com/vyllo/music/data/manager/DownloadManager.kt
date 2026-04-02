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
        if (observeJob != null) return
        
        observeJob = scope.launch(Dispatchers.Main) {
            WorkManager.getInstance(context)
                .getWorkInfosByTagFlow("download_song")
                .collectLatest { workInfos ->
                    workInfos.forEach { workInfo ->
                        val url = workInfo.tags.find { it.startsWith("download_") && it != "download_song" }
                            ?.removePrefix("download_")
                        
                        if (url != null) {
                            if (workInfo.state.isFinished) {
                                downloadProgress.remove(url)
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
    }

    fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
    }
}
