package com.example.diary.data.draft

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiaryDraftStoreTest {

    private lateinit var context: Context

    private fun draft(
        date: String,
        originDate: String? = date,
        content: String = date
    ) = DiaryDraft(
        date = date,
        originDate = originDate,
        content = content,
        savedAt = 1L
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "diary_drafts").deleteRecursively()
    }

    @Test
    fun saveLoadAndDeleteRoundTrip() = runBlocking {
        val d = draft("2026-11-08", content = "写到一半")
        DiaryDraftStore.save(context, d)
        assertEquals(d, DiaryDraftStore.load(context, "2026-11-08"))

        DiaryDraftStore.delete(context, "2026-11-08")
        assertNull(DiaryDraftStore.load(context, "2026-11-08"))
    }

    @Test
    fun saveMovedDraftPrunesSameOriginOldDate() = runBlocking {
        DiaryDraftStore.save(context, draft("2026-11-07", originDate = "2026-11-07", content = "a"))
        DiaryDraftStore.save(context, draft("2026-11-08", originDate = "2026-11-07", content = "b"))

        assertNull(DiaryDraftStore.load(context, "2026-11-07"))
        assertEquals("b", DiaryDraftStore.load(context, "2026-11-08")?.content)
    }

    @Test
    fun deleteSessionRemovesCurrentAndSameOriginSiblings() = runBlocking {
        val current = draft("2026-11-08", originDate = "2026-11-07", content = "b")
        DiaryDraftStore.save(context, current)
        // 手工造一个同 origin 的旧日期残留（正常写入会自动清掉）
        val stale = draft("2026-11-07", originDate = "2026-11-07", content = "a")
        File(context.filesDir, "diary_drafts/draft_2026-11-07.json")
            .writeText(DiaryDraftStore.encode(stale), Charsets.UTF_8)

        DiaryDraftStore.deleteSession(context, "2026-11-08")

        assertNull(DiaryDraftStore.load(context, "2026-11-08"))
        assertNull(DiaryDraftStore.load(context, "2026-11-07"))
    }

    @Test
    fun referencedPhotoNamesUnionsAllDrafts() = runBlocking {
        DiaryDraftStore.save(context, draft("2026-11-07", content = "[img:a.jpg] [img:b.jpg]"))
        DiaryDraftStore.save(context, draft("2026-11-08", content = "[img:c.jpg]"))

        assertEquals(
            setOf("a.jpg", "b.jpg", "c.jpg"),
            DiaryDraftStore.referencedPhotoNames(context)
        )
    }

    @Test
    fun corruptDraftIsIgnored() = runBlocking {
        val dir = File(context.filesDir, "diary_drafts").apply { mkdirs() }
        File(dir, "draft_2026-11-08.json").writeText("{not json", Charsets.UTF_8)

        assertNull(DiaryDraftStore.load(context, "2026-11-08"))
        assertTrue(DiaryDraftStore.loadAll(context).isEmpty())
        assertTrue(DiaryDraftStore.referencedPhotoNames(context).isEmpty())
    }
}
