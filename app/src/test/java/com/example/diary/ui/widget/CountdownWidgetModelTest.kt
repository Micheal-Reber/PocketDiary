package com.example.diary.ui.widget

import com.example.diary.data.local.CountdownEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CountdownWidgetModelTest {

    private val d = 2.625f
    private val sd = 2.625f

    private fun sp(numberLen: Int, widthDp: Int = 163, heightDp: Int = 177) =
        CountdownWidgetModel.responsiveNumberSp(numberLen, widthDp, heightDp, d, sd)

    @Test
    fun invalidInputs_returnDefault() {
        val fallback = CountdownWidgetModel.DEFAULT_NUMBER_SP
        assertEquals(fallback, sp(2, widthDp = 0))
        assertEquals(fallback, sp(2, heightDp = 0))
        assertEquals(
            fallback,
            CountdownWidgetModel.responsiveNumberSp(2, 163, 177, 0f, sd)
        )
    }

    @Test
    fun typical2x2_isCappedAtMax() {
        assertEquals(60f, sp(2), 0.01f)
    }

    @Test
    fun neverExceedsBounds() {
        listOf(1, 2, 3, 4).forEach { len ->
            val v = sp(len)
            assertTrue("len=$len v=$v", v >= CountdownWidgetModel.MIN_NUMBER_SP)
            assertTrue("len=$len v=$v", v <= CountdownWidgetModel.MAX_NUMBER_SP)
        }
    }

    @Test
    fun moreDigits_neverLarger() {
        // 位数越多宽度占用越大，字号应单调不增
        val values = (1..5).map { sp(it) }
        values.zipWithNext().forEach { (a, b) ->
            assertTrue("a=$a b=$b", b <= a + 0.001f)
        }
    }

    @Test
    fun largerWidget_isNotSmaller() {
        val small = sp(2, widthDp = 120, heightDp = 120)
        val big = sp(2, widthDp = 200, heightDp = 220)
        assertTrue("small=$small big=$big", big >= small)
    }

    @Test
    fun degenerateSize_returnsDefault() {
        // 预算算不出来时按默认字号兜底（而非 MIN，避免超小卡片放大）
        assertEquals(CountdownWidgetModel.DEFAULT_NUMBER_SP, sp(4, widthDp = 40, heightDp = 40))
    }

    @Test
    fun crampedButValid_neverOverflows() {
        // 1x1 这类小格子：字号必须被压到上限内，否则会溢出卡片
        val v = sp(2, widthDp = 110, heightDp = 110)
        assertTrue("v=$v", v <= CountdownWidgetModel.MAX_NUMBER_SP)
        assertTrue("v=$v", v >= CountdownWidgetModel.MIN_NUMBER_SP)
    }

    @Test
    fun unitRatios_areInSaneRange() {
        assertTrue(CountdownWidgetModel.UNIT_SP_RATIO in 0.2f..0.5f)
    }

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