package com.example.diary.ui.widget

import com.example.diary.data.local.CountdownEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CountdownWidgetModelTest {

    private fun ev(id: Long, date: String, pinned: Boolean = false) =
        CountdownEvent(id = id, name = "e$id", date = date, pinned = pinned)

    @Test
    fun featured_emptyList_isNull() {
        assertNull(CountdownWidgetModel.featuredEvent(emptyList(), LocalDate.parse("2026-10-12")))
    }

    @Test
    fun featured_prefersNearestNotEarliest() {
        // 有一个很久以前的事件和一个近在眼前的，必须选近的
        val list = listOf(ev(1, "2020-01-01"), ev(2, "2026-10-20"))
        assertEquals(2L, CountdownWidgetModel.featuredEvent(list, LocalDate.parse("2026-10-12"))?.id)
    }

    @Test
    fun featured_pinnedWins() {
        val list = listOf(ev(1, "2026-10-13"), ev(2, "2026-12-01", pinned = true))
        assertEquals(2L, CountdownWidgetModel.featuredEvent(list, LocalDate.parse("2026-10-12"))?.id)
    }

    @Test
    fun featured_todayBeatsNearFuture() {
        val list = listOf(ev(1, "2026-10-12"), ev(2, "2026-10-14"))
        assertEquals(1L, CountdownWidgetModel.featuredEvent(list, LocalDate.parse("2026-10-12"))?.id)
    }

    @Test
    fun featured_singleEvent() {
        assertEquals(9L, CountdownWidgetModel.featuredEvent(listOf(ev(9, "2030-01-01")), LocalDate.parse("2026-10-12"))?.id)
    }

    @Test
    fun resolve_selectedWins_overAuto() {
        val today = LocalDate.parse("2026-10-12")
        val list = listOf(ev(1, "2026-10-13"), ev(2, "2026-12-01", pinned = true), ev(3, "2030-01-01"))
        assertEquals(3L, CountdownWidgetModel.resolveEvent(list, today, 3L)?.id)
    }

    @Test
    fun resolve_staleSelection_fallsBackToFeatured() {
        val today = LocalDate.parse("2026-10-12")
        val list = listOf(ev(1, "2026-10-14"), ev(2, "2026-10-20"))
        assertEquals(1L, CountdownWidgetModel.resolveEvent(list, today, 99L)?.id)
    }

    @Test
    fun resolve_noSelection_fallsBackToFeatured() {
        val today = LocalDate.parse("2026-10-12")
        val list = listOf(ev(1, "2026-10-14"), ev(2, "2026-10-20"))
        assertEquals(1L, CountdownWidgetModel.resolveEvent(list, today, 0L)?.id)
        assertNull(CountdownWidgetModel.resolveEvent(emptyList(), today, 0L))
    }

    @Test
    fun daysFromToday_signs() {
        val today = LocalDate.parse("2026-10-12")
        assertEquals(0, CountdownWidgetModel.daysFromToday(ev(1, "2026-10-12"), today))
        assertEquals(8, CountdownWidgetModel.daysFromToday(ev(1, "2026-10-20"), today))
        assertEquals(-8, CountdownWidgetModel.daysFromToday(ev(1, "2026-10-04"), today))
    }
}