package com.example.diary.ui.widget

import android.content.Context

class CountdownWidgetState(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun read(appWidgetId: Int): Long = prefs.getLong(keyEvent(appWidgetId), 0L)

    fun write(appWidgetId: Int, eventId: Long) {
        prefs.edit().putLong(keyEvent(appWidgetId), eventId).apply()
    }

    fun clear(appWidgetId: Int) {
        prefs.edit().remove(keyEvent(appWidgetId)).apply()
    }

    private fun keyEvent(appWidgetId: Int) = "eid_$appWidgetId"

    private companion object {
        const val FILE = "countdown_widget_state"
    }
}
