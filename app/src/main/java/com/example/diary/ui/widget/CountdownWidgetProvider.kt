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
import com.example.diary.util.DateUtils
import com.example.diary.util.LocaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class CountdownWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        runAsync(context) { appContext ->
            CountdownWidgetRefresh.scheduleMidnight(appContext)
            renderAll(appContext, appWidgetIds.toList())
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val state = CountdownWidgetState(context)
        appWidgetIds.forEach { state.clear(it) }
    }

    override fun onEnabled(context: Context) {
        CountdownWidgetRefresh.scheduleMidnight(context)
    }

    override fun onDisabled(context: Context) {
        CountdownWidgetRefresh.cancelMidnight(context)
    }

    override fun onRestored(context: Context, oldIds: IntArray, newIds: IntArray) {
        if (oldIds.size != newIds.size) return
        val state = CountdownWidgetState(context)
        oldIds.indices.forEach { i ->
            val sel = state.read(oldIds[i])
            state.clear(oldIds[i])
            state.write(newIds[i], sel.eventId, sel.index)
        }
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
            CountdownWidgetRefresh.ACTION_NEXT -> shiftAsync(context, intent, 1)
            CountdownWidgetRefresh.ACTION_PREV -> shiftAsync(context, intent, -1)
        }
    }

    private fun shiftAsync(context: Context, intent: Intent, delta: Int) {
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        runAsync(context) { appContext -> shift(appContext, widgetId, delta) }
    }

    private fun widgetIds(context: Context): List<Int> = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, CountdownWidgetProvider::class.java))
        .toList()

    private suspend fun shift(context: Context, widgetId: Int, delta: Int) {
        val events = CountdownWidgetModel.visibleEvents(repository(context).getAllOnce())
        if (events.isEmpty()) return
        val stateStore = CountdownWidgetState(context)
        val sel = stateStore.read(widgetId)
        val current = CountdownWidgetModel.resolveIndex(events, sel.eventId, sel.index)
        val next = ((current + delta) % events.size + events.size) % events.size
        stateStore.write(widgetId, events[next].id, next)
        renderOne(context, widgetId, events, next)
    }

    private suspend fun renderAll(context: Context, widgetIds: List<Int>) {
        val events = CountdownWidgetModel.visibleEvents(repository(context).getAllOnce())
        val stateStore = CountdownWidgetState(context)
        val manager = AppWidgetManager.getInstance(context)
        widgetIds.forEach { id ->
            val sel = stateStore.read(id)
            val index = CountdownWidgetModel.resolveIndex(events, sel.eventId, sel.index)
            if (events.isEmpty()) {
                stateStore.write(id, 0L, 0)
            } else {
                stateStore.write(id, events[index].id, index)
            }
            val views = buildViews(context, id, events, index)
            runCatching { manager.updateAppWidget(id, views) }
                .onFailure { Log.e(TAG, "Failed to update widget $id", it) }
        }
    }

    private suspend fun renderOne(context: Context, widgetId: Int, events: List<CountdownEvent>, index: Int) {
        val views = buildViews(context, widgetId, events, index)
        runCatching { AppWidgetManager.getInstance(context).updateAppWidget(widgetId, views) }
            .onFailure { Log.e(TAG, "Failed to update widget $widgetId", it) }
    }

    private suspend fun buildViews(
        context: Context,
        widgetId: Int,
        events: List<CountdownEvent>,
        index: Int
    ): RemoteViews {
        val localized = LocaleHelper.wrap(context)
        val root = RemoteViews(context.packageName, R.layout.countdown_widget)

        root.setTextViewText(R.id.cd_empty_title, localized.getString(R.string.cd_widget_empty))
        root.setTextViewText(R.id.cd_empty_hint, localized.getString(R.string.cd_widget_empty_hint))
        root.setTextViewText(R.id.cd_prev, CHEVRON_PREV)
        root.setTextViewText(R.id.cd_next, CHEVRON_NEXT)
        root.setContentDescription(R.id.cd_prev, localized.getString(R.string.cd_widget_prev))
        root.setContentDescription(R.id.cd_next, localized.getString(R.string.cd_widget_next))

        if (events.isEmpty()) {
            root.setViewVisibility(R.id.cd_content, View.GONE)
            root.setViewVisibility(R.id.cd_empty, View.VISIBLE)
            root.setViewVisibility(R.id.cd_prev, View.GONE)
            root.setViewVisibility(R.id.cd_next, View.GONE)
            root.setOnClickPendingIntent(R.id.cd_empty, openListIntent(context, widgetId))
            return root
        }

        val event = events[index]
        val today = LocalDate.now()
        val state = CountdownWidgetModel.stateOf(event, today)
        val accent = eventAccent(event.colorIndex, state).toArgb()

        val number = when (state) {
            is DateMath.CountState.Today -> ""
            is DateMath.CountState.Countdown -> state.days.toString()
            is DateMath.CountState.Countup -> state.days.toString()
        }
        val unit = if (state is DateMath.CountState.Today) localized.getString(R.string.cd_badge_today)
        else localized.getString(R.string.cd_unit_days)

        val target = runCatching { LocalDate.parse(event.date) }.getOrNull()
        val dateLine = if (target == null) "" else localized.getString(
            R.string.cd_widget_date_line,
            DateUtils.formatDate(target),
            weekdayLabel(localized, target)
        )

        root.setTextViewText(R.id.cd_header_title, event.name)
        root.setTextViewText(R.id.cd_number, number)
        root.setTextViewText(R.id.cd_unit, unit)
        root.setTextViewText(R.id.cd_date, dateLine)

        applyResponsiveSize(context, widgetId, root, number.length)
        root.setInt(R.id.cd_header_bar, "setColorFilter", accent)

        root.setViewVisibility(R.id.cd_content, View.VISIBLE)
        root.setViewVisibility(R.id.cd_empty, View.GONE)
        root.setOnClickPendingIntent(R.id.cd_content, openDetailIntent(context, event.id))

        val multi = events.size > 1
        root.setViewVisibility(R.id.cd_prev, if (multi) View.VISIBLE else View.GONE)
        root.setViewVisibility(R.id.cd_next, if (multi) View.VISIBLE else View.GONE)
        if (multi) {
            root.setOnClickPendingIntent(
                R.id.cd_prev,
                navIntent(context, widgetId, CountdownWidgetRefresh.ACTION_PREV)
            )
            root.setOnClickPendingIntent(
                R.id.cd_next,
                navIntent(context, widgetId, CountdownWidgetRefresh.ACTION_NEXT)
            )
        }
        return root
    }

    private fun applyResponsiveSize(
        context: Context,
        widgetId: Int,
        root: RemoteViews,
        numberLen: Int
    ) {
        val metrics = context.resources.displayMetrics
        val options = runCatching {
            AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
        }.getOrNull()
        val widthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
        val heightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
        val numberSp = CountdownWidgetModel.responsiveNumberSp(
            numberLen = numberLen,
            widthDp = widthDp,
            heightDp = heightDp,
            density = metrics.density,
            scaledDensity = metrics.scaledDensity
        )
        root.setFloat(R.id.cd_number, "setTextSize", numberSp)
        root.setFloat(R.id.cd_unit, "setTextSize", numberSp * CountdownWidgetModel.UNIT_SP_RATIO)
    }

    private fun repository(context: Context): CountdownRepository =
        CountdownRepository(AppDatabase.getInstance(context).countdownDao(), context)

    private fun openDetailIntent(context: Context, eventId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_COUNTDOWN_ID, eventId)
        }
        return PendingIntent.getActivity(context, eventId.toInt(), intent, PENDING_FLAGS)
    }

    private fun openListIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_COUNTDOWN_TAB, true)
        }
        return PendingIntent.getActivity(context, widgetId, intent, PENDING_FLAGS)
    }

    private fun navIntent(context: Context, widgetId: Int, action: String): PendingIntent {
        val intent = Intent(context, CountdownWidgetProvider::class.java).apply {
            this.action = action
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        return PendingIntent.getBroadcast(context, widgetId, intent, PENDING_FLAGS)
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
        const val EXTRA_OPEN_COUNTDOWN_ID = "open_countdown_id"
        const val EXTRA_OPEN_COUNTDOWN_TAB = "open_countdown_tab"
        private const val TAG = "CountdownWidget"
        private const val CHEVRON_PREV = "‹"
        private const val CHEVRON_NEXT = "›"
    }
}

private val PENDING_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
