package com.example.diary.data.focus

import java.util.Random
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 白/粉/棕噪音的 PCM 合成。零素材、零循环接缝（AudioTrack 静态缓冲 + 无限 loop points）。
 * 采样率取 22.05kHz 单声道：噪音没有高频信息，省一半算力与功耗。
 */
object WhiteNoise {

    const val SAMPLE_RATE = 22_050
    const val DEFAULT_SECONDS = 3
    /** 归一化目标响度（相对满刻度）：白/粉/棕统一到同一 RMS，听感才可比 */
    const val TARGET_RMS = 0.085f
    private const val PEAK_CEILING = 0.98f

    enum class Kind(val id: String) {
        OFF("off"),
        WHITE("white"),
        PINK("pink"),
        BROWN("brown");

        companion object {
            fun from(id: String?): Kind = entries.firstOrNull { it.id == id } ?: OFF
        }
    }

    /**
     * 合成一段循环缓冲。首尾各有淡入淡出，避免 AudioTrack loop 回绕时的爆音。
     * 粉噪用 Paul Kellet 的 1/f 近似，棕噪用带泄漏的积分器（防直流漂移）。
     * 三种音色各自能量差很多，出缓冲后统一做 RMS 归一 + 峰值保护。
     */
    fun generate(
        kind: Kind,
        sampleRate: Int = SAMPLE_RATE,
        seconds: Int = DEFAULT_SECONDS,
        seed: Long = 20260101L
    ): ShortArray {
        if (kind == Kind.OFF) return ShortArray(0)
        val count = (sampleRate * seconds).coerceAtLeast(1)
        val raw = FloatArray(count)
        val random = Random(seed)
        when (kind) {
            Kind.WHITE -> for (i in 0 until count) {
                raw[i] = random.nextFloat() * 2f - 1f
            }
            Kind.BROWN -> {
                var last = 0f
                for (i in 0 until count) {
                    val white = random.nextFloat() * 2f - 1f
                    last = (last + 0.02f * white) / 1.02f
                    raw[i] = last * 3.5f
                }
            }
            Kind.PINK -> {
                var b0 = 0f; var b1 = 0f; var b2 = 0f
                var b3 = 0f; var b4 = 0f; var b5 = 0f; var b6 = 0f
                for (i in 0 until count) {
                    val white = random.nextFloat() * 2f - 1f
                    b0 = 0.99886f * b0 + white * 0.0555179f
                    b1 = 0.99332f * b1 + white * 0.0750759f
                    b2 = 0.96900f * b2 + white * 0.1538520f
                    b3 = 0.86650f * b3 + white * 0.3104856f
                    b4 = 0.55000f * b4 + white * 0.5329522f
                    b5 = -0.7616f * b5 - white * 0.0168980f
                    raw[i] = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362f
                    b6 = white * 0.115926f
                }
            }
            Kind.OFF -> Unit
        }

        normalizeRms(raw)
        applyEnvelope(raw, (count / 20).coerceAtLeast(1))
        val out = ShortArray(count)
        for (i in 0 until count) out[i] = toPcm(raw[i])
        return out
    }

    /** 统一 RMS 到 TARGET_RMS；峰值超限时压回来，宁可轻一点也不爆音 */
    private fun normalizeRms(buffer: FloatArray) {
        var sum = 0.0
        buffer.forEach { sum += it.toDouble() * it.toDouble() }
        val rms = Math.sqrt(sum / buffer.size).toFloat()
        if (rms <= 1e-6f) return
        var gain = TARGET_RMS / rms
        val peak = buffer.maxOf { abs(it) }
        if (peak * gain > PEAK_CEILING) gain = PEAK_CEILING / peak
        for (i in buffer.indices) buffer[i] *= gain
    }

    /** 首尾线性淡入淡出：循环点两侧都落在 0 上，回绕听不到接缝 */
    private fun applyEnvelope(buffer: FloatArray, fade: Int) {
        if (fade * 2 >= buffer.size) return
        for (i in 0 until fade) {
            val gain = i.toFloat() / fade
            buffer[i] *= gain
            buffer[buffer.size - 1 - i] *= gain
        }
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