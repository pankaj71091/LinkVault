package com.linkvault.app.testutil

import com.linkvault.app.data.repository.BackupPreferences

/**
 * In-memory [BackupPreferences] for unit tests. Doesn't go through
 * Android's DataStore (which would need a real [Context]) and exposes
 * the `lastBackupTime` value for assertions.
 *
 * Methods are `suspend` to match the production interface since the
 * DataStore migration (Task 5). The fake does no I/O so the suspension
 * is meaningless, but keeping the signature aligned avoids a separate
 * code path in tests.
 */
class FakePreferenceRepository : BackupPreferences {
    /** Public-for-read so tests can assert on it. */
    var storedLastBackupTime: Long = 0L
        private set

    override suspend fun getLastBackupTime(): Long = storedLastBackupTime

    override suspend fun updateLastBackupTime() {
        storedLastBackupTime = System.currentTimeMillis()
    }
}
