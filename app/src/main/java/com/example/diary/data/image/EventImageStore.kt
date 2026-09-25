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

    private suspend fun copyUriTo(context: Context, uri: Uri, out: File): File? =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { ins ->
                    out.outputStream().use { outs -> ins.copyTo(outs) }
                } ?: return@withContext null
                out
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }

    /** 相册选图拷贝覆盖写入（选图 URI 只在回调期有效，必须落地私有存储）。 */
    suspend fun importFromUri(context: Context, uri: Uri, eventId: Long): File? =
        copyUriTo(context, uri, file(context, eventId))

    /** 编辑草稿文件：选图先写草稿，点「完成」[promoteDraft] 才覆盖正式文件。 */
    fun draftFile(context: Context, eventId: Long): File =
        File(dir(context), "bg_$eventId.draft")

    /** 编辑页选图 → 草稿（正式文件不动，直接退出不影响已应用的背景）。 */
    suspend fun importToDraft(context: Context, uri: Uri, eventId: Long): File? =
        copyUriTo(context, uri, draftFile(context, eventId))

    /** 草稿 → 正式文件（覆盖旧图）；无草稿返回 null，失败返回 null（草稿保留）。 */
    suspend fun promoteDraft(context: Context, eventId: Long): File? =
        withContext(Dispatchers.IO) {
            val draft = draftFile(context, eventId)
            if (!draft.exists()) return@withContext null
            val out = file(context, eventId)
            try {
                if (out.exists() && !out.delete()) return@withContext null
                if (!draft.renameTo(out)) {
                    draft.copyTo(out, overwrite = true)
                    draft.delete()
                }
                out
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }

    /** 丢弃草稿（编辑页退出/新会话进入时调用）。 */
    fun deleteDraft(context: Context, eventId: Long) {
        runCatching { draftFile(context, eventId).delete() }
    }

    /** 删除事件背景文件（事件删除/恢复默认时调用）。 */
    suspend fun clear(context: Context, eventId: Long) = withContext(Dispatchers.IO) {
        runCatching { file(context, eventId).delete() }.getOrDefault(false)
        Unit
    }
}
