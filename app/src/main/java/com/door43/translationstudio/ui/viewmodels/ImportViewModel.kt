package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.App.Companion.deviceLanguageCode
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.ProgressManager
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TaskHandle
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.home.RepositoryItem
import com.door43.translationstudio.ui.launchWithProgress
import com.door43.usecases.AdvancedGogsRepoSearch
import com.door43.usecases.BackupRC
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
import java.io.File
import java.security.InvalidParameterException

private const val IMPORT_TRANSLATION_MIME = "application/tstudio"
private const val IMPORT_USFM_MIME = "text/usfm"

data class ImportState(
    val mergeConflict: ImportProjects.ImportUriResult? = null,
    val resultMessage: String? = null
)

sealed interface ImportAction {
    data class ImportUsfm(val uri: Uri) : ImportAction
    data class ImportProject(val uri: Uri, val overwrite: Boolean) : ImportAction
    data class ImportSource(val uri: Uri) : ImportAction
    data class ResetToMaster(val translationId: String) : ImportAction
    object ClearResult : ImportAction
    object ClearMergeConflict : ImportAction
    object ApplyMergeConflict : ImportAction
}

sealed class ImportEvent {
    data class ImportUsfm(val uri: Uri) : ImportEvent()
    data class ResolveMergeConflict(val translationId: String) : ImportEvent()
}

