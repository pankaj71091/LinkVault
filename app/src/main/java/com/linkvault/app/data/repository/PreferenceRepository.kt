package com.linkvault.app.data.repository

import android.content.Context
import android.content.SharedPreferences

/**
 * The two-method surface that [BackupRepository] needs from
 * [PreferenceRepository]. Exists so unit tests can supply an in-memory
 * implementation without going through Android's SharedPreferences
 * (which would require a real [Context]). The production class
 * [PreferenceRepository] implements this; the test fake in
 * `src/test/.../testutil/FakePreferenceRepository.kt` also does.
 */
interface BackupPreferences {
    fun getLastBackupTime(): Long
    fun updateLastBackupTime()
}

/**
 * Manages app-level settings and persistent state that doesn't belong in
 * the Room database (e.g. last backup timestamp).
 *
 * Kept as a concrete class that implements [BackupPreferences]. Constructed
 * with the application [Context]; the only state held is a single
 * `lastBackupTime` Long in SharedPreferences.
 */
class PreferenceRepository(context: Context) : BackupPreferences {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("linkvault_prefs", Context.MODE_PRIVATE)

    override fun getLastBackupTime(): Long = prefs.getLong(KEY_LAST_BACKUP_TIME, 0L)

    override fun updateLastBackupTime() {
        prefs.edit().putLong(KEY_LAST_BACKUP_TIME, System.currentTimeMillis()).apply()
    }

    companion object {
        private const val KEY_LAST_BACKUP_TIME = "last_backup_time"
    }
}
