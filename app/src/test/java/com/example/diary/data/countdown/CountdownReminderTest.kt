package com.example.diary.data.countdown

import com.example.diary.data.local.CountdownEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CountdownReminderTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun event(
        date: String = "2026-12-06",
        time: String? = null,
        repeatRule: Int = CountdownEvent.REPEAT_NONE,
        remindDaysBefore: Int = CountdownEvent.REMIND_OFF
    ) = CountdownEvent(
        id = 1L,
        name = "jlpt n2",
        date = date,
        repeatRule = repeatRule,
        remindDaysBefore = remindDaysBefore
    ).copy(time = time)

    @Test
    fun disabled_returnsNull() {
        assertNull(CountdownReminder.remindAt(event(), LocalDate.of(2026, 10, 1), zone))
    }

    @Test
    fun sameDay_defaultHourNine() {
        val at = CountdownReminder.remindAt(
            event(remindDaysBefore = 0), LocalDate.of(2026, 10, 1), zone
        )
        val dt = CountdownReminder.toLocalDateTime(at!!, zone)
        assertEquals(LocalDate.of(2026, 12, 6), dt.toLocalDate())
        assertEquals(9, dt.hour)
        assertEquals(0, dt.minute)
    }

    @Test
    fun daysBefore_shiftsBack() {
        val at = CountdownReminder.remindAt(
            event(remindDaysBefore = 7), LocalDate.of(2026, 10, 1), zone
        )
        val dt = CountdownReminder.toLocalDateTime(at!!, zone)
        assertEquals(LocalDate.of(2026, 11, 29), dt.toLocalDate())
    }

    @Test
    fun explicitTime_isUsed() {
        val at = CountdownReminder.remindAt(
            event(time = "20:30", remindDaysBefore = 1), LocalDate.of(2026, 10, 1), zone
        )
        val dt = CountdownReminder.toLocalDateTime(at!!, zone)
        assertEquals(LocalDate.of(2026, 12, 5), dt.toLocalDate())
        assertEquals(20, dt.hour)
        assertEquals(30, dt.minute)
    }

    @Test
    fun malformedTime_fallsBackToDefault() {
        for (bad in listOf("abc", "25:00", "10:99", "", "  ")) {
            val at = CountdownReminder.remindAt(
                event(time = bad, remindDaysBefore = 0), LocalDate.of(2026, 10, 1), zone
            )
            val dt = CountdownReminder.toLocalDateTime(at!!, zone)
            assertEquals("bad=$bad", 9, dt.hour)
        }
    }

    @Test
    fun yearlyRepeat_rollsForwardPastToday() {
        val at = CountdownReminder.remindAt(
            event(date = "2020-12-06", repeatRule = CountdownEvent.REPEAT_YEARLY, remindDaysBefore = 0),
            LocalDate.of(2026, 10, 1), zone
        )
        assertEquals(
            LocalDate.of(2026, 12, 6),
            CountdownReminder.toLocalDateTime(at!!, zone).toLocalDate()
        )
    }

    @Test
    fun nextRepeatAnchor_nullWhenNotRepeating() {
        assertNull(
            CountdownReminder.nextRepeatAnchor(event(), LocalDate.of(2026, 10, 1))
        )
    }

    @Test
    fun nextRepeatAnchor_advancesPastToday() {
        val next = CountdownReminder.nextRepeatAnchor(
            event(date = "2026-12-06", repeatRule = CountdownEvent.REPEAT_YEARLY),
            LocalDate.of(2026, 12, 6)
        )
        assertEquals(LocalDate.of(2027, 12, 6), next)
    }

    @Test
    fun nextRepeatAnchor_isAlwaysInFuture() {
        val next = CountdownReminder.nextRepeatAnchor(
            event(date = "2026-10-01", repeatRule = CountdownEvent.REPEAT_MONTHLY),
            LocalDate.of(2026, 10, 1)
        )
        assertTrue(next!!.isAfter(LocalDate.of(2026, 10, 1)))
    }

    @Test
    fun remindAtForAnchor_ignoresTodayResolution() {
        val anchor = LocalDate.of(2027, 3, 15)
        val at = CountdownReminder.remindAtForAnchor(
            event(date = "2020-01-01", repeatRule = CountdownEvent.REPEAT_YEARLY, remindDaysBefore = 2),
            anchor, zone
        )
        assertEquals(LocalDate.of(2027, 3, 13), CountdownReminder.toLocalDateTime(at, zone).toLocalDate())
    }
}