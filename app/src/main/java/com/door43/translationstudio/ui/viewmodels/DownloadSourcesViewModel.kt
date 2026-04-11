package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.door43.translationstudio.R
import com.door43.translationstudio.core.BibleCodes
import com.door43.translationstudio.core.ProgressManager
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.TaskHandle
import com.door43.translationstudio.ui.launchWithProgress
import com.door43.usecases.DownloadResourceContainers
import com.door43.usecases.GetAvailableSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

enum class FilterMode { ByLanguage, ByBook }

enum class SelectionType {
    LANGUAGE,
    OLD_TESTAMENT,
    NEW_TESTAMENT,
    OTHER_BOOK,
    BOOK_TYPE,
    SOURCE_FILTERED_BY_LANGUAGE,
    SOURCE_FILTERED_BY_BOOK;

    val isDownloadable: Boolean
        get() = when (this) {
            SOURCE_FILTERED_BY_LANGUAGE,
            SOURCE_FILTERED_BY_BOOK -> true
            else -> false
        }
}

data class FilterStep(
    val selection: SelectionType,
    val label: String? = null,
    val filter: String? = null,
    val promptLabel: String? = null
)

sealed class DownloadListItem {
    data class FilterCategory(
        val id: String,
        val title: String,
        val icon: ImageVector? = null
    ) : DownloadListItem()

    data class SourceSelection(
        val id: String,
        val projectSlug: String,
        val projectName: String,
        val resourceName: String,
        val isSelected: Boolean,
        val isDownloaded: Boolean,
        val errorMessage: String? = null
    ) : DownloadListItem()
}

data class DownloadSourcesState(
    val filterMode: FilterMode = FilterMode.ByLanguage,
    val navigationStack: List<FilterStep> = emptyList(),
    val searchQuery: String = "",
    val listItems: List<DownloadListItem> = emptyList(),
    val selectedSources: Set<String> = emptySet(),
    val downloadedSources: Set<String> = emptySet(),
    val selectAllChecked: Boolean = false
)

sealed interface DownloadAction {
    data class FilterModeChanged(val mode: FilterMode) : DownloadAction
    data class Search(val query: String) : DownloadAction
    data class NavigateForward(val item: DownloadListItem.FilterCategory) : DownloadAction
    data class NavigateStep(val index: Int) : DownloadAction
    data class SelectAll(val shouldSelectAll: Boolean) : DownloadAction
    data class ToggleSelection(val id: String) : DownloadAction
    object DownloadSources : DownloadAction
    object NavigateBack : DownloadAction
    object ClearState : DownloadAction
    object Initialize : DownloadAction
}

