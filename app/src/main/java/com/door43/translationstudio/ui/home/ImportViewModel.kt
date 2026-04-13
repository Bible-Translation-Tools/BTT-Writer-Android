package com.door43.translationstudio.ui.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.App.Companion.deviceLanguageCode
import com.door43.translationstudio.R
import com.door43.translationstudio.core.ProgressManager
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TargetTranslationMigrator
import com.door43.translationstudio.core.TaskHandle
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.launchWithProgress
import com.door43.usecases.AdvancedGogsRepoSearch
import com.door43.usecases.CloneRepository
import com.door43.usecases.ImportProjects
import com.door43.usecases.RegisterSSHKeys
import com.door43.util.FileUtilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.gogsclient.Repository
import org.unfoldingword.tools.logger.Logger
import java.io.File
import java.io.IOException
import java.security.InvalidParameterException

data class MergeConflict(
    val translation: TargetTranslation,
    val hasMergeConflict: Boolean,
    val isFromServer: Boolean,
    val onResolve: () -> Unit,
    val onOverwrite: () -> Unit,
    val onCancel: () -> Unit
)

data class ImportState(
    val mergeConflict: MergeConflict? = null,
    val sourceConflict: ImportProjects.ImportSourceResult? = null,
    val resultMessage: Pair<String, String>? = null,
    val backups: List<File> = emptyList(),
    val repositories: List<RepositoryItem> = emptyList(),
    val repoToImport: RepositoryItem? = null
)

sealed interface ImportAction {
    data class ImportProject(val uri: Uri, val overwrite: Boolean) : ImportAction
    data class ImportSourceUri(val uri: Uri, val overwrite: Boolean) : ImportAction
    data class ImportBackup(val backup: File) : ImportAction
    data class SearchRepositories(val user: String, val repo: String) : ImportAction
    data class ImportRepo(
        val repo: RepositoryItem,
        val accepted: Boolean,
        val overwrite: Boolean
    ) : ImportAction
    data object RegisterKeys : ImportAction
    data object ClearResult : ImportAction
    data object ClearMergeConflict : ImportAction
    data object ClearSourceConflict : ImportAction
    data object ClearImportRepo : ImportAction
}

sealed interface ImportEvent {
    data class ResolveMergeConflict(val translationId: String) : ImportEvent
    data class ProjectImported(val translationId: String) : ImportEvent
    data object AuthRequested : ImportEvent
}

