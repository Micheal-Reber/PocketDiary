package com.example.diary.data.focus

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.diary.data.preferences.FocusPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 用户自定义专注音频。
 *
 * 选中的文件会被**复制进 filesDir**（而不是每次拿 SAF 的 Uri 去读）：
 * 用户卸载重装、云备份、文件被移走、URI 权限被回收，前台服务都还能播。
 */
class CustomNoiseStore(private val context: Context) {

    private val preferences = FocusPreferences(context)

    private fun targetFile(): File = File(File(context.filesDir, DIR), FILE_NAME)

    fun currentFile(): File? = targetFile().takeIf { it.isFile && it.length() > 0 }

    suspend fun import(uri: Uri, displayName: String?): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, DIR)
            if (!dir.exists() && !dir.mkdirs()) return@runCatching false
            val name = displayName ?: queryDisplayName(uri) ?: uri.lastPathSegment ?: "audio"
            val temp = File(dir, "$FILE_NAME.tmp")
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching false
            if (temp.length() <= 0L) {
                temp.delete()
                return@runCatching false
            }
            val target = targetFile()
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            preferences.setCustomNoise(target.absolutePath, name)
            true
        }.getOrDefault(false)
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        targetFile().delete()
        preferences.setCustomNoise(null, null)
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()

    private companion object {
        const val DIR = "focus_audio"
        const val FILE_NAME = "custom_audio.bin"
    }
}