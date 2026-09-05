package com.linkvault.app.testutil

import com.linkvault.app.data.repository.BackupPreferences

/**
 * In-memory [BackupPreferences] for unit tests. Doesn't go through
 * Android's SharedPreferences (which would need a real [Context]) and
 * exposes the `lastBackupTime` value for assertions.
 */
class FakePreferenceRepository : BackupPreferences {
    /** Public-for-read so tests can assert on it. Renamed from
     *  `lastBackupTime` to avoid a JVM signature clash with the
     *  `getLastBackupTime()` interface method (Kotlin would otherwise
     *  generate a getter with the same JVM name). */
    var storedLastBackupTime: Long = 0L
        private set

    override fun getLastBackupTime(): Long = storedLastBackupTime

    override fun updateLastBackupTime() {
        storedLastBackupTime = System.currentTimeMillis()
    }
}
