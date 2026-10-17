package com.example.diary.data.focus

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.diary.data.local.FocusSession

/**
 * 到点提醒：铃声 + 震动，60 秒后自动停。
 *
 * 思路参考 nsh07/Tomato 的 TimerService.startAlarm()（GPL-3.0，只借鉴设计）：
 * 媒体播放器播系统默认闹钟音、震动波形可配、到时强制亮屏、响铃期间通知按钮换成「停止」，
 * 并且必须自动停——否则用户没听见时会一直响。
 *
 * 与 Tomato 的差别：**只在专注段结束时强提醒**，休息段结束只发通知。
 * 休息段往往只有 5 分钟，弹屏加响铃会变成骚扰；要提醒的是「该休息了」。
 */
class FocusAlarmRinger(context: Context) {

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private val autoStop = Runnable { stop() }

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

private var vibrating = false

val isRinging: Boolean
        get() = player?.isPlaying == true || vibrating

    fun start(
        volumePercent: Int,
        vibrate: Boolean,
        vibrateMs: Long,
        vibrateGapMs: Long,
        amplitude: Int
    ) {
        stop()
        if (volumePercent > 0) startTone(volumePercent)
        if (vibrate && vibrator?.hasVibrator() == true) startVibration(vibrateMs, vibrateGapMs, amplitude)
        handler.postDelayed(autoStop, AUTO_STOP_MS)
    }

    private fun startTone(volumePercent: Int) {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(appContext, uri)
                isLooping = true
                setVolume(volumePercent / 100f, volumePercent / 100f)
                prepare()
                start()
            }
        }.getOrNull()
    }

    private fun startVibration(onMs: Long, gapMs: Long, amplitude: Int) {
        val device = vibrator ?: return
        val (timings, amplitudes) = waveform(onMs, gapMs, amplitude)
        runCatching {
            device.vibrate(VibrationEffect.createWaveform(timings, amplitudes, REPEAT))
            vibrating = true
        }
    }

    fun stop() {
        handler.removeCallbacks(autoStop)
        runCatching {
            player?.let { if (it.isPlaying) it.stop() }
        }
        runCatching { player?.release() }
        player = null
        runCatching { vibrator?.cancel() }
        vibrating = false
    }

    companion object {
        /** 到点提醒最多响这么久，避免用户没听见时一直吵 */
        const val AUTO_STOP_MS = 60_000L
        private const val REPEAT = 1

        /**
         * 震动波形：震 → 停 → 震 → 停，重复一轮。
         * 纯函数便于单测（形状对不对是这类代码最容易写错的地方）。
         */
        fun waveform(onMs: Long, gapMs: Long, amplitude: Int): Pair<LongArray, IntArray> {
            val on = onMs.coerceIn(50L, 2000L)
            val gap = gapMs.coerceIn(0L, 2000L)
            val amp = amplitude.coerceIn(0, 255)
            return longArrayOf(0L, on, gap, on) to intArrayOf(0, amp, 0, amp)
        }

        /**
         * 要不要强提醒（响铃 + 震动 + 亮屏）：只有专注段结束才要，
         * 休息段结束是「该回来干活了」，不打断。
         */
        fun shouldStrongRemind(endedPhase: Int, alarmEnabled: Boolean): Boolean =
            alarmEnabled && endedPhase == FocusSession.PHASE_FOCUS
    }
}