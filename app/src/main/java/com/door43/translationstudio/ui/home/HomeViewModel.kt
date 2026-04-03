package com.door43.translationstudio.ui.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.data.setDefaultPref
import com.door43.translationstudio.R
import com.door43.translationstudio.core.BibleCodes
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.ProgressManager
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TaskHandle
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.translationstudio.ui.launchWithProgress
import com.door43.usecases.BackupRC
import com.door43.usecases.CheckForLatestRelease
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.merge.MergeStrategy
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.resourcecontainer.Project
import java.io.File

private const val SORT_BY_PROJECT: String = "sort_by_project"
private const val SORT_BY_BOOK: String = "sort_by_book"

enum class ProjectSort {
    ProjectThenLanguage,
    LanguageThenProject,
    ProgressThenProject;

    companion object {
        fun of(i: Int): ProjectSort {
            return entries.getOrNull(i) ?: ProjectThenLanguage
        }
    }
}

enum class BookSort(val value: Int) {
    BibleOrder(0),
    Alphabetical(1);

    companion object {
        fun of(i: Int): BookSort {
            return entries.getOrNull(i) ?: BibleOrder
        }
    }
}

data class HomeState(
    val translations: List<TranslationItem> = emptyList(),
    val projectSort: ProjectSort = ProjectSort.ProjectThenLanguage,
    val bookSort: BookSort = BookSort.BibleOrder
)

sealed interface HomeEvent {
    data class SnackbarMessage(val message: String) : HomeEvent
    data class ShareApp(val file: File) : HomeEvent
    object OnLogout : HomeEvent
}

sealed interface HomeAction {
    data class ProjectSortChanged(val sort: ProjectSort) : HomeAction
    data class BookSortChanged(val sort: BookSort) : HomeAction
    object Logout : HomeAction
    object ShareApp: HomeAction

}

