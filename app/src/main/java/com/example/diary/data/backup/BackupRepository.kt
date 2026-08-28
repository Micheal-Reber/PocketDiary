package com.example.diary.data.backup

import android.content.Context
import android.net.Uri
import com.example.diary.data.local.AppDatabase
import com.example.diary.data.preferences.ThemePreferences
import com.example.diary.data.repository.CountdownRepository
import com.example.diary.data.repository.DiaryRepository
import com.example.diary.data.repository.HabitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-level repository for backup/restore operations.
 * Orchestrates ExportService and ImportService.
 */
class BackupRepository(
    private val context: Context,
    private val diaryRepository: DiaryRepository,
    private val habitRepository: HabitRepository,
    private val countdownRepository: CountdownRepository,
    private val themePreferences: ThemePreferences,
    private val database: AppDatabase,
) {

    private val exportService = ExportService(
        context = context,
        diaryRepository = diaryRepository,
        habitRepository = habitRepository,
        countdownRepository = countdownRepository,
        themePreferences = themePreferences,
        database = database,
    )

    private val importService = ImportService(
        context = context,
        database = database,
        themePreferences = themePreferences,
    )

    /**
     * Exports all user data to a zip file via SAF.
     * @param outputUri The URI to write the zip file to (via ACTION_CREATE_DOCUMENT)
     * @return Result containing the output URI on success, or exception on failure
     */
    suspend fun export(outputUri: Uri) = withContext(Dispatchers.IO) {
        exportService.export(outputUri)
    }

    /**
     * Imports all user data from a zip file via SAF.
     * @param inputUri The URI of the zip file to import (via ACTION_OPEN_DOCUMENT)
     * @return ImportResult with counts of imported items or failure details
     */
    suspend fun importData(inputUri: Uri) = withContext(Dispatchers.IO) {
        importService.importData(inputUri)
    }
}