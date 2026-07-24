package com.example.diary

import android.app.Application
import android.util.Log
import com.example.diary.data.photo.PhotoStore

/**
 * Application class. The Room database singleton is owned by `MainActivity`
 * (the only consumer — passed into [com.example.diary.ui.navigation.AppNavigation]).
 * Keep this class as a hook for future process-wide initialization (e.g.,
 * crash reporting, logging).
 */
class DiaryApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Sweep leftover 0-byte photo stubs from a previous process that was
        // killed mid-camera-intent. Runs on a background thread so we don't
        // delay app start by disk I/O — these files are never referenced by
        // the DB, so the sweep is best-effort cleanup, not user-visible work.
        Thread({
            try {
                val removed = PhotoStore.cleanupEmptyStubs(this)
                if (removed > 0) {
                    Log.i("DiaryApplication", "Cleaned up $removed empty photo stub(s)")
                }
            } catch (e: Exception) {
                Log.w("DiaryApplication", "Photo stub cleanup failed", e)
            }
        }, "photo-stub-cleanup").start()
    }
}
