package com.nexus.control

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews

class StatusWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> updateOne(context, manager, id) }
    }

    companion object {
        private const val PREFS = "nexus_widget"
        private const val KEY_OPACITY = "opacity"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, StatusWidget::class.java))
            ids.forEach { updateOne(context, manager, it) }
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, id: Int) {
            val opacity = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_OPACITY, 78)
                .coerceIn(0, 100)
            val alpha = (255f * (opacity / 100f)).toInt()

            val views = RemoteViews(context.packageName, R.layout.widget_status)
            views.setInt(R.id.widget_root, "setBackgroundColor", Color.argb(alpha, 7, 17, 31))

            val intent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            manager.updateAppWidget(id, views)
        }
    }
}
