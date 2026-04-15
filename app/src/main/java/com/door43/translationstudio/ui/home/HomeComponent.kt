package com.door43.translationstudio.ui.home

import android.net.Uri
import com.door43.translationstudio.core.Progress
import com.door43.translationstudio.core.TargetTranslation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

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

    val projectSortOptions: List<ProjectSort>
    val bookSortOptions: List<BookSort>

    var lastFocusTargetTranslation: String?
    val lastOpened: TargetTranslation?

    fun onAction(action: Action)

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
        data class ShareApp(val file: File) : Event
        data class ImportProject(val uri: Uri) : Event
        data object OnLogout : Event
        data object OpenUpdateLibrary : Event
    }

    sealed interface Action {
        data class ShowProjectExists(val translationId: String) : Action
        data class DeleteProject(val project: TranslationItem): Action
        data class ProjectSortChanged(val sort: ProjectSort) : Action
        data class BookSortChanged(val sort: BookSort) : Action
        data class ShowProjectInfo(val item: TranslationItem) : Action
        data class ImportProject(val uri: Uri) : Action
        data object LoadProjects : Action
        data class LoadWithProgress(val translationIds: List<String>) : Action
        data object HideProjectInfo : Action
        data object Logout : Action
        data object ShareApp: Action
        data object RequestUpdateLibrary : Action
    }
}
