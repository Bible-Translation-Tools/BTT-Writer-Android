package com.door43.translationstudio.ui.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.App.Companion.deviceLanguageCode
import com.door43.translationstudio.R
import com.door43.translationstudio.core.MergeConflictsHandler
import com.door43.translationstudio.core.MissingNameItem
import com.door43.translationstudio.core.ProcessUSFM
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.ProgressManager
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TaskHandle
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.launchWithProgress
import com.door43.usecases.ImportProjects
import com.door43.util.FileUtilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.CategoryEntry
import org.unfoldingword.door43client.models.TargetLanguage
import java.util.Locale

enum class UsfmStep {
    LANGUAGE,
    PROMPT_BOOK_NAME,
    PROCESSED,
    DONE
}

data class UsfmImportState(
    val step: UsfmStep = UsfmStep.LANGUAGE,
    val uri: Uri? = null,
    val targetLanguage: TargetLanguage? = null,
    val processedResult: String = "",
    val infoMessage: Pair<String, String>? = null,
    val importSuccess: Boolean = false,
    val currentMissingItem: MissingNameItem? = null,
    val currentMissingDescription: String = "",
    val missingNamePrompt: String? = null,
    val hasMergeConflict: Boolean = false,
    val conflictingTranslationId: String? = null,
    val languages: List<TargetLanguage> = emptyList(),
    val filteredLanguages: List<TargetLanguage> = emptyList(),
    val categories: List<CategoryEntry> = emptyList(),
    val filteredCategories: List<CategoryEntry> = emptyList(),
    val categoryStack: List<Long> = listOf(0L),
    val active: Boolean = false
)

sealed interface UsfmAction {
    data class LanguageSelected(val language: TargetLanguage) : UsfmAction
    data class BookSelected(val projectId: String) : UsfmAction
    data class Search(val query: String) : UsfmAction
    data class CategorySelected(val categoryId: Long) : UsfmAction
    data object SkipBook : UsfmAction
    data object ConfirmImport : UsfmAction
    data class MergeImport(val overwrite: Boolean) : UsfmAction
    data object Dismiss : UsfmAction
    data object Finish : UsfmAction
}

sealed interface UsfmEvent {
    data object ProjectImported : UsfmEvent
    data class ResolveMergeConflict(val translationId: String) : UsfmEvent
}

