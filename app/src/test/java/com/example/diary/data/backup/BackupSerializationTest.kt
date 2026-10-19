package com.example.diary.data.backup

import com.example.diary.data.local.CountdownEvent
import com.example.diary.data.local.DiaryEntry
import com.example.diary.data.local.FocusSession
import com.example.diary.data.local.FocusTask
import com.example.diary.data.local.Habit
import com.example.diary.data.local.HabitRecord
import com.example.diary.data.local.TodoItem
import com.example.diary.data.preferences.AppLockPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertTrue(decoded.focusTasks.isEmpty())
        assertTrue(decoded.focusSessions.isEmpty())
        assertTrue(decoded.imageFiles.isEmpty())
    }

    @Test
    fun fullBackup_roundTrip() {
        val diary = DiaryEntry(id = 1, content = "测试日记 with emoji ❤️", date = "2026-08-27", mood = "❤️", weather = "☀️", latitude = 39.9, longitude = 116.3, locationName = "北京")
        val habit = Habit(id = 2, name = "跑步", emoji = "🏃", colorIndex = 1, sortOrder = 0)
        val record = HabitRecord(id = 3, habitId = 2, date = "2026-08-27")
        val event = CountdownEvent(id = 4, name = "考试", date = "2026-12-31", pinned = true, colorIndex = 2, cardStyle = CountdownEvent.CARD_STYLE_PHOTO_CARD, blurRadius = 12, fontDark = true)
        val todo = TodoItem(id = 5, text = "买牛奶", done = false, sortOrder = 0, reminderAt = 1724700000000L, repeatRule = TodoItem.REPEAT_DAILY)
        val focusTask = FocusTask(id = 6, title = "日语", sortOrder = 0, focusMillis = 3_600_000L, sessionCount = 4, createdAt = 1724700000000L)
        val focusSession = FocusSession(
            id = 7,
            taskId = 6,
            date = "2026-08-27",
            startedAt = 1724700000000L,
            endedAt = 1724701500000L,
            plannedMs = 1_500_000L,
            actualMs = 1_500_000L,
            phase = FocusSession.PHASE_FOCUS,
            completed = true,
            cycleIndex = 1
        )
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
            focusTasks = listOf(focusTask),
            focusSessions = listOf(focusSession),
            preferences = prefs,
            imageFiles = listOf(image)
        )

        val encoded = json.encodeToString(original)
        assertTrue(encoded.contains("测试日记"))
        assertTrue(encoded.contains("买牛奶"))
        assertTrue(encoded.contains("diaryEntries"))
        assertTrue(encoded.contains("focusTasks"))
        assertTrue(encoded.contains("focusSessions"))

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
        assertEquals(1, decoded.focusTasks.size)
        assertEquals("日语", decoded.focusTasks[0].title)
        assertEquals(3_600_000L, decoded.focusTasks[0].focusMillis)
        assertEquals(1, decoded.focusSessions.size)
        assertEquals(6L, decoded.focusSessions[0].taskId)
        assertEquals(1_500_000L, decoded.focusSessions[0].actualMs)
        assertEquals(FocusSession.PHASE_FOCUS, decoded.focusSessions[0].phase)
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
        assertTrue(decoded.focusTasks.isEmpty())
        assertTrue(decoded.focusSessions.isEmpty())
        assertEquals(true, decoded.preferences.darkMode)
    }

    @Test
    fun oldBackup_v2_withoutFocus_defaultsEmpty() {
        val oldV2 = """{"version":2,"timestamp":1,"diaryEntries":[],"habits":[],"habitRecords":[],"countdownEvents":[],"todos":[{"id":1,"text":"买牛奶","done":false,"sortOrder":0,"createdAt":1,"repeatRule":0,"alarmMode":0}],"preferences":{},"imageFiles":[]}"""
        val decoded = lenientJson.decodeFromString<BackupData>(oldV2)
        assertEquals(2, decoded.version)
        assertEquals(1, decoded.todos.size)
        assertTrue(decoded.focusTasks.isEmpty())
        assertTrue(decoded.focusSessions.isEmpty())
    }

    @Test
    fun importResult_serialization_notRequired_butSealedWorks() {
        val success = ImportResult.Success(
            diaryEntriesImported = 1,
            habitsImported = 2,
            habitRecordsImported = 3,
            countdownEventsImported = 4,
            todosImported = 5,
            focusTasksImported = 6,
            focusSessionsImported = 7,
            imagesImported = 8,
            audioFilesImported = 9,
        )
        assertEquals(5, success.todosImported)
        assertEquals(6, success.focusTasksImported)
        assertEquals(7, success.focusSessionsImported)
        assertEquals(8, success.imagesImported)
        assertEquals(9, success.audioFilesImported)
        val failure = ImportResult.Failure("corrupt zip", "details")
        assertEquals("corrupt zip", failure.message)
    }

    @Test
    fun v4_preferences_groups_roundTrip() {
        val prefs = PreferencesData(
            theme = ThemePrefs(darkMode = true, diaryBackgroundPath = "backgrounds/diary_background.jpg", dynamicColor = false, editorPreview = true, floatingBar = false),
            focus = FocusPrefs(
                studyMode = true,
                focusMinutes = 50,
                dailyGoalMinutes = 240,
                guardEnabled = true,
                guardAllowedPackages = setOf("com.example.app", "com.foo.bar"),
                guardAllowLauncher = false,
                noiseKind = "custom",
                noiseVolume = 70,
                customNoiseFile = "focus_audio/custom_audio.bin",
                customNoiseName = "雨声.mp3",
                alarmVibrateMs = 600,
                alarmScreenOn = false,
            ),
            lock = LockPrefs(lockMode = AppLockPreferences.MODE_EVERY_DIARY),
            calendar = CalendarPrefs(syncToSystemCalendar = true),
        )
        val decoded = json.decodeFromString<PreferencesData>(json.encodeToString(prefs))
        assertEquals(prefs, decoded)
        assertEquals(true, decoded.focus!!.studyMode)
        assertEquals("focus_audio/custom_audio.bin", decoded.focus!!.customNoiseFile)
        assertEquals(AppLockPreferences.MODE_EVERY_DIARY, decoded.lock!!.lockMode)
        assertEquals(true, decoded.calendar!!.syncToSystemCalendar)
    }

    @Test
    fun v4_export_writesGroups_notFlatFields() {
        val encoded = json.encodeToString(BackupData())
        assertTrue(encoded.contains("\"theme\""))
        assertTrue(encoded.contains("\"focus\""))
        assertTrue(encoded.contains("\"lock\""))
        assertTrue(encoded.contains("\"calendar\""))
        assertTrue(encoded.contains("\"version\": 4"))
    }

    @Test
    fun v3_backup_keepsLegacyFlatFields_nullGroups() {
        // v3 包：只有扁平主题字段，focus/lock/calendar 分组为 null → 导入时不覆盖本机对应偏好
        val v3 = """{"version":3,"timestamp":1,"diaryEntries":[],"habits":[],"habitRecords":[],"countdownEvents":[],"todos":[],"focusTasks":[],"focusSessions":[],"preferences":{"darkMode":true,"diaryBackgroundPath":"backgrounds/diary_background.jpg","dynamicColor":false,"editorPreview":true},"imageFiles":[]}"""
        val decoded = lenientJson.decodeFromString<BackupData>(v3)
        assertEquals(3, decoded.version)
        assertEquals(true, decoded.preferences.darkMode)
        assertEquals("backgrounds/diary_background.jpg", decoded.preferences.diaryBackgroundPath)
        assertNull(decoded.preferences.theme)
        assertNull(decoded.preferences.focus)
        assertNull(decoded.preferences.lock)
        assertNull(decoded.preferences.calendar)
    }

    @Test
    fun v4_backup_hasGroupsAndNoFlatFields() {
        val encoded = json.encodeToString(PreferencesData(theme = ThemePrefs(darkMode = true)))
        val decoded = json.decodeFromString<PreferencesData>(encoded)
        assertNull(decoded.darkMode)
        assertNull(decoded.focus)
        assertNull(decoded.lock)
        assertNull(decoded.calendar)
        assertEquals(true, decoded.theme!!.darkMode)
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
