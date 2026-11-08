package com.example.diary.data.draft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiaryDraftTest {

    private fun draft(
        date: String = "2026-11-08",
        originDate: String? = "2026-11-08",
        content: String = "今天",
        mood: String? = "😊",
        lat: Double? = 31.2,
        lon: Double? = 121.4,
        locationName: String? = "上海",
        weather: String? = "☀️",
        existingId: Long? = 7L,
        savedAt: Long = 123L
    ) = DiaryDraft(
        date = date,
        originDate = originDate,
        content = content,
        mood = mood,
        latitude = lat,
        longitude = lon,
        locationName = locationName,
        weather = weather,
        existingId = existingId,
        savedAt = savedAt
    )

    @Test
    fun jsonRoundTripKeepsEveryField() {
        val original = draft()
        val decoded = DiaryDraftStore.decode(DiaryDraftStore.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun corruptJsonDecodesToNull() {
        assertEquals(null, DiaryDraftStore.decode("{not json"))
    }

    @Test
    fun matchesSameContentIgnoringOuterWhitespace() {
        assertTrue(
            draft(content = "  今天 \n").matches(
                "2026-11-08", "今天", "😊", 31.2, 121.4, "上海", "☀️", 7L
            )
        )
    }

    @Test
    fun doesNotMatchDifferentDateOrEntry() {
        val d = draft()
        assertFalse(d.matches("2026-11-09", "今天", "😊", 31.2, 121.4, "上海", "☀️", 7L))
        assertFalse(d.matches("2026-11-08", "今天", "😊", 31.2, 121.4, "上海", "☀️", null))
    }

    @Test
    fun doesNotMatchDifferentMetadata() {
        val d = draft()
        assertFalse(d.matches("2026-11-08", "今天", null, 31.2, 121.4, "上海", "☀️", 7L))
        assertFalse(d.matches("2026-11-08", "今天", "😊", 31.2, 121.4, "北京", "☀️", 7L))
    }
}
