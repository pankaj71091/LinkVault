package com.linkvault.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The two-method surface that [BackupRepository] needs from
 * [PreferenceRepository]. Exists so unit tests can supply an in-memory
 * implementation without going through Android's DataStore (which would
 * require a real [Context]). The production class [PreferenceRepository]
 * implements this; the test fake in
 * `src/test/.../testutil/FakePreferenceRepository.kt` also does.
 *
 * Methods are `suspend` because DataStore's read/write API is
 * inherently async — moving away from SharedPreferences' synchronous
 * first-read that used to block [HomeViewModel.init] on the main thread
 * is one of the wins of this migration.
 */
interface BackupPreferences {
    suspend fun getLastBackupTime(): Long
    suspend fun updateLastBackupTime()
}

/**
 * DataStore key for the last-backup timestamp. Declared at file scope
 * (not in the class companion) because the `produceMigrations` lambda
 * below runs lazily on first DataStore access — the class may not be
 * fully loaded yet at that point, and the key is also referenced from
 * the top-level DataStore delegate. Single source of truth.
 */
internal val KEY_LAST_BACKUP_TIME = longPreferencesKey("last_backup_time")

/**
 * The DataStore delegate. Kept at file-scope (not as a property of the
 * class) because `preferencesDataStore` requires being installed as a
 * top-level extension on Context — that's how DataStore's
 * single-instance-per-process guarantee is implemented.
 *
 * [produceMigrations] runs the [SharedPreferencesMigration] on the first
 * DataStore access after upgrade, copying the existing `lastBackupTime`
 * value out of the old SharedPreferences file and into the DataStore
 * proto. The old file is then left in place (DataStore doesn't delete
 * it), which is harmless — but could be cleaned up later if desired.
 */
private val Context.linkvaultDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "linkvault_prefs",
    produceMigrations = { ctx ->
        listOf(
            SharedPreferencesMigration(
                context = ctx,
                sharedPreferencesName = "linkvault_prefs",
                keysToMigrate = setOf(KEY_LAST_BACKUP_TIME.name)
            )
        )
    }
)

/**
 * Manages app-level settings and persistent state that doesn't belong in
 * the Room database (e.g. last backup timestamp).
 *
 * Backed by Jetpack DataStore (Preferences) since the v0.2 migration.
 * DataStore is async (no main-thread stalls on first read), type-safe
 * (no string keys scattered through the code), and forward-compatible
 * (new keys can be added without a migration step for existing fields).
 */
class PreferenceRepository(context: Context) : BackupPreferences {
    private val dataStore = context.applicationContext.linkvaultDataStore

    override suspend fun getLastBackupTime(): Long =
        dataStore.data
            .map { prefs -> prefs[KEY_LAST_BACKUP_TIME] ?: 0L }
            .first()

    override suspend fun updateLastBackupTime() {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_BACKUP_TIME] = System.currentTimeMillis()
        }
    }
}
