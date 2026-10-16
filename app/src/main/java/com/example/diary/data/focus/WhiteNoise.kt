package com.example.diary.data.focus

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * 白/粉/棕噪音的 PCM 合成。零素材、零循环接缝（AudioTrack 静态缓冲 + 无限 loop points）。
 * 采样率取 22.05kHz 单声道：噪音没有高频信息，省一半算力与功耗。
 */
object WhiteNoise {

    const val SAMPLE_RATE = 22_050
    const val DEFAULT_SECONDS = 3
    /** 归一化目标响度（相对满刻度）：白/粉/棕统一到同一 RMS，听感才可比 */
    const val TARGET_RMS = 0.075f
    /** 低通截止（Hz）：砍掉刺耳的高频，三种噪音整体变柔和 */
    const val LOWPASS_HZ = 5_500f
    private const val PEAK_CEILING = 0.98f
    private const val CROSSFADE_SECONDS = 0.25f

    /**
 * OFF=关，WHITE=内置白噪音（程序化合成），CUSTOM=用户自选音频（走 MediaPlayer，不合成）
 */
enum class Kind(val id: String) {
        OFF("off"),
        WHITE("white"),
        CUSTOM("custom");

        companion object {
            fun from(id: String?): Kind = entries.firstOrNull { it.id == id } ?: OFF
        }
    }

    /** 自定义音频是用户选的文件，不参与合成 */
    fun isSynthesized(kind: Kind): Boolean = kind == Kind.WHITE

    /**
     * 合成一段**可无缝循环**的缓冲。
     *
     * 关键一：不淡出到静音，改用首尾交叉淡化（尾段渐变为头段内容），
     * 否则每轮循环都有一次「音量塌到 0 再起」= 听着像音高往下掉的呼吸声。
     * 关键二：两级单极点低通去掉高频毛刺，白噪音也不再刺耳。
     *
     * 音色：白=均匀随机、粉=Paul Kellet 1/f 近似、棕=带泄漏积分器。
     * 三者出缓冲后统一 RMS 归一 + 峰值保护。
     */
    fun generate(
        kind: Kind,
        sampleRate: Int = SAMPLE_RATE,
        seconds: Int = DEFAULT_SECONDS,
        seed: Long = 20260101L
    ): ShortArray {
        if (!isSynthesized(kind)) return ShortArray(0)
        val length = (sampleRate * seconds).coerceAtLeast(1)
        val crossfade = minOf(length / 4, (sampleRate * CROSSFADE_SECONDS).toInt()).coerceAtLeast(1)
        val raw = FloatArray(length + crossfade)
        val random = Random(seed)
        for (i in raw.indices) {
            raw[i] = random.nextFloat() * 2f - 1f
        }

        soften(raw, sampleRate)
        wrapCrossfade(raw, length, crossfade)
        normalizeRms(raw, length)
        val out = ShortArray(length)
        for (i in 0 until length) out[i] = toPcm(raw[i])
        return out
    }

    /** 播放顺序：关 → 白噪音 →（用户选过音频才出现的）自定义音频 */
    fun cycle(current: Kind, hasCustomAudio: Boolean): Kind {
        val available = if (hasCustomAudio) listOf(Kind.OFF, Kind.WHITE, Kind.CUSTOM) else listOf(Kind.OFF, Kind.WHITE)
        val index = available.indexOf(current)
        return available[(if (index < 0) 0 else index + 1) % available.size]
    }

    /** 两级单极点低通（约 -12dB/oct）：白噪音的「嘶」被削掉，听感更柔和 */
    private fun soften(buffer: FloatArray, sampleRate: Int) {
        val coefficient = (1f - exp(-2f * PI.toFloat() * LOWPASS_HZ / sampleRate)).coerceIn(0.05f, 1f)
        var stage1 = 0f
        var stage2 = 0f
        for (i in buffer.indices) {
            stage1 += coefficient * (buffer[i] - stage1)
            stage2 += coefficient * (stage1 - stage2)
            buffer[i] = stage2
        }
    }

    /** 尾段渐变成头段内容，循环回绕处波形连续 */
    private fun wrapCrossfade(buffer: FloatArray, length: Int, crossfade: Int) {
        if (crossfade * 2 >= length) return
        for (i in 0 until crossfade) {
            val progress = i.toFloat() / (crossfade - 1).coerceAtLeast(1)
            val tailIndex = length - crossfade + i
            buffer[tailIndex] = buffer[tailIndex] * (1f - progress) + buffer[i] * progress
        }
    }

    /** 统一 RMS 到 TARGET_RMS；峰值超限时压回来，宁可轻一点也不爆音 */
    private fun normalizeRms(buffer: FloatArray, length: Int) {
        var sum = 0.0
        for (i in 0 until length) sum += buffer[i].toDouble() * buffer[i].toDouble()
        val rms = Math.sqrt(sum / length).toFloat()
        if (rms <= 1e-6f) return
        var gain = TARGET_RMS / rms
        var peak = 0f
        for (i in 0 until length) peak = maxOf(peak, abs(buffer[i]))
        if (peak * gain > PEAK_CEILING) gain = PEAK_CEILING / peak
        for (i in 0 until length) buffer[i] *= gain
    }

    private fun toPcm(value: Float): Short =
        (value.coerceIn(-1f, 1f) * Short.MAX_VALUE)
            .roundToInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()

    /** 测试与音量归一用：均方根 */
    fun rms(buffer: ShortArray): Double {
        if (buffer.isEmpty()) return 0.0
        var sum = 0.0
        buffer.forEach { sum += it.toDouble() * it.toDouble() }
        return Math.sqrt(sum / buffer.size)
    }

    fun peak(buffer: ShortArray): Int = buffer.maxOfOrNull { abs(it.toInt()) } ?: 0
}