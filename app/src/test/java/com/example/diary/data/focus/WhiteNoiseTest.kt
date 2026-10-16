package com.example.diary.data.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WhiteNoiseTest {

    @Test
    fun `off generates nothing`() {
        assertEquals(0, WhiteNoise.generate(WhiteNoise.Kind.OFF).size)
    }

    @Test
    fun `buffer length is sample rate times seconds`() {
        val samples = WhiteNoise.generate(WhiteNoise.Kind.WHITE, sampleRate = 8_000, seconds = 2)
        assertEquals(16_000, samples.size)
    }

    @Test
    fun `generation is deterministic for same seed`() {
        val a = WhiteNoise.generate(WhiteNoise.Kind.PINK, sampleRate = 4_000, seconds = 1, seed = 42L)
        val b = WhiteNoise.generate(WhiteNoise.Kind.PINK, sampleRate = 4_000, seconds = 1, seed = 42L)
        assertTrue(a.contentEquals(b))
    }

    @Test
    fun `every kind stays inside the amplitude envelope`() {
        val ceiling = (0.99f * Short.MAX_VALUE).toInt()
        WhiteNoise.Kind.entries.filter { it != WhiteNoise.Kind.OFF }.forEach { kind ->
            val samples = WhiteNoise.generate(kind, sampleRate = 8_000, seconds = 1)
            assertTrue("peak too high for $kind", WhiteNoise.peak(samples) <= ceiling)
            assertTrue("silent buffer for $kind", WhiteNoise.rms(samples) > 100.0)
        }
    }

    @Test
    fun `all kinds are loudness normalized to the same target`() {
        val target = WhiteNoise.TARGET_RMS * Short.MAX_VALUE
        WhiteNoise.Kind.entries.filter { it != WhiteNoise.Kind.OFF }.forEach { kind ->
            val rms = WhiteNoise.rms(WhiteNoise.generate(kind, sampleRate = 8_000, seconds = 2))
            assertTrue("$kind too quiet: $rms", rms > target * 0.6)
            assertTrue("$kind too loud: $rms", rms < target * 1.4)
        }
    }

    @Test
    fun `loop seam has no loudness dip`() {
        // 曾经的实现是首尾淡出到静音：每轮循环音量塌到 0 再起，
        // 听感就是"音高一路往下掉"。交叉淡化后首尾都必须保持整体响度。
        WhiteNoise.Kind.entries.filter { it != WhiteNoise.Kind.OFF }.forEach { kind ->
            val samples = WhiteNoise.generate(kind, sampleRate = 8_000, seconds = 2)
            val window = 800
            val overall = meanAbs(samples, 0, samples.size)
            val head = meanAbs(samples, 0, window)
            val tail = meanAbs(samples, samples.size - window, samples.size)
            assertTrue("$kind dips at the loop head", head > overall * 0.4)
            assertTrue("$kind dips at the loop tail", tail > overall * 0.4)
        }
    }

    private fun meanAbs(samples: ShortArray, from: Int, to: Int): Double {
        var sum = 0.0
        for (i in from until to) sum += abs(samples[i].toInt()).toDouble()
        return sum / (to - from)
    }

    @Test
    fun `low pass makes every kind softer`() {
        // 高频代理指标：一阶差分的平均幅度 / 整体平均幅度。
        // 未滤波的均匀随机信号约 1.41；两级 5.5kHz 低通后白噪音实测 ≈1.07，
        // 阈值留余量但仍要明显低于未滤波基线，听感对应"不刺耳"。
        WhiteNoise.Kind.entries.filter { it != WhiteNoise.Kind.OFF }.forEach { kind ->
            val samples = WhiteNoise.generate(kind, sampleRate = 22_050, seconds = 2)
            var diff = 0.0
            for (i in 1 until samples.size) diff += abs((samples[i] - samples[i - 1]).toDouble())
            val level = meanAbs(samples, 0, samples.size)
            val ratio = diff / samples.size / level
            assertTrue("$kind still too bright: $ratio", ratio < 1.2)
        }
    }

    @Test
    fun `kind parsing falls back to off`() {
        assertEquals(WhiteNoise.Kind.PINK, WhiteNoise.Kind.from("pink"))
        assertEquals(WhiteNoise.Kind.OFF, WhiteNoise.Kind.from("nonsense"))
        assertEquals(WhiteNoise.Kind.OFF, WhiteNoise.Kind.from(null))
    }
}