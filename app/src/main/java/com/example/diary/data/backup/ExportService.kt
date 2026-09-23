package com.example.diary.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.diary.data.image.BackgroundImageStore
import com.example.diary.data.local.AppDatabase
import com.example.diary.data.preferences.ThemePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Service for exporting all user data to a zip file for phone migration.
 * Zip layout: data.json (everything incl. preferences) + image files at their
 * filesDir-relative paths (same paths listed in imageFiles).
 *
 * Writes to a cacheDir temp file first so a mid-write failure never leaves a
 * half-written zip at the SAF destination. Images are streamed (CRC pass then
 * write pass) instead of loading whole files into memory.
 */
class ExportService(
    private val context: Context,
    private val themePreferences: ThemePreferences,
    private val database: AppDatabase,
) {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    /**
     * Exports all user data to a zip file.
     * @param outputUri The URI to write the zip file to (via SAF)
     */
    suspend fun export(outputUri: Uri): Result<Uri> = withContext(Dispatchers.IO) {
        val tmpZip = File(context.cacheDir, "export_tmp.zip")
        try {
            val backupData = collectBackupData()
            val dataJson = json.encodeToString(backupData)

            // Build complete zip in cache first.
            FileOutputStream(tmpZip).use { outputStream ->
                ZipOutputStream(outputStream).use { zip ->
                    addStringToZip(zip, "data.json", dataJson)
                    addImagesToZip(zip, backupData.imageFiles)
                }
            }

            // Copy to SAF destination only after the zip is fully written.
            val parcelFileDescriptor = context.contentResolver.openFileDescriptor(outputUri, "w")
                ?: return@withContext Result.failure(IllegalStateException("Failed to open output URI"))
            parcelFileDescriptor.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { out ->
                    tmpZip.inputStream().use { it.copyTo(out) }
                }
            }

            Result.success(outputUri)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tmpZip.delete()
        }
    }

    private suspend fun collectBackupData(): BackupData = database.withTransaction {
        // One-shot suspend queries — collecting Flows inside withTransaction can deadlock.
        val diaryEntries = database.diaryDao().getAllEntriesOnce()
        val habits = database.habitDao().getAllHabits()
        val habitRecords = database.habitDao().getRecordsSince("1970-01-01")
        val countdownEvents = database.countdownDao().getAllOnce()
        val todos = database.todoDao().getAll()
        val preferences = collectPreferences()
        val imageFiles = collectImageFiles()

        BackupData(
            diaryEntries = diaryEntries,
            habits = habits,
            habitRecords = habitRecords,
            countdownEvents = countdownEvents,
            todos = todos,
            preferences = preferences,
            imageFiles = imageFiles,
        )
    }

    private suspend fun collectPreferences(): PreferencesData {
        // 导出时把绝对路径归一成 filesDir 相对路径，换机导入不悬空
        val rawPath = themePreferences.diaryBackgroundPath.first()
        return PreferencesData(
            darkMode = themePreferences.isDarkMode.first(),
            diaryBackgroundPath = BackgroundImageStore.normalizeStoredPath(context, rawPath),
            dynamicColor = themePreferences.dynamicColor.first(),
            editorPreview = themePreferences.editorPreview.first(),
        )
    }

    /** Walks filesDir and collects all image files for backup. */
    private fun collectImageFiles(): List<ImageFileInfo> {
        val filesDir = context.filesDir
        val imageFiles = mutableListOf<ImageFileInfo>()

        // Diary photos: filesDir/diary_photos/<entryId>/*.jpg
        val diaryPhotosDir = File(filesDir, "diary_photos")
        if (diaryPhotosDir.exists()) {
            diaryPhotosDir.walkTopDown().forEach { file ->
                if (file.isFile && file.extension.lowercase() == "jpg") {
                    imageFiles.add(
                        ImageFileInfo(
                            relativePath = getRelativePath(filesDir, file),
                            sizeBytes = file.length(),
                            entityType = "diary_entry",
                            entityId = file.parentFile?.name?.toLongOrNull() ?: 0,
                            photoIndex = extractPhotoIndex(file),
                        )
                    )
                }
            }
        }

        // Countdown backgrounds: filesDir/countdown_backgrounds/bg_<eventId>.jpg
        val countdownBgDir = File(filesDir, "countdown_backgrounds")
        if (countdownBgDir.exists()) {
            countdownBgDir.listFiles()?.forEach { file ->
                if (file.isFile && file.extension.lowercase() == "jpg") {
                    imageFiles.add(
                        ImageFileInfo(
                            relativePath = getRelativePath(filesDir, file),
                            sizeBytes = file.length(),
                            entityType = "countdown_event",
                            entityId = file.nameWithoutExtension.substringAfterLast('_').toLongOrNull() ?: 0,
                        )
                    )
                }
            }
        }

        // Diary background: filesDir/backgrounds/diary_background.jpg
        val diaryBgFile = File(filesDir, "backgrounds/diary_background.jpg")
        if (diaryBgFile.exists()) {
            imageFiles.add(
                ImageFileInfo(
                    relativePath = getRelativePath(filesDir, diaryBgFile),
                    sizeBytes = diaryBgFile.length(),
                    entityType = "diary_background",
                    entityId = 0,
                )
            )
        }

        return imageFiles
    }

    /**
     * Adds image files as STORED (no compression). CRC32/size must be set
     * before putNextEntry or ZipOutputStream throws — computed via a streaming
     * first pass so the whole file never sits in memory.
     */
    private fun addImagesToZip(zipOutputStream: ZipOutputStream, imageFiles: List<ImageFileInfo>) {
        val filesDir = context.filesDir

        imageFiles.forEach { info ->
            // Normalize and reject anything outside filesDir
            val sourceFile = File(filesDir, info.relativePath)
            if (!sourceFile.isFile) return@forEach
            val canonicalBase = filesDir.canonicalFile
            val canonicalSource = sourceFile.canonicalFile
            if (!canonicalSource.path.startsWith(canonicalBase.path + File.separator)) return@forEach

            val size = canonicalSource.length()
            val crcValue = computeCrc32(canonicalSource)

            val entry = ZipEntry(info.relativePath).apply {
                method = ZipEntry.STORED
                this.size = size
                compressedSize = size
                crc = crcValue
            }
            zipOutputStream.putNextEntry(entry)
            FileInputStream(canonicalSource).use { input ->
                input.copyTo(zipOutputStream)
            }
            zipOutputStream.closeEntry()
        }
    }

    /** Streaming CRC32 so large images never load fully into a ByteArray. */
    private fun computeCrc32(file: File): Long {
        val crc = CRC32()
        FileInputStream(file).use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                crc.update(buf, 0, n)
            }
        }
        return crc.value
    }

    private fun addStringToZip(zipOutputStream: ZipOutputStream, entryName: String, content: String) {
        val entry = ZipEntry(entryName)
        entry.method = ZipEntry.DEFLATED
        zipOutputStream.putNextEntry(entry)
        zipOutputStream.write(content.toByteArray())
        zipOutputStream.closeEntry()
    }

    private fun getRelativePath(base: File, file: File): String {
        val basePath = base.canonicalPath
        val filePath = file.canonicalPath
        return filePath.substring(basePath.length + 1).replace('\\', '/')
    }

    /** photo index from filename: img_<index>.jpg */
    private fun extractPhotoIndex(file: File): Int {
        val name = file.nameWithoutExtension
        val parts = name.split("_")
        return if (parts.size >= 2) parts.last().toIntOrNull() ?: -1 else -1
    }
}
