package com.door43.translationstudio.ui.dialogs.import

import android.net.Uri
import com.door43.translationstudio.core.Progress
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.ui.home.RepositoryItem
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

    fun importUsfm(fileUri: Uri)
    fun importProject(uri: Uri, overwrite: Boolean)
    fun importSource(uri: Uri, overwrite: Boolean)
    fun importBackup(backup: File)
    fun importRepo(repo: RepositoryItem, accepted: Boolean, overwrite: Boolean)
    fun searchRepositories(user: String, repo: String)
    fun registerKeys()
    fun clearResult()
    fun clearMergeConflict()
    fun clearSourceConflict()
    fun clearImportRepo()

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

    sealed interface Result {
        data class MergeConflict(val translationId: String) : Result
        data class ProjectsImported(val translationIds: List<String>) : Result
        data class OpenUsfmImport(val fileUri: String) : Result
    }
}
