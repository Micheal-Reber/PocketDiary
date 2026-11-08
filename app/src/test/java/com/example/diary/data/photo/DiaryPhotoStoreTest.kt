package com.example.diary.data.photo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiaryPhotoStoreTest {

    private lateinit var context: Context

    private fun tmp(name: String) =
        File(context.filesDir, "diary_photos/tmp/$name")

    private fun baked(entryId: Long, name: String) =
        File(context.filesDir, "diary_photos/$entryId/$name")

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "diary_photos").deleteRecursively()
        File(context.filesDir, "diary_photos/tmp").mkdirs()
    }

    @Test
    fun bakeMovesOnlyReferencedPhotos() = runBlocking {
        tmp("a.jpg").writeText("a", Charsets.UTF_8)
        tmp("b.jpg").writeText("b", Charsets.UTF_8)

        DiaryPhotoStore.bakeTmp(context, 1L, listOf("a.jpg"))

        assertTrue(baked(1L, "a.jpg").exists())
        assertFalse(tmp("a.jpg").exists())
        assertTrue(tmp("b.jpg").exists())
        assertFalse(baked(1L, "b.jpg").exists())
    }

    @Test
    fun cleanOrphanTmpKeepsReferencedFiles() = runBlocking {
        tmp("keep.jpg").writeText("k", Charsets.UTF_8)
        tmp("drop.jpg").writeText("d", Charsets.UTF_8)

        DiaryPhotoStore.cleanOrphanTmp(context, setOf("keep.jpg"))

        assertTrue(tmp("keep.jpg").exists())
        assertFalse(tmp("drop.jpg").exists())
    }
}
