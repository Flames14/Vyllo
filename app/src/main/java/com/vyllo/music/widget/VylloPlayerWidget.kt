package com.vyllo.music.widget

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.background
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.Spacer
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.vyllo.music.R
import com.vyllo.music.service.MusicService
import kotlinx.coroutines.guava.await

/** Glance playback widget, driven from MusicService's MediaSession state. */
class VylloPlayerWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val state = currentState<Preferences>()
            PlayerWidgetContent(
                title = state[KEY_TITLE] ?: context.getString(R.string.widget_nothing_playing),
                artist = state[KEY_ARTIST].orEmpty(),
                isPlaying = state[KEY_IS_PLAYING] ?: false
            )
        }
    }

    suspend fun publish(context: Context, title: String, artist: String, isPlaying: Boolean) {
        val manager = GlanceAppWidgetManager(context)
        manager.getGlanceIds(VylloPlayerWidget::class.java).forEach { id ->
            updateAppWidgetState(context, id) { prefs ->
                prefs[KEY_TITLE] = title
                prefs[KEY_ARTIST] = artist
                prefs[KEY_IS_PLAYING] = isPlaying
            }
            update(context, id)
        }
    }

    private companion object {
        val KEY_TITLE = stringPreferencesKey("widget_title")
        val KEY_ARTIST = stringPreferencesKey("widget_artist")
        val KEY_IS_PLAYING = booleanPreferencesKey("widget_is_playing")
    }
}

@Composable
@SuppressLint("RestrictedApi") // Glance's public resource-based ColorProvider is marked library-group restricted.
private fun PlayerWidgetContent(title: String, artist: String, isPlaying: Boolean) {
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(ColorProvider(R.color.widget_background))
            .padding(16.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text(
            text = title,
            style = TextStyle(fontSize = 16.sp, color = ColorProvider(R.color.widget_text_primary))
        )
        if (artist.isNotBlank()) {
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = artist,
                style = TextStyle(fontSize = 13.sp, color = ColorProvider(R.color.widget_text_secondary))
            )
        }
        Spacer(GlanceModifier.height(10.dp))
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            androidx.glance.Button(
                text = "⏮",
                onClick = actionRunCallback<WidgetPlaybackAction>(
                    actionParametersOf(WidgetPlaybackAction.KEY to WidgetPlaybackAction.PREVIOUS)
                )
            )
            Spacer(GlanceModifier.width(8.dp))
            androidx.glance.Button(
                text = if (isPlaying) "⏸" else "▶",
                onClick = actionRunCallback<WidgetPlaybackAction>(
                    actionParametersOf(WidgetPlaybackAction.KEY to WidgetPlaybackAction.TOGGLE)
                )
            )
            Spacer(GlanceModifier.width(8.dp))
            androidx.glance.Button(
                text = "⏭",
                onClick = actionRunCallback<WidgetPlaybackAction>(
                    actionParametersOf(WidgetPlaybackAction.KEY to WidgetPlaybackAction.NEXT)
                )
            )
        }
    }
}

/** Executes transport commands through the existing MediaSession. */
class WidgetPlaybackAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val action = parameters[KEY] ?: return
        val token = SessionToken(context, ComponentName(context, MusicService::class.java))
        val future: ListenableFuture<MediaController> = MediaController.Builder(context, token).buildAsync()
        try {
            val controller = future.await()
            when (action) {
                PREVIOUS -> controller.seekToPreviousMediaItem()
                NEXT -> controller.seekToNextMediaItem()
                TOGGLE -> if (controller.isPlaying) controller.pause() else controller.play()
            }
        } finally {
            MediaController.releaseFuture(future)
        }
    }

    companion object {
        val KEY = androidx.glance.action.ActionParameters.Key<String>("widget_transport_action")
        const val PREVIOUS = "previous"
        const val NEXT = "next"
        const val TOGGLE = "toggle"
    }
}
