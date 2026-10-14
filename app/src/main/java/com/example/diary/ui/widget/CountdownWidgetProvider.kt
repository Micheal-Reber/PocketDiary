package com.example.diary.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.example.diary.MainActivity
import com.example.diary.R
import com.example.diary.data.countdown.DateMath
import com.example.diary.data.local.AppDatabase
import com.example.diary.data.local.CountdownEvent
import com.example.diary.data.repository.CountdownRepository
import com.example.diary.ui.countdown.eventAccent
import com.example.diary.ui.countdown.weekdayLabel
import com.example.diary.util.LocaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class CountdownWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        runAsync(context) { appContext ->
            CountdownWidgetRefresh.scheduleMidnight(appContext)
            renderAll(appContext, appWidgetIds.toList())
        }
    }

    override fun onEnabled(context: Context) {
        CountdownWidgetRefresh.scheduleMidnight(context)
    }

    override fun onDisabled(context: Context) {
        CountdownWidgetRefresh.cancelMidnight(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            CountdownWidgetRefresh.ACTION_MIDNIGHT,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_USER_PRESENT -> runAsync(context) { appContext ->
                CountdownWidgetRefresh.scheduleMidnight(appContext)
                renderAll(appContext, widgetIds(appContext))
            }
        }
    }

    private fun widgetIds(context: Context): List<Int> = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, CountdownWidgetProvider::class.java))
        .toList()

    private suspend fun renderAll(context: Context, widgetIds: List<Int>) {
        val events = repository(context).getAllOnce()
        val today = LocalDate.now()
        val selection = CountdownWidgetState(context)
        val manager = AppWidgetManager.getInstance(context)
        widgetIds.forEach { id ->
            val event = CountdownWidgetModel.resolveEvent(events, today, selection.read(id))
            val views = buildViews(context, id, event)
            runCatching { manager.updateAppWidget(id, views) }
                .onFailure { Log.e(TAG, "Failed to update widget $id", it) }
        }
    }

    private suspend fun buildViews(
        context: Context,
        widgetId: Int,
        event: CountdownEvent?
    ): RemoteViews {
        val localized = LocaleHelper.wrap(context)
        val root = RemoteViews(context.packageName, R.layout.countdown_widget_v5)

        root.setTextViewText(R.id.cd_empty_title, localized.getString(R.string.cd_widget_empty))
        root.setTextViewText(R.id.cd_empty_hint, localized.getString(R.string.cd_widget_empty_hint))

        if (event == null) {
            root.setViewVisibility(R.id.cd_content, View.GONE)
            root.setViewVisibility(R.id.cd_empty, View.VISIBLE)
            root.setOnClickPendingIntent(R.id.cd_empty, openPickerIntent(context, widgetId))
            return root
        }

        val today = LocalDate.now()
        val state = CountdownWidgetModel.stateOf(event, today)
        val accent = eventAccent(event.colorIndex, state).toArgb()

        val isToday = state is DateMath.CountState.Today
        val number = when (state) {
            is DateMath.CountState.Today -> localized.getString(R.string.cd_badge_today)
            is DateMath.CountState.Countdown -> state.days.toString()
            is DateMath.CountState.Countup -> state.days.toString()
        }
        val unit = if (isToday) "" else localized.getString(R.string.cd_unit_days)

        val label = localized.getString(
            when {
                isToday -> R.string.cd_widget_label_today
                state is DateMath.CountState.Countup -> R.string.cd_widget_label_countup
                else -> R.string.cd_widget_label_countdown
            },
            event.name
        )

        val target = runCatching { LocalDate.parse(event.date) }.getOrNull()
        val datePattern = localized.getString(R.string.cd_widget_date_pattern)
        val dateText = target?.let {
            runCatching {
                it.format(DateTimeFormatter.ofPattern(datePattern, LocaleHelper.resolve()))
            }.getOrNull()
        }.orEmpty()

        root.setTextViewText(R.id.cd_label, label)
        root.setTextViewText(R.id.cd_number, number)
        root.setTextViewText(R.id.cd_unit, unit)
        root.setViewVisibility(R.id.cd_unit, if (isToday) View.GONE else View.VISIBLE)
        root.setTextViewText(R.id.cd_date, dateText)
        root.setTextViewText(R.id.cd_weekday, target?.let { weekdayLabel(localized, it) }.orEmpty())

        root.setTextColor(R.id.cd_number, accent)

        root.setViewVisibility(R.id.cd_content, View.VISIBLE)
        root.setViewVisibility(R.id.cd_empty, View.GONE)
        root.setOnClickPendingIntent(R.id.cd_content, openPickerIntent(context, widgetId))
        return root
    }

    private fun repository(context: Context): CountdownRepository =
        CountdownRepository(AppDatabase.getInstance(context).countdownDao(), context)

    private fun openPickerIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_COUNTDOWN_PICK, true)
            putExtra(EXTRA_WIDGET_ID, widgetId)
        }
        return PendingIntent.getActivity(context, widgetId, intent, PENDING_FLAGS)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val selection = CountdownWidgetState(context)
        appWidgetIds.forEach { selection.clear(it) }
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        val selection = CountdownWidgetState(context)
        oldWidgetIds.forEachIndexed { index, oldId ->
            val eventId = selection.read(oldId)
            selection.clear(oldId)
            if (eventId > 0L) selection.write(newWidgetIds[index], eventId)
        }
    }

    private fun runAsync(context: Context, block: suspend (Context) -> Unit) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                block(appContext)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Widget update failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_COUNTDOWN_PICK = "open_countdown_pick"
        const val EXTRA_WIDGET_ID = "appwidget_id"
        private const val TAG = "CountdownWidget"
    }
}

private val PENDING_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
