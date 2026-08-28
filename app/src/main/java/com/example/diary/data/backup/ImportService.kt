package com.example.diary.data.backup

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.room.withTransaction
import com.example.diary.data.local.AppDatabase
import com.example.diary.data.local.CountdownEvent
import com.example.diary.data.local.DiaryEntry
import com.example.diary.data.local.Habit
import com.example.diary.data.local.HabitRecord
import com.example.diary.data.preferences.ThemePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Service for importing user data from a zip file for phone migration.
 */
class ImportService(
    private val context: Context,
    private val database: AppDatabase,
    private val themePreferences: ThemePreferences,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Imports all user data from a zip file.
     * @param inputUri The URI of the zip file to import (via SAF)
     * @return ImportResult with counts of imported items or failure details
     */
    suspend fun importData(inputUri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            val parcelFileDescriptor = context.contentResolver.openFileDescriptor(inputUri, "r")
                ?: return@withContext ImportResult.Failure("Failed to open input URI")

            val inputStream = ParcelFileDescriptor.AutoCloseInputStream(parcelFileDescriptor)
            val zipInputStream = ZipInputStream(inputStream)

            var dataJson: String? = null
            var preferencesJson: String? = null
            val imageFiles = mutableListOf<Pair<String, InputStream>>()

            try {
                var entry: ZipEntry? = zipInputStream.nextEntry
                while (entry != null) {
                    val name = entry.name
                    when {
                        name == "data.json" -> {
                            dataJson = readStreamToString(zipInputStream)
                        }
                        name == "preferences.json" -> {
                            preferencesJson = readStreamToString(zipInputStream)
                        }
                        name.startsWith("images/") && !entry.isDirectory -> {
                            // Store image data for later extraction
                            val bytes = readStreamToBytes(zipInputStream)
                            imageFiles.add(name to java.io.ByteArrayInputStream(bytes))
                        }
                    }
                    entry = zipInputStream.nextEntry
                }
            } finally {
                zipInputStream.close()
            }

            // Parse and restore data
            val backupData = if (dataJson != null) {
                json.decodeFromString<BackupData>(dataJson)
            } else {
                return@withContext ImportResult.Failure("Missing data.json in backup")
            }

            val preferences = if (preferencesJson != null) {
                json.decodeFromString<PreferencesData>(preferencesJson)
            } else {
                PreferencesData()
            }

            // Restore database in transaction (using suspend transaction)
            val result = database.withTransaction {
                restoreDatabase(backupData)
            }

            // Restore images
            val imagesImported = restoreImages(imageFiles)

            // Restore preferences
            restorePreferences(preferences)

            ImportResult.Success(
                diaryEntriesImported = result.diaryEntriesImported,
                habitsImported = result.habitsImported,
                habitRecordsImported = result.habitRecordsImported,
                countdownEventsImported = result.countdownEventsImported,
                imagesImported = imagesImported,
            )
        } catch (e: Exception) {
            ImportResult.Failure("Import failed: ${e.message}", e.toString())
        }
    }

    /**
     * Restores database entities from backup data.
     */
    private suspend fun restoreDatabase(backupData: BackupData): ImportResult.Success {
        var diaryEntriesImported = 0
        var habitsImported = 0
        var habitRecordsImported = 0
        var countdownEventsImported = 0

        // Restore diary entries (REPLACE on date conflict)
        backupData.diaryEntries.forEach { entry ->
            database.diaryDao().insert(entry.copy(id = 0))
            diaryEntriesImported++
        }

        // Restore habits (REPLACE on id conflict)
        backupData.habits.forEach { habit ->
            database.habitDao().insert(habit.copy(id = 0))
            habitsImported++
        }

        // Restore habit records (REPLACE on habitId+date conflict)
        backupData.habitRecords.forEach { record ->
            database.habitDao().insertRecord(record)
            habitRecordsImported++
        }

        // Restore countdown events (REPLACE on id conflict)
        backupData.countdownEvents.forEach { event ->
            database.countdownDao().insert(event.copy(id = 0))
            countdownEventsImported++
        }

        return ImportResult.Success(
            diaryEntriesImported = diaryEntriesImported,
            habitsImported = habitsImported,
            habitRecordsImported = habitRecordsImported,
            countdownEventsImported = countdownEventsImported,
            imagesImported = 0, // Will be updated after image restore
        )
    }

    /**
     * Restores image files to filesDir.
     */
    private fun restoreImages(imageFiles: List<Pair<String, InputStream>>): Int {
        val filesDir = context.filesDir
        var count = 0

        imageFiles.forEach { (relativePath, inputStream) ->
            val targetFile = File(filesDir, relativePath)
            targetFile.parentFile?.mkdirs()

            FileOutputStream(targetFile).use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()
            count++
        }

        return count
    }

    /**
     * Restores preferences to DataStore.
     */
    private suspend fun restorePreferences(preferences: PreferencesData) {
        themePreferences.setDarkMode(preferences.darkMode)
        themePreferences.setDynamicColor(preferences.dynamicColor)
        themePreferences.setEditorPreview(preferences.editorPreview)
        themePreferences.setDiaryBackgroundPath(preferences.diaryBackgroundPath)
    }

    /**
     * Reads an InputStream to a String.
     */
    private fun readStreamToString(inputStream: InputStream): String {
        return inputStream.readBytes().decodeToString()
    }

    /**
     * Reads an InputStream to a ByteArray.
     */
    private fun readStreamToBytes(inputStream: InputStream): ByteArray {
        return inputStream.readAllBytes()
    }
}