class DownloadSourcesViewModel(
    private val getAvailableSources: GetAvailableSources,
    private val downloadResourceContainers: DownloadResourceContainers
) : ViewModel(), KoinComponent, ProgressOwner {

    private val application: Application by inject()

    private val progressManager = ProgressManager(viewModelScope)
    override val progress get() = progressManager.progress

    private val _state = MutableStateFlow(DownloadSourcesState())
    val state = _state.asStateFlow()

    private var availableSources: GetAvailableSources.Result? = null
    private var initialized = false
    private val downloadErrors = mutableMapOf<String, String?>()

    fun onAction(action: DownloadAction) {
        when (action) {
            DownloadAction.Initialize -> initialize()
            is DownloadAction.FilterModeChanged -> setFilterMode(action.mode)
            is DownloadAction.Search -> {
                _state.update { it.copy(searchQuery = action.query) }
                updateList()
            }
            is DownloadAction.ToggleSelection -> toggleSelection(action.id)
            is DownloadAction.NavigateForward -> onNavigateForward(action.item)
            is DownloadAction.NavigateStep -> onNavigateStep(action.index)
            is DownloadAction.SelectAll -> setSelectAll(action.shouldSelectAll)
            DownloadAction.NavigateBack -> onNavigateBack()
            DownloadAction.DownloadSources -> downloadSources()
            DownloadAction.ClearState -> resetState()
        }
    }

    private fun setFilterMode(mode: FilterMode) {
        val initialStep = if (mode == FilterMode.ByLanguage) {
            val prompt = application.getString(R.string.choose_language)
            FilterStep(
                selection = SelectionType.LANGUAGE,
                label = prompt,
                promptLabel = prompt
            )
        } else {
            val prompt = application.getString(R.string.choose_category)
            FilterStep(
                selection = SelectionType.BOOK_TYPE,
                label = prompt,
                promptLabel = prompt
            )
        }
        _state.update {
            it.copy(
                filterMode = mode,
                navigationStack = listOf(initialStep),
                selectedSources = emptySet(),
                searchQuery = ""
            )
        }
        updateList()
    }

    private fun onNavigateForward(item: DownloadListItem.FilterCategory) {
        val currentStack = _state.value.navigationStack.toMutableList()
        if (currentStack.isEmpty()) return

        val lastIndex = currentStack.lastIndex
        val resolvedStep = currentStack[lastIndex].copy(
            filter = item.id,
            label = item.title,
        )

        val nextSelectionType = getNextSelectionType(resolvedStep)
        val nextPrompt = getPromptForSelectionType(nextSelectionType)

        currentStack[lastIndex] = resolvedStep
        currentStack.add(FilterStep(
            selection = nextSelectionType,
            label = nextPrompt,
            promptLabel = nextPrompt
        ))

        _state.update { it.copy(navigationStack = currentStack) }
        updateList()
    }

    private fun onNavigateStep(index: Int) {
        val currentStack = _state.value.navigationStack

        if (index >= currentStack.size - 1) return

        val newStack = currentStack.take(index + 1).toMutableList()

        val targetIndex = newStack.lastIndex
        val targetStep = newStack[targetIndex]
        newStack[targetIndex] = targetStep.copy(
            filter = null,
            label = targetStep.promptLabel
        )

        _state.update {
            it.copy(
                navigationStack = newStack,
                searchQuery = ""
            )
        }

        updateList()
    }

    private fun getNextSelectionType(resolvedStep: FilterStep): SelectionType {
        val stack = _state.value.navigationStack

        return when (resolvedStep.selection) {
            SelectionType.LANGUAGE -> SelectionType.BOOK_TYPE
            SelectionType.BOOK_TYPE -> {
                // "By Language" path: Language → Category → Sources (skip book selection)
                // "By Book" path: Category → Book(OT/NT/Other)
                if (stack.any { it.selection == SelectionType.LANGUAGE }) {
                    SelectionType.SOURCE_FILTERED_BY_LANGUAGE
                } else {
                    getCategoryForFilter(resolvedStep.filter)
                }
            }

            SelectionType.OLD_TESTAMENT,
            SelectionType.NEW_TESTAMENT,
            SelectionType.OTHER_BOOK -> SelectionType.SOURCE_FILTERED_BY_BOOK

            else -> SelectionType.SOURCE_FILTERED_BY_LANGUAGE
        }
    }

    private fun getPromptForSelectionType(type: SelectionType): String {
        return when (type) {
            SelectionType.LANGUAGE -> application.getString(R.string.choose_language)
            SelectionType.BOOK_TYPE -> application.getString(R.string.choose_category)
            SelectionType.OLD_TESTAMENT,
            SelectionType.NEW_TESTAMENT,
            SelectionType.OTHER_BOOK -> application.getString(R.string.choose_book)
            SelectionType.SOURCE_FILTERED_BY_LANGUAGE,
            SelectionType.SOURCE_FILTERED_BY_BOOK -> application.getString(R.string.choose_sources)
        }
    }

    private fun getCategoryForFilter(filter: String?): SelectionType {
        return getCategoryForFilter(filter?.toIntOrNull())
    }

    private fun getCategoryForFilter(resId: Int?): SelectionType {
        return when (resId) {
            R.string.old_testament_label -> SelectionType.OLD_TESTAMENT
            R.string.new_testament_label -> SelectionType.NEW_TESTAMENT
            else -> SelectionType.OTHER_BOOK
        }
    }

    private fun getCategoryBooks(
        category: SelectionType,
        result: GetAvailableSources.Result
    ): Map<String, List<Int>> {
        return when (category) {
            SelectionType.OLD_TESTAMENT -> result.otBooks
            SelectionType.NEW_TESTAMENT -> result.ntBooks
            else -> result.otherBooks
        }
    }

    private fun getBiblicalOrderMap(category: SelectionType): Map<String, Int>? {
        val books = when (category) {
            SelectionType.OLD_TESTAMENT -> BibleCodes.getOtBooks()
            SelectionType.NEW_TESTAMENT -> BibleCodes.getNtBooks()
            else -> return null
        }
        return books.withIndex().associate { (i, slug) -> slug to i }
    }

    private fun onNavigateBack() {
        val currentStack = _state.value.navigationStack.toMutableList()
        if (currentStack.size > 1) {
            currentStack.removeAt(currentStack.lastIndex)

            val lastIndex = currentStack.lastIndex
            val lastStep = currentStack[lastIndex]
            currentStack[lastIndex] = lastStep.copy(
                filter = null,
                label = lastStep.promptLabel
            )

            _state.update { it.copy(navigationStack = currentStack) }
            updateList()
        }
    }

    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        progressManager.runTask(message, block)
    }

    private fun loadAvailableSources() {
        launchWithProgress(
            application.getString(R.string.loading_sources)
        ) { handle ->
            availableSources = withContext(Dispatchers.IO) {
                getAvailableSources.execute { progress, details ->
                    handle.update(progress, handle.initialMessage, details)
                }
            }
            updateList()
        }
    }

    private fun downloadSources() {
        val toDownload = _state.value.selectedSources.toList()
        if (toDownload.isEmpty()) return

        launchWithProgress { handle ->
            val result = withContext(Dispatchers.IO) {
                downloadResourceContainers.download(toDownload) { progress, message ->
                    handle.update(progress, message)
                }
            }
            val newlyDownloaded = result.downloadedTranslations

            result.failedSourceDownloads.forEach { slug ->
                downloadErrors[slug] = result.failureMessages[slug]
            }

            _state.update { state ->
                state.copy(
                    downloadedSources = state.downloadedSources + newlyDownloaded,
                    selectedSources = state.selectedSources - newlyDownloaded.toSet()
                )
            }
            updateList()
        }
    }

    private fun updateList() {
        val result = availableSources ?: return
        val currentStep = _state.value.navigationStack.last()

        val items = when (currentStep.selection) {
            SelectionType.LANGUAGE -> mapLanguages(result)
            SelectionType.BOOK_TYPE -> mapBookTypes(result)
            SelectionType.OLD_TESTAMENT,
            SelectionType.NEW_TESTAMENT,
            SelectionType.OTHER_BOOK -> mapBooks(currentStep, result)
            SelectionType.SOURCE_FILTERED_BY_LANGUAGE,
            SelectionType.SOURCE_FILTERED_BY_BOOK -> mapSources(currentStep, result)
        }

        _state.update { it.copy(listItems = items) }
    }

    private fun mapLanguages(result: GetAvailableSources.Result): List<DownloadListItem> {
        val searchQuery = _state.value.searchQuery

        return result.byLanguage.mapNotNull { (slug, indices) ->
            val source = result.sources.getOrNull(indices.firstOrNull() ?: -1)
            source?.let {
                DownloadListItem.FilterCategory(
                    id = slug,
                    title = "${it.language.name} ($slug)"
                )
            }
        }.filter {
            searchQuery.isEmpty() || it.title.contains(searchQuery, ignoreCase = true)
        }
    }

    private fun mapBookTypes(result: GetAvailableSources.Result): List<DownloadListItem> {
        return listOf(
            R.string.old_testament_label to Icons.AutoMirrored.Filled.LibraryBooks,
            R.string.new_testament_label to Icons.AutoMirrored.Filled.LibraryBooks,
            R.string.other_label to Icons.Default.LocalLibrary
        ).mapNotNull { (resId, icon) ->
            val categoryName = application.getString(resId)
            // Only show category if it contains the previously filtered language
            if (isLanguageInCategory(resId, result)) {
                DownloadListItem.FilterCategory(
                    id = resId.toString(),
                    title = categoryName,
                    icon = icon
                )
            } else null
        }
    }

    private fun mapBooks(
        currentStep: FilterStep,
        result: GetAvailableSources.Result
    ): List<DownloadListItem> {
        val bookMap = getCategoryBooks(currentStep.selection, result)

        val items = bookMap.mapNotNull { (bookSlug, indices) ->
            if (indices.isEmpty()) return@mapNotNull null
            // Prefer English name, fall back to first available
            val source = indices
                .mapNotNull { result.sources.getOrNull(it) }
                .firstOrNull { it.language.slug == "en" }
                ?: result.sources.getOrNull(indices.first())
            source?.let {
                DownloadListItem.FilterCategory(
                    id = bookSlug,
                    title = "${it.project.name} ($bookSlug)"
                )
            }
        }

        val orderMap = getBiblicalOrderMap(currentStep.selection)
        return if (orderMap != null) {
            items.sortedBy { orderMap[it.id] ?: Int.MAX_VALUE }
        } else {
            items.sortedBy { it.title }
        }
    }

    private fun mapSources(
        currentStep: FilterStep,
        result: GetAvailableSources.Result
    ): List<DownloadListItem> {
        val state = _state.value
        val byLanguage = currentStep.selection == SelectionType.SOURCE_FILTERED_BY_LANGUAGE

        val items = getFilteredIndices(result).mapNotNull { index ->
            result.sources.getOrNull(index)?.let { source ->
                val slug = source.resourceContainerSlug
                DownloadListItem.SourceSelection(
                    id = slug,
                    projectSlug = source.project.slug,
                    projectName = if (byLanguage)
                        "${source.project.name} (${source.project.slug})"
                    else
                        "${source.language.name} (${source.language.slug})",
                    resourceName = "${source.resource.name} (${source.resource.slug})",
                    isSelected = state.selectedSources.contains(slug),
                    isDownloaded = state.downloadedSources.contains(slug),
                    errorMessage = downloadErrors[slug]
                )
            }
        }

        if (byLanguage) {
            val categoryFilter = state.navigationStack
                .firstOrNull { it.selection == SelectionType.BOOK_TYPE }?.filter
            val orderMap = getBiblicalOrderMap(getCategoryForFilter(categoryFilter))
            if (orderMap != null) {
                return items.sortedBy { orderMap[it.projectSlug] ?: Int.MAX_VALUE }
            }
        }

        return items.sortedBy { it.id }
    }

    private fun getFilteredIndices(result: GetAvailableSources.Result): List<Int> {
        val stack = _state.value.navigationStack
        var languageFilter: String? = null
        var bookFilter: String? = null
        var categoryFilter: String? = null

        stack.forEach { step ->
            when (step.selection) {
                SelectionType.LANGUAGE -> languageFilter = step.filter
                SelectionType.BOOK_TYPE -> categoryFilter = step.filter
                SelectionType.OLD_TESTAMENT,
                SelectionType.NEW_TESTAMENT,
                SelectionType.OTHER_BOOK -> bookFilter = step.filter
                else -> {}
            }
        }

        // "By Book" path: all translations for that specific book
        if (bookFilter != null && stack.any { it.selection == SelectionType.SOURCE_FILTERED_BY_BOOK }) {
            return (result.otBooks[bookFilter] ?: emptyList()) +
                    (result.ntBooks[bookFilter] ?: emptyList()) +
                    (result.otherBooks[bookFilter] ?: emptyList())
        }

        // "By Language" path: filter by language AND category
        val languageIndices = result.byLanguage[languageFilter] ?: return emptyList()
        val categoryBooks = getCategoryBooks(getCategoryForFilter(categoryFilter), result)

        return languageIndices.filter { index ->
            val source = result.sources.getOrNull(index)
            source != null && categoryBooks.containsKey(source.project.slug)
        }
    }

    private fun toggleSelection(id: String) {
        val newSelection = _state.value.selectedSources.toMutableSet()
        if (newSelection.contains(id)) newSelection.remove(id) else newSelection.add(id)

        val allVisibleSelected = _state.value.listItems
            .filterIsInstance<DownloadListItem.SourceSelection>()
            .all { it.isDownloaded || newSelection.contains(it.id) }

        _state.update {
            it.copy(
                selectedSources = newSelection,
                selectAllChecked = allVisibleSelected
            )
        }
        updateList()
    }

    private fun setSelectAll(shouldSelectAll: Boolean) {
        val currentItems = _state.value.listItems
        val newSelection = _state.value.selectedSources.toMutableSet()

        currentItems.filterIsInstance<DownloadListItem.SourceSelection>().forEach {
            if (shouldSelectAll && !it.isDownloaded) {
                newSelection.add(it.id)
            } else {
                newSelection.remove(it.id)
            }
        }

        _state.update {
            it.copy(
                selectedSources = newSelection,
                selectAllChecked = shouldSelectAll
            )
        }
        updateList()
    }

    private fun isLanguageInCategory(
        categoryResId: Int,
        result: GetAvailableSources.Result
    ): Boolean {
        val selectedLanguage = _state.value.navigationStack.firstOrNull {
            it.selection == SelectionType.LANGUAGE
        }?.filter ?: return true

        val categoryBooks = getCategoryBooks(getCategoryForFilter(categoryResId), result)
        val languageIndices = result.byLanguage[selectedLanguage] ?: return false

        return languageIndices.any { index ->
            val source = result.sources.getOrNull(index)
            categoryBooks.containsKey(source?.project?.slug)
        }
    }

    private fun initialize() {
        if (initialized) return
        initialized = true
        setFilterMode(FilterMode.ByLanguage)
        loadAvailableSources()
    }

    private fun resetState() {
        availableSources = null
        initialized = false
        downloadErrors.clear()
        _state.value = DownloadSourcesState()
    }
}