package com.example.diary.data.backup

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.diary.data.local.AppDatabase
import com.example.diary.data.local.CountdownEvent
import com.example.diary.data.local.DiaryEntry
import com.example.diary.data.local.Habit
import com.example.diary.data.local.HabitRecord
import com.example.diary.data.preferences.ThemePreferences
import com.example.diary.data.repository.CountdownRepository
import com.example.diary.data.repository.DiaryRepository
import com.example.diary.data.repository.HabitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Service for exporting all user data to a zip file for phone migration.
 */
class ExportService(
    private val context: Context,
    private val diaryRepository: DiaryRepository,
    private val habitRepository: HabitRepository,
    private val countdownRepository: CountdownRepository,
    private val themePreferences: ThemePreferences,
    private val database: AppDatabase,
) {

    private val json = Json { prettyPrint = true }

    /**
     * Exports all user data to a zip file.
     * @param outputUri The URI to write the zip file to (via SAF)
     * @return Result containing the output file on success, or exception on failure
     */
    suspend fun export(outputUri: Uri): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            // Collect all data
            val backupData = collectBackupData()

            // Serialize to JSON strings
            val dataJson = json.encodeToString(backupData)
            val preferencesJson = json.encodeToString(backupData.preferences)

            // Create zip file via SAF
            val parcelFileDescriptor = context.contentResolver.openFileDescriptor(outputUri, "w")
                ?: return@withContext Result.failure(IllegalStateException("Failed to open output URI"))

            val outputStream = FileOutputStream(parcelFileDescriptor.fileDescriptor)
            val zipOutputStream = ZipOutputStream(outputStream)

            try {
                // Add data.json
                addStringToZip(zipOutputStream, "data.json", dataJson)

                // Add preferences.json
                addStringToZip(zipOutputStream, "preferences.json", preferencesJson)

                // Add image files
                addImagesToZip(zipOutputStream, backupData.imageFiles)

            } finally {
                zipOutputStream.close()
                parcelFileDescriptor.close()
            }

            Result.success(outputUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Collects all data needed for backup.
     */
    private suspend fun collectBackupData(): BackupData {
        // Get all Room entities
        val diaryEntries = database.diaryDao().getAllEntries().first()
        val habits = database.habitDao().getActiveHabits().first()
        val habitRecords = database.habitDao().getRecordsSince("1970-01-01") // All records
        val countdownEvents = database.countdownDao().observeAll().first()

        // Get preferences
        val preferences = collectPreferences()

        // Collect image file info
        val imageFiles = collectImageFiles()

        return BackupData(
            diaryEntries = diaryEntries,
            habits = habits,
            habitRecords = habitRecords,
            countdownEvents = countdownEvents,
            preferences = preferences,
            imageFiles = imageFiles,
        )
    }

    /**
     * Collects all preferences from DataStore.
     */
    private suspend fun collectPreferences(): PreferencesData {
        val darkMode = themePreferences.isDarkMode.first()
        val dynamicColor = themePreferences.dynamicColor.first()
        val editorPreview = themePreferences.editorPreview.first()
        val diaryBackgroundPath = themePreferences.diaryBackgroundPath.first()

        return PreferencesData(
            darkMode = darkMode,
            diaryBackgroundPath = diaryBackgroundPath,
            dynamicColor = dynamicColor,
            editorPreview = editorPreview,
        )
    }

    /**
     * Walks the filesDir and collects all image files for backup.
     */
    private fun collectImageFiles(): List<ImageFileInfo> {
        val filesDir = context.filesDir
        val imageFiles = mutableListOf<ImageFileInfo>()

        // Diary photos: filesDir/diary_photos/<entryId>/*.jpg
        val diaryPhotosDir = File(filesDir, "diary_photos")
        if (diaryPhotosDir.exists()) {
            diaryPhotosDir.walkTopDown().forEach { file ->
                if (file.isFile && file.extension.lowercase() == "jpg") {
                    val relativePath = getRelativePath(filesDir, file)
                    val entryId = extractEntryIdFromDiaryPhotoPath(file)
                    imageFiles.add(ImageFileInfo(
                        relativePath = relativePath,
                        sizeBytes = file.length(),
                        entityType = "diary_entry",
                        entityId = entryId,
                        photoIndex = extractPhotoIndex(file),
                    ))
                }
            }
        }

        // Countdown backgrounds: filesDir/countdown_backgrounds/bg_<eventId>.jpg
        val countdownBgDir = File(filesDir, "countdown_backgrounds")
        if (countdownBgDir.exists()) {
            countdownBgDir.listFiles()?.forEach { file ->
                if (file.isFile && file.extension.lowercase() == "jpg") {
                    val relativePath = getRelativePath(filesDir, file)
                    val eventId = extractEventIdFromCountdownBgPath(file)
                    imageFiles.add(ImageFileInfo(
                        relativePath = relativePath,
                        sizeBytes = file.length(),
                        entityType = "countdown_event",
                        entityId = eventId,
                    ))
                }
            }
        }

        // Diary background: filesDir/backgrounds/diary_background.jpg
        val diaryBgFile = File(filesDir, "backgrounds/diary_background.jpg")
        if (diaryBgFile.exists()) {
            val relativePath = getRelativePath(filesDir, diaryBgFile)
            imageFiles.add(ImageFileInfo(
                relativePath = relativePath,
                sizeBytes = diaryBgFile.length(),
                entityType = "diary_background",
                entityId = 0,
            ))
        }

        return imageFiles
    }

    /**
     * Adds image files to the zip archive.
     */
    private fun addImagesToZip(zipOutputStream: ZipOutputStream, imageFiles: List<ImageFileInfo>) {
        val filesDir = context.filesDir

        imageFiles.forEach { info ->
            val sourceFile = File(filesDir, info.relativePath)
            if (sourceFile.exists()) {
                val entry = ZipEntry(info.relativePath)
                // Use STORED (no compression) for images since they're already compressed
                entry.method = ZipEntry.STORED
                entry.size = sourceFile.length()
                zipOutputStream.putNextEntry(entry)

                FileInputStream(sourceFile).use { input ->
                    input.copyTo(zipOutputStream)
                }
                zipOutputStream.closeEntry()
            }
        }
    }

    /**
     * Adds a string as a zip entry.
     */
    private fun addStringToZip(zipOutputStream: ZipOutputStream, entryName: String, content: String) {
        val entry = ZipEntry(entryName)
        entry.method = ZipEntry.DEFLATED
        zipOutputStream.putNextEntry(entry)
        zipOutputStream.write(content.toByteArray())
        zipOutputStream.closeEntry()
    }

    /**
     * Gets the relative path of a file from a base directory.
     */
    private fun getRelativePath(base: File, file: File): String {
        val basePath = base.canonicalPath
        val filePath = file.canonicalPath
        return filePath.substring(basePath.length + 1).replace('\\', '/')
    }

    /**
     * Extracts entry ID from diary photo path: diary_photos/<entryId>/img_<index>.jpg
     */
    private fun extractEntryIdFromDiaryPhotoPath(file: File): Long {
        val parent = file.parentFile
        return parent?.name?.toLongOrNull() ?: 0
    }

    /**
     * Extracts photo index from filename: img_<index>.jpg
     */
    private fun extractPhotoIndex(file: File): Int {
        val name = file.nameWithoutExtension
        val parts = name.split("_")
        return if (parts.size >= 2) parts.last().toIntOrNull() ?: -1 else -1
    }

    /**
     * Extracts event ID from countdown background path: bg_<eventId>.jpg
     */
    private fun extractEventIdFromCountdownBgPath(file: File): Long {
        val name = file.nameWithoutExtension
        val parts = name.split("_")
        return if (parts.size >= 2) parts.last().toLongOrNull() ?: 0 else 0
    }
}