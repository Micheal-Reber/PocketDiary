package com.example.diary.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateUtils {
    private val reminderFmt = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
    private val dateFmt = java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd")
    private val timeFmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm")

    fun formatReminder(at: Long, repeat: Int): String {
        val base = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(java.util.Date(at))
        return if (repeat == com.example.diary.data.local.TodoItem.REPEAT_DAILY) "$base · 每天" else base
    }

    fun formatDate(date: java.time.LocalDate): String = date.format(dateFmt)

    fun formatTime(time: java.time.LocalTime): String = time.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

    fun formatDateTime(at: Long): String = SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(at))

    fun formatReminderAt(reminderAt: Long?, repeatRule: Int): String? {
        return reminderAt?.let {
            val base = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(java.util.Date(it))
            if (com.example.diary.data.local.TodoItem.REPEAT_DAILY == repeatRule) "$base · 每天" else base
        }
    }
}