class ImportViewModel(
    private var profile: Profile,
    private val translator: Translator,
    private val advancedGogsRepoSearch: AdvancedGogsRepoSearch,
    private val cloneRepository: CloneRepository,
    private val registerSSHKeys: RegisterSSHKeys,
    private val importProjects: ImportProjects,
    private val backupRC: BackupRC,
    private val library: Door43Client,
    private val directoryProvider: IDirectoryProvider
) : ViewModel(), KoinComponent, ProgressOwner {

    private val application: Application by inject()

    private val progressManager = ProgressManager(viewModelScope)
    override val progress get() = progressManager.progress

    private val _state = MutableStateFlow(ImportState())
    val state: StateFlow<ImportState> = _state.asStateFlow()

    private val _event = Channel<ImportEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    // ----------------------- OLD CODE ----------------------- //

    private val _translation = MutableLiveData<TargetTranslation?>(null)
    val translation: LiveData<TargetTranslation?> = _translation

    private val _repositories = MutableLiveData<List<RepositoryItem>>(null)
    val repositories: LiveData<List<RepositoryItem>> = _repositories

    private val _cloneRepoResult = MutableLiveData<CloneRepository.Result?>(null)
    val cloneRepoResult: LiveData<CloneRepository.Result?> = _cloneRepoResult

    private val _importFromUriResult = MutableLiveData<ImportProjects.ImportUriResult?>(null)
    val importFromUriResult: LiveData<ImportProjects.ImportUriResult?> = _importFromUriResult

    private val _registeredSSHKeys = MutableLiveData<Boolean?>()
    val registeredSSHKeys: LiveData<Boolean?> = _registeredSSHKeys

    private val _importSourceResult = MutableLiveData<ImportProjects.ImportSourceResult?>(null)
    val importSourceResult: LiveData<ImportProjects.ImportSourceResult?> = _importSourceResult

    private val _backupRestoreResult = MutableLiveData<ImportProjects.ImportUriResult?>(null)
    val backupRestoreResult: MutableLiveData<ImportProjects.ImportUriResult?> = _backupRestoreResult

    // ----------------------- OLD CODE ----------------------- //

    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        progressManager.runTask(message, block)
    }

    fun onAction(action: ImportAction) {
        when (action) {
            is ImportAction.ImportUsfm -> importUsfm(action.uri)
            is ImportAction.ImportProject -> importProject(action.uri, action.overwrite)
            is ImportAction.ImportSource -> importSource(action.uri)
            is ImportAction.ResetToMaster -> viewModelScope.launch {
                resetToMaster(action.translationId)
            }
            ImportAction.ClearResult -> _state.update { it.copy(resultMessage = null) }
            ImportAction.ClearMergeConflict -> _state.update { it.copy(mergeConflict = null) }
            ImportAction.ApplyMergeConflict -> applyMergeConflict()
        }
    }

    private fun importUsfm(uri: Uri) {
        val filename = FileUtilities.getUriDisplayName(application, uri)
        val isUsfm = filename.contains(Translator.USFM_EXTENSION, ignoreCase = true)
        val isTxt = filename.contains(Translator.TXT_EXTENSION, ignoreCase = true)
        val isZip = filename.contains(Translator.ZIP_EXTENSION, ignoreCase = true)
        if (isUsfm || isTxt || isZip) {
            _event.trySend(ImportEvent.ImportUsfm(uri))
        } else {
            val error = "${application.getString(R.string.invalid_file)}\n$filename"
            _state.update { it.copy(resultMessage = error) }
        }
    }

    private fun importProject(uri: Uri, overwrite: Boolean) {
        launchWithProgress(
            application.getString(R.string.import_source_text)
        ) { handle ->
            val filename = FileUtilities.getUriDisplayName(application, uri)
            val isTstudio = filename.contains(Translator.TSTUDIO_EXTENSION, ignoreCase = true)
            val isZip = filename.contains(Translator.ZIP_EXTENSION, ignoreCase = true)
            if (isTstudio || isZip) {
                if (overwrite) {
                    _state.value.mergeConflict?.importedSlug?.let {
                        resetToMaster(it)
                    }
                }

                val result = withContext(Dispatchers.IO) {
                    importProjects.importProject(uri, overwrite) { progress, message ->
                        handle.update(progress, message)
                    }
                }
                when {
                    result.success && result.alreadyExists && !overwrite -> {
                        _state.update { it.copy(mergeConflict = result) }
                    }
                    result.success -> {
                        val message = application.getString(R.string.import_success) +
                                "\n${result.readablePath}"
                        _state.update { it.copy(resultMessage = message) }
                    }
                    result.invalidFileName -> {
                        val message = application.getString(R.string.invalid_file) +
                                "\n${result.readablePath}"
                        _state.update { it.copy(resultMessage = message) }
                    }
                    else -> {
                        val message = application.getString(R.string.import_failed) +
                                "\n${result.readablePath}"
                        _state.update { it.copy(resultMessage = message) }
                    }
                }
            } else {
                val error = "${application.getString(R.string.invalid_file)}\n$filename"
                _state.update { it.copy(resultMessage = error) }
            }
        }
    }

    private suspend fun resetToMaster(translationId: String) {
        withContext(Dispatchers.IO) {
            val targetTranslation = translator.getTargetTranslation(translationId)
            targetTranslation?.resetToMasterBackup()
        }
        _state.update { it.copy(mergeConflict = null) }
    }

    private fun applyMergeConflict() {
        val result = _state.value.mergeConflict ?: return
        if (result.hasMergeConflict) {
            result.importedSlug?.let {
                _event.trySend(
                    ImportEvent.ResolveMergeConflict(it)
                )
            }
        } else {
            val message = application.getString(R.string.import_success) +
                    "\n${result.importedSlug}"
            _state.update { it.copy(resultMessage = message) }
        }
        _state.update { it.copy(mergeConflict = null) }
    }





    fun loadTargetTranslation(translationID: String) {
        translator.getTargetTranslation(translationID)?.let {
            it.setDefaultContributor(profile.nativeSpeaker)
            _translation.value = it
        } ?: throw InvalidParameterException(
            "The target translation '$translationID' is invalid"
        )
    }

    fun searchRepositories(userQuery: String, repoQuery: String, limit: Int) {
        launchWithProgress(
            application.getString(R.string.searching_repositories)
        ) { handle ->
            val result = withContext(Dispatchers.IO) {
                advancedGogsRepoSearch.execute(
                    userQuery,
                    repoQuery,
                    limit
                ) { progress, message ->
                    handle.update(progress, message)
                }
            }
            _repositories.value = result.map(::mapRepository)
        }
    }

    fun cloneRepository(cloneUrl: String) {
        launchWithProgress(
            application.getString(R.string.cloning_repository)
        ) { handle ->
            _cloneRepoResult.value = withContext(Dispatchers.IO) {
                cloneRepository.execute(cloneUrl) { progress, message ->
                    handle.update(progress, message)
                }
            }
        }
    }

    fun importSource(uri: Uri) {
        launchWithProgress(
            application.getString(R.string.import_source_text)
        ) {
            _importSourceResult.value = withContext(Dispatchers.IO) {
                importProjects.importSource(uri)
            }
        }
    }

    fun importSource(dir: File) {
        launchWithProgress(
            application.getString(R.string.import_source_text)
        ) {
            _importSourceResult.value = withContext(Dispatchers.IO) {
                importProjects.importSource(dir)
            }
        }
    }

    fun registerSSHKeys(force: Boolean) {
        launchWithProgress { handle ->
            val result = withContext(Dispatchers.IO) {
                registerSSHKeys.execute(force) { progress, message ->
                    handle.update(progress, message)
                }
            }
            _registeredSSHKeys.value = result
        }
    }

    fun backupAndDeleteTranslation(dir: File) {
        try {
            backupRC.backupTargetTranslation(dir)
            translator.deleteTargetTranslation(dir)
        } catch (e: Exception) {
            Log.w(this::class.simpleName, e)
        }
    }

    fun mapRepository(repository: Repository): RepositoryItem {
        val repoName = repository.fullName.split("/".toRegex())
        var projectName = ""
        var languageName = ""
        var code = "en" // default font language if language is not found
        var direction = "ltor" // default font language direction if language is not found
        var notSupportedID = 0
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
                    notSupportedID = when (resourceTypeSlug) {
                        "tw" -> R.string.translation_words
                        "tn" -> R.string.label_translation_notes
                        "tq" -> R.string.translation_questions
                        else -> R.string.unsupported
                    }
                }

                val project = library.index.getProject(deviceLanguageCode, projectSlug, true)
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
                notSupportedID = R.string.unsupported
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
            notSupportedID
        ) { repository.toJSON() }
    }

    fun getBackupTranslations(): List<File> {
        return directoryProvider
            .backupsDir
            .listFiles()
            ?.asList()
            ?.filter { it.length() > 0 }
            ?: listOf()
    }

    fun restoreFromBackup(backup: File) {
        val uri = Uri.fromFile(backup)
        launchWithProgress(
            application.resources.getString(
                R.string.importing_file,
                backup.name
            )
        ) { handle ->
            _importFromUriResult.value = withContext(Dispatchers.IO) {
                importProjects.importProject(
                    uri,
                    false
                ) { progress, message ->
                    handle.update(progress, message)
                }
            }
        }
    }

    fun clearResults() {
        _registeredSSHKeys.value = null
        _importFromUriResult.value = null
        _importSourceResult.value = null
    }

    fun clearCloneResult() {
        _cloneRepoResult.value = null
    }
}