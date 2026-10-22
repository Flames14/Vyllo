package com.vyllo.music.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.vyllo.music.MainActivity
import com.vyllo.music.R
import com.vyllo.music.VylloDeepLinks

/** Home-screen shortcut that opens directly to the recognition screen. */
class RecognitionWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = VylloDeepLinks.open(VylloDeepLinks.RECOGNITION)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.recognition_widget)
            views.setTextViewText(R.id.recognition_widget_title, context.getString(R.string.recognition_widget_title))
            views.setTextViewText(R.id.recognition_widget_subtitle, context.getString(R.string.recognition_widget_subtitle))
            views.setOnClickPendingIntent(R.id.recognition_widget_root, pending)
            manager.updateAppWidget(id, views)
        }
    }

    private companion object {
        const val REQUEST_CODE = 317
    }
}
