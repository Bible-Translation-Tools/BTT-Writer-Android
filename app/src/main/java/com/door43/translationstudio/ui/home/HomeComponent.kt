package com.door43.translationstudio.ui.home

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.activate
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.decompose.router.slot.dismiss
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.data.setDefaultPref
import com.door43.translationstudio.R
import com.door43.translationstudio.core.BibleCodes
import com.door43.translationstudio.core.ComponentScope
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.Progress
import com.door43.translationstudio.core.ProgressManager
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TaskHandle
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.core.launchWithProgress
import com.door43.translationstudio.ui.dialogs.download.DefaultDownloadSourcesComponent
import com.door43.translationstudio.ui.dialogs.download.DownloadSourcesComponent
import com.door43.translationstudio.ui.dialogs.export.DefaultExportComponent
import com.door43.translationstudio.ui.dialogs.export.ExportComponent
import com.door43.translationstudio.ui.dialogs.feedback.DefaultFeedbackComponent
import com.door43.translationstudio.ui.dialogs.feedback.FeedbackComponent
import com.door43.translationstudio.ui.dialogs.import.DefaultImportComponent
import com.door43.translationstudio.ui.dialogs.import.DefaultImportUsfmComponent
import com.door43.translationstudio.ui.dialogs.import.ImportComponent
import com.door43.translationstudio.ui.dialogs.import.ImportUsfmComponent
import com.door43.translationstudio.ui.dialogs.update.DefaultUpdateLibraryComponent
import com.door43.translationstudio.ui.dialogs.update.UpdateLibraryComponent
import com.door43.translationstudio.ui.navigation.RootComponent
import com.door43.usecases.BackupRC
import com.door43.usecases.GogsLogout
import com.door43.usecases.TranslationProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
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

interface HomeComponent {
    val state: StateFlow<HomeState>
    val event: Flow<Event>
    val progress: StateFlow<Progress?>
    val dialogSlot: Value<ChildSlot<*, DialogChild>>

    val projectSortOptions: List<ProjectSort>
    val bookSortOptions: List<BookSort>

    var lastFocusTargetTranslation: String?
    val lastOpened: TargetTranslation?

    fun deleteProject(project: TranslationItem)
    fun changeProjectSort(sort: ProjectSort)
    fun changeBookSort(sort: BookSort)
    fun showProjectInfo(item: TranslationItem)
    fun importProject(uri: Uri)
    fun loadWithProgress(translationIds: List<String>)
    fun hideProjectInfo()
    fun requestUpdateLibrary()

    fun showFeedbackDialog()
    fun showImportDialog(projectUri: Uri? = null)
    fun showUpdateLibraryDialog(triggerUpdate: Boolean = false)
    fun showExportDialog(translationId: String, showPrint: Boolean)
    fun dismissDialog()

    fun onNewTranslation()
    fun onChangeTranslationLanguage(
        disabledLanguages: List<String> = emptyList(),
        translationId: String? = null
    )
    fun openSettings()
    fun publishProject(translationId: String)
    fun openProject(translationId: String, mergeConflictFilterOn: Boolean)
    fun exitApp()
    fun shareApp()
    fun exportToApp(file: File)
    fun openLogin()
    fun logout()

    data class SortTrigger(
        val projectSort: ProjectSort,
        val bookSort: BookSort,
        val translations: List<TranslationItem>
    )

    data class HomeState(
        val translations: List<TranslationItem> = emptyList(),
        val projectSort: ProjectSort = ProjectSort.ProjectThenLanguage,
        val bookSort: BookSort = BookSort.BibleOrder,
        val projectInfo: TranslationItem? = null,
        val scrollToTopTrigger: Int = 0
    )

    sealed interface Event {
        data class SnackbarMessage(val message: String) : Event
    }

    @Serializable
    sealed interface DialogConfig {
        @Serializable
        data object Feedback : DialogConfig

        @Serializable
        data class Import(val projectUri: String? = null) : DialogConfig

        @Serializable
        data class UpdateLibrary(val triggerUpdate: Boolean = false) : DialogConfig

