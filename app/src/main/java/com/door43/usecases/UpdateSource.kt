package com.door43.usecases

import android.content.Context
import android.util.Log
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.Platform
import com.door43.translationstudio.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.bibletranslationtools.resourcecatalog.ResourceCatalogClient
import org.bibletranslationtools.resourcecatalog.library.models.Translation

class UpdateSource(
    private val context: Context,
    private val catalogClient: ResourceCatalogClient,
    private val prefRepository: IPreferenceRepository
) {
    data class Result(
        val success: Boolean,
        val updatedCount: Int,
        val addedCount: Int
    )

    suspend fun execute(onProgress: (Float, String?) -> Unit = { _, _ -> }): Result {
        // Snapshot current state: map of resourceContainerSlug -> lastModifiedOnServer
        val previouslyUpdated = snapshotLastModified(onProgress)

        onProgress(-1f, null)

        // Pull updates from the server
        val success = try {
            val server = prefRepository.getDefaultPref(
                IPreferenceRepository.KEY_PREF_MEDIA_SERVER,
                context.resources.getString(R.string.pref_default_media_server)
            )
            val rootApiUrl = server + prefRepository.getRootCatalogApi()
            catalogClient.updateSources(rootApiUrl, onProgress)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update sources", e)
            false
        }

        if (!success) {
            return Result(success = false, updatedCount = 0, addedCount = 0)
        }

        onProgress(-1f, null)

        // Compare new state with previous snapshot
        val (updatedCount, addedCount) = countChanges(previouslyUpdated, onProgress)

        return Result(success = true, updatedCount = updatedCount, addedCount = addedCount)
    }

    /** Fetch all translations and their server-side last-modified timestamps in parallel. */
    private suspend fun snapshotLastModified(
        onProgress: (Float, String?) -> Unit
    ): Map<String, Int> = coroutineScope {
        val translations = findAllTranslations()
        val total = translations.size
        if (total == 0) return@coroutineScope emptyMap()

        var done = 0
        val deferred = translations.map { t ->
            async(Dispatchers.IO) {
                val lastModified = catalogClient.getResourceContainerLastModified(
                    t.language.slug,
                    t.project.slug,
                    t.resource.slug
                )
                val current = synchronized(this@coroutineScope) { ++done }
                if (current % 16 == 0 || current == total) {
                    onProgress(current / total.toFloat(), null)
                }
                t.resourceContainerSlug to lastModified
            }
        }
        deferred.awaitAll().toMap()
    }

    /** Compare current server state against the prior snapshot. */
    private suspend fun countChanges(
        previouslyUpdated: Map<String, Int>,
        onProgress: (Float, String?) -> Unit
    ): Pair<Int, Int> = coroutineScope {
        val translations = findAllTranslations()
        val total = translations.size
        if (total == 0) return@coroutineScope 0 to 0

        var done = 0
        val results = translations.map { t ->
            async(Dispatchers.IO) {
                val id = t.resourceContainerSlug
                val previous = previouslyUpdated[id]
                val change = when {
                    previous == null -> Change.ADDED
                    else -> try {
                        val now = catalogClient.getResourceContainerLastModified(
                            t.language.slug,
                            t.project.slug,
                            t.resource.slug
                        )
                        if (now > previous) Change.UPDATED else Change.NONE
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to check last-modified for $id", e)
                        Change.NONE
                    }
                }
                val current = synchronized(this@coroutineScope) { ++done }
                if (current % 16 == 0 || current == total) {
                    onProgress(current / total.toFloat(), null)
                }
                change
            }
        }.awaitAll()

        val updated = results.count { it == Change.UPDATED }
        val added = results.count { it == Change.ADDED }
        updated to added
    }

    private suspend fun findAllTranslations(): List<Translation> = withContext(Dispatchers.IO) {
        catalogClient.library.findTranslations(
            null, null, null, "book", null,
            Platform.MIN_CHECKING_LEVEL, -1
        )
    }

    private enum class Change { ADDED, UPDATED, NONE }

    companion object {
        private const val TAG = "UpdateSource"
    }
}