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

    fun backgroundFile(context: Context): File =
        File(File(context.filesDir, "backgrounds").apply { if (!exists()) mkdirs() }, "diary_background.jpg")

    /** Copy a picked image into private storage, overwriting any previous one. */
    suspend fun importFromUri(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val out = backgroundFile(context)
            val input = context.contentResolver.openInputStream(uri)
            if (input == null) {
                out.delete()
                null
            } else {
                input.use { ins ->
                    out.outputStream().use { outs -> ins.copyTo(outs) }
                }
                out
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Decode downscaled so the long edge stays around [maxDim] pixels; null if missing/broken. */
    suspend fun decode(path: String?, maxDim: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        if (path.isNullOrEmpty()) return@withContext null
        val file = File(path)
        if (!file.exists()) return@withContext null
        try {
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
    }

    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        runCatching { backgroundFile(context).delete() }
    }
}
