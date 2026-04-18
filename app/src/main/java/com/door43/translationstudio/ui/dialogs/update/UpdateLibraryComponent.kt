package com.door43.translationstudio.ui.dialogs.update

import android.app.Application
import android.net.Uri
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.door43.translationstudio.R
import com.door43.translationstudio.core.ComponentScope
import com.door43.translationstudio.core.Progress
import com.door43.translationstudio.core.ProgressManager
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.TaskHandle
import com.door43.translationstudio.core.launchWithProgress
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

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

class DefaultUpdateLibraryComponent(
    componentContext: ComponentContext,
    triggerUpdate: Boolean = false,
    private val onResult: (UpdateLibraryComponent.Result) -> Unit
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
            updateSources()
        }

        lifecycle.doOnDestroy {
            coroutineScope.cancel()
        }
    }

    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        progressManager.runTask(message, block)
    }

    override fun openDownloadSources() {
        onResult(UpdateLibraryComponent.Result.OpenDownloadSources)
    }

    override fun updateSources() {
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

    override fun importIndex(uri: Uri) {
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

    override fun downloadIndex() {
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

    override fun updateLanguages() {
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

    override fun checkAppUpdate() {
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

    override fun downloadLatestRelease(release: CheckForLatestRelease.Release) {
        launchWithProgress {
            _state.update { it.copy(latestRelease = null) }
            withContext(Dispatchers.IO) {
                downloadLatestRelease.execute(release)
            }
        }
    }

    override fun clearResult() {
        _state.update { it.copy(resultMessage = null) }
    }

    override fun clearLatestRelease() {
        _state.update { it.copy(latestRelease = null) }
    }

    override fun clearUpdateSourceResult() {
        _state.update { it.copy(updateSourceResult = null) }
    }

    private fun updateResultMessage(title: String, message: String) {
        _state.update {
            it.copy(resultMessage = title to message)
        }
    }
}
