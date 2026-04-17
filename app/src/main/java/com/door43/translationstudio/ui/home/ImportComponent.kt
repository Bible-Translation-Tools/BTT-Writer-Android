package com.door43.translationstudio.ui.home

import android.net.Uri
import com.door43.translationstudio.core.Progress
import com.door43.translationstudio.core.TargetTranslation
import com.door43.usecases.ImportProjects
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

data class MergeConflict(
    val translation: TargetTranslation,
    val hasMergeConflict: Boolean,
    val isFromServer: Boolean,
    val onResolve: () -> Unit,
    val onOverwrite: () -> Unit,
    val onCancel: () -> Unit
)

interface ImportComponent {
    val state: StateFlow<State>
    val progress: StateFlow<Progress?>
    val event: Flow<Event>

    fun onAction(action: Action)

    data class State(
        val mergeConflict: MergeConflict? = null,
        val sourceConflict: ImportProjects.ImportSourceResult? = null,
        val resultMessage: Pair<String, String>? = null,
        val backups: List<File> = emptyList(),
        val repositories: List<RepositoryItem> = emptyList(),
        val repoToImport: RepositoryItem? = null
    )

    sealed interface Event {
        data object AuthRequested : Event
    }

    sealed interface Action {
        data class ImportProject(val uri: Uri, val overwrite: Boolean) : Action
        data class ImportSourceUri(val uri: Uri, val overwrite: Boolean) : Action
        data class ImportBackup(val backup: File) : Action
        data class SearchRepositories(val user: String, val repo: String) : Action
        data class ImportRepo(
            val repo: RepositoryItem,
            val accepted: Boolean,
            val overwrite: Boolean
        ) : Action
        data object RegisterKeys : Action
        data object ClearResult : Action
        data object ClearMergeConflict : Action
        data object ClearSourceConflict : Action
        data object ClearImportRepo : Action
        data class UsfmProjectsImported(val translationIds: List<String>) : Action
        data class UsfmMergeConflict(val translationId: String) : Action
    }

    sealed interface Result {
        data class MergeConflict(val translationId: String) : Result
        data class ProjectsImported(val translationIds: List<String>) : Result
    }
}
