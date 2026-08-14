package com.linkvault.app.data.repository

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages app-level settings and persistent state that doesn't belong in
 * the Room database (e.g. last backup timestamp).
 */
class PreferenceRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("linkvault_prefs", Context.MODE_PRIVATE)

    fun getLastBackupTime(): Long = prefs.getLong(KEY_LAST_BACKUP_TIME, 0L)

    fun updateLastBackupTime() {
        prefs.edit().putLong(KEY_LAST_BACKUP_TIME, System.currentTimeMillis()).apply()
    }

    companion object {
        private const val KEY_LAST_BACKUP_TIME = "last_backup_time"
    }
}