class HomeViewModel(
    private val translator: Translator,
    private val profile: Profile,
    private val gogsLogout: GogsLogout,
    private val directoryProvider: IDirectoryProvider,
    private val pullTargetTranslation: PullTargetTranslation,
    private val examineImportsForCollisions: ExamineImportsForCollisions,
    private val importProjects: ImportProjects,
    private val checkForLatestRelease: CheckForLatestRelease,
    private val updateSource: UpdateSource,
    private val updateCatalogs: UpdateCatalogs,
    private val registerSSHKeys: RegisterSSHKeys,
    private val downloadLatestRelease: DownloadLatestRelease,
    private val backupRC: BackupRC,
    private val library: Door43Client,
    private val calculateProgress: TranslationProgress,
    private val prefRepository: IPreferenceRepository
) : ViewModel(), KoinComponent, ProgressOwner {

    private val application: Application by inject()

    private val progressManager = ProgressManager(viewModelScope)
    override val progress get() = progressManager.progress

    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    private val _event = Channel<HomeEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    val projectSortOptions: List<ProjectSort> = buildList {
        add(ProjectSort.ProjectThenLanguage)
        add(ProjectSort.LanguageThenProject)
        add(ProjectSort.ProgressThenProject)
    }

    val bookSortOptions: List<BookSort> = buildList {
        add(BookSort.BibleOrder)
        add(BookSort.Alphabetical)
    }

    private val bookList = BibleCodes.getBibleBooks()

    // ============================== OLD CODE FOR REMOVAL ================================ //

    private val _progressOld = MutableLiveData<ProgressHelper.Progress?>()
    val progressOld: LiveData<ProgressHelper.Progress?> = _progressOld

    private val _loggedOut = MutableLiveData<Boolean?>(null)
    val loggedOut: LiveData<Boolean?> = _loggedOut

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

    private val _translationProgress = MutableLiveData<Double?>()
    val translationProgress: LiveData<Double?> = _translationProgress

    // ============================== OLD CODE FOR REMOVAL ================================ //

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
            translator.lastFocusTargetTranslation?.let { lastTarget ->
                return translator.getTargetTranslation(lastTarget)?.let {
                    val progress = calculateProgress.execute(it)
                    TranslationItem(it, progress, ::getProject)
                }
            }
            return null
        }

    val loggedIn: Boolean
        get() = profile.gogsUser != null

    init {
        viewModelScope.launch {
            state
                .map {
                    Triple(it.projectSort, it.bookSort, it.translations)
                }
                .distinctUntilChanged()
                .collect { (projectSort, bookSort, _) ->
                    sortTranslations(projectSort, bookSort)
                }
        }

        val projectSort = ProjectSort.of(
            prefRepository.getDefaultPref(SORT_BY_PROJECT, 0)
        )
        val bookSort = BookSort.of(
            prefRepository.getDefaultPref(SORT_BY_BOOK, 0)
        )
        _state.update { it.copy(projectSort = projectSort, bookSort = bookSort) }

        loadTranslations()
    }

    fun onAction(action: HomeAction) {
        when (action) {
            is HomeAction.ProjectSortChanged -> onProjectSortChanged(action.sort)
            is HomeAction.BookSortChanged -> onBookSortChanged(action.sort)
            HomeAction.Logout -> logout()
            HomeAction.ShareApp -> shareApp()
        }
    }

    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        progressManager.runTask(message, block)
    }

    private fun loadTranslations() {
        launchWithProgress(
            application.getString(R.string.loading)
        ) {
            val items = translator.targetTranslations.map {
                TranslationItem(
                    translation = it,
                    progress = calculateProgress.execute(it),
                    onGetProject = ::getProject
                )
            }
            _state.update { it.copy(translations = items) }
        }
    }

    private fun logout() {
        launchWithProgress(
            application.getString(R.string.log_out)
        ) {
            withContext(Dispatchers.IO) {
                gogsLogout.execute()
                profile.logout()
            }

            _event.trySend(HomeEvent.OnLogout)
        }
    }

    private fun onProjectSortChanged(sort: ProjectSort) {
        viewModelScope.launch {
            prefRepository.setDefaultPref(SORT_BY_PROJECT, sort.ordinal)
        }
        _state.update { it.copy(projectSort = sort) }
    }

    private fun onBookSortChanged(sort: BookSort) {
        viewModelScope.launch {
            prefRepository.setDefaultPref(SORT_BY_BOOK, sort.ordinal)
        }
        _state.update { it.copy(bookSort = sort) }
    }

    private fun sortTranslations(projectSort: ProjectSort, bookSort: BookSort) {
        val sortedTranslations =
            _state.value.translations.sortedWith { lhs: TranslationItem, rhs: TranslationItem ->
                var compare: Int
                when (projectSort) {
                    ProjectSort.ProjectThenLanguage -> {
                        compare = compareProject(lhs, rhs, bookSort)
                        if (compare == 0) {
                            compare = lhs.translation.targetLanguageName
                                .compareTo(rhs.translation.targetLanguageName, ignoreCase = true)
                        }
                        return@sortedWith compare
                    }

                    ProjectSort.LanguageThenProject -> {
                        compare = lhs.translation.targetLanguageName
                            .compareTo(rhs.translation.targetLanguageName, ignoreCase = true)
                        if (compare == 0) {
                            compare = compareProject(lhs, rhs, bookSort)
                        }
                        return@sortedWith compare
                    }

                    ProjectSort.ProgressThenProject -> {
                        compare = ((rhs.progress - lhs.progress) * 100).toInt()

                        if (compare == 0) {
                            compare = compareProject(lhs, rhs, bookSort)
                        }
                        return@sortedWith compare
                    }
                }
        }
        _state.update { it.copy(translations = sortedTranslations) }
    }

    private fun compareProject(
        lhs: TranslationItem,
        rhs: TranslationItem,
        sortProjectColumn: BookSort
    ): Int {
        if (sortProjectColumn == BookSort.BibleOrder) {
            val lhsIndex = bookList.indexOf(lhs.translation.projectId)
            val rhsIndex = bookList.indexOf(rhs.translation.projectId)
            // if not bible books, then compare by name
            if ((lhsIndex == rhsIndex) && (lhsIndex < 0)) {
                return lhs.formattedProjectName.compareTo(
                    rhs.formattedProjectName,
                    ignoreCase = true
                )
            }
            return lhsIndex - rhsIndex
        }

        // compare project names
        return lhs.formattedProjectName.compareTo(rhs.formattedProjectName, ignoreCase = true)
    }

    private fun shareApp() {
        launchWithProgress {
            val file = withContext(Dispatchers.IO) {
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
            file?.let { _event.trySend(HomeEvent.ShareApp(it)) }
        }
    }

    fun findTranslationItem(translationId: String?): TranslationItem? {
        return _state.value.translations.singleOrNull {
            it.translation.id == translationId
        }
    }

    fun getTargetTranslation(translationId: String): TargetTranslation? {
        return translator.getTargetTranslation(translationId)
    }

    fun deleteTargetTranslation(item: TranslationItem, orphaned: Boolean) {
        backupRC.backupTargetTranslation(item.translation, orphaned)
        translator.deleteTargetTranslation(item.translation.id)
        translator.clearTargetTranslationSettings(item.translation.id)

        _state.update { state ->
            state.copy(
                translations = state.translations.filter {
                    it.translation.id != item.translation.id
                }
            )
        }
    }

    fun pullTargetTranslation(mergeStrategy: MergeStrategy) {
        viewModelScope.launch {
            _progressOld.value = ProgressHelper.Progress()
            _pullTranslationResult.value = withContext(Dispatchers.IO) {
                findTranslationItem(notifyTargetTranslationWithUpdates)?.let { item ->
                    pullTargetTranslation.execute(item.translation, mergeStrategy)
                }
            }
            _progressOld.value = null
        }
    }

    fun getProject(targetTranslation: TargetTranslation): Project {
        val existingSources = targetTranslation.sourceTranslations
        // Gets an existing source project or default if none selected

        return if (existingSources.isNotEmpty()) {
            val lastSource = existingSources[existingSources.size - 1]
            library.index.getTranslation(lastSource)?.project
                ?: library.index.getProject(
                    targetTranslation.targetLanguageName,
                    targetTranslation.projectId,
                    true
                )
        } else {
            library.index.getProject(
                targetTranslation.targetLanguageName,
                targetTranslation.projectId,
                true
            )
        }!!
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
            _progressOld.value = ProgressHelper.Progress()
            _importResult.value = withContext(Dispatchers.IO) {
                importProjects.importProject(projectsFolder, overwrite)
            }
            _progressOld.value = null
        }
    }

    fun checkForLatestRelease() {
        viewModelScope.launch {
            _progressOld.value = ProgressHelper.Progress()
            _latestRelease.value = withContext(Dispatchers.IO) {
                checkForLatestRelease.execute()
            }
            _progressOld.value = null
        }
    }

    fun updateSource(message: String) {
        viewModelScope.launch {
            _progressOld.value = ProgressHelper.Progress()
            _updateSourceResult.value = withContext(Dispatchers.IO) {
                updateSource.execute(message) { progress, message ->
                    _progressOld.postValue(
                        ProgressHelper.Progress(
                            message,
                            progress.toInt(),
                            1
                        )
                    )
                }
            }
            _progressOld.value = null
        }
    }

    fun updateCatalogs(message: String) {
        viewModelScope.launch {
            _progressOld.value = ProgressHelper.Progress()
            _uploadCatalogResult.value = withContext(Dispatchers.IO) {
                updateCatalogs.execute(true, message) { progress, message ->
                    _progressOld.postValue(
                        ProgressHelper.Progress(
                            message,
                            progress.toInt(),
                            1
                        )
                    )
                }
            }
            _progressOld.value = null
        }
    }

    fun registerSSHKeys(force: Boolean) {
        viewModelScope.launch {
            _progressOld.value = ProgressHelper.Progress(
                application.getString(R.string.registering_keys)
            )
            _registeredSSHKeys.value = withContext(Dispatchers.IO) {
                registerSSHKeys.execute(force) { progress, message ->
                    _progressOld.postValue(
                        ProgressHelper.Progress(
                            message,
                            progress.toInt(),
                            1
                        )
                    )
                }
            }
            _progressOld.value = null
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