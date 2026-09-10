package com.mrrob.llmchat.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.mrrob.llmchat.MainActivity
import com.mrrob.llmchat.R

/** Home-screen widget: two big buttons - New Chat and Voice. */
class AsterWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_aster).apply {
                setOnClickPendingIntent(R.id.widget_new_chat, pending(context, "new_chat", 1))
                setOnClickPendingIntent(R.id.widget_voice, pending(context, "voice", 2))
            }
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    private fun pending(context: Context, action: String, code: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("aster_action", action)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
