package com.linkvault.app.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.linkvault.app.LinkVaultApplication
import com.linkvault.app.data.remote.PageMetadataFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Looks up the bookmark by id, fetches its page's title/favicon, and
 * applies whatever was found. Every path returns [Result.success] —
 * including "couldn't fetch anything" — since this is best-effort
 * enrichment of a bookmark that's already safely saved either way; there's
 * nothing here worth WorkManager retrying aggressively for.
 *
 * Reaches into [LinkVaultApplication] for the repository rather than
 * having it constructor-injected, so this works with WorkManager's default
 * WorkerFactory (reflection-based, needs the standard (Context,
 * WorkerParameters) constructor) without needing a custom
 * Configuration.Provider setup — not worth the extra ceremony for a
 * dependency graph this small.
 */
class MetadataFetchWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val bookmarkId = inputData.getLong(KEY_BOOKMARK_ID, -1L)
        if (bookmarkId == -1L) return Result.success()

        val bookmarkRepository = (applicationContext as LinkVaultApplication).bookmarkRepository
        val bookmark = bookmarkRepository.getBookmarkById(bookmarkId) ?: return Result.success()

        val metadata = withContext(Dispatchers.IO) {
            PageMetadataFetcher.fetch(bookmark.url)
        } ?: return Result.success()

        bookmarkRepository.applyFetchedMetadata(
            bookmarkId = bookmarkId,
            fetchedTitle = metadata.title,
            fetchedFaviconUrl = metadata.faviconUrl
        )

        return Result.success()
    }

    companion object {
        const val KEY_BOOKMARK_ID = "bookmark_id"

        fun inputData(bookmarkId: Long) = workDataOf(KEY_BOOKMARK_ID to bookmarkId)
    }
}
