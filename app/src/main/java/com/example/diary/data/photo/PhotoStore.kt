package com.example.diary.data.photo

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Owns diary photo files inside app private storage so we never depend on caller-side URIs
 * staying valid. Paths returned are CSV strings (matches [com.example.diary.data.local.DiaryEntry.photoPaths]).
 */
class PhotoStore(private val context: Context) {

    private val dir: File by lazy {
        File(context.filesDir, "photos").apply { if (!exists()) mkdirs() }
    }

    /** Create an empty file for the camera intent to write to. Returns its content URI. */
    fun newCameraOutputUri(): Pair<File, Uri> {
        val file = File(dir, uniquePhotoName())
        val authority = context.packageName + ".fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        return file to uri
    }

    /** Copy a picked photo into our own storage so the URI is durable. */
    fun importPicked(uri: Uri): File? = try {
        val file = File(dir, uniquePhotoName())
        // openInputStream may legitimately return null for content URIs we can't open;
        // delete the placeholder file we just created in that case to avoid orphan stubs.
        val opened = context.contentResolver.openInputStream(uri)
        if (opened == null) {
            file.delete()
            null
        } else {
            try {
                opened.use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                }
                file
            } catch (e: Exception) {
                // copyTo failed mid-write → remove the partial stub so it doesn't
                // linger as a 0-byte artifact on disk.
                file.delete()
                null
            }
        }
    } catch (e: Exception) {
        null
    }

    companion object {
        /** Parse a CSV of file paths into a list. (best-effort, used for rendering). */
        fun parsePaths(csv: String): List<String> =
            if (csv.isEmpty()) emptyList() else csv.split(';').filter { it.isNotBlank() }

        /**
         * Delete 0-byte stub files left behind in the photos directory by
         * camera intents that never completed — typically because the app
         * was killed (process death, OOM, force-stop) between the camera
         * app launching and the result callback firing. The user-visible
         * entry never references these files (the DB write never happened),
         * but they sit on disk forever otherwise.
         *
         * Safe to call from any thread; runs synchronously and uses
         * runCatching internally so a single bad file can't abort the
         * sweep. Returns the number of files removed (for logging).
         */
        fun cleanupEmptyStubs(context: Context): Int {
            val dir = File(context.filesDir, "photos")
            if (!dir.exists()) return 0
            val files = dir.listFiles() ?: return 0
            var removed = 0
            for (f in files) {
                if (f.isFile && f.length() == 0L) {
                    if (runCatching { f.delete() }.getOrDefault(false)) removed++
                }
            }
            return removed
        }

        private fun uniquePhotoName(): String {
            // nanoTime has better uniqueness guarantees than currentTimeMillis under tight loops.
            return "img_${System.nanoTime()}.jpg"
        }
    }
}
