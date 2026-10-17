package com.example.diary.data.focus

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import com.example.diary.R
import com.example.diary.data.local.AppDatabase
import com.example.diary.data.local.FocusSession
import com.example.diary.data.preferences.FocusPreferences
import com.example.diary.data.repository.FocusRepository
import com.example.diary.util.LocaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 番茄钟前台服务。
 *
 * 职责边界：只负责「让计时在离开 UI 后继续」与「到点结算」。
 * 计时真值是持久化的 FocusRecord 差值，不是这里的协程循环 ——
 * 循环被系统杀掉后，下一个 EXPIRE 广播或进程重建都能按差值恢复。
 */
class FocusService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private var ticker: Job? = null

    private lateinit var store: FocusSessionStore
    private lateinit var repository: FocusRepository
    private lateinit var noisePlayer: NoisePlayer
    private lateinit var ringer: FocusAlarmRinger
    private var enhancedNotification: Boolean = FocusPreferences.DEFAULT_NOTIFICATION_ENHANCED

    override fun onCreate() {
        super.onCreate()
        store = FocusSessionStore(this)
        repository = FocusRepository(AppDatabase.getInstance(this).focusDao(), this)
        noisePlayer = NoisePlayer(this)
        ringer = FocusAlarmRinger(this)
        FocusNotifier.ensureChannel(this)
        FocusRuntime.setServiceAlive(true)
        // 通知增强档是用户开关：异步读一次缓存进字段，别在主线程阻塞读 DataStore，
        // 也别为了它每秒重读
        scope.launch { enhancedNotification = FocusPreferences(this@FocusService).notificationEnhanced.first() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val taskId = intent?.getLongExtra(EXTRA_TASK_ID, -1L)?.takeIf { it > 0L }
        val title = intent?.getStringExtra(EXTRA_TASK_TITLE).orEmpty()
        val autoStartNext = intent?.getBooleanExtra(EXTRA_AUTO_START_NEXT, true) ?: true

        if (action != ACTION_STOP && action != ACTION_NOISE) {
            val seed = FocusRuntime.current() ?: FocusRecord(taskId = taskId, taskTitle = title)
            runCatching {
                startForeground(NOTIFICATION_ID, FocusNotifier.buildOngoing(this, seed, 0L, notificationEnhanced()))
            }
        }

        when (action) {
            ACTION_START -> scope.launch { handleStart(taskId, title, autoStartNext) }
            ACTION_PAUSE -> scope.launch { handlePause() }
            ACTION_RESUME -> scope.launch { handleResume() }
            ACTION_SKIP -> scope.launch { advance(completed = false, autoStartNext = autoStartNext) }
            ACTION_EXPIRE -> scope.launch { onAlarm(autoStartNext) }
            ACTION_STOP -> scope.launch { abandon(billPartial = true) }
            ACTION_NOISE -> scope.launch {
                // 正在计时时点顶栏图标切音色：要立刻换，别等下一段
                FocusRuntime.current()?.let { syncNoise(it) }
            }
            ACTION_STOP_ALARM -> ringer.stop()
            else -> scope.launch { adoptRestoredSession() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ticker?.cancel()
        noisePlayer.release()
        if (::ringer.isInitialized) ringer.stop()
        FocusRuntime.setServiceAlive(false)
        job.cancel()
        super.onDestroy()
    }

    // ---- 动作 ----

    private suspend fun handleStart(taskId: Long?, title: String, autoStartNext: Boolean) {
        val existing = FocusRuntime.current()
        // 已经在跑就只是接管，别把进度清零
        if (existing != null && existing.running) {
            syncAlarm(existing)
            resumeTicking()
            return
        }
        val segments = repository.currentSegments(this)
        val phase = existing?.phase ?: FocusSession.PHASE_FOCUS
        val cycleIndex = existing?.cycleIndex ?: 0
        val session = FocusTimer.beginSession(phase, cycleIndex, SystemClock.elapsedRealtime(), startNow = true)
        val record = (existing ?: FocusRecord()).copy(
            taskId = taskId ?: existing?.taskId,
            taskTitle = title.ifBlank { existing?.taskTitle.orEmpty() },
            startElapsed = session.startElapsed,
            pauseElapsed = 0L,
            pausedAccumMs = 0L,
            running = true,
            autoStartNext = autoStartNext,
            focusMinutes = (segments.focusMs / 60_000L).toInt(),
            shortBreakMinutes = (segments.shortBreakMs / 60_000L).toInt(),
            longBreakMinutes = (segments.longBreakMs / 60_000L).toInt(),
            longBreakInterval = segments.longBreakInterval,
            startWallClock = System.currentTimeMillis(),
            bootInstant = nowBootInstant()
        )
        publish(record)
        syncAlarm(record)
        syncNoise(record)
        resumeTicking()
    }

    private suspend fun handlePause() {
        val current = FocusRuntime.current() ?: return
        val session = FocusTimer.pause(current.toSession(), SystemClock.elapsedRealtime())
        val updated = current.copy(
            startElapsed = session.startElapsed,
            pauseElapsed = session.pauseElapsed,
            pausedAccumMs = session.pausedAccumMs,
            running = false
        )
        publish(updated)
        FocusAlarmScheduler.cancel(this, updated.taskId)
        ticker?.cancel()
        noisePlayer.fadeOutAndStop()
        // 已经把手机拿在手上操作了，到点铃声不再需要
        ringer.stop()
        refreshNotification(updated)
    }

    private suspend fun handleResume() {
        val current = FocusRuntime.current() ?: return handleStart(null, "", true)
        val session = FocusTimer.resume(current.toSession(), SystemClock.elapsedRealtime())
        val updated = current.copy(
            startElapsed = session.startElapsed,
            pauseElapsed = session.pauseElapsed,
            pausedAccumMs = session.pausedAccumMs,
            running = true
        )
        publish(updated)
        syncAlarm(updated)
        syncNoise(updated)
        resumeTicking()
    }

    /** 手动跳过本段：已流逝时长照常入账 */
    private suspend fun advance(completed: Boolean, autoStartNext: Boolean) {
        val current = FocusRuntime.current() ?: return
        settle(current, completed)
        val segments = current.segments()
        val (nextPhase, nextCycle) = FocusTimer.nextPhase(
            current.phase, current.cycleIndex, segments.longBreakInterval
        )
        val next = current.copy(
            phase = nextPhase,
            cycleIndex = nextCycle,
            startElapsed = if (autoStartNext) SystemClock.elapsedRealtime() else 0L,
            pauseElapsed = 0L,
            pausedAccumMs = 0L,
            running = false,
            startWallClock = System.currentTimeMillis(),
            bootInstant = nowBootInstant()
        )
        publish(next)
        FocusAlarmScheduler.cancel(this, current.taskId)
        ticker?.cancel()
        noisePlayer.fadeOutAndStop()
        announceSegmentEnd(current, nextPhase, segments)
        refreshNotification(next)
    }

    /** 闹钟/循环到点：先核对是否真的到点（降级路径会提前触发） */
    private suspend fun onAlarm(autoStartNext: Boolean) {
        val current = FocusRuntime.current() ?: return adoptRestoredSession()
        val segments = current.segments()
        val remaining = FocusTimer.remainingMs(current.toSession(), SystemClock.elapsedRealtime(), segments)
        if (remaining > 0L) {
            syncAlarm(current)
            resumeTicking()
            return
        }
        settle(current, completed = true)
        val (nextPhase, nextCycle) = FocusTimer.nextPhase(
            current.phase, current.cycleIndex, segments.longBreakInterval
        )
        val next = current.copy(
            phase = nextPhase,
            cycleIndex = nextCycle,
            startElapsed = if (autoStartNext) SystemClock.elapsedRealtime() else 0L,
            pauseElapsed = 0L,
            pausedAccumMs = 0L,
            running = autoStartNext,
            startWallClock = System.currentTimeMillis(),
            bootInstant = nowBootInstant()
        )
        publish(next)
        syncNoise(next)
        announceSegmentEnd(current, nextPhase, segments)
        if (autoStartNext) {
            syncAlarm(next)
            resumeTicking()
        } else {
            FocusAlarmScheduler.cancel(this, next.taskId)
            ticker?.cancel()
            refreshNotification(next)
        }
    }

    /** 放弃当前段：已流逝的时长照常入账 */
    private suspend fun abandon(billPartial: Boolean) {
        val current = FocusRuntime.current()
        if (current != null && billPartial) settle(current, completed = false)
        current?.let { FocusAlarmScheduler.cancel(this, it.taskId) }
        FocusRuntime.publish(null)
        noisePlayer.fadeOutAndStop()
        ringer.stop()
        store.clear()
        ticker?.cancel()
        FocusNotifier.cancelOngoing(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * 服务被拉起但运行态是空的（进程重启后只剩闹钟活着）：
     * 能恢复就恢复，恢复不了就把前台撤掉，别留一条空通知。
     */
    private suspend fun adoptRestoredSession() {
        val restored = store.load()
        if (restored == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        publish(restored)
        if (restored.running) {
            syncNoise(restored)
            syncAlarm(restored)
            resumeTicking()
        } else {
            refreshNotification(restored)
        }
    }

    // ---- 内部 ----

    /**
     * 一段结束时的提醒：只有专注段结束才强提醒（响铃 + 震动 + 亮屏，60 秒自动停），
     * 休息段结束只发一条通知——休息往往只有 5 分钟，弹屏响铃会变成骚扰。
     */
    private suspend fun announceSegmentEnd(
        ended: FocusRecord,
        nextPhase: Int,
        segments: FocusTimer.Segments
    ) {
        val prefs = FocusPreferences(this)
        val enabled = prefs.alarmEnabled.first()
        val strong = FocusAlarmRinger.shouldStrongRemind(ended.phase, enabled)
        val screenOn = strong && prefs.alarmScreenOn.first()
        if (strong) {
            ringer.start(
                volumePercent = prefs.alarmVolume.first(),
                vibrate = prefs.alarmVibrate.first(),
                vibrateMs = prefs.alarmVibrateMs.first().toLong(),
                vibrateGapMs = prefs.alarmVibrateGapMs.first().toLong(),
                amplitude = prefs.alarmAmplitude.first()
            )
        }
        FocusNotifier.showSegmentDone(
            context = this,
            record = ended,
            nextLabel = labelOf(this, nextPhase, segments),
            stopAction = strong,
            screenOn = screenOn
        )
    }

    /** 本段入账：不足一整段也按实际流逝时长算 */
    private suspend fun settle(record: FocusRecord, completed: Boolean) {
        val billable = FocusTimer.billableMs(
            record.toSession(), SystemClock.elapsedRealtime(), record.segments()
        )
        if (billable <= 0L) return
        repository.recordSession(record, billable, completed, System.currentTimeMillis())
    }

    private suspend fun publish(record: FocusRecord) {
        FocusRuntime.publish(record)
        store.save(record)
    }

    /** 白噪音只跟着专注段：暂停/休息/结束都淡出 */
    private suspend fun syncNoise(record: FocusRecord) {
        if (!record.running || record.phase != FocusSession.PHASE_FOCUS) {
            noisePlayer.fadeOutAndStop()
            return
        }
        val prefs = FocusPreferences(this)
        val kind = WhiteNoise.Kind.from(prefs.noiseKind.first())
        noisePlayer.play(kind, prefs.noiseVolume.first(), CustomNoiseStore(this).currentFile())
    }

    private fun syncAlarm(record: FocusRecord) {
        if (!record.running || record.startElapsed == 0L) {
            FocusAlarmScheduler.cancel(this, record.taskId)
            return
        }
        val remaining = FocusTimer.remainingMs(record.toSession(), SystemClock.elapsedRealtime(), record.segments())
        FocusAlarmScheduler.schedule(
            this, record.taskId,
            SystemClock.elapsedRealtime() + remaining.coerceAtLeast(0L)
        )
    }

    private fun resumeTicking() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val current = FocusRuntime.current() ?: break
                if (!current.running) break
                val remaining = FocusTimer.remainingMs(
                    current.toSession(), SystemClock.elapsedRealtime(), current.segments()
                )
                if (remaining <= 0L) {
                    onAlarm(current.autoStartNext)
                    break
                }
                refreshNotification(current)
                delay(TICK_MS)
            }
        }
    }

    private fun refreshNotification(record: FocusRecord) {
        val remaining = FocusTimer.remainingMs(record.toSession(), SystemClock.elapsedRealtime(), record.segments())
        runCatching {
            startForeground(
                NOTIFICATION_ID,
                FocusNotifier.buildOngoing(this, record, remaining, notificationEnhanced())
            )
        }
    }

    private fun labelOf(context: Context, phase: Int, segments: FocusTimer.Segments): String {
        val minutes = FocusTimer.phaseLengthMs(phase, segments) / 60_000L
        val name = LocaleHelper.wrap(context).let {
            when (phase) {
                FocusSession.PHASE_FOCUS -> it.getString(R.string.focus_phase_focus)
                FocusSession.PHASE_LONG_BREAK -> it.getString(R.string.focus_phase_long_break)
                else -> it.getString(R.string.focus_phase_short_break)
            }
        }
        return "$name ${minutes}m"
    }

    private fun notificationEnhanced(): Boolean = enhancedNotification

    private fun nowBootInstant() =
        FocusTimer.bootInstant(System.currentTimeMillis(), SystemClock.elapsedRealtime())

    companion object {
        const val ACTION_START = "com.example.diary.action.FOCUS_START"
        const val ACTION_PAUSE = "com.example.diary.action.FOCUS_PAUSE"
        const val ACTION_RESUME = "com.example.diary.action.FOCUS_RESUME"
        const val ACTION_SKIP = "com.example.diary.action.FOCUS_SKIP"
        const val ACTION_EXPIRE = "com.example.diary.action.FOCUS_EXPIRE"
        const val ACTION_STOP = "com.example.diary.action.FOCUS_STOP"
        const val ACTION_NOISE = "com.example.diary.action.FOCUS_NOISE"
        const val ACTION_STOP_ALARM = "com.example.diary.action.FOCUS_STOP_ALARM"
        const val EXTRA_TASK_ID = "focus_task_id"
        const val EXTRA_TASK_TITLE = "focus_task_title"
        const val EXTRA_AUTO_START_NEXT = "focus_auto_start_next"

        private const val NOTIFICATION_ID = 7101
        private const val TICK_MS = 1000L
    }
}