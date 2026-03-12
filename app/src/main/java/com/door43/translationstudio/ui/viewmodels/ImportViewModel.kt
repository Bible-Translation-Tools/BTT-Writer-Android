package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.App.Companion.deviceLanguageCode
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.translationstudio.ui.home.RepositoryItem
import com.door43.usecases.AdvancedGogsRepoSearch
import com.door43.usecases.BackupRC
import com.door43.usecases.CloneRepository
import com.door43.usecases.ImportProjects
import com.door43.usecases.RegisterSSHKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.gogsclient.Repository
import java.io.File
import java.security.InvalidParameterException

class ImportViewModel(
    private val application: Application,
    private var profile: Profile,
    private val translator: Translator,
    private val advancedGogsRepoSearch: AdvancedGogsRepoSearch,
    private val cloneRepository: CloneRepository,
    private val registerSSHKeys: RegisterSSHKeys,
    private val importProjects: ImportProjects,
    private val backupRC: BackupRC,
    private val library: Door43Client,
    private val directoryProvider: IDirectoryProvider
) : AndroidViewModel(application) {

    private val _translation = MutableLiveData<TargetTranslation?>(null)
    val translation: LiveData<TargetTranslation?> = _translation

    private val _progress = MutableLiveData<ProgressHelper.Progress?>(null)
    val progress: LiveData<ProgressHelper.Progress?> = _progress

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

    fun loadTargetTranslation(translationID: String) {
        translator.getTargetTranslation(translationID)?.let {
            it.setDefaultContributor(profile.nativeSpeaker)
            _translation.value = it
        } ?: throw InvalidParameterException(
            "The target translation '$translationID' is invalid"
        )
    }

    fun searchRepositories(userQuery: String, repoQuery: String, limit: Int) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(
                application.getString(R.string.searching_repositories)
            )
            val result = withContext(Dispatchers.IO) {
                advancedGogsRepoSearch.execute(
                    userQuery,
                    repoQuery,
                    limit
                ) { progress, message ->
                    _progress.postValue(
                        ProgressHelper.Progress(
                            message,
                            progress.toInt(),
                            1
                        )
                    )
                }
            }
            _repositories.value = result.map(::mapRepository)
            _progress.value = null
        }
    }

    fun cloneRepository(cloneUrl: String) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(
                application.getString(R.string.cloning_repository)
            )
            _cloneRepoResult.value = withContext(Dispatchers.IO) {
                cloneRepository.execute(cloneUrl) { progress, message ->
                    _progress.postValue(
                        ProgressHelper.Progress(
                            message,
                            progress.toInt(),
                            1
                        )
                    )
                }
            }
            _progress.value = null
        }
    }

    fun importProjectFromUri(path: Uri, mergeOverwrite: Boolean) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(application.getString(R.string.import_source_text))
            val result = withContext(Dispatchers.IO) {
                importProjects.importProject(path, mergeOverwrite) { progress, message ->
                    _progress.postValue(
                        ProgressHelper.Progress(
                            message,
                            progress.toInt(),
                            1
                        )
                    )
                }
            }
            _importFromUriResult.value = result
            _progress.value = null
        }
    }

    fun importSource(uri: Uri) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(application.getString(R.string.import_source_text))
            _importSourceResult.value = withContext(Dispatchers.IO) {
                importProjects.importSource(uri)
            }
            _progress.value = null
        }
    }

    fun importSource(dir: File) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(application.getString(R.string.import_source_text))
            _importSourceResult.value = withContext(Dispatchers.IO) {
                importProjects.importSource(dir)
            }
            _progress.value = null
        }
    }

    fun registerSSHKeys(force: Boolean) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                registerSSHKeys.execute(force) { progress, message ->
                    _progress.postValue(
                        ProgressHelper.Progress(
                            message,
                            progress.toInt(),
                            1
                        )
                    )
                }
            }
            _registeredSSHKeys.value = result
            _progress.value = null
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
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(
                application.resources.getString(R.string.importing_file)
            )
            _importFromUriResult.value = withContext(Dispatchers.IO) {
                importProjects.importProject(
                    uri,
                    false
                ) { progress, message ->
                    _progress.postValue(
                        ProgressHelper.Progress(
                            message,
                            progress.toInt(),
                            1
                        )
                    )
                }
            }
            _progress.value = null
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