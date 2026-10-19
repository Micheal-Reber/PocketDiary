package com.example.diary.data.backup

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.room.withTransaction
import com.example.diary.R
import com.example.diary.data.calendar.SystemCalendarSync
import com.example.diary.data.countdown.CountdownReminderScheduler
import com.example.diary.data.focus.CustomNoiseStore
import com.example.diary.data.focus.WhiteNoise
import com.example.diary.data.image.BackgroundImageStore
import com.example.diary.data.local.AppDatabase
import com.example.diary.data.preferences.AppLockPreferences
import com.example.diary.data.preferences.CalendarPreferences
import com.example.diary.data.preferences.FocusPreferences
import com.example.diary.data.preferences.ThemePreferences
import com.example.diary.data.repository.CountdownRepository
import com.example.diary.data.repository.TodoRepository
import com.example.diary.data.todo.TodoReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Service for importing user data from a zip file for phone migration.
 *
 * Atomicity strategy (full-overwrite):
 *  1. Stream-unzip into cacheDir staging (size-capped) — DB/old images untouched on failure.
 *  2. Rename existing image dirs to *.bak, move staged images into place.
 *  3. Restore all tables in one transaction; on failure, restore *.bak and drop new images.
 *  4. Preferences + reschedule reminders; on success delete *.bak.
 */
