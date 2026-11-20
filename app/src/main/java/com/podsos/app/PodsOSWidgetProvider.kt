package com.podsos.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class PodsOSWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(
                context.packageName,
                R.layout.podsos_widget
            )

            views.setTextViewText(
                R.id.widget_connection,
                "● DISCONNECTED"
            )
            views.setTextViewText(R.id.widget_left_battery, "--%")
            views.setTextViewText(R.id.widget_right_battery, "--%")
            views.setTextViewText(R.id.widget_case_battery, "--%")
            views.setTextViewText(
                R.id.widget_noise_mode,
                "NOISE CONTROL  •  UNAVAILABLE"
            )

            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(
                R.id.widget_connection,
                pendingIntent
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
