package com.example.diary.data.focus

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper

/**
 * 专注白噪音播放器：静态缓冲 + 无限循环点，天然无缝；
 * 音量与淡入淡出独立于系统媒体音量，挂在番茄钟前台服务里息屏也能继续。
 */
class NoisePlayer(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private var track: AudioTrack? = null
    private var focusRequest: AudioFocusRequest? = null
    private var fadeRunnable: Runnable? = null
    private var currentVolume = 0f
    private var currentKind = WhiteNoise.Kind.OFF

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> fadeOutAndStop()
            else -> Unit
        }
    }

    fun play(kind: WhiteNoise.Kind, volumePercent: Int) {
        if (kind == WhiteNoise.Kind.OFF) {
            stop()
            return
        }
        // 同一种音色只调音量；换音色才重建缓冲（否则点一下听不出变化）
        if (track != null && currentKind == kind) {
            setVolume(volumePercent)
            return
        }
        stop()
        val samples = WhiteNoise.generate(kind)
        if (samples.isEmpty()) return
        currentKind = kind
        requestAudioFocus()
        val created = runCatching { buildTrack(samples) }.getOrNull() ?: return
        track = created
        currentVolume = 0f
        created.setVolume(0f)
        runCatching {
            created.setLoopPoints(0, samples.size, -1)
            created.play()
        }
        fadeTo(volumeScale(volumePercent), FADE_IN_MS)
    }

    fun setVolume(volumePercent: Int) {
        track?.setVolume(volumeScale(volumePercent))
    }

    fun fadeOutAndStop() {
        val current = track ?: return
        fadeTo(0f, FADE_OUT_MS) {
            runCatching { current.stop() }
            runCatching { current.release() }
            if (track === current) track = null
            currentVolume = 0f
            abandonAudioFocus()
        }
    }

    fun stop() {
        val current = track ?: return
        fadeRunnable?.let(handler::removeCallbacks)
        fadeRunnable = null
        runCatching { current.stop() }
        runCatching { current.release() }
        track = null
        currentVolume = 0f
        currentKind = WhiteNoise.Kind.OFF
        abandonAudioFocus()
    }

    fun release() = stop()

    private fun buildTrack(samples: ShortArray): AudioTrack {
        val minBytes = AudioTrack.getMinBufferSize(
            WhiteNoise.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bytes = samples.size * 2
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(WhiteNoise.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBytes, bytes))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(samples, 0, samples.size)
        return track
    }

    private fun fadeTo(target: Float, durationMs: Long, onEnd: (() -> Unit)? = null) {
        val current = track
        if (current == null) {
            onEnd?.invoke()
            return
        }
        fadeRunnable?.let(handler::removeCallbacks)
        val from = currentVolume
        val steps = 12
        var step = 0
        val runnable = object : Runnable {
            override fun run() {
                step++
                val progress = (step.toFloat() / steps).coerceAtMost(1f)
                currentVolume = from + (target - from) * progress
                current.setVolume(currentVolume)
                if (step < steps) {
                    handler.postDelayed(this, durationMs / steps)
                } else {
                    fadeRunnable = null
                    onEnd?.invoke()
                }
            }
        }
        fadeRunnable = runnable
        handler.postDelayed(runnable, durationMs / steps)
    }

    private fun requestAudioFocus() {
        if (focusRequest != null) return
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        audioManager.requestAudioFocus(request)
        focusRequest = request
    }

    private fun abandonAudioFocus() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        focusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
        focusRequest = null
    }

    private fun volumeScale(percent: Int): Float =
        (percent.coerceIn(0, 100) / 100f).coerceIn(0f, 1f)

    private companion object {
        const val FADE_IN_MS = 2000L
        const val FADE_OUT_MS = 1200L
    }
}