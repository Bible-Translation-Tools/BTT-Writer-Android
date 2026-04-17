package com.door43.translationstudio.ui.home

import android.app.Application
import android.net.Uri
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.door43.translationstudio.R
import com.door43.translationstudio.core.ProgressManager
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.TaskHandle
import com.door43.translationstudio.ui.launchWithProgress
import com.door43.translationstudio.ui.navigation.ComponentScope
import com.door43.usecases.CheckForLatestRelease
import com.door43.usecases.DownloadIndex
import com.door43.usecases.DownloadLatestRelease
import com.door43.usecases.UpdateCatalogs
import com.door43.usecases.UpdateSource
import com.door43.util.FileUtilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DefaultUpdateLibraryComponent(
    componentContext: ComponentContext,
    triggerUpdate: Boolean = false,
) : UpdateLibraryComponent,
    ComponentContext by componentContext,
    ComponentScope, ProgressOwner, KoinComponent {

    private val application: Application by inject()
    private val downloadIndex: DownloadIndex by inject()
    private val updateCatalogs: UpdateCatalogs by inject()
    private val checkForLatestRelease: CheckForLatestRelease by inject()
    private val downloadLatestRelease: DownloadLatestRelease by inject()
    private val updateSource: UpdateSource by inject()

    override val coroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val progressManager = ProgressManager(coroutineScope)
    override val progress get() = progressManager.progress

    private val _state = MutableStateFlow(UpdateLibraryComponent.State())
    override val state = _state.asStateFlow()

    private val _event = Channel<UpdateLibraryComponent.Event>(Channel.BUFFERED)
    override val event = _event.receiveAsFlow()

    init {
        if (triggerUpdate) {
            updateSource()
        }

        lifecycle.doOnDestroy {
            coroutineScope.cancel()
        }
    }

    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        progressManager.runTask(message, block)
    }

    override fun onAction(action: UpdateLibraryComponent.Action) {
        when (action) {
            is UpdateLibraryComponent.Action.ImportIndex -> importIndex(action.uri)
            is UpdateLibraryComponent.Action.DownloadLatestRelease -> downloadLatestRelease(action.release)
            is UpdateLibraryComponent.Action.UpdateSource -> updateSource()
            is UpdateLibraryComponent.Action.DownloadIndex -> downloadIndex()
            is UpdateLibraryComponent.Action.UpdateLanguages -> updateLanguages()
            is UpdateLibraryComponent.Action.CheckAppUpdate -> checkAppUpdate()
            is UpdateLibraryComponent.Action.ClearResult -> _state.update { it.copy(resultMessage = null) }
            is UpdateLibraryComponent.Action.ClearLatestRelease -> _state.update { it.copy(latestRelease = null) }
            is UpdateLibraryComponent.Action.ClearUpdateSourceResult -> _state.update {
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
                    _event.trySend(UpdateLibraryComponent.Event.IndexUpdated)
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
                _event.trySend(UpdateLibraryComponent.Event.IndexUpdated)
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
        launchWithProgress(
            application.getString(R.string.checking_for_updates)
        ) {
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