class ImportService(
    private val context: Context,
    private val database: AppDatabase,
    private val themePreferences: ThemePreferences,
    private val focusPreferences: FocusPreferences,
    private val appLockPreferences: AppLockPreferences,
    private val calendarPreferences: CalendarPreferences,
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val imageDirNames = listOf("diary_photos", "countdown_backgrounds", "backgrounds", "focus_audio")

    /** Hard caps so a hostile/broken zip cannot OOM the process. */
    private val maxTotalBytes = 512L * 1024 * 1024
    private val maxSingleFileBytes = 64L * 1024 * 1024

    /**
     * Imports all user data from a zip file.
     * @param inputUri The URI of the zip file to import (via SAF)
     */
    suspend fun importData(inputUri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "import_staging")
        try {
            staging.deleteRecursively()
            staging.mkdirs()

            val parcelFileDescriptor = context.contentResolver.openFileDescriptor(inputUri, "r")
                ?: return@withContext ImportResult.Failure("Failed to open input URI")

            val inputStream = ParcelFileDescriptor.AutoCloseInputStream(parcelFileDescriptor)
            val zipInputStream = ZipInputStream(inputStream)

            var dataJson: String? = null
            var preferencesJson: String? = null
            var totalImageBytes = 0L

            try {
                var entry: ZipEntry? = zipInputStream.nextEntry
                while (entry != null) {
                    val name = entry.name
                    when {
                        !entry.isDirectory && name == "data.json" -> {
                            dataJson = readEntryCapped(zipInputStream, staging, "data.json", maxSingleFileBytes)
                                .decodeToString()
                        }
                        !entry.isDirectory && name == "preferences.json" -> {
                            preferencesJson = readEntryCapped(zipInputStream, staging, "preferences.json", maxSingleFileBytes)
                                .decodeToString()
                        }
                        !entry.isDirectory && isImagePath(name) -> {
                            val relative = name.removePrefix("images/")
                            if (relative.isBlank()) {
                                return@withContext ImportResult.Failure("Invalid image path in backup: $name")
                            }
                            val written = writeEntryToFile(zipInputStream, staging, relative, maxSingleFileBytes)
                            totalImageBytes += written
                            if (totalImageBytes > maxTotalBytes) {
                                return@withContext ImportResult.Failure("Backup images exceed size limit")
                            }
                        }
                    }
                    entry = zipInputStream.nextEntry
                }
            } finally {
                zipInputStream.close()
            }

            val backupData = dataJson?.let {
                runCatching { json.decodeFromString<BackupData>(it) }
                    .getOrElse { return@withContext ImportResult.Failure("Invalid data.json: ${it.message}") }
            } ?: return@withContext ImportResult.Failure("Missing data.json in backup")

            // 版本闸门：未来格式 / 非法 version 拒绝，避免静默全量覆盖
            if (backupData.version < 1 || backupData.version > BackupData.CURRENT_VERSION) {
                return@withContext ImportResult.Failure(
                    context.getString(R.string.backup_version_gate, backupData.version, BackupData.CURRENT_VERSION)
                )
            }

            val preferences = preferencesJson?.let {
                runCatching { json.decodeFromString<PreferencesData>(it) }.getOrNull()
            } ?: backupData.preferences

            // Capture pre-import ids so stale alarms can be cancelled after success,
            // and a snapshot of every preference we are about to overwrite for rollback.
            val oldTodoIds = database.todoDao().getAll().map { it.id }
            val oldCountdownIds = database.countdownDao().getAllOnce().map { it.id }
            val oldPreferences = capturePreferences()

            // --- Swap image dirs: old → *.bak, staged → filesDir ---
            // v1..v3 的包没有 focus/ 音频：本机自定义白噪音音频原样留着，别被"全量覆盖"误删
            val swapDirs = if (preferences.focus != null) imageDirNames else imageDirNames - "focus_audio"
            val filesDir = context.filesDir.canonicalFile
            val bakDirs = swapDirs.map { name ->
                File(filesDir, name) to File(filesDir, "$name.bak")
            }
            try {
                bakDirs.forEach { (live, bak) ->
                    if (bak.exists()) bak.deleteRecursively()
                    if (live.exists()) live.renameTo(bak)
                }
                BackgroundImageStore.clear(context) // drop decode cache (file already moved aside)

                val moved = moveStagedImages(staging, filesDir, swapDirs)
                val imagesImported = moved.filterKeys { it != "focus_audio" }.values.sum()
                val audioFilesImported = moved["focus_audio"] ?: 0

                // Restore preferences before the DB so a DB failure can compensate
                // the DataStore writes before the old image tree is restored.
                val counts = try {
                    restorePreferences(preferences)
                    database.withTransaction { restoreDatabase(backupData) }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Room rolls back its transaction; restore DataStore and images too.
                    runCatching { restorePreferences(oldPreferences) }
                    restoreBakDirsSuspend(bakDirs)
                    return@withContext ImportResult.Failure("Import failed restoring database: ${e.message}", e.toString())
                }

                // Success — drop backups of the old images.
                bakDirs.forEach { (_, bak) -> if (bak.exists()) bak.deleteRecursively() }

                // --- Reminders: cancel stale ids, reschedule imported todos ---
                val todoRepository = TodoRepository(database.todoDao(), context)
                oldTodoIds.forEach { TodoReminderScheduler.cancel(context, it) }
                TodoReminderScheduler.rescheduleAll(context, todoRepository)

                // 倒数日提醒：旧 id 的闹钟要清掉，导入进来的按新数据重排
                val countdownRepository = CountdownRepository(database.countdownDao(), context)
                oldCountdownIds.forEach { CountdownReminderScheduler.cancel(context, it) }
                CountdownReminderScheduler.rescheduleAll(context, countdownRepository.getAllWithReminder())
                SystemCalendarSync.rescheduleAll(context, countdownRepository)

                ImportResult.Success(
                    diaryEntriesImported = counts.diary,
                    habitsImported = counts.habits,
                    habitRecordsImported = counts.habitRecords,
                    countdownEventsImported = counts.countdown,
                    todosImported = counts.todos,
                    focusTasksImported = counts.focusTasks,
                    focusSessionsImported = counts.focusSessions,
                    imagesImported = imagesImported,
                    audioFilesImported = audioFilesImported,
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                restoreBakDirsSuspend(bakDirs)
                ImportResult.Failure("Import failed: ${e.message}", e.toString())
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            ImportResult.Failure("Import failed: ${e.message}", e.toString())
        } finally {
            staging.deleteRecursively()
        }
    }

    /**
     * Reads a small zip entry (data.json / preferences.json) fully into memory,
     * failing if it exceeds [maxBytes].
     */
    private fun readEntryCapped(
        zip: ZipInputStream,
        staging: File,
        name: String,
        maxBytes: Long,
    ): ByteArray {
        val tmp = File(staging, name)
        var total = 0L
        tmp.outputStream().use { out ->
            val buf = ByteArray(8192)
            while (true) {
                val n = zip.read(buf)
                if (n < 0) break
                total += n
                if (total > maxBytes) throw IllegalStateException("Entry too large: $name")
                out.write(buf, 0, n)
            }
        }
        return tmp.readBytes()
    }

    /**
     * Streams a zip image entry to staging/[relativePath] with zip-slip protection.
     * Returns bytes written; throws IllegalStateException if the single-file cap is hit.
     */
    private fun writeEntryToFile(
        zip: ZipInputStream,
        staging: File,
        relativePath: String,
        maxFileBytes: Long,
    ): Long {
        val stagingRoot = staging.canonicalFile
        val target = File(staging, relativePath).canonicalFile
        if (!target.path.startsWith(stagingRoot.path + File.separator)) {
            throw IllegalStateException("Unsafe path in backup: $relativePath")
        }

        target.parentFile?.mkdirs()
        var written = 0L
        FileOutputStream(target).use { out ->
            val buf = ByteArray(8192)
            while (true) {
                val n = zip.read(buf)
                if (n < 0) break
                written += n
                if (written > maxFileBytes) throw IllegalStateException("Image too large: $relativePath")
                out.write(buf, 0, n)
            }
        }
        return written
    }

    /** Moves staged image files into filesDir (staging is emptied as it goes). */
    private fun moveStagedImages(staging: File, filesDir: File, dirs: List<String>): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        dirs.forEach { name ->
            val src = File(staging, name)
            if (!src.exists()) return@forEach
            val dst = File(filesDir, name)
            dst.parentFile?.mkdirs()
            if (dst.exists()) dst.deleteRecursively()
            // Copy file-by-file so we stay inside filesDir (rename across volumes can fail).
            var n = 0
            src.walkTopDown().filter { it.isFile }.forEach { file ->
                val rel = file.relativeTo(src).path.replace('\\', '/')
                val out = File(dst, rel)
                out.parentFile?.mkdirs()
                file.copyTo(out, overwrite = true)
                n++
            }
            counts[name] = n
        }
        return counts
    }

    /** Puts *.bak dirs back after a failed import. */
    private suspend fun restoreBakDirsSuspend(bakDirs: List<Pair<File, File>>) {
        bakDirs.forEach { (live, bak) ->
            if (live.exists()) live.deleteRecursively()
            if (bak.exists()) bak.renameTo(live)
        }
        runCatching { BackgroundImageStore.clear(context) }
    }

    private data class RestoreCounts(
        val diary: Int,
        val habits: Int,
        val habitRecords: Int,
        val countdown: Int,
        val todos: Int,
        val focusTasks: Int,
        val focusSessions: Int,
    )

    /** Clears all tables then inserts backup rows (ids preserved for relations). */
    private suspend fun restoreDatabase(backupData: BackupData): RestoreCounts {
        val diaryDao = database.diaryDao()
        val habitDao = database.habitDao()
        val countdownDao = database.countdownDao()
        val todoDao = database.todoDao()
        val focusDao = database.focusDao()

        // Overwrite existing data
        diaryDao.deleteAll()
        habitDao.deleteAllRecords()
        habitDao.deleteAll()
        countdownDao.deleteAll()
        todoDao.deleteAll()
        focusDao.deleteAllSessions()
        focusDao.deleteAllTasks()

        // Habit records reference habit ids — restore habits first (keep original ids)
        if (backupData.habits.isNotEmpty()) habitDao.insertAll(backupData.habits)
        if (backupData.habitRecords.isNotEmpty()) habitDao.insertAllRecords(backupData.habitRecords)
        if (backupData.diaryEntries.isNotEmpty()) diaryDao.insertAll(backupData.diaryEntries)
        if (backupData.countdownEvents.isNotEmpty()) countdownDao.insertAll(backupData.countdownEvents)
        if (backupData.todos.isNotEmpty()) todoDao.insertAll(backupData.todos)
        // focus_sessions.taskId 外键 ON DELETE SET NULL —— 先 tasks 后 sessions 保住绑定
        if (backupData.focusTasks.isNotEmpty()) focusDao.insertTasks(backupData.focusTasks)
        if (backupData.focusSessions.isNotEmpty()) focusDao.insertSessions(backupData.focusSessions)

        return RestoreCounts(
            diary = backupData.diaryEntries.size,
            habits = backupData.habits.size,
            habitRecords = backupData.habitRecords.size,
            countdown = backupData.countdownEvents.size,
            todos = backupData.todos.size,
            focusTasks = backupData.focusTasks.size,
            focusSessions = backupData.focusSessions.size,
        )
    }

    private fun isImagePath(name: String): Boolean {
        val n = name.removePrefix("images/")
        return n.startsWith("diary_photos/") ||
            n.startsWith("countdown_backgrounds/") ||
            n.startsWith("backgrounds/") ||
            n.startsWith("focus_audio/")
    }

    private suspend fun capturePreferences(): PreferencesData {
        val theme = ThemePrefs(
            darkMode = themePreferences.isDarkMode.first(),
            diaryBackgroundPath = themePreferences.diaryBackgroundPath.first(),
            dynamicColor = themePreferences.dynamicColor.first(),
            editorPreview = themePreferences.editorPreview.first(),
            floatingBar = themePreferences.floatingBar.first(),
        )
        return PreferencesData(
            darkMode = theme.darkMode,
            diaryBackgroundPath = theme.diaryBackgroundPath,
            dynamicColor = theme.dynamicColor,
            editorPreview = theme.editorPreview,
            theme = theme,
            focus = FocusPrefs(
                studyMode = focusPreferences.studyMode.first(),
                focusMinutes = focusPreferences.focusMinutes.first(),
                shortBreakMinutes = focusPreferences.shortBreakMinutes.first(),
                longBreakMinutes = focusPreferences.longBreakMinutes.first(),
                longBreakInterval = focusPreferences.longBreakInterval.first(),
                dailyGoalMinutes = focusPreferences.dailyGoalMinutes.first(),
                guardEnabled = focusPreferences.guardEnabled.first(),
                guardAllowedPackages = focusPreferences.guardAllowedPackages.first(),
                guardAllowLauncher = focusPreferences.guardAllowLauncher.first(),
                noiseKind = focusPreferences.noiseKind.first(),
                noiseVolume = focusPreferences.noiseVolume.first(),
                customNoiseFile = CustomNoiseStore(context).currentFile()
                    ?.let { getRelativePath(context.filesDir, it) },
                customNoiseName = focusPreferences.customNoiseName.first(),
                notificationEnhanced = focusPreferences.notificationEnhanced.first(),
                alarmEnabled = focusPreferences.alarmEnabled.first(),
                alarmVolume = focusPreferences.alarmVolume.first(),
                alarmVibrate = focusPreferences.alarmVibrate.first(),
                alarmVibrateMs = focusPreferences.alarmVibrateMs.first(),
                alarmVibrateGapMs = focusPreferences.alarmVibrateGapMs.first(),
                alarmAmplitude = focusPreferences.alarmAmplitude.first(),
                alarmScreenOn = focusPreferences.alarmScreenOn.first(),
            ),
            lock = LockPrefs(lockMode = appLockPreferences.lockMode.first()),
            calendar = CalendarPrefs(
                syncToSystemCalendar = calendarPreferences.syncToSystemCalendar.first(),
            ),
        )
    }

    /**
     * 只覆盖备份里有的分组：某组为 null（v1..v3 旧包）就完全不动本机对应偏好。
     */
    private suspend fun restorePreferences(preferences: PreferencesData) {
        val theme = preferences.theme ?: legacyTheme(preferences)
        if (theme != null) restoreTheme(theme)
        preferences.focus?.let { restoreFocus(it) }
        preferences.lock?.let { appLockPreferences.setLockMode(it.lockMode) }
        preferences.calendar?.let {
            calendarPreferences.setSyncToSystemCalendar(it.syncToSystemCalendar)
        }
    }

    /** v1..v3 的扁平字段 → 主题组；v3 没有的 floatingBar 保留本机当前值 */
    private suspend fun legacyTheme(p: PreferencesData): ThemePrefs? {
        val hasAny = p.darkMode != null || p.diaryBackgroundPath != null ||
            p.dynamicColor != null || p.editorPreview != null
        if (!hasAny) return null
        return ThemePrefs(
            darkMode = p.darkMode ?: themePreferences.isDarkMode.first(),
            diaryBackgroundPath = p.diaryBackgroundPath,
            dynamicColor = p.dynamicColor ?: themePreferences.dynamicColor.first(),
            editorPreview = p.editorPreview ?: themePreferences.editorPreview.first(),
            floatingBar = themePreferences.floatingBar.first(),
        )
    }

    private suspend fun restoreTheme(theme: ThemePrefs) {
        themePreferences.setDarkMode(theme.darkMode)
        themePreferences.setDynamicColor(theme.dynamicColor)
        themePreferences.setEditorPreview(theme.editorPreview)
        themePreferences.setFloatingBar(theme.floatingBar)
        // 相对路径（backgrounds/...）或旧包绝对路径 → 归一到本机 backgroundFile
        themePreferences.setDiaryBackgroundPath(
            BackgroundImageStore.normalizeStoredPath(context, theme.diaryBackgroundPath)
        )
    }

    private suspend fun restoreFocus(focus: FocusPrefs) {
        focusPreferences.setStudyMode(focus.studyMode)
        focusPreferences.setFocusMinutes(focus.focusMinutes)
        focusPreferences.setShortBreakMinutes(focus.shortBreakMinutes)
        focusPreferences.setLongBreakMinutes(focus.longBreakMinutes)
        focusPreferences.setLongBreakInterval(focus.longBreakInterval)
        focusPreferences.setDailyGoalMinutes(focus.dailyGoalMinutes)
        focusPreferences.setGuardEnabled(focus.guardEnabled)
        focusPreferences.setGuardAllowLauncher(focus.guardAllowLauncher)
        // 换机后旧手机的包大多没装，留着只会让白名单看起来少了一堆
        focusPreferences.setGuardAllowedPackages(installedOnly(focus.guardAllowedPackages))
        focusPreferences.setNoiseKind(focus.noiseKind)
        focusPreferences.setNoiseVolume(focus.noiseVolume)
        focusPreferences.setNotificationEnhanced(focus.notificationEnhanced)
        focusPreferences.setAlarmEnabled(focus.alarmEnabled)
        focusPreferences.setAlarmVolume(focus.alarmVolume)
        focusPreferences.setAlarmVibrate(focus.alarmVibrate)
        focusPreferences.setAlarmVibrateMs(focus.alarmVibrateMs)
        focusPreferences.setAlarmVibrateGapMs(focus.alarmVibrateGapMs)
        focusPreferences.setAlarmAmplitude(focus.alarmAmplitude)
        focusPreferences.setAlarmScreenOn(focus.alarmScreenOn)
        restoreCustomNoise(focus)
    }

    /** 音频路径按新机 filesDir 重写；文件没随包带过来就清空，别留悬空路径 */
    private suspend fun restoreCustomNoise(focus: FocusPrefs) {
        val file = focus.customNoiseFile
            ?.takeIf { it.isNotBlank() }
            ?.let { File(context.filesDir, it) }
            ?.takeIf { it.isFile && it.length() > 0 }
        focusPreferences.setCustomNoise(file?.absolutePath, if (file == null) null else focus.customNoiseName)
        if (focus.noiseKind == WhiteNoise.Kind.CUSTOM.id && file == null) {
            focusPreferences.setNoiseKind(WhiteNoise.Kind.OFF.id)
        }
    }

    private fun installedOnly(packages: Set<String>): Set<String> {
        if (packages.isEmpty()) return packages
        val pm = context.packageManager
        return packages.filter { pkg ->
            runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess
        }.toSet()
    }

    private fun getRelativePath(base: File, file: File): String =
        file.canonicalPath
            .substring(base.canonicalPath.length + 1)
            .replace('\\', '/')
}
