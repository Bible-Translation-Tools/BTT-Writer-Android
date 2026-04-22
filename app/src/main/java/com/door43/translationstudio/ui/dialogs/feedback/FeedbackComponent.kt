package com.door43.translationstudio.ui.dialogs.feedback

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
import com.door43.usecases.UploadFeedback
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

interface FeedbackComponent {
    val state: StateFlow<FeedbackState>
    val progress: StateFlow<Progress?>
    val event: Flow<FeedbackEvent>

    val initialMessage: String

    fun reportBug(message: String)
    fun uploadFeedback(message: String)
    fun downloadLatestRelease(release: CheckForLatestRelease.Release)
    fun clearError()
    fun clearRelease()

    data class FeedbackState(
        val message: String = "",
        val release: CheckForLatestRelease.Release? = null,
        val uploadError: String? = null,
        val success: Boolean = false
    )

    sealed interface FeedbackEvent {
        data class SnackbarMessage(val message: String) : FeedbackEvent
    }
}

class DefaultFeedbackComponent(
    componentContext: ComponentContext,
    override val initialMessage: String = ""
) : FeedbackComponent,
    ComponentContext by componentContext,
    ComponentScope, ProgressOwner, KoinComponent {

    private val application: Application by inject()
    private val checkForLatestRelease: CheckForLatestRelease by inject()
    private val downloadLatestRelease: DownloadLatestRelease by inject()
    private val uploadFeedback: UploadFeedback by inject()

    override val coroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val progressManager = ProgressManager(coroutineScope)
    override val progress get() = progressManager.progress

    private val _state = MutableStateFlow(FeedbackComponent.FeedbackState())
    override val state = _state.asStateFlow()

    private val _event = Channel<FeedbackComponent.FeedbackEvent>(Channel.BUFFERED)
    override val event = _event.receiveAsFlow()

    init {
        lifecycle.doOnDestroy {
            coroutineScope.cancel()
        }
    }

    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        progressManager.runTask(message, block)
    }

    override fun reportBug(message: String) {
        if (message.isEmpty()) {
            val msg = application.getString(R.string.input_required)
            _event.trySend(FeedbackComponent.FeedbackEvent.SnackbarMessage(msg))
            return
        }

        _state.update { it.copy(message = message) }
        launchWithProgress { handle ->
            checkForLatestRelease(handle)
        }
    }

    override fun uploadFeedback(message: String) {
        if (message.isEmpty()) {
            val msg = application.getString(R.string.input_required)
            _event.trySend(FeedbackComponent.FeedbackEvent.SnackbarMessage(msg))
            return
        }

        launchWithProgress { handle ->
            doUploadFeedback(message, handle)
        }
    }

    override fun downloadLatestRelease(release: CheckForLatestRelease.Release) {
        launchWithProgress {
            _state.update { it.copy(release = null) }
            withContext(Dispatchers.IO) {
                downloadLatestRelease.execute(release)
            }
        }
    }

    override fun clearError() {
        _state.update { it.copy(uploadError = null) }
    }

    override fun clearRelease() {
        _state.update { it.copy(release = null) }
    }

    private suspend fun checkForLatestRelease(handle: TaskHandle) {
        handle.update(-1f, application.getString(R.string.checking_for_updates))
        val result = withContext(Dispatchers.IO) {
            checkForLatestRelease.execute()
        }
        if (result.release != null) {
            _state.update { it.copy(release = result.release) }
        } else {
            doUploadFeedback(_state.value.message, handle)
        }
    }

    private suspend fun doUploadFeedback(message: String, handle: TaskHandle) {
        handle.update(-1f, application.getString(R.string.uploading_feedback))
        val success = withContext(Dispatchers.IO) {
            uploadFeedback.execute(message)
        }
        if (success) {
            _state.update { it.copy(success = true) }
        } else {
            val msg = if (App.isNetworkAvailable) {
                application.getString(R.string.upload_feedback_failed)
            } else {
                application.getString(R.string.internet_not_available)
            }
            _state.update { it.copy(uploadError = msg) }
        }
    }
}


