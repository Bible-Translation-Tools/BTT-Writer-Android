package com.door43.translationstudio.ui.crash

import android.app.Application
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import com.door43.translationstudio.core.ComponentScope
import com.door43.translationstudio.core.Progress
import com.door43.translationstudio.core.ProgressManager
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.TaskHandle
import com.door43.translationstudio.core.launchWithProgress
import com.door43.usecases.CheckForLatestRelease
import com.door43.usecases.DownloadLatestRelease
import com.door43.usecases.UploadCrashReport
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
import org.bibletranslationtools.logger.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

interface CrashComponent {

    val state: StateFlow<CrashState>
    val progress: StateFlow<Progress?>
    val event: Flow<Event>

    val isNetworkAvailable: Boolean

    fun checkForLatestRelease()
    fun uploadCrashReport()
    fun updateNotes(notes: String)
    fun downloadLatestRelease()
    fun flushAndRestart()

    data class CrashState(
        val notes: String = "",
        val success: Boolean = false
    )

    sealed interface Event {
        data object UploadError : Event
        data object UpdateAvailable : Event
    }

    sealed interface Result {
        data object Restart : Result
        data object Exit : Result
    }
}

class DefaultCrashComponent(
    componentContext: ComponentContext,
    private val onResult: (CrashComponent.Result) -> Unit
) : CrashComponent,
        ComponentContext by componentContext,
        KoinComponent, ComponentScope, ProgressOwner {

    private val application: Application by inject()
    private val checkForLatestRelease: CheckForLatestRelease by inject()
    private val downloadLatestRelease: DownloadLatestRelease by inject()
    private val uploadCrashReport: UploadCrashReport by inject()

    override val coroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val progressManager = ProgressManager(coroutineScope)
    override val progress get() = progressManager.progress

    private val _state = MutableStateFlow(CrashComponent.CrashState())
    override val state: StateFlow<CrashComponent.CrashState> = _state.asStateFlow()

    private val _event = Channel<CrashComponent.Event>(Channel.BUFFERED)
    override val event = _event.receiveAsFlow()

    private var release: CheckForLatestRelease.Release? = null
    override val isNetworkAvailable: Boolean get() = App.isNetworkAvailable

    init {
        lifecycle.doOnDestroy {
            coroutineScope.cancel()
        }
    }

    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        progressManager.runTask(message, block)
    }

    override fun checkForLatestRelease() {
        launchWithProgress(
            application.resources.getString(R.string.checking_for_updates)
        ) {
            val result = withContext(Dispatchers.IO) {
                checkForLatestRelease.execute()
            }
            if (result.release != null) {
                release = result.release
                _event.trySend(CrashComponent.Event.UpdateAvailable)
            } else {
                uploadCrashReport()
            }
        }
    }

    override fun uploadCrashReport() {
        val notes = state.value.notes.ifBlank { return }

        launchWithProgress(
            application.resources.getString(R.string.uploading)
        ) {
            val uploaded = withContext(Dispatchers.IO) {
                uploadCrashReport.execute(notes)
            }
            if (uploaded) {
                _state.update { it.copy(success = true) }
            } else {
                _event.trySend(CrashComponent.Event.UploadError)
            }
        }
    }

    override fun updateNotes(notes: String) {
        _state.update { it.copy(notes = notes) }
    }

    override fun downloadLatestRelease() {
        release?.let { release ->
            Logger.flush()
            launchWithProgress(
                application.resources.getString(R.string.downloading)
            ) {
                withContext(Dispatchers.IO) {
                    downloadLatestRelease.execute(release)
                }
                onResult(CrashComponent.Result.Exit)
            }
        }
    }

    override fun flushAndRestart() {
        Logger.flush()
        onResult(CrashComponent.Result.Restart)
    }
}