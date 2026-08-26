package com.example.diary.data.image

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 每个倒数日事件一张背景图（区别于 BackgroundImageStore 的全局单文件）。
 * 文件名按 eventId 命名，存在即视为「设置了照片背景」；读取解码复用
 * BackgroundImageStore.decode（path+mtime 内存缓存直接可用）。
 */
object EventImageStore {

    private fun dir(context: Context): File =
        File(context.filesDir, "countdown_backgrounds").apply { mkdirs() }

    fun file(context: Context, eventId: Long): File =
        File(dir(context), "bg_$eventId.jpg")

    fun exists(context: Context, eventId: Long): Boolean =
        file(context, eventId).exists()

    /** 相册选图拷贝覆盖写入（选图 URI 只在回调期有效，必须落地私有存储）。 */
    suspend fun importFromUri(context: Context, uri: Uri, eventId: Long): File? =
        withContext(Dispatchers.IO) {
            try {
                val out = file(context, eventId)
                context.contentResolver.openInputStream(uri)?.use { ins ->
                    out.outputStream().use { outs -> ins.copyTo(outs) }
                } ?: return@withContext null
                out
            } catch (_: Exception) {
                null
            }
        }

    /** 删除事件背景文件（事件删除/恢复默认时调用）。 */
    suspend fun clear(context: Context, eventId: Long) = withContext(Dispatchers.IO) {
        runCatching { file(context, eventId).delete() }.getOrDefault(false)
        Unit
    }
}
