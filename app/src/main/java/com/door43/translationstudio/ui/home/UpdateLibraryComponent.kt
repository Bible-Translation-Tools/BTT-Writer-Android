package com.door43.translationstudio.ui.home

import android.net.Uri
import com.door43.translationstudio.core.Progress
import com.door43.usecases.CheckForLatestRelease
import com.door43.usecases.UpdateSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface UpdateLibraryComponent {
    val state: StateFlow<State>
    val progress: StateFlow<Progress?>
    val event: Flow<Event>

    fun openDownloadSources()
    fun updateSources()
    fun importIndex(uri: Uri)
    fun downloadIndex()
    fun updateLanguages()
    fun checkAppUpdate()
    fun downloadLatestRelease(release: CheckForLatestRelease.Release)
    fun clearResult()
    fun clearLatestRelease()
    fun clearUpdateSourceResult()

    data class State(
        val resultMessage: Pair<String, String>? = null,
        val latestRelease: CheckForLatestRelease.Release? = null,
        val updateSourceResult: UpdateSource.Result? = null
    )

    sealed interface Event {
        data object IndexUpdated : Event
    }

    sealed interface Result {
        data object OpenDownloadSources : Result
    }
}
