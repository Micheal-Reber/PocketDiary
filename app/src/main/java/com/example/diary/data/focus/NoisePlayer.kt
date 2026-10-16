package com.example.diary.data.focus

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper

/**
 * 专注白噪音播放器：静态缓冲 + 无限循环点，天然无缝；
 * 音量与淡入淡出独立于系统媒体音量，挂在番茄钟前台服务里息屏也能继续。
 */
class NoisePlayer(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private var track: AudioTrack? = null
    private var player: MediaPlayer? = null
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

    fun play(kind: WhiteNoise.Kind, volumePercent: Int, customFile: java.io.File? = null) {
        if (kind == WhiteNoise.Kind.OFF) {
            stop()
            return
        }
        if (kind == WhiteNoise.Kind.CUSTOM) {
            playCustom(customFile, volumePercent)
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

    /**
     * 用户自选音频：MediaPlayer 循环播。短文件的循环点会有一丝接缝，
     * 这是素材自带的限制，合成白噪音那条路径才真正无缝。
     */
    private fun playCustom(file: java.io.File?, volumePercent: Int) {
        if (file == null || !file.isFile) {
            stop()
            return
        }
        if (player != null && currentKind == WhiteNoise.Kind.CUSTOM) {
            setVolume(volumePercent)
            return
        }
        stop()
        currentKind = WhiteNoise.Kind.CUSTOM
        requestAudioFocus()
        val created = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(file.absolutePath)
                isLooping = true
                setVolume(0f, 0f)
                setOnPreparedListener { it.start() }
                setOnErrorListener { _, _, _ -> true }
                prepareAsync()
            }
        }.getOrNull() ?: return
        player = created
        currentVolume = 0f
        fadeTo(volumeScale(volumePercent), FADE_IN_MS)
    }

    fun setVolume(volumePercent: Int) {
        val target = volumeScale(volumePercent)
        currentVolume = target
        track?.setVolume(target)
        player?.setVolume(target, target)
    }

    fun fadeOutAndStop() {
        if (track == null && player == null) return
        fadeTo(0f, FADE_OUT_MS) { releaseSources() }
    }

    fun stop() {
        fadeRunnable?.let(handler::removeCallbacks)
        fadeRunnable = null
        releaseSources()
    }

    fun release() = stop()

    private fun releaseSources() {
        track?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        track = null
        player = null
        currentVolume = 0f
        currentKind = WhiteNoise.Kind.OFF
        abandonAudioFocus()
    }

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
        val current = track ?: player
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
                when (current) {
                    is AudioTrack -> current.setVolume(currentVolume)
                    is MediaPlayer -> current.setVolume(currentVolume, currentVolume)
                }
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