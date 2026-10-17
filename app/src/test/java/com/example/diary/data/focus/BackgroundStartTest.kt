package com.example.diary.data.focus

import android.app.AppOpsManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundStartTest {

    @Test
    fun `only an explicit denial counts as blocked`() {
        assertFalse(BackgroundStart.isDeniedByMode(AppOpsManager.MODE_ALLOWED))
        assertTrue(BackgroundStart.isDeniedByMode(AppOpsManager.MODE_IGNORED))
        assertTrue(BackgroundStart.isDeniedByMode(AppOpsManager.MODE_ERRORED))
    }

    @Test
    fun `an unreadable app op is treated as allowed`() {
        assertFalse(BackgroundStart.isDeniedByMode(null))
    }
}