package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.R
import com.door43.translationstudio.core.ContainerCache
import com.door43.translationstudio.core.DownloadImages
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.ProgressManager
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TaskHandle
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.launchWithProgress
import com.door43.usecases.CreateRepository
import com.door43.usecases.ExportProjects
import com.door43.usecases.GogsLogout
import com.door43.usecases.PullTargetTranslation
import com.door43.usecases.PushTargetTranslation
import com.door43.usecases.RegisterSSHKeys
import com.door43.util.FileUtilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.eclipse.jgit.merge.MergeStrategy
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.resourcecontainer.Project
import org.unfoldingword.tools.logger.Logger
import java.io.File
import java.util.Locale

data class ExportMessage(
    val title: String,
    val message: String
)

data class ExportState(
    val test: Boolean = false,
    val exportMessage: ExportMessage? = null,
    val projectTitle: String = ""
)

sealed interface ExportEvent {
    data class SnackBarMessage(val message: String) : ExportEvent
    data class AppExport(val file: File) : ExportEvent
}

sealed interface ExportAction {
    object ClearExport : ExportAction
    data class PrintPdf(
        val includeImages: Boolean,
        val includeIncomplete: Boolean,
        val uri: Uri
    ) : ExportAction
    data class ExportUsfm(val uri: Uri) : ExportAction
    data class ExportProject(val uri: Uri) : ExportAction
    object ExportToApp : ExportAction
}