class UsfmImportViewModel(
    private val translator: Translator,
    private val importProjects: ImportProjects,
    private val library: Door43Client,
    private val directoryProvider: IDirectoryProvider,
    private val assetsProvider: AssetsProvider,
    private val profile: Profile
) : ViewModel(), KoinComponent, ProgressOwner {

    private val application: Application by inject()

    private val progressManager = ProgressManager(viewModelScope)
    override val progress get() = progressManager.progress

    private val _state = MutableStateFlow(UsfmImportState())
    val state: StateFlow<UsfmImportState> = _state.asStateFlow()

    private val _event = Channel<UsfmEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    private var processUSFM: ProcessUSFM? = null
    private var missingNameCounter = 0

    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        progressManager.runTask(message, block)
    }

    fun onAction(action: UsfmAction) {
        when (action) {
            is UsfmAction.LanguageSelected -> processFile(action.language)
            is UsfmAction.BookSelected -> setBook(action.projectId)
            is UsfmAction.Search -> search(action.query)
            is UsfmAction.CategorySelected -> navigateToCategory(action.categoryId)
            UsfmAction.SkipBook -> promptNextName()
            UsfmAction.ConfirmImport -> doImport(false)
            is UsfmAction.MergeImport -> doImport(action.overwrite)
            UsfmAction.Dismiss -> cleanup()
            UsfmAction.Finish -> {
                _event.trySend(UsfmEvent.ProjectImported)
                cleanup()
            }
        }
    }

    fun startImport(uri: Uri) {
        val filename = FileUtilities.getFileName(application, uri)
        val isUsfm = filename.contains(Translator.USFM_EXTENSION, ignoreCase = true)
        val isTxt = filename.contains(Translator.TXT_EXTENSION, ignoreCase = true)
        val isZip = filename.contains(Translator.ZIP_EXTENSION, ignoreCase = true)
        if (isUsfm || isTxt || isZip) {
            launchWithProgress {
                val languages = withContext(Dispatchers.IO) {
                    library.index.getTargetLanguages().sorted()
                }
                _state.update {
                    UsfmImportState(
                        step = UsfmStep.LANGUAGE,
                        uri = uri,
                        languages = languages,
                        filteredLanguages = languages,
                        active = true
                    )
                }
            }
        } else {
            val title = application.getString(R.string.title_import_usfm_error)
            val message = "${application.getString(R.string.invalid_file)}\n$filename"
            _state.update {
                it.copy(
                    active = true,
                    infoMessage = title to message
                )
            }
        }
    }

    private fun processFile(language: TargetLanguage) {
        val uri = _state.value.uri ?: return
        _state.update { it.copy(targetLanguage = language) }

        launchWithProgress(application.getString(R.string.reading_usfm)) { handle ->
            val usfm = withContext(Dispatchers.IO) {
                ProcessUSFM.Builder(
                    application,
                    directoryProvider,
                    profile,
                    library,
                    assetsProvider
                )
                    .fromUri(language, uri) { progress, message ->
                        handle.update(progress, message)
                    }
                    .build()
            }

            processUSFM = usfm

            if (usfm != null && usfm.booksMissingNames.isNotEmpty()) {
                missingNameCounter = usfm.booksMissingNames.size
                promptNextName()
            } else {
                showResults()
            }
        }
    }

    private fun promptNextName() {
        val usfm = processUSFM ?: return
        if (missingNameCounter <= 0) {
            showResults()
            return
        }
        missingNameCounter--
        val item = usfm.booksMissingNames[missingNameCounter]
        val description = usfm.getShortFilePath(item.description ?: "")

        val prompt = if (item.invalidName != null) {
            application.getString(R.string.invalid_book_name_prompt, description, item.invalidName)
        } else {
            application.getString(R.string.missing_book_name_prompt, description)
        }

        val categories = library.index.getProjectCategories(
            0L, deviceLanguageCode, "all"
        )
        _state.update {
            it.copy(
                step = UsfmStep.PROMPT_BOOK_NAME,
                currentMissingItem = item,
                currentMissingDescription = description,
                missingNamePrompt = prompt,
                categories = categories,
                filteredCategories = categories,
                categoryStack = listOf(0L)
            )
        }
    }

    private fun setBook(projectId: String) {
        val usfm = processUSFM ?: return
        val item = _state.value.currentMissingItem ?: return
        if (item.contents != null && item.description != null) {
            launchWithProgress(application.getString(R.string.reading_usfm)) { _ ->
                withContext(Dispatchers.IO) {
                    usfm.processText(
                        book = item.contents,
                        name = item.description,
                        promptForName = false,
                        useName = projectId
                    )
                }
                promptNextName()
            }
        } else {
            promptNextName()
        }
    }

    private fun showResults() {
        val usfm = processUSFM ?: return
        val language = _state.value.targetLanguage ?: return
        val languageLabel = String.format(
            application.getString(R.string.selected_language),
            "${language.slug} - ${language.name}"
        )
        val results = usfm.resultsString
        val message = "$languageLabel\n$results"

        val conflicting = checkExistentTranslation()

        val infoMessage = if (!usfm.isProcessSuccess) {
            val title = application.getString(R.string.title_import_usfm_error)
            title to message
        } else null

        val step = if (usfm.isProcessSuccess) UsfmStep.PROCESSED else UsfmStep.DONE

        _state.update {
            it.copy(
                step = step,
                infoMessage = infoMessage,
                processedResult = message,
                hasMergeConflict = conflicting != null,
                conflictingTranslationId = conflicting?.id
            )
        }
    }

    private fun checkExistentTranslation(): TargetTranslation? {
        val imports = processUSFM?.importProjects ?: return null
        for (file in imports) {
            val conflicting = translator.getConflictingTargetTranslation(file)
            if (conflicting != null) return conflicting
        }
        return null
    }

    private fun doImport(overwrite: Boolean) {
        val usfm = processUSFM ?: return

        launchWithProgress(application.getString(R.string.importing_usfm)) { handle ->
            val result = withContext(Dispatchers.IO) {
                importProjects.importProjects(usfm.importProjects, overwrite) { progress, message ->
                    handle.update(progress, message)
                }
            }

            result.conflictingTargetTranslation?.let {
                val hasConflicts = MergeConflictsHandler.isTranslationMergeConflicted(
                    it.id,
                    translator
                )
                if (hasConflicts) {
                    _event.trySend(UsfmEvent.ResolveMergeConflict(it.id))
                    cleanup()
                    return@launchWithProgress
                }
            }

            _state.update {
                it.copy(
                    step = UsfmStep.DONE,
                    importSuccess = result.success
                )
            }
        }
    }

    private fun search(query: String) {
        val languages = _state.value.languages
        if (query.isEmpty()) {
            _state.update { it.copy(filteredLanguages = languages) }
            return
        }
        val lowerQuery = query.lowercase(Locale.getDefault())
        val filtered = languages.filter { language ->
            language.slug.lowercase(Locale.getDefault()).startsWith(lowerQuery) ||
                    language.name.lowercase(Locale.getDefault()).contains(lowerQuery)
        }
        val sorted = filtered.sortedWith(Comparator { lhs, rhs ->
            var lhId = lhs.slug
            var rhId = rhs.slug
            if (lhId.lowercase(Locale.getDefault()).startsWith(lowerQuery)) {
                lhId = "!!$lhId"
            }
            if (rhId.lowercase(Locale.getDefault()).startsWith(lowerQuery)) {
                rhId = "!!$rhId"
            }
            if (lhs.name.lowercase(Locale.getDefault()).startsWith(lowerQuery)) {
                lhId = "!$lhId"
            }
            if (rhs.name.lowercase(Locale.getDefault()).startsWith(lowerQuery)) {
                rhId = "!$rhId"
            }
            lhId.compareTo(rhId, ignoreCase = true)
        })
        _state.update { it.copy(filteredLanguages = sorted) }
    }

    private fun navigateToCategory(categoryId: Long) {
        val categories = library.index.getProjectCategories(
            categoryId, deviceLanguageCode, "all"
        )
        _state.update {
            it.copy(
                categories = categories,
                filteredCategories = categories,
                categoryStack = it.categoryStack + categoryId
            )
        }
    }

    private fun cleanup() {
        processUSFM?.cleanup()
        processUSFM = null
        missingNameCounter = 0
        _state.update { UsfmImportState() }
    }
}
