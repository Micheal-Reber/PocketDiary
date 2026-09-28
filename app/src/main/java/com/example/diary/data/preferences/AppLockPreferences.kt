package com.example.diary.data.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom

class AppLockPreferences(context: Context) {

    private val appContext: Context = context.applicationContext

    companion object {
        val LOCK_ENABLED_KEY = booleanPreferencesKey("lock_enabled")
        val LOCK_HASH_KEY = stringPreferencesKey("lock_hash")
        val LOCK_SALT_KEY = stringPreferencesKey("lock_salt")
        val LOCK_MODE_KEY = intPreferencesKey("lock_mode")
        val LOCK_DAILY_KEY = booleanPreferencesKey("lock_daily")
        val LOCK_UNLOCKED_DATE_KEY = stringPreferencesKey("lock_unlocked_date")
        val LOCK_PIN_LEN_KEY = intPreferencesKey("lock_pin_len")
        const val MODE_EVERY_APP = 0
        const val MODE_EVERY_DIARY = 1
        const val MODE_DAILY = 2
        private const val MIN_PIN = 4
        private const val MAX_PIN = 6
    }

    val lockEnabled: Flow<Boolean> = appContext.dataStore.data.map { it[LOCK_ENABLED_KEY] ?: false }

    val hasPassword: Flow<Boolean> = appContext.dataStore.data.map { it[LOCK_HASH_KEY] != null }

    // 兼容旧 lock_daily 布尔键：无 lock_mode 时 true=当天免验
    val lockMode: Flow<Int> = appContext.dataStore.data.map { prefs ->
        prefs[LOCK_MODE_KEY] ?: if (prefs[LOCK_DAILY_KEY] == true) MODE_DAILY else MODE_EVERY_APP
    }

    val unlockedDate: Flow<String?> = appContext.dataStore.data.map { it[LOCK_UNLOCKED_DATE_KEY] }

    val pinLength: Flow<Int> = appContext.dataStore.data.map { it[LOCK_PIN_LEN_KEY] ?: 4 }

    suspend fun setLockEnabled(enabled: Boolean) {
        update { it[LOCK_ENABLED_KEY] = enabled }
    }

    suspend fun setLockMode(mode: Int) {
        update { it[LOCK_MODE_KEY] = mode }
    }

    suspend fun setPassword(pin: String) {
        if (pin.length !in MIN_PIN..MAX_PIN) return
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltHex = salt.joinToString("") { "%02x".format(it) }
        update { prefs ->
            prefs[LOCK_SALT_KEY] = saltHex
            prefs[LOCK_HASH_KEY] = hashPin(saltHex, pin)
            prefs[LOCK_PIN_LEN_KEY] = pin.length
        }
    }

    suspend fun verify(pin: String): Boolean = try {
        val prefs = appContext.dataStore.data.first()
        val salt = prefs[LOCK_SALT_KEY]
        val hash = prefs[LOCK_HASH_KEY]
        salt != null && hash != null && hashPin(salt, pin) == hash
    } catch (e: java.io.IOException) {
        Log.w("AppLockPreferences", "Failed to read lock password", e)
        false
    }

    suspend fun markUnlockedToday() {
        update { it[LOCK_UNLOCKED_DATE_KEY] = java.time.LocalDate.now().toString() }
    }

    private fun hashPin(saltHex: String, pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest((saltHex + pin).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private suspend fun update(block: (MutablePreferences) -> Unit) {
        try {
            appContext.dataStore.edit(block)
        } catch (e: java.io.IOException) {
            Log.w("AppLockPreferences", "Failed to persist lock preference", e)
        }
    }
}
