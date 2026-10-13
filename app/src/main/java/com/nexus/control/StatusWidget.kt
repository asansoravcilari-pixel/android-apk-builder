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
        ids.forEach { updateOne(context, manager, it) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, StatusWidget::class.java))
            ids.forEach { updateOne(context, manager, it) }
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, id: Int) {
            val opacity = context.getSharedPreferences("nexus_widget", Context.MODE_PRIVATE).getInt("opacity", 78).coerceIn(0, 100)
            val alpha = (255f * (opacity / 100f)).toInt()
            val views = RemoteViews(context.packageName, R.layout.widget_status)
            views.setInt(R.id.widget_root, "setBackgroundColor", Color.argb(alpha, 4, 11, 20))
            val local = context.getSharedPreferences("nexus_local_state_v1", Context.MODE_PRIVATE)
            val maintenance = local.getBoolean("maintenance_active", false)
            val maintenanceNote = local.getString("maintenance_note", "").orEmpty()
            views.setTextViewText(
                R.id.widget_status,
                if (maintenance) "Bakım modu aktif${if (maintenanceNote.isNotBlank()) " · ${maintenanceNote.take(32)}" else ""}"
                else "Core: bağlantı bekleniyor"
            )
            val pending = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            manager.updateAppWidget(id, views)
        }
    }
}
