package com.example.diary.data.image

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Owns the diary-list background image: copies the user's pick into app
 * private storage (so the picker URI doesn't have to stay valid) and decodes
 * it downscaled — full-size camera photos would blow the bitmap budget if
 * decoded raw.
 */
object BackgroundImageStore {

    // In-memory decode cache. The key includes lastModified because imports
    // ALWAYS overwrite the same fixed filename (diary_background.jpg) — a
    // path-only key would serve stale bitmaps after re-import. maxDim is part
    // of the key for safety against future callers using other sizes.
    @Volatile
    private var cacheKey: Triple<String, Long, Int>? = null

    @Volatile
    private var cacheValue: ImageBitmap? = null

    private fun clearCache() {
        cacheKey = null
        cacheValue = null
    }

    fun backgroundFile(context: Context): File =
        File(File(context.filesDir, "backgrounds").apply { if (!exists()) mkdirs() }, "diary_background.jpg")

    /**
     * Copy a picked image into private storage, overwriting any previous one.
     * Explicitly drops the decode cache on success — some filesystems have
     * coarse lastModified granularity, so a fast re-import could otherwise
     * collide with the old timestamp.
     */
    suspend fun importFromUri(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val out = backgroundFile(context)
            val input = context.contentResolver.openInputStream(uri)
            if (input == null) {
                out.delete()
                clearCache()
                null
            } else {
                input.use { ins ->
                    out.outputStream().use { outs -> ins.copyTo(outs) }
                }
                clearCache()
                out
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decode downscaled so the long edge stays around [maxDim] pixels; null if
     * missing/broken. Cached in memory per (path, lastModified, maxDim).
     */
    suspend fun decode(path: String?, maxDim: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        if (path.isNullOrEmpty()) {
            clearCache()
            return@withContext null
        }
        val file = File(path)
        if (!file.exists()) {
            clearCache()
            return@withContext null
        }
        val stamp = runCatching { file.lastModified() }.getOrDefault(0L)
        cacheKey?.takeIf { it == Triple(path, stamp, maxDim) }?.let {
            return@withContext cacheValue
        }
        val bitmap = try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxDim || bounds.outHeight / (sample * 2) >= maxDim) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
        cacheKey = Triple(path, stamp, maxDim)
        cacheValue = bitmap
        bitmap
    }

    /** Delete the background file and drop the cache. */
    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        val deleted = runCatching { backgroundFile(context).delete() }.getOrDefault(false)
        clearCache()
        deleted
    }
}
