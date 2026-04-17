package com.door43.translationstudio.ui.home

import android.net.Uri
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.value.Value
import com.door43.translationstudio.core.Progress
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.ui.dialogs.FeedbackComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
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
    }

    sealed interface DialogChild {
        data class Feedback(val component: FeedbackComponent) : DialogChild
        data class Import(val component: ImportComponent) : DialogChild
        data class UpdateLibrary(val component: UpdateLibraryComponent) : DialogChild
        data class ImportUsfm(val component: ImportUsfmComponent) : DialogChild
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
