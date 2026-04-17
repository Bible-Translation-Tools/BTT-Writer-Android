package com.door43.translationstudio.ui.dialogs

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.App
import com.door43.translationstudio.App.Companion.deviceLanguageCode
import com.door43.translationstudio.R
import com.door43.translationstudio.core.DownloadImages
import com.door43.translationstudio.core.MergeConflictsHandler
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.Progress
import com.door43.translationstudio.core.ProgressManager
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TaskHandle
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.launchWithProgress
import com.door43.translationstudio.ui.navigation.ComponentScope
import com.door43.usecases.CreateRepository
import com.door43.usecases.ExportProjects
import com.door43.usecases.GogsLogout
import com.door43.usecases.PullTargetTranslation
import com.door43.usecases.PushTargetTranslation
import com.door43.usecases.RegisterSSHKeys
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
import org.eclipse.jgit.merge.MergeStrategy
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.resourcecontainer.Project
import org.unfoldingword.tools.logger.Logger
import java.io.File

data class DialogMessage(
    val title: String,
    val message: String
)

data class UploadSuccess(
    val url: String,
    val details: String? = null
)

interface ExportComponent {

    val state: StateFlow<State>
    val event: Flow<Event>
    val progress: StateFlow<Progress?>

    val targetTranslation: TargetTranslation
    val projectName: String
    val projectTitle: String
    val startFromPrint: Boolean

    fun onMergeConflict()
    fun onAction(action: Action)

    data class State(
        val info: DialogMessage? = null,
        val uploadError: DialogMessage? = null,
        val mergeConflict: DialogMessage? = null,
        val uploadSuccess: UploadSuccess? = null
    )

    sealed interface Event {
        data class SnackbarMessage(val message: String) : Event
        data object AuthRequested : Event
    }

    sealed interface Action {
        data class PrintPdf(
            val includeImages: Boolean,
            val includeIncomplete: Boolean,
            val uri: Uri
        ) : Action
        data class ExportUsfm(val uri: Uri) : Action
        data class ExportProject(val uri: Uri) : Action
        data object ClearExport : Action
        data object ExportToApp : Action
        data object ExportToCloud : Action
        data class Logout(val thenLogin: Boolean) : Action
        data object RegisterKeys : Action
        data object ClearInfoMessage : Action
        data object ClearErrorMessage : Action
        data object ClearUploadSuccess : Action
        data object ResetToMaster : Action
        data object ClearMergeConflict : Action
    }

    sealed interface Result {
        data class Error(val text: String) : Result
        data class ExportToApp(val file: File) : Result
        data object OpenLogin : Result
        data object Logout : Result
        data class MergeConflict(val translationId: String) : Result
    }
}

