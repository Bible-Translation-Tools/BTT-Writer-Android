package com.door43.translationstudio.ui.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.door43.translationstudio.R
import com.door43.translationstudio.core.ProgressManager
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.TaskHandle
import com.door43.translationstudio.ui.launchWithProgress
import com.door43.usecases.CheckForLatestRelease
import com.door43.usecases.DownloadIndex
import com.door43.usecases.DownloadLatestRelease
import com.door43.usecases.UpdateCatalogs
import com.door43.usecases.UpdateSource
import com.door43.util.FileUtilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

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

class UpdateLibraryViewModel(
    private val downloadIndex: DownloadIndex,
    private val updateCatalogs: UpdateCatalogs,
    private val checkForLatestRelease: CheckForLatestRelease,
    private val downloadLatestRelease: DownloadLatestRelease,
    private val updateSource: UpdateSource
) : ViewModel(), KoinComponent, ProgressOwner {

    private val application: Application by inject()

    private val progressManager = ProgressManager(viewModelScope)
    override val progress get() = progressManager.progress

    private val _state = MutableStateFlow(UpdateState())
    val state = _state.asStateFlow()

    private val _event = Channel<UpdateEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        progressManager.runTask(message, block)
    }

    fun onAction(action: UpdateAction) {
        when (action) {
            is UpdateAction.ImportIndex -> importIndex(action.uri)
            is UpdateAction.DownloadLatestRelease -> downloadLatestRelease(action.release)
            is UpdateAction.UpdateSource -> updateSource()
            is UpdateAction.DownloadIndex -> downloadIndex()
            is UpdateAction.UpdateLanguages -> updateLanguages()
            is UpdateAction.CheckAppUpdate -> checkAppUpdate()
            is UpdateAction.ClearResult -> _state.update { it.copy(resultMessage = null) }
            is UpdateAction.ClearLatestRelease -> _state.update { it.copy(latestRelease = null) }
            is UpdateAction.ClearUpdateSourceResult -> _state.update {
                it.copy(updateSourceResult = null)
            }
        }
    }

    private fun updateSource() {
        launchWithProgress(
            application.getString(R.string.updating_sources)
        ) { handle ->
            val result = withContext(Dispatchers.IO) {
                updateSource.execute { progress, details ->
                    handle.update(progress, handle.initialMessage, details)
                }
            }

            if (result.success) {
                _state.update { it.copy(updateSourceResult = result) }
            } else {
                updateResultMessage(
                    title = application.getString(R.string.error),
                    message = application.getString(R.string.options_update_failed)
                )
            }
        }
    }

    private fun importIndex(uri: Uri) {
        val filename = FileUtilities.getFileName(application, uri)
        val isSqlite = filename.contains(".sqlite", ignoreCase = true)

        if (isSqlite) {
            launchWithProgress(
                application.getString(R.string.importing_index)
            ) {
                val success = withContext(Dispatchers.IO) {
                    downloadIndex.import(uri)
                }
                if (success) {
                    _event.trySend(UpdateEvent.IndexUpdated)
                } else {
                    updateResultMessage(
                        title = application.getString(R.string.error),
                        message = application.getString(R.string.options_update_failed)
                    )
                }
            }
        } else {
            updateResultMessage(
                title = application.getString(R.string.error),
                message = application.getString(R.string.import_index_failed)
            )
        }
    }

    private fun downloadIndex() {
        launchWithProgress(
            application.getString(R.string.importing_index)
        ) { handle ->
            val success = withContext(Dispatchers.IO) {
                downloadIndex.download { progress, message ->
                    handle.update(progress, message)
                }
            }
            if (success) {
                _event.trySend(UpdateEvent.IndexUpdated)
            } else {
                updateResultMessage(
                    title = application.getString(R.string.error),
                    message = application.getString(R.string.options_update_failed)
                )
            }
        }
    }

    private fun updateLanguages() {
        launchWithProgress(
            application.getString(R.string.updating_languages)
        ) { handle ->
            val result = withContext(Dispatchers.IO) {
                updateCatalogs.execute(true) { progress, details ->
                    handle.update(progress, handle.initialMessage, details)
                }
            }
            if (result.success) {
                updateResultMessage(
                    title = application.getString(R.string.success),
                    message = application.getString(
                        R.string.update_languages_success,
                        result.addedCount
                    )
                )
            } else {
                updateResultMessage(
                    title = application.getString(R.string.error),
                    message = application.getString(R.string.options_update_failed)
                )
            }
        }
    }

    private fun checkAppUpdate() {
        launchWithProgress {
            val result = withContext(Dispatchers.IO) {
                checkForLatestRelease.execute()
            }

            if (result.release != null) {
                _state.update { it.copy(latestRelease = result.release) }
            } else {
                updateResultMessage(
                    title = application.getString(R.string.check_for_updates),
                    message = application.getString(R.string.have_latest_app_update)
                )
            }
        }
    }

    private fun downloadLatestRelease(release: CheckForLatestRelease.Release) {
        launchWithProgress {
            _state.update { it.copy(latestRelease = null) }
            withContext(Dispatchers.IO) {
                downloadLatestRelease.execute(release)
            }
        }
    }

    private fun updateResultMessage(title: String, message: String) {
        _state.update {
            it.copy(resultMessage = title to message)
        }
    }
}