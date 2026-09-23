package com.example.diary.data.backup

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.room.withTransaction
import com.example.diary.data.image.BackgroundImageStore
import com.example.diary.data.local.AppDatabase
import com.example.diary.data.preferences.ThemePreferences
import com.example.diary.data.repository.TodoRepository
import com.example.diary.data.todo.TodoReminderScheduler
import kotlinx.coroutines.Dispatchers
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
) {

    private val json = Json { ignoreUnknownKeys = true }

    private val imageDirNames = listOf("diary_photos", "countdown_backgrounds", "backgrounds")

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
                    "备份版本 ${backupData.version} 不兼容（当前支持 1..${BackupData.CURRENT_VERSION}）"
                )
            }

            val preferences = preferencesJson?.let {
                runCatching { json.decodeFromString<PreferencesData>(it) }.getOrNull()
            } ?: backupData.preferences

            // Capture pre-import todo ids so stale alarms can be cancelled after success.
            val oldTodoIds = database.todoDao().getAll().map { it.id }

            // --- Swap image dirs: old → *.bak, staged → filesDir ---
            val filesDir = context.filesDir.canonicalFile
            val bakDirs = imageDirNames.map { name ->
                File(filesDir, name) to File(filesDir, "$name.bak")
            }
            try {
                bakDirs.forEach { (live, bak) ->
                    if (bak.exists()) bak.deleteRecursively()
                    if (live.exists()) live.renameTo(bak)
                }
                BackgroundImageStore.clear(context) // drop decode cache (file already moved aside)

                val imagesImported = moveStagedImages(staging, filesDir)

                // --- DB restore (single transaction) ---
                val counts = try {
                    database.withTransaction { restoreDatabase(backupData) }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Roll back image swap; DB already rolled back by Room.
                    restoreBakDirsSuspend(bakDirs)
                    return@withContext ImportResult.Failure("Import failed restoring database: ${e.message}", e.toString())
                }

                restorePreferences(preferences)

                // Success — drop backups of the old images.
                bakDirs.forEach { (_, bak) -> if (bak.exists()) bak.deleteRecursively() }

                // --- Reminders: cancel stale ids, reschedule imported todos ---
                val todoRepository = TodoRepository(database.todoDao(), context)
                oldTodoIds.forEach { TodoReminderScheduler.cancel(context, it) }
                TodoReminderScheduler.rescheduleAll(context, todoRepository)

                ImportResult.Success(
                    diaryEntriesImported = counts.diary,
                    habitsImported = counts.habits,
                    habitRecordsImported = counts.habitRecords,
                    countdownEventsImported = counts.countdown,
                    todosImported = counts.todos,
                    imagesImported = imagesImported,
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
    private fun moveStagedImages(staging: File, filesDir: File): Int {
        var count = 0
        imageDirNames.forEach { name ->
            val src = File(staging, name)
            if (!src.exists()) return@forEach
            val dst = File(filesDir, name)
            dst.parentFile?.mkdirs()
            if (dst.exists()) dst.deleteRecursively()
            // Copy file-by-file so we stay inside filesDir (rename across volumes can fail).
            src.walkTopDown().filter { it.isFile }.forEach { file ->
                val rel = file.relativeTo(src).path.replace('\\', '/')
                val out = File(dst, rel)
                out.parentFile?.mkdirs()
                file.copyTo(out, overwrite = true)
                count++
            }
        }
        return count
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
    )

    /** Clears all tables then inserts backup rows (ids preserved for relations). */
    private suspend fun restoreDatabase(backupData: BackupData): RestoreCounts {
        val diaryDao = database.diaryDao()
        val habitDao = database.habitDao()
        val countdownDao = database.countdownDao()
        val todoDao = database.todoDao()

        // Overwrite existing data
        diaryDao.deleteAll()
        habitDao.deleteAllRecords()
        habitDao.deleteAll()
        countdownDao.deleteAll()
        todoDao.deleteAll()

        // Habit records reference habit ids — restore habits first (keep original ids)
        if (backupData.habits.isNotEmpty()) habitDao.insertAll(backupData.habits)
        if (backupData.habitRecords.isNotEmpty()) habitDao.insertAllRecords(backupData.habitRecords)
        if (backupData.diaryEntries.isNotEmpty()) diaryDao.insertAll(backupData.diaryEntries)
        if (backupData.countdownEvents.isNotEmpty()) countdownDao.insertAll(backupData.countdownEvents)
        if (backupData.todos.isNotEmpty()) todoDao.insertAll(backupData.todos)

        return RestoreCounts(
            diary = backupData.diaryEntries.size,
            habits = backupData.habits.size,
            habitRecords = backupData.habitRecords.size,
            countdown = backupData.countdownEvents.size,
            todos = backupData.todos.size,
        )
    }

    private fun isImagePath(name: String): Boolean {
        val n = name.removePrefix("images/")
        return n.startsWith("diary_photos/") ||
            n.startsWith("countdown_backgrounds/") ||
            n.startsWith("backgrounds/")
    }

    private suspend fun restorePreferences(preferences: PreferencesData) {
        themePreferences.setDarkMode(preferences.darkMode)
        themePreferences.setDynamicColor(preferences.dynamicColor)
        themePreferences.setEditorPreview(preferences.editorPreview)
        // 相对路径（backgrounds/...）或旧包绝对路径 → 归一到本机 backgroundFile
        themePreferences.setDiaryBackgroundPath(
            BackgroundImageStore.normalizeStoredPath(context, preferences.diaryBackgroundPath)
        )
    }
}