class ImportViewModel(
    private val translator: Translator,
    private val advancedGogsRepoSearch: AdvancedGogsRepoSearch,
    private val cloneRepository: CloneRepository,
    private val registerSSHKeys: RegisterSSHKeys,
    private val importProjects: ImportProjects,
    private val library: Door43Client,
    private val directoryProvider: IDirectoryProvider,
    private val targetTranslationMigrator: TargetTranslationMigrator
) : ViewModel(), KoinComponent, ProgressOwner {

    private val application: Application by inject()

    private val progressManager = ProgressManager(viewModelScope)
    override val progress get() = progressManager.progress

    private val _state = MutableStateFlow(ImportState())
    val state: StateFlow<ImportState> = _state.asStateFlow()

    private val _event = Channel<ImportEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    init {
        viewModelScope.launch {
            val backups = getBackupTranslations().sorted()
            _state.update { it.copy(backups = backups) }
        }
    }

    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        progressManager.runTask(message, block)
    }

    fun onAction(action: ImportAction) {
        when (action) {
            is ImportAction.ImportProject -> importProject(action.uri, action.overwrite)
            is ImportAction.ImportSourceUri -> importSource(action.uri, action.overwrite)
            is ImportAction.ImportBackup -> importBackup(action.backup)
            is ImportAction.SearchRepositories -> searchRepositories(
                action.user,
                action.repo
            )
            is ImportAction.ImportRepo -> importRepository(
                action.repo,
                action.accepted,
                action.overwrite
            )
            is ImportAction.RegisterKeys -> forceRegisterSSHKeys()
            is ImportAction.ClearResult -> _state.update {
                it.copy(resultMessage = null, repositories = emptyList())
            }
            is ImportAction.ClearMergeConflict -> _state.update { it.copy(mergeConflict = null) }
            is ImportAction.ClearSourceConflict -> _state.update { it.copy(sourceConflict = null) }
            is ImportAction.ClearImportRepo -> _state.update { it.copy(repoToImport = null) }
        }
    }

    private fun importProject(
        uri: Uri,
        overwrite: Boolean,
        message: String = application.getString(R.string.import_source_text)
    ) {
        launchWithProgress(message) { handle ->
            val filename = FileUtilities.getFileName(application, uri)
            val isTstudio = filename.contains(Translator.TSTUDIO_EXTENSION, ignoreCase = true)
            val isZip = filename.contains(Translator.ZIP_EXTENSION, ignoreCase = true)
            if (isTstudio || isZip) {
                if (overwrite) resetToMaster()

                _state.update { it.copy(mergeConflict = null) }

                val result = withContext(Dispatchers.IO) {
                    importProjects.importProject(uri, overwrite) { progress, message ->
                        handle.update(progress, message)
                    }
                }
                when {
                    result.success && result.alreadyExists && !overwrite -> {
                        _state.update {
                            it.copy(mergeConflict = MergeConflict(
                                translation = getTargetTranslation(
                                    result.importedSlug!!
                                ),
                                hasMergeConflict = result.hasMergeConflict,
                                isFromServer = false,
                                onResolve = { resolveMergeConflict() },
                                onOverwrite = {
                                    launchWithProgress {
                                        importProject(result.filePath, true)
                                    }
                                },
                                onCancel = {
                                    viewModelScope.launch {
                                        resetToMaster()
                                    }
                                }
                            ))
                        }
                    }
                    result.success -> {
                        _event.trySend(ImportEvent.ProjectImported(
                            result.importedSlug!!
                        ))
                        updateResult(
                            application.getString(R.string.import_from_storage),
                            application.getString(R.string.import_success) +
                                    "\n${result.readablePath}"
                        )
                    }
                    result.invalidFileName -> {
                        updateResult(
                            application.getString(R.string.import_from_storage),
                            application.getString(R.string.invalid_file) +
                                    "\n${result.readablePath}"
                        )
                    }
                    else -> {
                        updateResult(
                            application.getString(R.string.import_from_storage),
                            application.getString(R.string.import_failed) +
                                    "\n${result.readablePath}"
                        )
                    }
                }
            } else {
                updateResult(
                    application.getString(R.string.import_from_storage),
                    "${application.getString(R.string.invalid_file)}\n$filename"
                )
            }
        }
    }

    private fun importSource(uri: Uri, overwrite: Boolean) {
        launchWithProgress(
            application.getString(R.string.import_source_text)
        ) {
            val result = withContext(Dispatchers.IO) {
                importProjects.importSource(uri, overwrite)
            }

            when {
                result.success -> {
                    val dirName = FileUtilities.getDirectoryName(application, uri)
                    updateResult(
                        application.getString(R.string.success),
                        application.getString(R.string.import_success) + " $dirName"
                    )
                }
                result.hasConflict -> {
                    _state.update { it.copy(sourceConflict = result) }
                }
                else -> {
                    updateResult(
                        application.getString(R.string.could_not_import),
                        result.error ?: "Unknown error"
                    )
                }
            }
        }
    }

    private fun importBackup(backup: File) {
        val uri = Uri.fromFile(backup)
        val message = application.resources.getString(
            R.string.importing_file,
            backup.name
        )
        importProject(uri, false, message)
    }

    private fun searchRepositories(user: String, repo: String) {
        launchWithProgress(
            application.getString(R.string.searching_repositories)
        ) { handle ->
            val result = withContext(Dispatchers.IO) {
                advancedGogsRepoSearch.execute(user, repo, 50) { progress, message ->
                    handle.update(progress, message)
                }
            }
            _state.update { it.copy(repositories = result.map(::mapRepository)) }
        }
    }

    private fun mapRepository(repository: Repository): RepositoryItem {
        val repoName = repository.fullName.split("/".toRegex())
        var projectName = ""
        var languageName = ""
        var code = "en" // default font language if language is not found
        var direction = "ltr" // default font language direction if language is not found
        var unsupportedTag = ""
        var targetTranslationSlug = ""

        if (repoName.isNotEmpty()) {
            targetTranslationSlug = repoName[repoName.size - 1]
            try {
                val projectSlug = TargetTranslation.getProjectSlugFromId(targetTranslationSlug)
                val targetLanguageSlug = TargetTranslation.getTargetLanguageSlugFromId(
                    targetTranslationSlug
                )
                val resourceTypeSlug = TargetTranslation.getResourceTypeFromId(
                    targetTranslationSlug
                )
                if (resourceTypeSlug != "text") { // we only support text
                    unsupportedTag = when (resourceTypeSlug) {
                        "tw" -> application.getString(R.string.translation_words)
                        "tn" -> application.getString(R.string.label_translation_notes)
                        "tq" -> application.getString(R.string.translation_questions)
                        else -> application.getString(R.string.unsupported)
                    }
                }

                val project = library.index.getProject(
                    sourceLanguageSlug = deviceLanguageCode,
                    projectSlug = projectSlug,
                    enableDefaultLanguage = true
                )
                projectName = if (project != null) {
                    project.name
                } else {
                    targetTranslationSlug
                }
                val targetLanguage = library.index.getTargetLanguage(targetLanguageSlug)
                if (targetLanguage != null) {
                    languageName = targetLanguage.name
                    direction = targetLanguage.direction
                    code = targetLanguage.slug
                } else {
                    languageName = targetLanguageSlug
                }
            } catch (e: StringIndexOutOfBoundsException) {
                e.printStackTrace()
                projectName = targetTranslationSlug
                unsupportedTag = application.getString(R.string.unsupported)
            }
        }

        return RepositoryItem(
            languageName,
            projectName,
            targetTranslationSlug,
            code,
            direction,
            repository.fullName,
            repository.htmlUrl,
            repository.isPrivate,
            unsupportedTag
        )
    }

    private fun importRepository(repo: RepositoryItem, accepted: Boolean, overwrite: Boolean) {
        launchWithProgress(
            application.getString(R.string.cloning_repository)
        ) { handle ->
            if (repo.isSupported || accepted) {
                cloneRepository(repo, overwrite, handle)
            } else {
                _state.update { it.copy(repoToImport = repo) }
            }
        }
    }

    private suspend fun cloneRepository(
        repo: RepositoryItem,
        overwrite: Boolean,
        handle: TaskHandle
    ) {
        if (overwrite) resetToMaster()

        _state.update { it.copy(mergeConflict = null, repoToImport = null) }

        val result = withContext(Dispatchers.IO) {
            cloneRepository.execute(repo.url) { progress, message ->
                handle.update(progress, message)
            }
        }

        when (result.status) {
            CloneRepository.Status.SUCCESS -> {
                result.cloneDir?.let {
                    val clonedDir = targetTranslationMigrator.migrate(it)

                    Logger.i(this.javaClass.name, "Repository cloned from $it")

                    clonedDir?.let { tempRepoDir ->
                        TargetTranslation.open(tempRepoDir)?.let { tempTargetTranslation ->
                            val existingTargetTranslation = translator.getTargetTranslation(
                                tempTargetTranslation.id
                            )

                            if (existingTargetTranslation != null && !overwrite) {
                                try {
                                    val mergedWithoutConflicts = existingTargetTranslation.merge(
                                        tempRepoDir,
                                        null
                                    )
                                    _state.update { state ->
                                        state.copy(
                                            mergeConflict = MergeConflict(
                                                translation = existingTargetTranslation,
                                                hasMergeConflict = !mergedWithoutConflicts,
                                                isFromServer = true,
                                                onResolve = { resolveMergeConflict() },
                                                onOverwrite = {
                                                    launchWithProgress { handle ->
                                                        cloneRepository(
                                                            repo = repo,
                                                            overwrite = true,
                                                            handle = handle
                                                        )
                                                    }
                                                },
                                                onCancel = {
                                                    viewModelScope.launch {
                                                        resetToMaster()
                                                    }
                                                }
                                            )
                                        )
                                    }
                                } catch (e: Exception) {
                                    Logger.e(
                                        this.javaClass.name,
                                        "Failed to merge the target translation",
                                        e
                                    )
                                    reportImportFailed()
                                }
                            } else {
                                // restore the new target translation
                                try {
                                    translator.restoreTargetTranslation(tempTargetTranslation)
                                    _event.trySend(ImportEvent.ProjectImported(
                                        tempTargetTranslation.id
                                    ))
                                    updateResult(
                                        title = application.getString(R.string.import_from_door43),
                                        message = application.getString(R.string.title_import_success)
                                    )
                                } catch (e: IOException) {
                                    Logger.e(
                                        this.javaClass.name,
                                        "Failed to import the target translation " + tempTargetTranslation.id,
                                        e
                                    )
                                    reportImportFailed()
                                }
                            }
                        } ?: run {
                            Logger.e(this.javaClass.name, "Failed to open the online backup")
                            reportImportFailed()
                        }
                    }
                }
            }
            CloneRepository.Status.AUTH_FAILURE -> {
                Logger.i(this.javaClass.name, "Authentication failed")
                if (!directoryProvider.hasSSHKeys()) {
                    registerSSHKeys(false, handle) {
                        cloneRepository(repo, overwrite, handle)
                    }
                } else {
                    _state.update { it.copy(repoToImport = repo) }
                    _event.trySend(ImportEvent.AuthRequested)
                }
            }
            else -> {
                updateResult(
                    title = application.getString(R.string.error),
                    message = application.getString(R.string.restore_failed)
                )
            }
        }
    }

    private suspend fun resetToMaster() {
        val mergeConflict = _state.value.mergeConflict ?: return
        withContext(Dispatchers.IO) {
            mergeConflict.translation.resetToMasterBackup()
        }
    }

    private fun resolveMergeConflict() {
        val result = _state.value.mergeConflict ?: return
        if (result.hasMergeConflict) {
            _event.trySend(
                ImportEvent.ResolveMergeConflict(result.translation.id)
            )
        } else {
            val title = if (result.isFromServer) {
                application.getString(R.string.import_from_door43)
            } else application.getString(R.string.import_from_storage)
            updateResult(
                title,
                application.getString(R.string.import_success) +
                        "\n${result.translation.id}"
            )
        }
    }

    private fun updateResult(title: String, message: String) {
        _state.update { it.copy(resultMessage = title to message) }
    }

    private suspend fun getBackupTranslations(): List<File> {
        return withContext(Dispatchers.IO) {
            directoryProvider
                .backupsDir
                .listFiles()
                ?.asList()
                ?.filter { it.length() > 0 }
                ?: listOf()
        }
    }

    private fun forceRegisterSSHKeys() {
        launchWithProgress { handle ->
            registerSSHKeys(true, handle) {
                _state.value.repoToImport?.let {
                    cloneRepository(it, false, handle)
                }
            }
        }
    }

    private suspend fun registerSSHKeys(
        force: Boolean,
        handle: TaskHandle,
        onSuccess: suspend () -> Unit
    ) {
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
            onSuccess()
        } else {
            _event.trySend(ImportEvent.AuthRequested)
        }
    }

    private fun reportImportFailed() {
        updateResult(
            title = application.getString(R.string.error),
            message = application.getString(R.string.restore_failed)
        )
    }

    private fun getTargetTranslation(translationID: String): TargetTranslation {
        return translator.getTargetTranslation(translationID)
            ?: throw InvalidParameterException("The target translation '$translationID' is invalid")
    }
}
