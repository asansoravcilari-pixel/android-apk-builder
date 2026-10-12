package com.nexus.control
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
class StatusWidget: AppWidgetProvider() {
 override fun onUpdate(context:Context, manager:AppWidgetManager, ids:IntArray) {
  ids.forEach { id ->
   val views=RemoteViews(context.packageName,R.layout.widget_status)
   val intent=Intent(context,MainActivity::class.java)
   views.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
   manager.updateAppWidget(id,views)
  }
 }
}