        @Serializable
        data class ImportUsfm(val fileUri: String) : DialogConfig

        @Serializable
        data object DownloadSources : DialogConfig

        @Serializable
        data class Export(val translationId: String, val showPrint: Boolean) : DialogConfig
    }

    sealed interface DialogChild {
        data class Feedback(val component: FeedbackComponent) : DialogChild
        data class Import(val component: ImportComponent) : DialogChild
        data class UpdateLibrary(val component: UpdateLibraryComponent) : DialogChild
        data class ImportUsfm(val component: ImportUsfmComponent) : DialogChild
        data class DownloadSources(val component: DownloadSourcesComponent) : DialogChild
        data class Export(val component: ExportComponent) : DialogChild
    }

    sealed interface Result {
        data object Logout : Result
        data object OpenLogin : Result
        data object OpenSettings : Result
        data class PublishProject(val translationId: String) : Result
        data class OpenProject(
            val translationId: String,
            val mergeConflictFilterOn: Boolean
        ) : Result
        data object ExitApp : Result
        data object ShareApp : Result
        data class ExportToApp(val file: File) : Result
        data class ChangeTranslationLanguage(
            val disabledLanguages: List<String>,
            val translationId: String?
        ) : Result
        data object OpenNewTranslation : Result
    }
}

class DefaultHomeComponent(
    componentContext: ComponentContext,
    private val sharedFlow: SharedFlow<RootComponent.SharedEvent>,
    private val onResult: (HomeComponent.Result) -> Unit
) : HomeComponent,
    ComponentContext by componentContext,
    ComponentScope, ProgressOwner, KoinComponent {

    private val application: Application by inject()
    private val translator: Translator by inject()
    private val prefRepository: IPreferenceRepository by inject()
    private val calculateProgress: TranslationProgress by inject()
    private val profile: Profile by inject()
    private val gogsLogout: GogsLogout by inject()
    private val backupRC: BackupRC by inject()
    private val library: Door43Client by inject()

    override val coroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val progressManager = ProgressManager(coroutineScope)
    override val progress get() = progressManager.progress

    private val _state = MutableStateFlow(HomeComponent.HomeState())
    override val state = _state.asStateFlow()

    private val _event = Channel<HomeComponent.Event>(Channel.BUFFERED)
    override val event = _event.receiveAsFlow()

    override val projectSortOptions: List<ProjectSort> = buildList {
        add(ProjectSort.ProjectThenLanguage)
        add(ProjectSort.LanguageThenProject)
        add(ProjectSort.ProgressThenProject)
    }

    override val bookSortOptions: List<BookSort> = buildList {
        add(BookSort.BibleOrder)
        add(BookSort.Alphabetical)
    }

    private val dialogNavigation = SlotNavigation<HomeComponent.DialogConfig>()

    override val dialogSlot = childSlot(
        source = dialogNavigation,
        serializer = HomeComponent.DialogConfig.serializer(),
        handleBackButton = true,
        childFactory = ::createDialogChild
    )

    private val bookList = BibleCodes.getBibleBooks()

    override var lastFocusTargetTranslation: String?
        get() = translator.lastFocusTargetTranslation
        set(value) { translator.lastFocusTargetTranslation = value }

    override val lastOpened: TargetTranslation?
        get() = lastFocusTargetTranslation?.let { lastTarget ->
            translator.getTargetTranslation(lastTarget)
        }

    init {
        coroutineScope.launch {
            state
                .map { s ->
                    HomeComponent.SortTrigger(s.projectSort, s.bookSort, s.translations)
                }
                .distinctUntilChanged()
                .collect { trigger ->
                    sortTranslations(trigger.projectSort, trigger.bookSort)
                }
        }

        coroutineScope.launch {
            sharedFlow.collectLatest { event ->
                when (event) {
                    is RootComponent.SharedEvent.LoadProjects -> loadProjects()
                    is RootComponent.SharedEvent.DuplicateProject -> {
                        showProjectExists(event.translationId)
                    }
                    is RootComponent.SharedEvent.SnackbarMessage -> {
                        _event.trySend(HomeComponent.Event.SnackbarMessage(event.message))
                    }
                    is RootComponent.SharedEvent.RequestLibraryUpdate -> {
                        showUpdateLibraryDialog(triggerUpdate = true)
                    }
                    is RootComponent.SharedEvent.ImportProject -> showImportDialog(event.uri)
                }
            }
        }

        val projectSort = ProjectSort.of(
            prefRepository.getDefaultPref(SORT_BY_PROJECT, 0)
        )
        val bookSort = BookSort.of(
            prefRepository.getDefaultPref(SORT_BY_BOOK, 0)
        )
        _state.update { it.copy(projectSort = projectSort, bookSort = bookSort) }

        loadProjects()

        lifecycle.doOnDestroy {
            coroutineScope.cancel()
        }
    }

    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        progressManager.runTask(message, block)
    }

    override fun deleteProject(project: TranslationItem) {
        launchWithProgress {
            deleteProject(project, false)

            _state.update { state ->
                state.copy(
                    translations = state.translations.filter {
                        it.translation.id != project.translation.id
                    }
                )
            }
        }
    }

    override fun changeProjectSort(sort: ProjectSort) {
        coroutineScope.launch {
            prefRepository.setDefaultPref(SORT_BY_PROJECT, sort.ordinal)
        }
        _state.update { it.copy(projectSort = sort) }
        _state.update { it.copy(scrollToTopTrigger = it.scrollToTopTrigger + 1) }
    }

    override fun changeBookSort(sort: BookSort) {
        coroutineScope.launch {
            prefRepository.setDefaultPref(SORT_BY_BOOK, sort.ordinal)
        }
        _state.update { it.copy(bookSort = sort) }
        _state.update { it.copy(scrollToTopTrigger = it.scrollToTopTrigger + 1) }
    }

    override fun showProjectInfo(item: TranslationItem) {
        _state.update { it.copy(projectInfo = item) }
    }

    override fun importProject(uri: Uri) {
        showImportDialog(uri)
    }

    override fun loadWithProgress(translationIds: List<String>) {
        coroutineScope.launch {
            val newUpdates = translationIds.mapNotNull { id ->
                getTargetTranslation(id)?.let { translation ->
                    val progress = calculateProgress.execute(translation)

                    id to TranslationItem(
                        name = getProject(translation)?.name ?: "Unknown",
                        translation = translation,
                        progress = progress
                    )
                }
            }.toMap()

            if (newUpdates.isEmpty()) return@launch

            _state.update { state ->
                val currentItemsMap = state.translations.associateBy {
                    it.translation.id
                }.toMutableMap()

                newUpdates.forEach { (id, newItem) ->
                    currentItemsMap[id] = newItem
                }

                state.copy(translations = currentItemsMap.values.toList())
            }
        }
    }

    override fun hideProjectInfo() {
        _state.update { it.copy(projectInfo = null) }
    }

    override fun requestUpdateLibrary() {
        showUpdateLibraryDialog(triggerUpdate = true)
    }

    override fun showFeedbackDialog() {
        dialogNavigation.activate(HomeComponent.DialogConfig.Feedback)
    }

    override fun showImportDialog(projectUri: Uri?) {
        dialogNavigation.activate(
            HomeComponent.DialogConfig.Import(projectUri?.toString())
        )
    }

    override fun showUpdateLibraryDialog(triggerUpdate: Boolean) {
        dialogNavigation.activate(
            HomeComponent.DialogConfig.UpdateLibrary(triggerUpdate)
        )
    }

    override fun showExportDialog(translationId: String, showPrint: Boolean) {
        dialogNavigation.activate(
            HomeComponent.DialogConfig.Export(
                translationId = translationId,
                showPrint = showPrint
            )
        )
    }

    override fun dismissDialog() {
        dialogNavigation.dismiss()
    }

    override fun logout() {
        launchWithProgress(
            application.getString(R.string.log_out)
        ) {
            withContext(Dispatchers.IO) {
                gogsLogout.execute()
                profile.logout()
            }

            onResult(HomeComponent.Result.Logout)
        }
    }

    override fun openSettings() {
        onResult(HomeComponent.Result.OpenSettings)
    }

    override fun publishProject(translationId: String) {
        onResult(HomeComponent.Result.PublishProject(translationId))
    }

    override fun openProject(translationId: String, mergeConflictFilterOn: Boolean) {
        lastFocusTargetTranslation = translationId
        onResult(HomeComponent.Result.OpenProject(translationId, mergeConflictFilterOn))
    }

    override fun exitApp() {
        onResult(HomeComponent.Result.ExitApp)
    }

    override fun shareApp() {
        onResult(HomeComponent.Result.ShareApp)
    }

    override fun exportToApp(file: File) {
        onResult(HomeComponent.Result.ExportToApp(file))
    }

    override fun openLogin() {
        onResult(HomeComponent.Result.OpenLogin)
    }

    override fun onNewTranslation() {
        onResult(HomeComponent.Result.OpenNewTranslation)
    }

    override fun onChangeTranslationLanguage(
        disabledLanguages: List<String>,
        translationId: String?
    ) {
        onResult(HomeComponent.Result.ChangeTranslationLanguage(
            disabledLanguages = disabledLanguages,
            translationId = translationId
        ))
    }

    private fun loadProjects() {
        launchWithProgress(
            application.getString(R.string.loading)
        ) {
            val existingProgress = _state.value.translations.associate {
                it.translation.id to it.progress
            }
            val items = withContext(Dispatchers.IO) {
                translator.targetTranslations.map {
                    TranslationItem(
                        name = getProject(it)?.name ?: "Unknown",
                        translation = it,
                        progress = existingProgress[it.id] ?: calculateProgress.execute(it)
                    )
                }
            }
            _state.update { it.copy(translations = items) }
        }
    }

    private suspend fun sortTranslations(projectSort: ProjectSort, bookSort: BookSort) {
        val sortedTranslations = withContext(Dispatchers.Default) {
            _state.value.translations.sortedWith { lhs: TranslationItem, rhs: TranslationItem ->
                var compare: Int
                when (projectSort) {
                    ProjectSort.ProjectThenLanguage -> {
                        compare = compareProject(lhs, rhs, bookSort)
                        if (compare == 0) {
                            compare = lhs.translation.targetLanguageName.compareTo(
                                rhs.translation.targetLanguageName,
                                ignoreCase = true
                            )
                        }
                        return@sortedWith compare
                    }

                    ProjectSort.LanguageThenProject -> {
                        compare = lhs.translation.targetLanguageName.compareTo(
                            rhs.translation.targetLanguageName,
                            ignoreCase = true
                        )
                        if (compare == 0) {
                            compare = compareProject(lhs, rhs, bookSort)
                        }
                        return@sortedWith compare
                    }

                    ProjectSort.ProgressThenProject -> {
                        compare = rhs.progress.compareTo(lhs.progress)

                        if (compare == 0) {
                            compare = compareProject(lhs, rhs, bookSort)
                        }
                        return@sortedWith compare
                    }
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

    private fun showProjectExists(translationId: String) {
        launchWithProgress {
            val data: Pair<Project, TargetTranslation>? = withContext(Dispatchers.IO) {
                getTargetTranslation(translationId)?.let { targetTranslation ->
                    getProject(targetTranslation)?. let { project ->
                        project to targetTranslation
                    }
                }
            }
            val (project, translation) = data ?: return@launchWithProgress

            val message = application.getString(
                R.string.duplicate_target_translation,
                project.name,
                translation.targetLanguageName
            )
            _event.trySend(HomeComponent.Event.SnackbarMessage(message))
        }
    }

    private suspend fun getProject(targetTranslation: TargetTranslation): Project? {
        return withContext(Dispatchers.IO) {
            val existingSources = targetTranslation.sourceTranslations
            if (existingSources.isNotEmpty()) {
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
            }
        }
    }

    private suspend fun deleteProject(project: TranslationItem, orphaned: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                backupRC.backupTargetTranslation(project.translation, orphaned)
                translator.deleteTargetTranslation(project.translation.id)
                translator.clearTargetTranslationSettings(project.translation.id)
            } catch (_: Exception) {
                deleteProject(project, true)
            }
        }
    }

    private fun getTargetTranslation(translationId: String): TargetTranslation? {
        return translator.getTargetTranslation(translationId)
    }

    private fun createDialogChild(
        config: HomeComponent.DialogConfig,
        componentContext: ComponentContext
    ): HomeComponent.DialogChild = when (config) {
        is HomeComponent.DialogConfig.Feedback -> HomeComponent.DialogChild.Feedback(
            component = DefaultFeedbackComponent(
                componentContext = componentContext
            )
        )
        is HomeComponent.DialogConfig.Import -> HomeComponent.DialogChild.Import(
            component = DefaultImportComponent(
                componentContext = componentContext,
                projectUri = config.projectUri?.toUri(),
                onResult = ::onImportResult
            )
        )
        is HomeComponent.DialogConfig.UpdateLibrary -> HomeComponent.DialogChild.UpdateLibrary(
            component = DefaultUpdateLibraryComponent(
                componentContext = componentContext,
                triggerUpdate = config.triggerUpdate,
                onResult = ::onUpdateLibraryResult
            )
        )
        is HomeComponent.DialogConfig.ImportUsfm -> HomeComponent.DialogChild.ImportUsfm(
            DefaultImportUsfmComponent(
                componentContext = componentContext,
                fileUri = config.fileUri,
                onResult = ::onImportUsfmResult
            )
        )
        is HomeComponent.DialogConfig.DownloadSources -> HomeComponent.DialogChild.DownloadSources(
            DefaultDownloadSourcesComponent(
                componentContext = componentContext
            )
        )
        is HomeComponent.DialogConfig.Export -> HomeComponent.DialogChild.Export(
            DefaultExportComponent(
                componentContext = componentContext,
                translationId = config.translationId,
                showPrint = config.showPrint,
                onResult = ::onExportResult
            )
        )
    }

    private fun onImportResult(result: ImportComponent.Result) {
        when (result) {
            is ImportComponent.Result.MergeConflict -> {
                dismissDialog()
                openProject(result.translationId, true)
            }
            is ImportComponent.Result.ProjectsImported -> {
                dismissDialog()
                loadWithProgress(result.translationIds)
            }
            is ImportComponent.Result.OpenUsfmImport -> {
                dialogNavigation.activate(
                    HomeComponent.DialogConfig.ImportUsfm(result.fileUri)
                )
            }
        }
    }

    private fun onUpdateLibraryResult(result: UpdateLibraryComponent.Result) {
        when (result) {
            is UpdateLibraryComponent.Result.OpenDownloadSources -> {
                dialogNavigation.activate(HomeComponent.DialogConfig.DownloadSources)
            }
        }
    }

    private fun onImportUsfmResult(result: ImportUsfmComponent.Result) {
        when (result) {
            is ImportUsfmComponent.Result.ProjectsImported -> {
                dismissDialog()
                loadWithProgress(result.translationIds)
            }
            is ImportUsfmComponent.Result.MergeConflict -> {
                dismissDialog()
                openProject(result.translationId, true)
            }
        }
    }

    private fun onExportResult(result: ExportComponent.Result) {
        when (result) {
            is ExportComponent.Result.Error -> {}
            is ExportComponent.Result.ExportToApp -> {
                dismissDialog()
                exportToApp(result.file)
            }
            is ExportComponent.Result.OpenLogin -> {
                dismissDialog()
                openLogin()
            }
            is ExportComponent.Result.Logout -> {
                dismissDialog()
                logout()
            }
            is ExportComponent.Result.MergeConflict -> {
                dismissDialog()
                openProject(result.translationId, true)
            }
            is ExportComponent.Result.OpenFeedback -> {
                dismissDialog()
                showFeedbackDialog()
            }
        }
    }
}
