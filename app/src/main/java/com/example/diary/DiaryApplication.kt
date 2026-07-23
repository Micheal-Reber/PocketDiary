package com.example.diary

import android.app.Application
import com.example.diary.data.local.AppDatabase

class DiaryApplication : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
}
