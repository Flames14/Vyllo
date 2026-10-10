package com.vyllo.music

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.vyllo.music.core.crash.CrashReporter
import com.vyllo.music.core.startup.StartupWarmer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

@HiltAndroidApp
class VylloApplication : Application(), Configuration.Provider, ImageLoaderFactory {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var startupWarmer: StartupWarmer

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(): ImageLoader {
        val lowRam = com.vyllo.music.core.utils.DevicePerf.isLowRamDevice(this)
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    // 15% on low-RAM devices (128-256MB heaps), 25% otherwise.
                    .maxSizePercent(if (lowRam) 0.15 else 0.25)
                    .strongReferencesEnabled(false)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024) // 150MB disk cache
                    .build()
            }
            .allowHardware(true) // Direct GPU texture decoding for 0ms CPU-to-GPU memory copy
            .allowRgb565(true)
            .crossfade(150)
            .respectCacheHeaders(false)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        // First: capture startup crashes too, and never let later init work
        // run without a handler in place. Chained, so Android's own crash
        // handling is unaffected.
        CrashReporter.install(this)

        // Fire-and-forget: boots NewPipe and pre-warms the poToken generator on
        // a background worker so no launch frame waits for them, yet the first
        // tap on a track no longer pays the multi-second cold cost.
        startupWarmer.warmUpAsync()
    }
}
