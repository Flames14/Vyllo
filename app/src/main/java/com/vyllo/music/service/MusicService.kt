package com.vyllo.music.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.*
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import com.vyllo.music.MainActivity
import com.vyllo.music.R
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.core.utils.DevicePerf
import com.vyllo.music.data.manager.PlaybackAudioEffectsManager
import com.vyllo.music.data.manager.PreferenceManager
import com.vyllo.music.data.manager.WakeLockManager
import com.vyllo.music.domain.manager.PlaybackErrorHandler
import com.vyllo.music.domain.manager.LastFmScrobbleManager
import com.vyllo.music.domain.manager.PlaybackQueueManager
import com.vyllo.music.domain.repository.IMusicRepository
import com.vyllo.music.service.audio.AudioSinkFactory
import com.vyllo.music.service.audio.AndroidAutoCatalog
import com.vyllo.music.service.audio.CacheProvider
import com.vyllo.music.service.audio.CrossfadeController
import com.vyllo.music.service.audio.MediaSessionCallback
import com.vyllo.music.service.audio.MuteWatcher
import com.vyllo.music.service.audio.OutputDeviceWatcher
import com.vyllo.music.service.audio.PlaybackCommandsController
import com.vyllo.music.service.audio.PlaybackHttpClientFactory
import com.vyllo.music.service.audio.PlaybackPlayerFactory
import com.vyllo.music.service.audio.PlaybackResumption
import com.vyllo.music.service.audio.PlayheadPersistence
import com.vyllo.music.service.audio.PlayerErrorListener
import com.vyllo.music.service.audio.QueuePersistenceCoordinator
import com.vyllo.music.service.stats.PlaybackStatsRecorder
import com.vyllo.music.widget.VylloPlayerWidget
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
@androidx.media3.common.util.UnstableApi
class MusicService : MediaLibraryService() {

    @Inject
    lateinit var repository: IMusicRepository

    @Inject
    lateinit var androidAutoCatalog: AndroidAutoCatalog

    @Inject
    lateinit var preferenceManager: PreferenceManager

    @Inject
    lateinit var playbackQueueManager: PlaybackQueueManager

    @Inject
    lateinit var wakeLockManager: WakeLockManager

    @Inject
    lateinit var playbackQueueOrchestrator: PlaybackQueueOrchestrator

    @Inject
    lateinit var playbackAudioEffectsManager: PlaybackAudioEffectsManager

    @Inject
    lateinit var lastFmScrobbleManager: LastFmScrobbleManager

    private var mediaSession: MediaLibrarySession? = null
    private var player: ExoPlayer? = null
    private lateinit var playheadPersistence: PlayheadPersistence
    private lateinit var crossfadeController: CrossfadeController
    private var outputDeviceWatcher: OutputDeviceWatcher? = null
    private var muteWatcher: MuteWatcher? = null
    private var pausedByDeviceDisconnect = false
    private lateinit var statsRecorder: PlaybackStatsRecorder

