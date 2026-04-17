package com.door43.translationstudio.ui.home

import android.net.Uri
import com.door43.translationstudio.core.Progress
import com.door43.usecases.CheckForLatestRelease
import com.door43.usecases.UpdateSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface UpdateLibraryComponent {
    val state: StateFlow<UpdateState>
    val progress: StateFlow<Progress?>
    val event: Flow<UpdateEvent>

    fun onAction(action: UpdateAction)

    data class UpdateState(
        val resultMessage: Pair<String, String>? = null,
        val latestRelease: CheckForLatestRelease.Release? = null,
        val updateSourceResult: UpdateSource.Result? = null
    )

    sealed interface UpdateAction {
        data object UpdateSource : UpdateAction
        data class ImportIndex(val uri: Uri) : UpdateAction
        data object DownloadIndex : UpdateAction
        data object UpdateLanguages : UpdateAction
        data object CheckAppUpdate : UpdateAction
        data class DownloadLatestRelease(val release: CheckForLatestRelease.Release) : UpdateAction
        data object ClearResult : UpdateAction
        data object ClearLatestRelease : UpdateAction
        data object ClearUpdateSourceResult : UpdateAction
    }

    sealed interface UpdateEvent {
        data object IndexUpdated : UpdateEvent
    }
}
