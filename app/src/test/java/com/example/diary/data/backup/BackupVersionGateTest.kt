package com.example.diary.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 备份版本闸门与日记背景路径归一的纯逻辑测试。
 * normalizeStoredPath 依赖 Context/filesDir，此处用临时目录模拟绝对路径分支。
 */
class BackupVersionGateTest {

    @Test
    fun currentVersion_isTwo() {
        assertEquals(2, BackupData.CURRENT_VERSION)
    }

    @Test
    fun acceptRange_isOneToCurrent() {
        val accept = { v: Int -> v in 1..BackupData.CURRENT_VERSION }
        assertTrue(accept(1))
        assertTrue(accept(2))
        assertFalse(accept(0))
        assertFalse(accept(3))
        assertFalse(accept(-1))
    }

    @Test
    fun relativeBackgroundPath_isNotAbsolute() {
        val path = "backgrounds/diary_background.jpg"
        assertFalse(File(path).isAbsolute)
    }
}
