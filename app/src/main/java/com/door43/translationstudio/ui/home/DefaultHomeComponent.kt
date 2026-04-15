package com.door43.translationstudio.ui.home

import android.app.Application
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
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
import com.door43.translationstudio.ui.launchWithProgress
import com.door43.translationstudio.ui.navigation.ComponentScope
import com.door43.usecases.BackupRC
import com.door43.usecases.GogsLogout
import com.door43.usecases.TranslationProgress
import com.door43.util.FileUtilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.resourcecontainer.Project
import java.io.File

private const val SORT_BY_PROJECT: String = "sort_by_project"
private const val SORT_BY_BOOK: String = "sort_by_book"

class DefaultHomeComponent(
    componentContext: ComponentContext,
) : HomeComponent,
    ComponentContext by componentContext,
    ComponentScope, ProgressOwner, KoinComponent {

    private val application: Application by inject()
    private val translator: Translator by inject()
    private val prefRepository: IPreferenceRepository by inject()
    private val calculateProgress: TranslationProgress by inject()
    private val profile: Profile by inject()
    private val gogsLogout: GogsLogout by inject()
    private val directoryProvider: IDirectoryProvider by inject()
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

    private val bookList = BibleCodes.getBibleBooks()

    override var lastFocusTargetTranslation: String?
        get() = translator.lastFocusTargetTranslation
        set(value) { translator.lastFocusTargetTranslation = value }

    override val lastOpened: TargetTranslation?
        get() = translator.lastFocusTargetTranslation?.let { lastTarget ->
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

    override fun onAction(action: HomeComponent.Action) {
        when (action) {
            is HomeComponent.Action.ShowProjectExists -> showProjectExists(action.translationId)
            is HomeComponent.Action.DeleteProject -> deleteProject(action.project)
            is HomeComponent.Action.ProjectSortChanged -> onProjectSortChanged(action.sort)
            is HomeComponent.Action.BookSortChanged -> onBookSortChanged(action.sort)
            is HomeComponent.Action.ShowProjectInfo -> {
                _state.update { it.copy(projectInfo = action.item) }
            }
            is HomeComponent.Action.ImportProject -> coroutineScope.launch {
                _event.trySend(HomeComponent.Event.ImportProject(action.uri))
            }
            is HomeComponent.Action.LoadProjects -> loadProjects()
            is HomeComponent.Action.LoadWithProgress -> loadWithProgress(action.translationIds)
            is HomeComponent.Action.HideProjectInfo -> _state.update { it.copy(projectInfo = null) }
            is HomeComponent.Action.Logout -> logout()
            is HomeComponent.Action.ShareApp -> shareApp()
            is HomeComponent.Action.RequestUpdateLibrary -> {
                _event.trySend(HomeComponent.Event.OpenUpdateLibrary)
            }
        }
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

    private fun loadWithProgress(translationIds: List<String>) {
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

    private fun logout() {
        launchWithProgress(
            application.getString(R.string.log_out)
        ) {
            withContext(Dispatchers.IO) {
                gogsLogout.execute()
                profile.logout()
            }

            _event.trySend(HomeComponent.Event.OnLogout)
        }
    }

    private fun onProjectSortChanged(sort: ProjectSort) {
        coroutineScope.launch {
            prefRepository.setDefaultPref(SORT_BY_PROJECT, sort.ordinal)
        }
        _state.update { it.copy(projectSort = sort) }
        _state.update { it.copy(scrollToTopTrigger = it.scrollToTopTrigger + 1) }
    }

    private fun onBookSortChanged(sort: BookSort) {
        coroutineScope.launch {
            prefRepository.setDefaultPref(SORT_BY_BOOK, sort.ordinal)
        }
        _state.update { it.copy(bookSort = sort) }
        _state.update { it.copy(scrollToTopTrigger = it.scrollToTopTrigger + 1) }
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
            file?.let { _event.trySend(HomeComponent.Event.ShareApp(it)) }
        }
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

    private fun deleteProject(project: TranslationItem) {
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
}
