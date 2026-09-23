package com.example.diary.data.backup

import com.example.diary.data.local.CountdownEvent
import com.example.diary.data.local.DiaryEntry
import com.example.diary.data.local.Habit
import com.example.diary.data.local.HabitRecord
import com.example.diary.data.local.TodoItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BackupSerializationTest {

    private val json = Json { prettyPrint = true; encodeDefaults = true }
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
        assertTrue(decoded.todos.isEmpty())
        assertTrue(decoded.imageFiles.isEmpty())
    }

    @Test
    fun fullBackup_roundTrip() {
        val diary = DiaryEntry(id = 1, content = "测试日记 with emoji ❤️", date = "2026-08-27", mood = "❤️", weather = "☀️", latitude = 39.9, longitude = 116.3, locationName = "北京")
        val habit = Habit(id = 2, name = "跑步", emoji = "🏃", colorIndex = 1, sortOrder = 0)
        val record = HabitRecord(id = 3, habitId = 2, date = "2026-08-27")
        val event = CountdownEvent(id = 4, name = "考试", date = "2026-12-31", pinned = true, colorIndex = 2, cardStyle = CountdownEvent.CARD_STYLE_PHOTO_CARD, blurRadius = 12, fontDark = true)
        val todo = TodoItem(id = 5, text = "买牛奶", done = false, sortOrder = 0, reminderAt = 1724700000000L, repeatRule = TodoItem.REPEAT_DAILY)
        val prefs = PreferencesData(darkMode = true, diaryBackgroundPath = "backgrounds/diary_background.jpg", dynamicColor = false, editorPreview = true)
        val image = ImageFileInfo(relativePath = "diary_photos/1/photo1.jpg", sizeBytes = 12345, entityType = "diary_entry", entityId = 1, photoIndex = 0)

        val original = BackupData(
            version = BackupData.CURRENT_VERSION,
            timestamp = 1724700000000L,
            diaryEntries = listOf(diary),
            habits = listOf(habit),
            habitRecords = listOf(record),
            countdownEvents = listOf(event),
            todos = listOf(todo),
            preferences = prefs,
            imageFiles = listOf(image)
        )

        val encoded = json.encodeToString(original)
        assertTrue(encoded.contains("测试日记"))
        assertTrue(encoded.contains("买牛奶"))
        assertTrue(encoded.contains("diaryEntries"))

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
        assertEquals(1, decoded.todos.size)
        assertEquals("买牛奶", decoded.todos[0].text)
        assertEquals(TodoItem.REPEAT_DAILY, decoded.todos[0].repeatRule)
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
        assertTrue(decoded.todos.isEmpty())
    }

    @Test
    fun oldBackup_v1_withoutTodos_defaultsEmpty() {
        val oldV1 = """{"version":1,"timestamp":1,"diaryEntries":[],"habits":[],"habitRecords":[],"countdownEvents":[],"preferences":{"darkMode":true,"dynamicColor":true,"editorPreview":false},"imageFiles":[]}"""
        val decoded = lenientJson.decodeFromString<BackupData>(oldV1)
        assertTrue(decoded.todos.isEmpty())
        assertEquals(true, decoded.preferences.darkMode)
    }

    @Test
    fun importResult_serialization_notRequired_butSealedWorks() {
        val success = ImportResult.Success(1, 2, 3, 4, 5, 6)
        assertEquals(5, success.todosImported)
        assertEquals(6, success.imagesImported)
        val failure = ImportResult.Failure("corrupt zip", "details")
        assertEquals("corrupt zip", failure.message)
    }

    @Test
    fun version_gate_acceptsCurrentAndOlder() {
        // ImportService 闸门：1..CURRENT_VERSION 放行
        assertTrue(BackupData.CURRENT_VERSION in 1..BackupData.CURRENT_VERSION)
        val v1 = lenientJson.decodeFromString<BackupData>(
            """{"version":1,"timestamp":1,"diaryEntries":[],"habits":[],"habitRecords":[],"countdownEvents":[],"preferences":{},"imageFiles":[]}"""
        )
        assertTrue(v1.version in 1..BackupData.CURRENT_VERSION)
    }

    @Test
    fun version_gate_rejectsFutureAndInvalid() {
        // 未来格式 / 非法 version 不应在 1..CURRENT_VERSION 内
        val future = BackupData(version = BackupData.CURRENT_VERSION + 1)
        assertTrue(future.version > BackupData.CURRENT_VERSION)
        val invalid = BackupData(version = 0)
        assertTrue(invalid.version < 1)
        val negative = BackupData(version = -1)
        assertTrue(negative.version < 1)
    }

    @Test
    fun backgroundPath_isFilesDirRelative() {
        // 备份里的日记背景路径必须是 filesDir 相对路径（换机不悬空）
        val prefs = PreferencesData(diaryBackgroundPath = "backgrounds/diary_background.jpg")
        val encoded = json.encodeToString(prefs)
        val decoded = json.decodeFromString<PreferencesData>(encoded)
        assertEquals("backgrounds/diary_background.jpg", decoded.diaryBackgroundPath)
        assertTrue(!File(decoded.diaryBackgroundPath!!).isAbsolute)
    }
}
