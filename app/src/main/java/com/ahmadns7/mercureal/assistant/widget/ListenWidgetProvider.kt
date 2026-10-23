package com.ahmadns7.mercureal.assistant.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.ahmadns7.mercureal.assistant.ListenIntents
import com.ahmadns7.mercureal.assistant.R

/**
 * Home screen widget with one "tap to talk" button. Widgets cannot receive press-and-hold, so this
 * is the tap-to-open fallback: the tap opens the app and runs one listening turn.
 * RemoteViews is used instead of Glance to keep the dependency set unchanged.
 */
class ListenWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pi = PendingIntent.getActivity(
            context,
            0,
            ListenIntents.launchIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_listen)
            views.setOnClickPendingIntent(R.id.widget_mic, pi)
            manager.updateAppWidget(id, views)
        }
    }
}
