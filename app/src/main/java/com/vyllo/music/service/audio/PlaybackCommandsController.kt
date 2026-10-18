package com.vyllo.music.service.audio

import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.domain.manager.PlaybackQueueManager
import com.vyllo.music.domain.repository.IMusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Owns the custom media-notification buttons (like / shuffle / repeat): the
 * command definitions, the button row, and the command handling behind each one.
 *
 * Kept out of [com.vyllo.music.service.MusicService] so the service stays a thin
 * wiring shell. The icons are media3's built-in resources, and the button row is
 * repainted from live player/rating state so the notification never shows a
 * stale toggle.
 */
@UnstableApi
class PlaybackCommandsController(
    private val playerProvider: () -> Player?,
    private val scope: CoroutineScope,
    private val repository: IMusicRepository,
    private val playbackQueueManager: PlaybackQueueManager,
    private val likeLabel: String,
    private val shuffleLabel: String,
    private val repeatLabel: String,
) {

    companion object {
        const val ACTION_LIKE = "com.vyllo.music.session.LIKE"
        const val ACTION_SHUFFLE = "com.vyllo.music.session.SHUFFLE"
        const val ACTION_REPEAT = "com.vyllo.music.session.REPEAT"

        // Built lazily: SessionCommand needs a real android.os.Bundle, which the
        // JVM unit-test classpath cannot provide at class-init time.
        val LIKE_COMMAND: SessionCommand by lazy { SessionCommand(ACTION_LIKE, Bundle.EMPTY) }
        val SHUFFLE_COMMAND: SessionCommand by lazy { SessionCommand(ACTION_SHUFFLE, Bundle.EMPTY) }
        val REPEAT_COMMAND: SessionCommand by lazy { SessionCommand(ACTION_REPEAT, Bundle.EMPTY) }

        /** Custom commands advertised to controllers so the buttons are visible. */
        val COMMANDS: List<SessionCommand> by lazy {
            listOf(LIKE_COMMAND, SHUFFLE_COMMAND, REPEAT_COMMAND)
        }
    }

    private var session: MediaSession? = null

    private var likeStateJob: Job? = null

    @Volatile
    private var liked: Boolean = false

    /** Binds the live session and paints the first button row. */
    fun attach(session: MediaSession) {
        this.session = session
        refresh()
    }

    /** Current button row for [androidx.media3.session.MediaSession.Builder]. */
    fun layout(): List<CommandButton> {
        val player = playerProvider()
        return listOf(
            CommandButton.Builder(NotificationButtonState.likeIcon(liked))
                .setSessionCommand(LIKE_COMMAND)
                .setDisplayName(likeLabel)
                .build(),
            CommandButton.Builder(NotificationButtonState.shuffleIcon(player?.shuffleModeEnabled == true))
                .setSessionCommand(SHUFFLE_COMMAND)
                .setDisplayName(shuffleLabel)
                .build(),
            CommandButton.Builder(
                NotificationButtonState.repeatIcon(player?.repeatMode ?: Player.REPEAT_MODE_OFF)
            )
                .setSessionCommand(REPEAT_COMMAND)
                .setDisplayName(repeatLabel)
                .build(),
        )
    }

    /** Repaints the notification buttons from the current player state. */
    fun refresh() {
        session?.setCustomLayout(layout())
    }

    /** Reloads the like state for the new track, then repaints. */
    fun onTrackChanged() {
        val url = currentUrl() ?: return
        likeStateJob?.cancel()
        likeStateJob = scope.launch {
            liked = runCatching { repository.getRating(url) == 1 }.getOrDefault(false)
            refresh()
        }
    }

    /** Handles a custom command; returns true when the action was consumed. */
    fun handle(action: String?): Boolean = when (action) {
        ACTION_LIKE -> { toggleLike(); true }
        ACTION_SHUFFLE -> { toggleShuffle(); true }
        ACTION_REPEAT -> { cycleRepeat(); true }
        else -> false
    }

    private fun toggleShuffle() {
        val player = playerProvider() ?: return
        player.shuffleModeEnabled = !player.shuffleModeEnabled
        refresh()
    }

    private fun cycleRepeat() {
        val player = playerProvider() ?: return
        player.repeatMode = NotificationButtonState.nextRepeatMode(player.repeatMode)
        refresh()
    }

    private fun toggleLike() {
        val url = currentUrl() ?: return
        val item = playbackQueueManager.getQueueSnapshot().firstOrNull { it.url == url }
        if (item == null) {
            SecureLogger.w("MusicService", "Notification like ignored: $url not in the queue")
            return
        }
        val nextRating = if (liked) 0 else 1
        scope.launch {
            val ok = runCatching { repository.setRating(item, nextRating) }.getOrDefault(false)
            if (ok) {
                liked = nextRating == 1
            } else {
                SecureLogger.w("MusicService", "Notification like toggle failed for ${item.url}")
            }
            refresh()
        }
    }

    private fun currentUrl(): String? = playerProvider()?.currentMediaItem?.mediaId
}
