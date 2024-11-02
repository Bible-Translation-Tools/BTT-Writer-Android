package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.translationstudio.ui.home.TranslationItem
import com.door43.usecases.BackupRC
import com.door43.usecases.CheckForLatestRelease
import com.door43.usecases.DownloadIndex
import com.door43.usecases.DownloadLatestRelease
import com.door43.usecases.ExamineImportsForCollisions
import com.door43.usecases.GogsLogout
import com.door43.usecases.ImportProjects
import com.door43.usecases.PullTargetTranslation
import com.door43.usecases.RegisterSSHKeys
import com.door43.usecases.TranslationProgress
import com.door43.usecases.UpdateCatalogs
import com.door43.usecases.UpdateSource
import com.door43.usecases.cleanup
import com.door43.util.FileUtilities
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.merge.MergeStrategy
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.resourcecontainer.Project
import java.io.File
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val application: Application
) : AndroidViewModel(application) {

    @Inject lateinit var translator: Translator
    @Inject lateinit var profile: Profile
    @Inject lateinit var gogsLogout: GogsLogout
    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var pullTargetTranslation: PullTargetTranslation
    @Inject lateinit var examineImportsForCollisions: ExamineImportsForCollisions
    @Inject lateinit var importProjects: ImportProjects
    @Inject lateinit var checkForLatestRelease: CheckForLatestRelease
    @Inject lateinit var downloadIndex: DownloadIndex
    @Inject lateinit var updateSource: UpdateSource
    @Inject lateinit var updateCatalogs: UpdateCatalogs
    @Inject lateinit var registerSSHKeys: RegisterSSHKeys
    @Inject lateinit var downloadLatestRelease: DownloadLatestRelease
    @Inject lateinit var backupRC: BackupRC
    @Inject lateinit var library: Door43Client
    @Inject lateinit var calculateProgress: TranslationProgress

    private val _progress = MutableLiveData<ProgressHelper.Progress?>()
    val progress: LiveData<ProgressHelper.Progress?> = _progress

    private val _loggedOut = MutableLiveData<Boolean?>(null)
    val loggedOut: LiveData<Boolean?> = _loggedOut

    private val _translations = MutableLiveData<List<TranslationItem>?>()
    val translations: LiveData<List<TranslationItem>?> = _translations

    private val _exportedApp = MutableLiveData<File?>()
    val exportedApp: LiveData<File?> = _exportedApp

    private val _pullTranslationResult = MutableLiveData<PullTargetTranslation.Result?>(null)
    val pullTranslationResult: LiveData<PullTargetTranslation.Result?> = _pullTranslationResult

    private val _examineImportsResult = MutableLiveData<ExamineImportsForCollisions.Result?>()
    val examineImportsResult: LiveData<ExamineImportsForCollisions.Result?> = _examineImportsResult

    private val _importResult = MutableLiveData<ImportProjects.ImportResults?>()
    val importResult: LiveData<ImportProjects.ImportResults?> = _importResult

    private val _latestRelease = MutableLiveData<CheckForLatestRelease.Result?>()
    val latestRelease: LiveData<CheckForLatestRelease.Result?> = _latestRelease

    private val _registeredSSHKeys = MutableLiveData<Boolean?>()
    val registeredSSHKeys: LiveData<Boolean?> = _registeredSSHKeys

    private val _updateSourceResult = MutableLiveData<UpdateSource.Result?>()
    val updateSourceResult: LiveData<UpdateSource.Result?> = _updateSourceResult

    private val _uploadCatalogResult = MutableLiveData<UpdateCatalogs.Result?>()
    val uploadCatalogResult: LiveData<UpdateCatalogs.Result?> = _uploadCatalogResult

    private val _indexDownloaded = MutableLiveData<Boolean?>()
    val indexDownloaded: LiveData<Boolean?> = _indexDownloaded

    private val _translationProgress = MutableLiveData<Double?>()
    val translationProgress: LiveData<Double?> = _translationProgress

    var lastFocusTargetTranslation: String?
        get() = translator.lastFocusTargetTranslation
        set(value) { translator.lastFocusTargetTranslation = value }

    var notifyTargetTranslationWithUpdates: String?
        get() = translator.notifyTargetTranslationWithUpdates
        set(value) { translator.notifyTargetTranslationWithUpdates = value }

    /**
     * get last project opened and make sure it is still present
     * @return
     */
    val lastOpened: TranslationItem?
        get() {
            val lastTarget = translator.lastFocusTargetTranslation
            if (lastTarget != null) {
                return translator.getTargetTranslation(lastTarget)?.let {
                    val progress = calculateProgress.execute(it)
                    TranslationItem(it, progress, ::getProject)
                }
            }
            return null
        }

    val loggedIn: Boolean
        get() = profile.gogsUser != null

    fun loadTranslations() {
        viewModelScope.launch {
            _translations.value = withContext(Dispatchers.IO) {
                translator.targetTranslations.map {
                    val progress = calculateProgress.execute(it)
                    TranslationItem(it, progress, ::getProject)
                }
            }
        }
    }

    /**
     * Log out the current gogs user
     */
    fun logout() {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(
                application.getString(R.string.log_out)
            )
            _loggedOut.value = withContext(Dispatchers.IO) {
                gogsLogout.execute()
                profile.logout()
                true
            }
            _progress.value = null
        }
    }

    fun findTranslationItem(translationId: String?): TranslationItem? {
        return translations.value?.singleOrNull {
            it.translation.id == translationId
        }
    }

    fun getTargetTranslation(translationId: String?): TargetTranslation? {
        return translator.getTargetTranslation(translationId)
    }

    fun deleteTargetTranslation(item: TranslationItem, orphaned: Boolean) {
        backupRC.backupTargetTranslation(item.translation, orphaned)
        translator.deleteTargetTranslation(item.translation.id)
        translator.clearTargetTranslationSettings(item.translation.id)

        _translations.value = _translations.value?.filter {
            it.translation.id != item.translation.id
        }
    }

    fun exportApp() {
        viewModelScope.launch {
            _exportedApp.value = withContext(Dispatchers.IO) {
                val pInfo = application.packageManager.getPackageInfo(application.packageName, 0)
                pInfo.applicationInfo?.let { info ->
                    val apkFile = File(info.publicSourceDir)
                    val exportFile = File(
                        directoryProvider.sharingDir, info.loadLabel(
                            application.packageManager
                        ).toString() + "_" + pInfo.versionName + ".apk"
                    )
                    FileUtilities.copyFile(apkFile, exportFile)
                    exportFile
                }
            }
        }
    }

    fun pullTargetTranslation(mergeStrategy: MergeStrategy) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress()
            _pullTranslationResult.value = withContext(Dispatchers.IO) {
                findTranslationItem(notifyTargetTranslationWithUpdates)?.let { item ->
                    pullTargetTranslation.execute(item.translation, mergeStrategy)
                }
            }
            _progress.value = null
        }
    }

    fun getProject(targetTranslation: TargetTranslation): Project {
        val project: Project
        val existingSources = targetTranslation.sourceTranslations
        // Gets an existing source project or default if none selected
        if (existingSources.isNotEmpty()) {
            val lastSource = existingSources[existingSources.size - 1]
            project = library.index.getTranslation(lastSource)?.project ?: library.index.getProject(
                targetTranslation.targetLanguageName,
                targetTranslation.projectId,
                true
            )
        } else {
            project = library.index.getProject(
                targetTranslation.targetLanguageName,
                targetTranslation.projectId,
                true
            )
        }
        return project
    }

    fun examineImportsForCollisions(contentUri: Uri) {
        viewModelScope.launch {
            _examineImportsResult.value = withContext(Dispatchers.IO) {
                examineImportsForCollisions.execute(contentUri)
            }
        }
    }

    fun importProjects(projectsFolder: File, overwrite: Boolean) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress()
            _importResult.value = withContext(Dispatchers.IO) {
                importProjects.importProject(projectsFolder, overwrite)
            }
            _progress.value = null
        }
    }

    fun checkForLatestRelease() {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress()
            _latestRelease.value = withContext(Dispatchers.IO) {
                checkForLatestRelease.execute()
            }
            _progress.value = null
        }
    }

    fun downloadIndex() {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress()
            _indexDownloaded.value = withContext(Dispatchers.IO) {
                downloadIndex.execute { progress, max, message ->
                    _progress.postValue(
                        ProgressHelper.Progress(
                            message,
                            progress,
                            max
                        )
                    )
                }
            }
            _progress.value = null
        }
    }

    fun updateSource(message: String) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress()
            _updateSourceResult.value = withContext(Dispatchers.IO) {
                updateSource.execute(message) { progress, max, message ->
                    _progress.postValue(
                        ProgressHelper.Progress(
                            message,
                            progress,
                            max
                        )
                    )
                }
            }
            _progress.value = null
        }
    }

    fun updateCatalogs(message: String) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress()
            _uploadCatalogResult.value = withContext(Dispatchers.IO) {
                updateCatalogs.execute(message) { progress, max, message ->
                    _progress.postValue(
                        ProgressHelper.Progress(
                            message,
                            progress,
                            max
                        )
                    )
                }
            }
            _progress.value = null
        }
    }

    fun registerSSHKeys(force: Boolean) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(
                application.getString(R.string.registering_keys)
            )
            _registeredSSHKeys.value = withContext(Dispatchers.IO) {
                registerSSHKeys.execute(force) { progress, max, message ->
                    _progress.postValue(
                        ProgressHelper.Progress(
                            message,
                            progress,
                            max
                        )
                    )
                }
            }
            _progress.value = null
        }
    }

    fun hasSSHKeys(): Boolean {
        return directoryProvider.hasSSHKeys()
    }

    fun cleanupExamineImportResult() {
        _examineImportsResult.value?.cleanup()
        _examineImportsResult.value = null
    }

    fun downloadLatestRelease(release: CheckForLatestRelease.Release) {
        downloadLatestRelease.execute(release)
    }

    fun getTranslationProgress(targetTranslation: TargetTranslation) {
        viewModelScope.launch {
            _translationProgress.value = withContext(Dispatchers.IO) {
                calculateProgress.execute(targetTranslation)
            }
        }
    }

    fun clearResults() {
        _loggedOut.value = null
        _uploadCatalogResult.value = null
        _latestRelease.value = null
        _pullTranslationResult.value = null
    }
}