package com.example.diary.util

import com.example.diary.data.local.TodoItem
import java.text.SimpleDateFormat
import java.util.Locale

object DateUtils {
    // 单槽共享 formatter — SimpleDateFormat 非线程安全，仅在主线程 UI 格式化时使用
    private val reminderFmt = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
    private val dateFmt = java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd")
    private val timeFmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm")

    fun formatReminder(at: Long, repeat: Int): String {
        val base = reminderFmt.format(java.util.Date(at))
        return if (repeat == TodoItem.REPEAT_DAILY) "$base · 每天" else base
    }

    fun formatDate(date: java.time.LocalDate): String = date.format(dateFmt)

    fun formatTime(time: java.time.LocalTime): String = time.format(timeFmt)

    fun formatReminderAt(reminderAt: Long?, repeatRule: Int): String? {
        return reminderAt?.let {
            val base = reminderFmt.format(java.util.Date(it))
            if (TodoItem.REPEAT_DAILY == repeatRule) "$base · 每天" else base
        }
    }
}