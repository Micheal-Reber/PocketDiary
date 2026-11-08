package com.example.diary.data.draft

import android.content.Context
import android.util.Log
import com.example.diary.data.photo.DiaryPhotoStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class DiaryDraft(
    val version: Int = 1,
    val date: String,
    val originDate: String? = null,
    val content: String,
    val mood: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationName: String? = null,
    val weather: String? = null,
    val existingId: Long? = null,
    val savedAt: Long = System.currentTimeMillis()
) {
    fun matches(
        date: String,
        content: String,
        mood: String?,
        lat: Double?,
        lon: Double?,
        locationName: String?,
        weather: String?,
        existingId: Long?
    ): Boolean =
        this.date == date &&
            this.content.trim() == content.trim() &&
            this.mood == mood &&
            this.latitude == lat &&
            this.longitude == lon &&
            this.locationName == locationName &&
            this.weather == weather &&
            this.existingId == existingId
}

/**
 * 编辑器草稿：一天一份 JSON，落 filesDir/diary_drafts/。
 *
 * 与 [DiaryPhotoStore] 的 tmp 图片配合：草稿正文里的 [img:] 标记决定哪些
 * tmp 图片仍属于「未完成编辑」，保存/丢弃/打开编辑器时只清理无人引用的
 * 孤儿文件，避免 A 日期草稿的图片被 B 日期保存搬走或清掉。
 */
object DiaryDraftStore {

    private const val TAG = "DiaryDraftStore"
    private const val DIR_NAME = "diary_drafts"
    private const val FILE_PREFIX = "draft_"
    private const val FILE_SUFFIX = ".json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // 进程级作用域：ON_STOP 兜底写入不能随 Activity/Composition 取消
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    fun encode(draft: DiaryDraft): String = json.encodeToString(draft)

    fun decode(raw: String): DiaryDraft? =
        runCatching { json.decodeFromString<DiaryDraft>(raw) }.getOrNull()

    private fun dir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    private fun file(context: Context, date: String): File =
        File(dir(context), "$FILE_PREFIX$date$FILE_SUFFIX")

    private fun read(context: Context, date: String): DiaryDraft? {
        val f = file(context, date)
        if (!f.exists()) return null
        return try {
            decode(f.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            Log.w(TAG, "read failed: $date", e)
            null
        }
    }

    suspend fun save(context: Context, draft: DiaryDraft) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val app = context.applicationContext
            val target = file(app, draft.date)
            val tmp = File(target.parentFile, target.name + ".tmp")
            try {
                tmp.writeText(encode(draft), Charsets.UTF_8)
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }
                pruneMoved(app, draft)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "save failed: ${draft.date}", e)
                runCatching { tmp.delete() }
                Unit
            }
        }
    }

    /** ON_STOP 兜底：写入不随编辑器销毁而取消。 */
    fun saveAsync(context: Context, draft: DiaryDraft) {
        val app = context.applicationContext
        scope.launch { save(app, draft) }
    }

    /** 改日期后只保留新日期草稿；同一 originDate 的旧文件不再提示。 */
    private fun pruneMoved(context: Context, draft: DiaryDraft) {
        val origin = draft.originDate ?: return
        loadAllSync(context)
            .filter { it.originDate == origin && it.date != draft.date }
            .forEach { runCatching { file(context, it.date).delete() } }
    }

    suspend fun load(context: Context, date: String): DiaryDraft? = withContext(Dispatchers.IO) {
        read(context.applicationContext, date)
    }

    suspend fun delete(context: Context, date: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching { file(context.applicationContext, date).delete() }
            Unit
        }
    }

    /** 删除该编辑会话的全部草稿：当前日期 + 同一 originDate 的改日期残留。 */
    suspend fun deleteSession(
        context: Context,
        date: String,
        originDate: String? = null
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val app = context.applicationContext
            val draft = read(app, date)
            runCatching { file(app, date).delete() }
            val origin = draft?.originDate ?: originDate ?: return@withLock
            loadAllSync(app)
                .filter { it.originDate == origin && it.date != date }
                .forEach { runCatching { file(app, it.date).delete() } }
        }
    }

    suspend fun loadAll(context: Context): List<DiaryDraft> = withContext(Dispatchers.IO) {
        loadAllSync(context.applicationContext)
    }

    private fun loadAllSync(context: Context): List<DiaryDraft> =
        dir(context).listFiles()
            ?.filter { it.isFile && it.name.startsWith(FILE_PREFIX) && it.name.endsWith(FILE_SUFFIX) }
            ?.mapNotNull { f ->
                try {
                    decode(f.readText(Charsets.UTF_8))
                } catch (e: Exception) {
                    Log.w(TAG, "skip corrupt draft: ${f.name}", e)
                    null
                }
            }
            ?: emptyList()

    /** 所有草稿正文引用到的 tmp 图片名（清理孤儿文件时保留）。 */
    suspend fun referencedPhotoNames(context: Context): Set<String> =
        loadAll(context)
            .flatMap { DiaryPhotoStore.photoNamesIn(it.content) }
            .toSet()
}
