package com.vyllo.music.presentation.components.player

import androidx.media3.session.MediaController

@Volatile
var lastManualSeekAtMs: Long = 0L
    private set

fun markManualSeek() {
    lastManualSeekAtMs = android.os.SystemClock.elapsedRealtime()
}

fun isSeekHoldActive(): Boolean =
    android.os.SystemClock.elapsedRealtime() - lastManualSeekAtMs < 700L

fun seekRelative(
    controller: MediaController?,
    duration: Long,
    deltaMs: Long,
    onPositionChange: (Long) -> Unit
) {
    val player = controller ?: return
    val base = player.currentPosition.coerceAtLeast(0L)
    val newPos = (base + deltaMs).coerceIn(0L, duration.coerceAtLeast(0L))
    markManualSeek()
    player.seekTo(newPos)
    onPositionChange(newPos)
}
