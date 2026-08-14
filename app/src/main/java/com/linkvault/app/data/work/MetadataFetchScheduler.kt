package com.linkvault.app.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Schedules a one-off metadata fetch for a just-saved bookmark. Requires
 * network connectivity as a constraint rather than firing immediately
 * regardless — if there's no connection right when a link is saved (e.g.
 * saved while offline), WorkManager just waits and runs it once a
 * connection appears, rather than attempting and failing right away.
 *
 * [context] should be the Application context — this is only ever
 * constructed once, inside LinkVaultApplication, so that's guaranteed here.
 */
class MetadataFetchScheduler(private val context: Context) {

    fun scheduleFetch(bookmarkId: Long) {
        val request = OneTimeWorkRequestBuilder<MetadataFetchWorker>()
            .setInputData(MetadataFetchWorker.inputData(bookmarkId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }
}