class ExportViewModel(
    private val export: ExportProjects,
    private val downloadImages: DownloadImages,
    private val translator: Translator,
    private val profile: Profile,
    private val directoryProvider: IDirectoryProvider,
    private val prefRepository: IPreferenceRepository,
    private val library: Door43Client,
    private val gogsLogout: GogsLogout,
    private val createRepository: CreateRepository,
    private val pullTargetTranslation: PullTargetTranslation,
    private val pushTargetTranslation: PushTargetTranslation,
    private val registerSSHKeys: RegisterSSHKeys,
    val targetTranslation: TargetTranslation
) : ViewModel(), KoinComponent, ProgressOwner {

    private val application: Application by inject()

    private val progressManager = ProgressManager(viewModelScope)
    override val progress get() = progressManager.progress

    private val _state = MutableStateFlow(ExportState())
    val state: StateFlow<ExportState> = _state.asStateFlow()

    private val _event = Channel<ExportEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    init {
        var title = targetTranslation.projectTranslation.title
            .replace("\n+$".toRegex(), "")
        if (title.isEmpty()) {
            targetTranslation.resourceSlug?.let { resourceSlug ->
                val sourceContainer = ContainerCache.cacheClosest(
                    library,
                    null,
                    targetTranslation.projectId,
                    resourceSlug
                )
                if (sourceContainer != null) {
                    title = sourceContainer.readChunk("front", "title")
                        .replace("\n+$".toRegex(), "")
                }
            }
        }
        if (title.isEmpty()) {
            title = targetTranslation.projectId
        }
        title = "$title - ${targetTranslation.targetLanguageName}"

        _state.update { it.copy(projectTitle = title) }
    }

    private val _pullTranslationResult = MutableLiveData<PullTargetTranslation.Result?>(null)
    val pullTranslationResult: LiveData<PullTargetTranslation.Result?> = _pullTranslationResult

    private val _pushTranslationResult = MutableLiveData<PushTargetTranslation.Result?>(null)
    val pushTranslationResult: LiveData<PushTargetTranslation.Result?> = _pushTranslationResult

    private val _registeredSSHKeys = MutableLiveData<Boolean?>()
    val registeredSSHKeys: LiveData<Boolean?> = _registeredSSHKeys

    private val _repoCreated = MutableLiveData<Boolean>()
    val repoCreated: LiveData<Boolean> = _repoCreated

    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        progressManager.runTask(message, block)
    }

    fun onAction(action: ExportAction) {
        when (action) {
            is ExportAction.PrintPdf -> exportPDF(
                action.uri,
                action.includeImages,
                action.includeIncomplete
            )
            is ExportAction.ExportUsfm -> exportUSFM(action.uri)
            is ExportAction.ExportProject -> exportProject(action.uri)
            ExportAction.ExportToApp -> exportToApp()
            ExportAction.ClearExport -> clearExport()
        }
    }

    private fun exportProject(uri: Uri) {
        if (!validateUriExtension(uri, Translator.TSTUDIO_EXTENSION)) {
            reportExportFailed()
            return
        }

        launchWithProgress(
            application.getString(R.string.exporting)
        ) {
            val result = withContext(Dispatchers.IO) {
                export.exportProject(targetTranslation, uri)
            }

            val title = application.getString(R.string.backup_to_sd)
            val message = if (result.success) {
                val format = application.getString(R.string.export_success)
                String.format(
                    format,
                    FileUtilities.getUriDisplayName(application, result.uri)
                )
            } else {
                application.getString(R.string.export_failed)
            }

            _state.update {
                it.copy(exportMessage = ExportMessage(title, message))
            }
        }
    }

    private fun exportUSFM(uri: Uri) {
        if (!validateUriExtension(uri, Translator.USFM_EXTENSION)) {
            reportExportFailed()
            return
        }

        launchWithProgress(
            application.getString(R.string.exporting)
        ) {
            val result = withContext(Dispatchers.IO) {
                export.exportUSFM(targetTranslation, uri)
            }

            val title = application.getString(R.string.title_export_usfm)
            val message = if (result.success) {
                val format = application.getString(R.string.export_success)
                String.format(
                    format,
                    FileUtilities.getUriDisplayName(application, result.uri)
                )
            } else {
                application.getString(R.string.export_failed)
            }

            _state.update {
                it.copy(exportMessage = ExportMessage(title, message))
            }
        }
    }

    private fun exportPDF(
        uri: Uri,
        includeImages: Boolean,
        includeIncompleteFrames: Boolean
    ) {
        if (!validateUriExtension(uri, Translator.PDF_EXTENSION)) {
            reportExportFailed()
            return
        }

        launchWithProgress(
            application.getString(R.string.printing)
        ) { handle ->
            val imagesDir = if (includeImages) {
                handle.update(
                    value = 1f,
                    message = application.getString(R.string.downloading_images)
                )
                withContext(Dispatchers.IO) {
                    downloadImages.download { progress, message ->
                        handle.update(progress, message)
                    }
                }
            } else null

            if (includeImages && imagesDir?.exists() == false) {
                val title = application.getString(R.string.download_failed)
                val message = application.getString(R.string.downloading_images_for_print_failed)
                _state.update {
                    it.copy(exportMessage = ExportMessage(title, message))
                }
                return@launchWithProgress
            }

            val result = withContext(Dispatchers.IO) {
                export.exportPDF(
                    targetTranslation,
                    uri,
                    includeImages,
                    includeIncompleteFrames,
                    imagesDir
                )
            }

            val (title, message) = if (result.success) {
                val title = application.getString(R.string.success)
                val message = application.getString(
                    R.string.print_success,
                    FileUtilities.getUriDisplayName(application, result.uri)
                )
                title to message
            } else {
                val title = application.getString(R.string.error)
                val message = application.getString(R.string.print_failed)
                title to message
            }

            _state.update {
                it.copy(exportMessage = ExportMessage(title, message))
            }
        }
    }

    fun pullTargetTranslation(strategy: MergeStrategy) {
        launchWithProgress(
            application.getString(R.string.uploading)
        ) { handle ->
            _pullTranslationResult.value = withContext(Dispatchers.IO) {
                pullTargetTranslation.execute(
                    targetTranslation,
                    strategy,
                    null
                ) { progress, message ->
                    handle.update(progress, message)
                }
            }
        }
    }

    fun pushTargetTranslation() {
        launchWithProgress(
            application.getString(R.string.uploading)
        ) { handle ->
            _pullTranslationResult.value = null
            _pushTranslationResult.value = withContext(Dispatchers.IO) {
                pushTargetTranslation.execute(targetTranslation) { progress, message ->
                    handle.update(progress, message)
                }
            }
        }
    }

    fun registerSSHKeys(force: Boolean) {
        launchWithProgress(
            application.getString(R.string.registering_keys)
        ) { handle ->
            _registeredSSHKeys.value = withContext(Dispatchers.IO) {
                registerSSHKeys.execute(force) { progress, message ->
                    handle.update(progress, message)
                }
            }
        }
    }

    fun createRepository() {
        launchWithProgress(
            application.getString(R.string.creating_repository)
        ) { handle ->
            _repoCreated.value =
                createRepository.execute(targetTranslation) { progress, message ->
                    handle.update(progress, message)
                }
        }
    }

    /**
     * Log out the current gogs user
     */
    fun logout() {
        launchWithProgress(
            application.getString(R.string.log_out)
        ) {
            withContext(Dispatchers.IO) {
                gogsLogout.execute()
            }
        }
    }

    private fun exportToApp() {
        launchWithProgress(
            application.getString(R.string.exporting)
        ) {
            val exportFile = withContext(Dispatchers.IO) {
                try {
                    val filename = "${targetTranslation.id}.${Translator.TSTUDIO_EXTENSION}"
                    val exportFile = File(directoryProvider.sharingDir, filename)
                    export.exportProject(targetTranslation, exportFile)
                    exportFile
                } catch (e: Exception) {
                    Logger.e(
                        this@ExportViewModel::class.simpleName,
                        "Failed to export the target translation " + targetTranslation.id,
                        e
                    )
                    null
                }
            }

            if (exportFile?.exists() == true) {
                _event.trySend(ExportEvent.AppExport(exportFile))
            } else {
                _event.trySend(ExportEvent.SnackBarMessage(
                    application.getString(R.string.translation_export_failed)
                ))
            }
        }
    }

    private fun getProject(targetTranslationId: String): Project? {
        return library.index.getProject(
            Locale.getDefault().language,
            targetTranslationId
        )
    }

    fun getProject(targetTranslation: TargetTranslation): Project? {
        return library.index.getProject(
            "en",
            targetTranslation.projectId,
            true
        )
    }

    fun clearResults() {
        _pushTranslationResult.value = null
        _pullTranslationResult.value = null
    }

    private fun clearExport() {
        _state.update { it.copy(exportMessage = null) }
    }

    private fun validateUriExtension(uri: Uri, extension: String): Boolean {
        val filename = FileUtilities.getUriDisplayName(application, uri)
        val filenameRegex = Regex(".*\\.$extension(\\s\\(\\d+\\))?$")
        return filename.matches(filenameRegex)
    }

    private fun reportExportFailed() {
        val title = application.getString(R.string.error)
        val message = application.getString(R.string.export_failed)
        _state.update {
            it.copy(exportMessage = ExportMessage(title, message))
        }
    }
}