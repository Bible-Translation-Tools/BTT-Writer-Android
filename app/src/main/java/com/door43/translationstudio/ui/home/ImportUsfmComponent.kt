package com.door43.translationstudio.ui.home

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.App.Companion.deviceLanguageCode
import com.door43.translationstudio.R
import com.door43.translationstudio.core.MergeConflictsHandler
import com.door43.translationstudio.core.MissingNameItem
import com.door43.translationstudio.core.ProcessUSFM
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.Progress
import com.door43.translationstudio.core.ProgressManager
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TaskHandle
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.launchWithProgress
import com.door43.translationstudio.ui.navigation.ComponentScope
import com.door43.usecases.ImportProjects
import com.door43.util.FileUtilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

interface ImportUsfmComponent {

    val state: StateFlow<State>
    val progress: StateFlow<Progress?>

    fun languageSelected(language: TargetLanguage)
    fun bookSelected(projectId: String)
    fun categorySelected(categoryId: Long)
    fun search(query: String)
    fun navigateBack()
    fun skipBook()
    fun confirmImport()
    fun mergeImport(overwrite: Boolean)
    fun onProjectsImported(translationIds: List<String>)

    data class State(
        val step: UsfmStep = UsfmStep.LANGUAGE,
        val uri: Uri? = null,
        val targetLanguage: TargetLanguage? = null,
        val processedResult: String = "",
        val infoMessage: Pair<String, String>? = null,
        val importSuccess: Boolean = false,
        val importedTranslationIds: List<String> = emptyList(),
        val currentMissingItem: MissingNameItem? = null,
        val currentMissingDescription: String = "",
        val missingNamePrompt: String? = null,
        val existentTranslations: List<TargetTranslation> = emptyList(),
        val languages: List<TargetLanguage> = emptyList(),
        val filteredLanguages: List<TargetLanguage> = emptyList(),
        val categories: List<CategoryEntry> = emptyList(),
        val filteredCategories: List<CategoryEntry> = emptyList(),
        val categoryStack: List<Long> = listOf(0L),
        val started: Boolean = false
    )

    sealed interface Result {
        data class ProjectsImported(val translationIds: List<String>) : Result
        data class MergeConflict(val translationId: String) : Result
    }
}

class DefaultImportUsfmComponent(
    componentContext: ComponentContext,
    fileUri: String,
    private val onResult: (ImportUsfmComponent.Result) -> Unit
) : ImportUsfmComponent,
    ComponentContext by componentContext,
    KoinComponent, ComponentScope, ProgressOwner {

    private val application: Application by inject()
    private val translator: Translator by inject()
    private val importProjects: ImportProjects by inject()
    private val library: Door43Client by inject()
    private val directoryProvider: IDirectoryProvider by inject()
    private val assetsProvider: AssetsProvider by inject()
    private val profile: Profile by inject()

    override val coroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val progressManager = ProgressManager(coroutineScope)
    override val progress get() = progressManager.progress

    private val _state = MutableStateFlow(ImportUsfmComponent.State())
    override val state: StateFlow<ImportUsfmComponent.State> = _state.asStateFlow()

    private var processUSFM: ProcessUSFM? = null
    private var missingNameCounter = 0

    init {
        startImport(fileUri.toUri())

        lifecycle.doOnDestroy {
            coroutineScope.cancel()
        }
    }

    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        progressManager.runTask(message, block)
    }

    override fun languageSelected(language: TargetLanguage) {
        processFile(language)
    }

    override fun bookSelected(projectId: String) {
        setBook(projectId)
    }

    override fun categorySelected(categoryId: Long) {
        navigateToCategory(categoryId)
    }

    override fun search(query: String) {
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

    override fun navigateBack() {
        val stack = _state.value.categoryStack
        if (stack.size <= 1) return
        val parentId = stack[stack.size - 2]
        val categories = library.index.getProjectCategories(
            parentId, deviceLanguageCode, "all"
        )
        _state.update {
            it.copy(
                categories = categories,
                filteredCategories = categories,
                categoryStack = stack.dropLast(1)
            )
        }
    }

    override fun skipBook() {
        promptNextName()
    }

    override fun confirmImport() {
        doImport(false)
    }

    override fun mergeImport(overwrite: Boolean) {
        doImport(overwrite)
    }

    override fun onProjectsImported(translationIds: List<String>) {
        processUSFM?.cleanup()
        onResult(ImportUsfmComponent.Result.ProjectsImported(translationIds))
    }

    private fun startImport(uri: Uri) {
        if (_state.value.started) return
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
                    ImportUsfmComponent.State(
                        started = true,
                        uri = uri,
                        step = UsfmStep.LANGUAGE,
                        languages = languages,
                        filteredLanguages = languages
                    )
                }
            }
        } else {
            val title = application.getString(R.string.title_import_usfm_error)
            val message = "${application.getString(R.string.invalid_file)}\n$filename"
            _state.update {
                it.copy(
                    started = true,
                    step = UsfmStep.DONE,
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

        val existentTranslations = checkExistentTranslations()

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
                existentTranslations = existentTranslations
            )
        }
    }

    private fun checkExistentTranslations(): List<TargetTranslation> {
        val imports = processUSFM?.importProjects ?: return emptyList()
        val translations = arrayListOf<TargetTranslation>()

        for (file in imports) {
            val existent = translator.getConflictingTargetTranslation(file)
            if (existent != null) translations.add(existent)
        }
        return translations
    }

    private fun doImport(overwrite: Boolean) {
        val usfm = processUSFM ?: return

        launchWithProgress(application.getString(R.string.importing_usfm)) { handle ->
            val result = withContext(Dispatchers.IO) {
                importProjects.importProjects(usfm.importProjects, overwrite) { progress, message ->
                    handle.update(progress, message)
                }
            }

            // Show merge conflict if there is a single one
            result.conflictingTargetTranslations.singleOrNull()?.let {
                val hasConflicts = MergeConflictsHandler.isTranslationMergeConflicted(
                    it.id,
                    translator
                )
                if (hasConflicts) {
                    processUSFM?.cleanup()
                    onResult(ImportUsfmComponent.Result.MergeConflict(it.id))
                    return@launchWithProgress
                }
            }

            _state.update {
                it.copy(
                    step = UsfmStep.DONE,
                    importSuccess = result.success,
                    importedTranslationIds = result.targetTranslations.map { t -> t.id }
                )
            }
        }
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
}