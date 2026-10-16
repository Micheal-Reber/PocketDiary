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
    fun `envelope fades both ends to silence`() {
        val samples = WhiteNoise.generate(WhiteNoise.Kind.WHITE, sampleRate = 8_000, seconds = 1)
        assertTrue(abs(samples.first().toInt()) <= 2)
        assertTrue(abs(samples.last().toInt()) <= 2)
    }

    @Test
    fun `kind parsing falls back to off`() {
        assertEquals(WhiteNoise.Kind.PINK, WhiteNoise.Kind.from("pink"))
        assertEquals(WhiteNoise.Kind.OFF, WhiteNoise.Kind.from("nonsense"))
        assertEquals(WhiteNoise.Kind.OFF, WhiteNoise.Kind.from(null))
    }
}