    private val serviceJob = SupervisorJob()
    // Safety net: background queue work must never be able to crash the app.
    // Any stray exception from a transition/prefetch coroutine is logged here
    // instead of propagating as an uncaught exception.
    private val serviceScope = CoroutineScope(
        Dispatchers.Main + serviceJob +
            kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
                SecureLogger.e("MusicService", "Background playback task failed", throwable)
            }
    )

    private val volumeBoostProcessor = VolumeBoostAudioProcessor()
    private val centerCancelAudioProcessor = CenterCancelAudioProcessor()
    private val loudnessProcessor = LoudnessNormalizationAudioProcessor()

    // lazy: the constructor must not read the @Inject lateinit fields, which Hilt
    // only populates during super.onCreate().
    private val queuePersistence by lazy {
        QueuePersistenceCoordinator(playbackQueueManager, preferenceManager)
    }

    private val playbackErrorHandler = PlaybackErrorHandler(
        maxRetries = 3,
        baseDelayMs = 2_000L,
        maxDelayMs = 10_000L,
        // Retries are service-managed (see onPlayerError): the coroutine-based
        // path holds wake locks across the delay so recovery works with the
        // screen off. This Handler-based fallback is kept only for compat.
        onRetry = { player?.prepare(); player?.play() },
        onMaxRetriesReached = { SecureLogger.w("MusicService", "Playback stalled after max retries") }
    )

    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "keep_audio_playing_enabled") {
            val enabled = prefs.getBoolean(key, false)
            player?.setAudioAttributes(
                AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(),
                !enabled // false means keep playing when mic/camera is used
            )
        } else if (key == PreferenceManager.KEY_VOLUME_BOOST) {
            volumeBoostProcessor.volumeMultiplier = preferenceManager.volumeBoostMultiplier
        } else if (key == PreferenceManager.KEY_PLAYBACK_SPEED || key == PreferenceManager.KEY_PLAYBACK_PITCH) {
            applyPlaybackParameters()
        } else if (key == PreferenceManager.KEY_LOUDNESS_PRESET) {
            applyLoudnessPreset()
        } else if (key == PreferenceManager.KEY_SKIP_SILENCE) {
            applySkipSilence()
        } else if (key == PreferenceManager.KEY_PAUSE_WHEN_MUTED) {
            restartMuteWatcher()
        } else if (key == PreferenceManager.KEY_KARAOKE) {
            centerCancelAudioProcessor.enabled = preferenceManager.karaokeEnabled
        }
        playbackAudioEffectsManager.onPreferenceChanged(key)
    }

    private fun applyLoudnessPreset() {
        val target = LoudnessNormalizationAudioProcessor.targetFor(preferenceManager.loudnessPreset)
        loudnessProcessor.enabled = target != null
        if (target != null) loudnessProcessor.targetRmsDb = target
    }

    private fun applySkipSilence() {
        player?.skipSilenceEnabled = preferenceManager.skipSilence
    }

    /** Rebuild the stats item from the MediaItem Media3 is holding. */
    private fun MediaItem.toStatsItem() = com.vyllo.music.domain.model.MusicItem(
        title = mediaMetadata.title?.toString().orEmpty(),
        url = mediaId,
        uploader = mediaMetadata.artist?.toString().orEmpty(),
        thumbnailUrl = mediaMetadata.artworkUri?.toString().orEmpty(),
        durationSecs = (mediaMetadata.durationMs ?: 0L) / 1_000L
    )

    private fun startOutputDeviceWatcher() {
        if (outputDeviceWatcher != null) return
        outputDeviceWatcher = OutputDeviceWatcher(
            context = this,
            onDisconnected = ::handleDeviceDisconnect,
            onReconnected = ::handleDeviceReconnect
        ).also { it.start() }
    }

    private fun handleDeviceDisconnect() {
        if (!preferenceManager.pauseOnDeviceDisconnect) return
        val activePlayer = player ?: return
        if (activePlayer.isPlaying) {
            activePlayer.pause()
            pausedByDeviceDisconnect = true
        }
    }

    private fun handleDeviceReconnect() {
        if (!preferenceManager.resumeOnDeviceReconnect || !pausedByDeviceDisconnect) return
        pausedByDeviceDisconnect = false
        player?.play()
    }

    private fun startMuteWatcherIfEnabled() {
        if (!preferenceManager.pauseWhenMuted) return
        if (muteWatcher != null) return
        muteWatcher = MuteWatcher(this) {
            player?.let { if (it.isPlaying) it.pause() }
        }.also { it.start() }
    }

    private fun restartMuteWatcher() {
        muteWatcher?.stop()
        muteWatcher = null
        startMuteWatcherIfEnabled()
    }

    /**
     * Recurring sleep schedule. The check only makes sense while music can be
     * playing, which is exactly when this service is alive, so a light poll is
     * enough — no alarms or wakeups for a schedule that would have nothing to
     * pause.
     */
    private fun startSleepScheduleWatcher() {
        serviceScope.launch {
            while (true) {
                delay(SLEEP_SCHEDULE_POLL_MS)
                fireSleepScheduleIfDue()
            }
        }
    }

    private fun fireSleepScheduleIfDue() {
        val schedule = preferenceManager.sleepSchedule
        if (!schedule.enabled) return

        val now = java.time.LocalDateTime.now()
        val due = schedule.isDue(
            dayOfWeek = now.dayOfWeek.value,
            minuteOfDay = now.hour * 60 + now.minute,
            todayEpochDay = now.toLocalDate().toEpochDay(),
            lastFiredEpochDay = preferenceManager.sleepScheduleLastFiredDay
        )
        if (!due) return

        preferenceManager.sleepScheduleLastFiredDay = now.toLocalDate().toEpochDay()
        player?.let { if (it.isPlaying) it.pause() }
        SecureLogger.d("MusicService", "Sleep schedule paused playback")
    }

    /**
     * Push the stored rate onto the player. Also used at startup: Media3 keeps
     * the previous speed on the player instance across media items, but a fresh
     * process starts at 1.0x unless we restore it here.
     */
    private fun applyPlaybackParameters() {
        player?.playbackParameters = PlaybackParameters(
            preferenceManager.playbackSpeed,
            preferenceManager.playbackPitch
        )
    }

    companion object {
        private const val SLEEP_SCHEDULE_POLL_MS = 30_000L

        fun getSimpleCache(context: android.content.Context): SimpleCache =
            CacheProvider.getSimpleCache(context)
    }

    override fun onCreate() {
        super.onCreate()

        val okHttpClient = PlaybackHttpClientFactory.create()
        val cacheDataSourceFactory = PlaybackPlayerFactory.createCacheDataSourceFactory(this, okHttpClient)

        val keepAudioPlaying = preferenceManager.isKeepAudioPlayingEnabled

        preferenceManager.registerOnPreferenceChangeListener(preferenceListener)
        volumeBoostProcessor.volumeMultiplier = preferenceManager.volumeBoostMultiplier
        centerCancelAudioProcessor.enabled = preferenceManager.karaokeEnabled
        applyLoudnessPreset()

        val renderersFactory = AudioSinkFactory.createRenderersFactory(
            this,
            volumeBoostProcessor,
            arrayOf(centerCancelAudioProcessor, loudnessProcessor),
            audioOffload = preferenceManager.audioOffloadEnabled
        )

        player = PlaybackPlayerFactory.buildPlayer(
            context = this,
            cacheDataSourceFactory = cacheDataSourceFactory,
            renderersFactory = renderersFactory,
            keepAudioPlaying = keepAudioPlaying,
            lowRamPlayback = DevicePerf.isLowRamDevice(this)
        )

        // Apply settings and register their observers as soon as the player
        // exists. These used to be reached only after a speed/pitch change,
        // which made persisted audio settings appear enabled but inert after a
        // fresh service start.
        applySkipSilence()
        startOutputDeviceWatcher()
        startMuteWatcherIfEnabled()

        player?.addListener(
            PlayerErrorListener(
                playerProvider = { player },
                serviceScope = serviceScope,
                playbackErrorHandler = playbackErrorHandler,
                playbackQueueOrchestrator = playbackQueueOrchestrator,
                playbackQueueManager = playbackQueueManager,
                playbackAudioEffectsManager = playbackAudioEffectsManager
            )
        )

        // Keep PlaybackQueueManager's physical order in sync with ExoPlayer shuffle
        // so sequential next/prev/lookahead follow the shuffled order.
        player?.addListener(object : Player.Listener {
            override fun onShuffleModeEnabledChanged(enabled: Boolean) {
                playbackQueueManager.setShuffleEnabled(enabled)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (::crossfadeController.isInitialized) {
                    crossfadeController.onMediaItemTransition(reason)
                }
                if (::statsRecorder.isInitialized) {
                    statsRecorder.onTrackStarted(mediaItem?.toStatsItem())
                }
                val durationMs = player?.duration?.takeIf { it > 0L } ?: 0L
                lastFmScrobbleManager.onTrackStarted(
                    item = mediaItem?.toStatsItem(),
                    durationMs = durationMs,
                    isPlaying = player?.isPlaying == true
                )
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) lastFmScrobbleManager.onPlaybackStarted()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION && ::statsRecorder.isInitialized) {
                    statsRecorder.onTrackEnded(
                        oldPosition.mediaItem?.toStatsItem(),
                        oldPosition.positionMs
                    )
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && ::statsRecorder.isInitialized) {
                    statsRecorder.onTrackStarted(player?.currentMediaItem?.toStatsItem())
                    lastFmScrobbleManager.onDurationAvailable(player?.duration ?: 0L)
                }
                if (playbackState == Player.STATE_ENDED) {
                    lastFmScrobbleManager.onStopped()
                }
            }
        })

        crossfadeController = CrossfadeController(
            playerProvider = { player },
            crossfadeMillis = { preferenceManager.crossfadeSeconds * 1_000L },
            scope = serviceScope
        )
        crossfadeController.start()
        statsRecorder = PlaybackStatsRecorder(repository, serviceScope)

        // Restore the user's speed/pitch (1.0x by default) instead of forcing
        // 1.0x, which used to reset a deliberately slowed-down track on every
        // service start.
        applyPlaybackParameters()
        player?.audioSessionId?.let { playbackAudioEffectsManager.attachToAudioSession(it) }

        serviceScope.launch {
            playbackQueueManager.queueVersion.collectLatest {
                playbackQueueOrchestrator.prefetchUpcomingStreams(serviceScope, player)
            }
        }

        // Restore the last session's queue (process death / reboot) and persist
        // structural edits back to storage.
        queuePersistence.start(serviceScope)

        startSleepScheduleWatcher()

        // Scrobbling: replay anything queued while offline and keep the current
        // position sampled so a manual skip still knows how far the track played.
        lastFmScrobbleManager.start(serviceScope)
        serviceScope.launch {
            while (true) {
                lastFmScrobbleManager.onProgress(player?.currentPosition ?: 0L)
                delay(3_000L)
            }
        }

        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val commandButtons = PlaybackCommandsController(
            playerProvider = { player },
            scope = serviceScope,
            repository = repository,
            playbackQueueManager = playbackQueueManager,
            likeLabel = getString(R.string.notification_button_like),
            shuffleLabel = getString(R.string.notification_button_shuffle),
            repeatLabel = getString(R.string.notification_button_repeat)
        )

        val sessionCallback = MediaSessionCallback(
            playerProvider = { player },
            serviceScope = serviceScope,
            playbackQueueOrchestrator = playbackQueueOrchestrator,
            commands = commandButtons,
            resumption = PlaybackResumption(preferenceManager, playbackQueueManager, repository),
            catalog = androidAutoCatalog,
            repository = repository,
            playbackQueueManager = playbackQueueManager,
            appPackageName = packageName
        )

        // Initialize the browse-capable media library session on the same player.
        val initializedPlayer = player
            ?: throw IllegalStateException("ExoPlayer not initialized before MediaSession build")
        val session = MediaLibrarySession.Builder(this, initializedPlayer, sessionCallback)
            .setSessionActivity(pendingIntent)
            .setCustomLayout(commandButtons.layout())
            .build()
        mediaSession = session
        commandButtons.attach(session)

        // Persist the playhead so a process death resumes mid-track.
        playheadPersistence = PlayheadPersistence(preferenceManager)
        playheadPersistence.start(serviceScope) { player }

        // Repaint the notification buttons from live player state so the like /
        // shuffle / repeat toggles never show a stale icon.
        player?.addListener(object : Player.Listener {
            override fun onShuffleModeEnabledChanged(enabled: Boolean) {
                commandButtons.refresh()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                commandButtons.refresh()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                commandButtons.onTrackChanged()
                updatePlayerWidget()
                // New track starts at zero; store that immediately so the old
                // position is not left dangling against the new URL.
                playheadPersistence.persist(player)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayerWidget()
                if (!isPlaying) playheadPersistence.persist(player)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updatePlayerWidget()
                if (playbackState == Player.STATE_ENDED) playheadPersistence.clear()
            }
        })
        updatePlayerWidget()
    }

    private fun updatePlayerWidget() {
        val activePlayer = player ?: return
        val mediaMetadata = activePlayer.currentMediaItem?.mediaMetadata
        val title = mediaMetadata?.title?.toString()?.takeIf { it.isNotBlank() }
            ?: getString(R.string.widget_nothing_playing)
        val artist = mediaMetadata?.artist?.toString().orEmpty()
        val isPlaying = activePlayer.isPlaying
        serviceScope.launch {
            runCatching {
                VylloPlayerWidget().publish(this@MusicService, title, artist, isPlaying)
            }.onFailure { SecureLogger.w("MusicService", "Player widget update failed", it) }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away must not kill background audio when the user
        // explicitly enabled background playback. Only tear down when the
        // setting is off; otherwise leave the foreground service running.
        if (!preferenceManager.isBackgroundPlaybackEnabled) {
            try {
                mediaSession?.player?.run {
                    playheadPersistence.persist(this)
                    playWhenReady = false
                    stop()
                }
                androidx.core.app.ServiceCompat.stopForeground(this, androidx.core.app.ServiceCompat.STOP_FOREGROUND_REMOVE)
            } catch (e: Exception) {
                SecureLogger.w("MusicService", "Error stopping service on task removed: ${e.message}")
            }
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        SecureLogger.d("MusicService", "Service onDestroy called")
        if (::crossfadeController.isInitialized) crossfadeController.stop()
        if (::statsRecorder.isInitialized) {
            statsRecorder.onTrackEnded(
                player?.currentMediaItem?.toStatsItem(),
                player?.currentPosition ?: 0L
            )
        }
        lastFmScrobbleManager.onStopped()
        outputDeviceWatcher?.stop()
        outputDeviceWatcher = null
        muteWatcher?.stop()
        muteWatcher = null
        serviceJob.cancel()
        playbackErrorHandler.release()
        playbackAudioEffectsManager.release()
        preferenceManager.unregisterOnPreferenceChangeListener(preferenceListener)
        wakeLockManager.release()

        mediaSession?.run {
            if (::playheadPersistence.isInitialized) playheadPersistence.persist(player)
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