class DefaultExportComponent(
    componentContext: ComponentContext,
    translationId: String,
    override val startFromPrint: Boolean,
    private val onResult: (ExportComponent.Result) -> Unit
) : ExportComponent,
    ComponentContext by componentContext,
    KoinComponent, ComponentScope, ProgressOwner {

    private val application: Application by inject()
    private val export: ExportProjects by inject()
    private val downloadImages: DownloadImages by inject()
    private val translator: Translator by inject()
    private val profile: Profile by inject()
    private val directoryProvider: IDirectoryProvider by inject()
    private val library: Door43Client by inject()
    private val gogsLogout: GogsLogout by inject()
    private val createRepository: CreateRepository by inject()
    private val pullTargetTranslation: PullTargetTranslation by inject()
    private val pushTargetTranslation: PushTargetTranslation by inject()
    private val registerSSHKeys: RegisterSSHKeys by inject()
    private val prefRepository: IPreferenceRepository by inject()

    override val coroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val progressManager = ProgressManager(coroutineScope)
    override val progress get() = progressManager.progress

    private val _state = MutableStateFlow(ExportComponent.State())
    override val state: StateFlow<ExportComponent.State> = _state.asStateFlow()

    private val _event = Channel<ExportComponent.Event>(Channel.BUFFERED)
    override val event = _event.receiveAsFlow()

    override lateinit var targetTranslation: TargetTranslation
    override lateinit var projectName: String
    override lateinit var projectTitle: String

    init {
        translator.getTargetTranslation(translationId)?.let { translation ->
            targetTranslation = translation
            projectName = getProject()?.name ?: targetTranslation.projectId
            projectTitle = "$projectName - ${targetTranslation.targetLanguageName}"
        } ?: run {
            val error = application.getString(R.string.target_translation_not_found)
            onResult(ExportComponent.Result.Error(error))
        }

        lifecycle.doOnDestroy {
            coroutineScope.cancel()
        }
    }

    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        progressManager.runTask(message, block)
    }

    override fun onMergeConflict() {
        onResult(ExportComponent.Result.MergeConflict(targetTranslation.id))
    }

    override fun onAction(action: ExportComponent.Action) {
        when (action) {
            is ExportComponent.Action.PrintPdf -> exportPDF(
                action.uri,
                action.includeImages,
                action.includeIncomplete
            )
            is ExportComponent.Action.ExportUsfm -> exportUSFM(action.uri)
            is ExportComponent.Action.ExportProject -> exportProject(action.uri)
            is ExportComponent.Action.ExportToApp -> exportToApp()
            is ExportComponent.Action.ExportToCloud -> exportToCloud()
            is ExportComponent.Action.ClearExport -> clearInfo()
            is ExportComponent.Action.Logout -> logout(action.thenLogin)
            is ExportComponent.Action.RegisterKeys -> forceRegisterSSHKeys()
            is ExportComponent.Action.ClearInfoMessage -> clearInfo()
            is ExportComponent.Action.ClearUploadSuccess -> clearUploadSuccess()
            is ExportComponent.Action.ResetToMaster -> resetToMaster()
            is ExportComponent.Action.ClearMergeConflict -> clearMergeConflict()
            is ExportComponent.Action.ClearErrorMessage -> clearError()
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
                application.getString(
                    R.string.export_success,
                    FileUtilities.getFileName(application, result.uri)
                )
            } else {
                application.getString(R.string.export_failed)
            }

            _state.update {
                it.copy(info = DialogMessage(title, message))
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
                application.getString(
                    R.string.export_success,
                    FileUtilities.getFileName(application, result.uri)
                )
            } else {
                application.getString(R.string.export_failed)
            }

            _state.update {
                it.copy(info = DialogMessage(title, message))
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
                    it.copy(info = DialogMessage(title, message))
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
                    FileUtilities.getFileName(application, result.uri)
                )
                title to message
            } else {
                val title = application.getString(R.string.error)
                val message = application.getString(R.string.print_failed)
                title to message
            }

            _state.update {
                it.copy(info = DialogMessage(title, message))
            }
        }
    }

    private fun exportToCloud() {
        launchWithProgress { handle ->
            pullTargetTranslation(MergeStrategy.RECURSIVE, handle)
        }
    }

    private suspend fun pullTargetTranslation(strategy: MergeStrategy, handle: TaskHandle) {
        handle.update(
            value = -1f,
            message = application.getString(R.string.pulling_repo)
        )
        val result = withContext(Dispatchers.IO) {
            pullTargetTranslation.execute(
                targetTranslation,
                strategy,
                null
            ) { progress, message ->
                handle.update(progress, message)
            }
        }

        when (result.status) {
            PullTargetTranslation.Status.UP_TO_DATE,
            PullTargetTranslation.Status.UNKNOWN -> {
                Logger.i(
                    this.javaClass.name,
                    "Changes on the server were synced with " + targetTranslation.id
                )
                pushTargetTranslation(handle)
            }
            PullTargetTranslation.Status.AUTH_FAILURE -> {
                Logger.i(this.javaClass.name, "Authentication failed")
                if (!directoryProvider.hasSSHKeys()) {
                    registerSSHKeys(false, handle)
                } else {
                    _event.trySend(ExportComponent.Event.AuthRequested)
                }
            }
            PullTargetTranslation.Status.NO_REMOTE_REPO -> {
                Logger.i(
                    this.javaClass.name,
                    "The repository " + targetTranslation.id + " could not be found"
                )
                createRepository(handle)
            }
            PullTargetTranslation.Status.MERGE_CONFLICTS -> {
                Logger.i(
                    this.javaClass.name,
                    "The server contains conflicting changes for " + targetTranslation.id
                )
                val conflicted = MergeConflictsHandler.isTranslationMergeConflicted(
                    targetTranslation.id,
                    translator
                )
                if (!conflicted) {
                    // probably the manifest or license gave a false positive
                    Logger.i(
                        this.javaClass.name,
                        "Changes on the server were synced with " + targetTranslation.id
                    )
                    pushTargetTranslation(handle)
                } else {
                    val title = application.getString(R.string.change_detected)
                    val message = application.getString(
                        R.string.merge_request,
                        targetTranslation.projectId,
                        targetTranslation.targetLanguageName
                    )
                    _state.update {
                        it.copy(mergeConflict = DialogMessage(title, message))
                    }
                }
            }
            else -> {
                reportUploadFailed()
            }
        }
    }

    private suspend fun pushTargetTranslation(handle: TaskHandle) {
        handle.update(
            value = -1f,
            message = application.getString(R.string.uploading)
        )
        val result = withContext(Dispatchers.IO) {
            pushTargetTranslation.execute(targetTranslation) { progress, message ->
                handle.update(progress, message)
            }
        }
        when {
            result.status == PushTargetTranslation.Status.OK -> {
                Logger.i(
                    this.javaClass.name,
                    "The target translation " + targetTranslation.id + " was pushed to the server"
                )
                reportUploadSuccess(result.message)
            }
            result.status == PushTargetTranslation.Status.AUTH_FAILURE -> {
                Logger.i(this.javaClass.name, "Authentication failed")
                _event.trySend(ExportComponent.Event.AuthRequested)
            }
            result.status.isRejected -> {
                Logger.i(this.javaClass.name, "Push Rejected")
                val title = application.getString(R.string.upload_failed)
                val message = application.getString(R.string.push_rejected)
                _state.update {
                    it.copy(mergeConflict = DialogMessage(title, message))
                }
            }
            else -> {
                reportUploadFailed()
            }
        }
    }

    private fun forceRegisterSSHKeys() {
        launchWithProgress { handle ->
            registerSSHKeys(true, handle)
        }
    }

    private suspend fun registerSSHKeys(force: Boolean, handle: TaskHandle) {
        handle.update(
            value = -1f,
            message = application.getString(R.string.registering_keys)
        )
        val registered = withContext(Dispatchers.IO) {
            registerSSHKeys.execute(force) { progress, message ->
                handle.update(progress, message)
            }
        }
        if (registered) {
            Logger.i(this.javaClass.name, "SSH keys were registered with the server")
            pullTargetTranslation(MergeStrategy.RECURSIVE, handle)
        } else {
            _event.trySend(ExportComponent.Event.AuthRequested)
        }
    }

    private suspend fun createRepository(handle: TaskHandle) {
        handle.update(
            value = -1f,
            message = application.getString(R.string.creating_repository)
        )
        val created = createRepository.execute(targetTranslation) { progress, message ->
            handle.update(progress, message)
        }
        if (created) {
            Logger.i(
                this.javaClass.name,
                "A new repository " + targetTranslation.id + " was created on the server"
            )
            pullTargetTranslation(MergeStrategy.RECURSIVE, handle)
        } else {
            reportUploadFailed()
        }
    }

    private fun logout(thenLogin: Boolean) {
        launchWithProgress(
            application.getString(R.string.log_out)
        ) {
            withContext(Dispatchers.IO) {
                gogsLogout.execute()
                profile.logout()
            }

            if (thenLogin) {
                onResult(ExportComponent.Result.OpenLogin)
            } else {
                onResult(ExportComponent.Result.Logout)
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
                        this@DefaultExportComponent::class.simpleName,
                        "Failed to export the target translation " + targetTranslation.id,
                        e
                    )
                    null
                }
            }

            if (exportFile?.exists() == true) {
                onResult(ExportComponent.Result.ExportToApp(exportFile))
            } else {
                _event.trySend(
                    ExportComponent.Event.SnackbarMessage(
                    application.getString(R.string.translation_export_failed)
                ))
            }
        }
    }

    fun getProject(targetTranslation: TargetTranslation): Project? {
        return library.index.getProject(
            "en",
            targetTranslation.projectId,
            true
        )
    }

    private fun validateUriExtension(uri: Uri, extension: String): Boolean {
        val filename = FileUtilities.getFileName(application, uri)
        val filenameRegex = Regex(".*\\.$extension(\\s\\(\\d+\\))?$")
        return filename.matches(filenameRegex)
    }

    private fun reportExportFailed() {
        val title = application.getString(R.string.export_failed)
        val exportFailed = application.getString(R.string.export_failed)
        _state.update { it.copy(info = DialogMessage(title, exportFailed)) }
    }

    private fun reportUploadFailed() {
        val title = application.getString(R.string.export_failed)
        val noInternet = application.getString(R.string.internet_not_available)
        val exportFailed = application.getString(R.string.export_failed)

        if (!App.isNetworkAvailable) {
            _state.update { it.copy(info = DialogMessage(title, noInternet)) }
        } else {
            _state.update { it.copy(uploadError = DialogMessage(title, exportFailed)) }
        }
    }

    private fun reportUploadSuccess(details: String?) {
        val apiURL = prefRepository.getDefaultPref(
            IPreferenceRepository.KEY_PREF_READER_SERVER,
            application.getString(R.string.pref_default_reader_server)
        )
        val url = (apiURL + "/" + profile.gogsUser?.username + "/" + targetTranslation.id).toUri()
        val success = UploadSuccess(
            url = url.toString(),
            details = details
        )
        _state.update { it.copy(uploadSuccess = success) }
    }

    private fun resetToMaster() {
        launchWithProgress {
            withContext(Dispatchers.IO) {
                targetTranslation.resetToMasterBackup()
            }
        }
    }

    private fun getProject(): Project? {
        return library.index.getProject(
            deviceLanguageCode,
            targetTranslation.projectId,
            true
        )
    }

    private fun clearInfo() {
        _state.update { it.copy(info = null) }
    }

    private fun clearError() {
        _state.update { it.copy(uploadError = null) }
    }

    private fun clearUploadSuccess() {
        _state.update { it.copy(uploadSuccess = null) }
    }

    private fun clearMergeConflict() {
        _state.update { it.copy(mergeConflict = null) }
    }
}