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

    // In-memory decode cache. Keyed by (path, lastModified, maxDim) so imports
    // that overwrite the same fixed filename invalidate via mtime; multi-entry
    // LruCache so list background / editor thumbs / body images don't evict
    // each other (single-slot thrash). Max entries small — each is a downsampled bitmap.
    private const val CACHE_MAX_ENTRIES = 8
    private val decodeCache = object : android.util.LruCache<Triple<String, Long, Int>, ImageBitmap?>(CACHE_MAX_ENTRIES) {
        override fun sizeOf(key: Triple<String, Long, Int>, value: ImageBitmap?): Int = 1
    }

    private fun clearCache() {
        decodeCache.evictAll()
    }

    fun backgroundFile(context: Context): File =
        File(File(context.filesDir, "backgrounds").apply { if (!exists()) mkdirs() }, "diary_background.jpg")

    /** DataStore/备份里存的规范相对路径（相对 filesDir）。 */
    const val RELATIVE_PATH = "backgrounds/diary_background.jpg"

    /**
     * 归一成存库值：
     * - null → null（无背景）
     * - 已是相对路径 → 原样
     * - 本机 filesDir 下的绝对路径 → 相对路径
     * - 旧包/换机绝对路径 → 重写为本机 RELATIVE_PATH（图片本体固定在 backgroundFile）
     */
    fun normalizeStoredPath(context: Context, path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (!File(path).isAbsolute) return path
        val filesDir = context.filesDir.canonicalFile
        val abs = File(path).canonicalFile
        if (abs.path.startsWith(filesDir.path + File.separator)) {
            return abs.path.substring(filesDir.path.length + 1).replace('\\', '/')
        }
        // 跨设备绝对路径：只要背景文件在本机存在就生效
        return if (backgroundFile(context).exists()) RELATIVE_PATH else null
    }

    /** 解析存库路径 → 本机 File；相对路径基于 filesDir，绝对路径原样（兼容旧值）。 */
    fun resolve(context: Context, storedPath: String): File {
        val f = File(storedPath)
        return if (f.isAbsolute) f else File(context.filesDir, storedPath)
    }

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
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decode downscaled so the long edge stays around [maxDim] pixels; null if
     * missing/broken. Cached in memory per (path, lastModified, maxDim).
     * [path] may be filesDir-relative (preferred) or absolute (legacy).
     * [context] needed only to resolve relative paths.
     */
    suspend fun decode(context: Context, path: String?, maxDim: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        if (path.isNullOrEmpty()) {
            return@withContext null
        }
        val file = resolve(context, path)
        if (!file.exists()) {
            return@withContext null
        }
        val stamp = runCatching { file.lastModified() }.getOrDefault(0L)
        val key = Triple(file.absolutePath, stamp, maxDim)
        decodeCache.get(key)?.let {
            return@withContext it
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
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        // null 也缓存：避免坏图每次重组重复解码
        decodeCache.put(key, bitmap)
        bitmap
    }

    /** 按背景文件本身解码（相对/绝对统一入口）。 */
    suspend fun decodeBackground(context: Context, maxDim: Int): ImageBitmap? {
        val stored = runCatching {
            // 直接按规范路径读，避免再走 DataStore
            if (backgroundFile(context).exists()) RELATIVE_PATH else null
        }.getOrNull()
        return decode(context, stored, maxDim)
    }

    /** Delete the background file and drop the cache. */
    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        val deleted = runCatching { backgroundFile(context).delete() }.getOrDefault(false)
        clearCache()
        deleted
    }
}
