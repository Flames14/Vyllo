package com.vyllo.music.service

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.vyllo.music.MainActivity
import com.vyllo.music.R
import com.vyllo.music.VylloDeepLinks
import com.vyllo.music.core.security.SecureLogger

/**
 * Quick-settings tile that jumps straight into music recognition.
 *
 * The tile is a stateless launcher: it owns no recognition session and shows no
 * live state, so a tap simply opens [MainActivity] on the recognition screen.
 * Recognition itself still runs in the app, where the mic permission and the UI
 * live.
 */
class MediaRecognitionTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        runCatching {
            qsTile?.apply {
                label = getString(R.string.qs_tile_recognize)
                icon = Icon.createWithResource(this@MediaRecognitionTileService, R.drawable.ic_qs_recognize)
                state = Tile.STATE_INACTIVE
                updateTile()
            }
        }.onFailure { SecureLogger.w(TAG, "Failed to initialise tile", it) }
    }

    override fun onClick() {
        super.onClick()
        runCatching { launchRecognition() }
            .onFailure { SecureLogger.w(TAG, "Failed to launch recognition", it) }
    }

    private fun launchRecognition() {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = VylloDeepLinks.open(VylloDeepLinks.RECOGNITION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // startActivityAndCollapse(PendingIntent) only exists from API 34, and the
        // Intent overload throws there; so the legacy call is both required and
        // unreachable on 34+. Lint cannot see through the SDK guard.
        @Suppress("StartActivityAndCollapseDeprecated")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private companion object {
        const val TAG = "QsTile"
    }
}
