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

    fun onAction(action: Action)

    data class State(
        val resultMessage: Pair<String, String>? = null,
        val latestRelease: CheckForLatestRelease.Release? = null,
        val updateSourceResult: UpdateSource.Result? = null
    )

    sealed interface Action {
        data object UpdateSource : Action
        data class ImportIndex(val uri: Uri) : Action
        data object DownloadIndex : Action
        data object UpdateLanguages : Action
        data object CheckAppUpdate : Action
        data class DownloadLatestRelease(val release: CheckForLatestRelease.Release) : Action
        data object ClearResult : Action
        data object ClearLatestRelease : Action
        data object ClearUpdateSourceResult : Action
    }

    sealed interface Event {
        data object IndexUpdated : Event
    }
}
