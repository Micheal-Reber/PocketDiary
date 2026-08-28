package com.example.diary.data.backup

import com.example.diary.data.local.CountdownEvent
import com.example.diary.data.local.DiaryEntry
import com.example.diary.data.local.Habit
import com.example.diary.data.local.HabitRecord
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSerializationTest {

    private val json = Json { prettyPrint = true }
    private val lenientJson = Json { ignoreUnknownKeys = true }

    @Test
    fun emptyBackup_roundTrip() {
        val original = BackupData()
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<BackupData>(encoded)
        assertEquals(original.version, decoded.version)
        assertTrue(decoded.diaryEntries.isEmpty())
        assertTrue(decoded.habits.isEmpty())
        assertTrue(decoded.habitRecords.isEmpty())
        assertTrue(decoded.countdownEvents.isEmpty())
        assertTrue(decoded.imageFiles.isEmpty())
    }

    @Test
    fun fullBackup_roundTrip() {
        val diary = DiaryEntry(id = 1, content = "测试日记 with emoji ❤️", date = "2026-08-27", mood = "❤️", weather = "☀️", latitude = 39.9, longitude = 116.3, locationName = "北京")
        val habit = Habit(id = 2, name = "跑步", emoji = "🏃", colorIndex = 1, sortOrder = 0)
        val record = HabitRecord(id = 3, habitId = 2, date = "2026-08-27")
        val event = CountdownEvent(id = 4, name = "考试", date = "2026-12-31", pinned = true, colorIndex = 2, cardStyle = CountdownEvent.CARD_STYLE_PHOTO_CARD, blurRadius = 12, fontDark = true)
        val prefs = PreferencesData(darkMode = true, diaryBackgroundPath = "backgrounds/diary_background.jpg", dynamicColor = false, editorPreview = true)
        val image = ImageFileInfo(relativePath = "diary_photos/1/photo1.jpg", sizeBytes = 12345, entityType = "diary_entry", entityId = 1, photoIndex = 0)

        val original = BackupData(
            version = 1,
            timestamp = 1724700000000L,
            diaryEntries = listOf(diary),
            habits = listOf(habit),
            habitRecords = listOf(record),
            countdownEvents = listOf(event),
            preferences = prefs,
            imageFiles = listOf(image)
        )

        val encoded = json.encodeToString(original)
        // JSON 包含关键字段
        assertTrue(encoded.contains("测试日记"))
        assertTrue(encoded.contains("diary_entries") || encoded.contains("diaryEntries"))

        val decoded = json.decodeFromString<BackupData>(encoded)
        assertEquals(1, decoded.diaryEntries.size)
        assertEquals("测试日记 with emoji ❤️", decoded.diaryEntries[0].content)
        assertEquals("2026-08-27", decoded.diaryEntries[0].date)
        assertEquals(1, decoded.habits.size)
        assertEquals("跑步", decoded.habits[0].name)
        assertEquals(1, decoded.habitRecords.size)
        assertEquals(1, decoded.countdownEvents.size)
        assertEquals("考试", decoded.countdownEvents[0].name)
        assertEquals(12, decoded.countdownEvents[0].blurRadius)
        assertEquals(true, decoded.preferences.darkMode)
        assertEquals("backgrounds/diary_background.jpg", decoded.preferences.diaryBackgroundPath)
        assertEquals(1, decoded.imageFiles.size)
        assertEquals("diary_photos/1/photo1.jpg", decoded.imageFiles[0].relativePath)
    }

    @Test
    fun preferences_roundTrip() {
        val prefs = PreferencesData(darkMode = true, diaryBackgroundPath = null, dynamicColor = false, editorPreview = true)
        val encoded = json.encodeToString(prefs)
        val decoded = json.decodeFromString<PreferencesData>(encoded)
        assertEquals(prefs, decoded)
    }

    @Test
    fun lenient_decode_ignoresUnknownKeys() {
        val jsonWithExtra = """{"version":1,"timestamp":123,"diaryEntries":[],"habits":[],"habitRecords":[],"countdownEvents":[],"preferences":{"darkMode":false,"dynamicColor":true,"editorPreview":false},"imageFiles":[],"unknownField":"ignored"}"""
        val decoded = lenientJson.decodeFromString<BackupData>(jsonWithExtra)
        assertEquals(1, decoded.version)
    }

    @Test
    fun importResult_serialization_notRequired_butSealedWorks() {
        val success = ImportResult.Success(1, 2, 3, 4, 5)
        assertEquals(1, success.diaryEntriesImported)
        val failure = ImportResult.Failure("corrupt zip", "details")
        assertEquals("corrupt zip", failure.message)
    }
}
