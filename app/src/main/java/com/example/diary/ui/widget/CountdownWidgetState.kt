package com.example.diary.ui.widget

import android.content.Context

class CountdownWidgetState(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun read(appWidgetId: Int): Selection =
        Selection(
            eventId = prefs.getLong(keyEvent(appWidgetId), 0L),
            index = prefs.getInt(keyIndex(appWidgetId), 0)
        )

    fun write(appWidgetId: Int, eventId: Long, index: Int) {
        prefs.edit()
            .putLong(keyEvent(appWidgetId), eventId)
            .putInt(keyIndex(appWidgetId), index)
            .apply()
    }

    fun clear(appWidgetId: Int) {
        prefs.edit()
            .remove(keyEvent(appWidgetId))
            .remove(keyIndex(appWidgetId))
            .apply()
    }

    data class Selection(val eventId: Long, val index: Int)

    private fun keyEvent(appWidgetId: Int) = "eid_$appWidgetId"
    private fun keyIndex(appWidgetId: Int) = "idx_$appWidgetId"

    private companion object {
        const val FILE = "countdown_widget_state"
    }
}
