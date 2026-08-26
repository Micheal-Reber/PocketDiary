package com.example.diary.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Diary photo lifecycle, two-phase like MyDiary:
 *
 *   pick → diary_photos/tmp/img_x.jpg   (editing; [img:img_x.jpg] markers in
 *                                         content reference the FILE NAME only)
 *   保存 → tmp/ migrates into diary_photos/<entryId>/  (baked location)
 *
 * Name-based markers survive the migration untouched. Crashes during editing
 * only ever leave files in tmp/, which are wiped on the next editor exit.
 */
object DiaryPhotoStore {

    const val MAX_PHOTOS_PER_ENTRY = 7
    private const val MAX_DIM = 1600
    private const val TAG = "DiaryPhotoStore"

    private val markerRegex = Regex("\\[img:([^\\]]+)\\]")

    private fun rootDir(context: Context): File = File(context.filesDir, "diary_photos")
    private fun tmpDir(context: Context): File = File(rootDir(context), "tmp").apply { mkdirs() }
    private fun entryDir(context: Context, entryId: Long): File =
        File(rootDir(context), entryId.toString()).apply { mkdirs() }

    fun markerOf(fileName: String): String = "[img:$fileName]"

    /** Photo file names inside content, in order of appearance. */
    fun photoNamesIn(content: String): List<String> =
        markerRegex.findAll(content).map { it.groupValues[1] }.toList()

    /**
     * Resolve a photo name to an existing file: the entry's baked folder
     * first, then the tmp folder (photo picked but not saved yet).
     */
    fun resolve(context: Context, entryId: Long?, fileName: String): File? {
        if (entryId != null && entryId > 0) {
            val baked = File(entryDir(context, entryId), fileName)
            if (baked.exists()) return baked
        }
        val tmp = File(tmpDir(context), fileName)
        return if (tmp.exists()) tmp else null
    }

    /**
     * Compress-copy a picked image into tmp/ (long edge ≤ 1600px, JPEG 90).
     * Returns the generated file name, or null on failure. Failures are
     * logged — never silent, so device-specific URI quirks are diagnosable.
     */
    suspend fun importFromUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                Log.e(TAG, "bounds decode failed: mime=${bounds.outMimeType} uri=$uri")
                return@withContext null
            }

            var sample = 1
            while (bounds.outWidth / (sample * 2) >= MAX_DIM || bounds.outHeight / (sample * 2) >= MAX_DIM) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            // Stream route first; fall back to a file descriptor for ROMs whose
            // content providers misbehave on repeated openInputStream calls.
            val bmp = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: context.contentResolver.openFileDescriptor(uri, "r")?.use {
                BitmapFactory.decodeFileDescriptor(it.fileDescriptor, null, opts)
            }
            if (bmp == null) {
                Log.e(TAG, "full decode failed via stream + descriptor: uri=$uri")
                return@withContext null
            }

            val name = "img_${System.nanoTime()}.jpg"
            val dest = File(tmpDir(context), name)
            FileOutputStream(dest).use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            bmp.recycle()
            if (!dest.exists() || dest.length() == 0L) {
                Log.e(TAG, "compress produced empty file: $name")
                dest.delete()
                return@withContext null
            }
            name
        } catch (e: Exception) {
            Log.e(TAG, "importFromUri failed", e)
            null
        }
    }

    /**
     * Migrate every tmp photo into the entry's own folder. Called on save —
     * markers reference file names only, so the move never breaks them.
     */
    suspend fun bakeTmp(context: Context, entryId: Long) = withContext(Dispatchers.IO) {
        tmpDir(context).listFiles()?.forEach { f ->
            runCatching { f.renameTo(File(entryDir(context, entryId), f.name)) }
        }
    }

    /** Delete a single photo file (baked location first, then tmp). */
    suspend fun deletePhoto(context: Context, entryId: Long?, fileName: String) =
        withContext(Dispatchers.IO) {
            resolve(context, entryId, fileName)?.delete()
            Unit
        }

    /** Remove every tmp photo — called when the editor exits without saving. */
    suspend fun clearTmp(context: Context) = withContext(Dispatchers.IO) {
        tmpDir(context).listFiles()?.forEach { it.delete() }
        Unit
    }

    /** Delete the whole entry folder — called when a diary is deleted. */
    suspend fun deleteEntryDir(context: Context, entryId: Long) = withContext(Dispatchers.IO) {
        entryDir(context, entryId).deleteRecursively()
        Unit
    }
}
