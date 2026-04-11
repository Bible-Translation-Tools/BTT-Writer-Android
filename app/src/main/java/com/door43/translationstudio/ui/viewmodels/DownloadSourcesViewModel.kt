package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.door43.translationstudio.R
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
    val filter: String? = null
)

sealed class DownloadListItem {
    data class FilterCategory(
        val id: String,
        val title: String,
        val icon: ImageVector? = null
    ) : DownloadListItem()

    data class SourceSelection(
        val id: String,
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
    val selectedSlugs: Set<String> = emptySet(),
    val downloadedSlugs: Set<String> = emptySet(),
    val selectAllChecked: Boolean = false,
    val isDownloadEnabled: Boolean = false
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

    private var rawResult: GetAvailableSources.Result? = null
    private val downloadErrors = mutableMapOf<String, String?>()

    init {
        loadAvailableSources()
        setFilterMode(FilterMode.ByLanguage)
    }

    fun onAction(action: DownloadAction) {
        when (action) {
            is DownloadAction.FilterModeChanged -> setFilterMode(action.mode)
            is DownloadAction.Search -> _state.update {
                it.copy(searchQuery = action.query)
            }
            is DownloadAction.ToggleSelection -> toggleSelection(action.id)
            is DownloadAction.NavigateForward -> onNavigateForward(action.item)
            is DownloadAction.NavigateStep -> onNavigateStep(action.index)
            is DownloadAction.SelectAll -> setSelectAll(action.shouldSelectAll)
            DownloadAction.NavigateBack -> onNavigateBack()
            DownloadAction.DownloadSources -> downloadSources()
        }
    }

    private fun setFilterMode(mode: FilterMode) {
        val initialStep = if (mode == FilterMode.ByLanguage) {
            FilterStep(
                selection = SelectionType.LANGUAGE,
                label = application.getString(R.string.choose_language)
            )
        } else {
            FilterStep(
                selection = SelectionType.BOOK_TYPE,
                label = application.getString(R.string.choose_category)
            )
        }
        _state.update {
            it.copy(
                filterMode = mode,
                navigationStack = listOf(initialStep),
                searchQuery = ""
            )
        }
        updateList()
    }

    private fun onNavigateForward(item: DownloadListItem) {
        val currentStack = _state.value.navigationStack.toMutableList()
        if (currentStack.isEmpty()) return

        val lastIndex = currentStack.lastIndex
        val currentStep = currentStack[lastIndex]

        when (item) {
            is DownloadListItem.FilterCategory -> {
                val resolvedStep = currentStep.copy(
                    filter = item.id,
                    label = item.title,
                )

                val nextSelectionType = getNextSelectionType(resolvedStep)
                val nextStep = FilterStep(
                    selection = nextSelectionType
                )

                currentStack[lastIndex] = resolvedStep
                currentStack.add(nextStep)

                _state.update { it.copy(navigationStack = currentStack) }
                updateList()
            }
            is DownloadListItem.SourceSelection -> {
                toggleSelection(item.id)
            }
        }
    }

    private fun onNavigateStep(index: Int) {
        val currentStack = _state.value.navigationStack

        if (index >= currentStack.size - 1) return

        val newStack = currentStack.take(index + 1).toMutableList()

        val targetIndex = newStack.lastIndex
        newStack[targetIndex] = newStack[targetIndex].copy(
            filter = null,
            label = null
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
            SelectionType.BOOK_TYPE -> getCategoryForFilter(resolvedStep.filter)

            SelectionType.OLD_TESTAMENT,
            SelectionType.NEW_TESTAMENT,
            SelectionType.OTHER_BOOK -> {
                if (stack.firstOrNull()?.selection == SelectionType.LANGUAGE) {
                    SelectionType.SOURCE_FILTERED_BY_LANGUAGE
                } else {
                    SelectionType.SOURCE_FILTERED_BY_BOOK
                }
            }

            else -> SelectionType.SOURCE_FILTERED_BY_LANGUAGE
        }
    }

    private fun getCategoryForFilter(filter: String?): SelectionType {
        return when (filter?.toIntOrNull()) {
            R.string.old_testament_label -> SelectionType.OLD_TESTAMENT
            R.string.new_testament_label -> SelectionType.NEW_TESTAMENT
            else -> SelectionType.OTHER_BOOK
        }
    }

    private fun onNavigateBack() {
        val currentStack = _state.value.navigationStack.toMutableList()
        if (currentStack.size > 1) {
            currentStack.removeAt(currentStack.lastIndex)

            val lastIndex = currentStack.lastIndex
            currentStack[lastIndex] = currentStack[lastIndex].copy(
                filter = null,
                label = null
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
            rawResult = withContext(Dispatchers.IO) {
                getAvailableSources.execute { progress, details ->
                    handle.update(progress, handle.initialMessage, details)
                }
            }
            updateList()
        }
    }

    private fun downloadSources() {
        val toDownload = _state.value.selectedSlugs.toList()
        if (toDownload.isEmpty()) return

        launchWithProgress { handle ->
            val result = withContext(Dispatchers.IO) {
                downloadResourceContainers.download(toDownload) { progress, message ->
                    handle.update(progress, message)
                }
            }
            val newlyDownloaded = result.downloadedTranslations
            _state.update { state ->
                state.copy(
                    downloadedSlugs = state.downloadedSlugs + newlyDownloaded,
                    selectedSlugs = state.selectedSlugs - newlyDownloaded.toSet()
                )
            }
            updateList()
        }
    }

    private fun updateList() {
        val result = rawResult ?: return
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
        val bookMap = when (currentStep.selection) {
            SelectionType.OLD_TESTAMENT -> result.otBooks
            SelectionType.NEW_TESTAMENT -> result.ntBooks
            else -> result.otherBooks
        }
        return bookMap.map { (bookSlug, indices) ->
            val source = result.sources.getOrNull(indices.firstOrNull() ?: -1)
            DownloadListItem.FilterCategory(
                id = bookSlug,
                title = "${source?.project?.name ?: ""} ($bookSlug)"
            )
        }.sortedBy { it.title }
    }

    private fun mapSources(
        currentStep: FilterStep,
        result: GetAvailableSources.Result
    ): List<DownloadListItem> {
        val filteredIndices = getFilteredIndices(result)
        return filteredIndices.mapNotNull { index ->
            result.sources.getOrNull(index)?.let { source ->
                val slug = source.resourceContainerSlug
                DownloadListItem.SourceSelection(
                    id = slug,
                    projectName = if (currentStep.selection == SelectionType.SOURCE_FILTERED_BY_LANGUAGE)
                        "${source.project.name} (${source.project.slug})"
                    else
                        "${source.language.name} (${source.language.slug})",
                    resourceName = "${source.resource.name} (${source.resource.slug})",
                    isSelected = _state.value.selectedSlugs.contains(slug),
                    isDownloaded = _state.value.downloadedSlugs.contains(slug),
                    errorMessage = downloadErrors[slug]
                )
            }
        }
    }

    private fun getFilteredIndices(result: GetAvailableSources.Result): List<Int> {
        val stack = _state.value.navigationStack
        var languageFilter: String? = null
        var bookFilter: String? = null

        // Extract filters from previous steps
        stack.forEach { step ->
            when (step.selection) {
                SelectionType.LANGUAGE -> languageFilter = step.filter
                SelectionType.OLD_TESTAMENT,
                SelectionType.NEW_TESTAMENT,
                SelectionType.OTHER_BOOK,
                SelectionType.BOOK_TYPE -> bookFilter = step.filter
                else -> {}
            }
        }

        // If filtering by Book, return all translations for that book
        if (bookFilter != null && stack.any { it.selection == SelectionType.SOURCE_FILTERED_BY_BOOK }) {
            return (result.otBooks[bookFilter] ?: emptyList()) +
                    (result.ntBooks[bookFilter] ?: emptyList()) +
                    (result.otherBooks[bookFilter] ?: emptyList())
        }

        // If filtering by Language, return translations for that language matching the category
        return result.byLanguage[languageFilter] ?: emptyList()
    }

    private fun toggleSelection(id: String) {
        val newSelection = _state.value.selectedSlugs.toMutableSet()
        if (newSelection.contains(id)) newSelection.remove(id) else newSelection.add(id)

        val allVisibleSelected = _state.value.listItems
            .filterIsInstance<DownloadListItem.SourceSelection>()
            .all { it.isDownloaded || newSelection.contains(it.id) }

        _state.update {
            it.copy(
                selectedSlugs = newSelection,
                selectAllChecked = allVisibleSelected,
                isDownloadEnabled = newSelection.isNotEmpty()
            )
        }
        updateList()
    }

    private fun setSelectAll(shouldSelectAll: Boolean) {
        val currentItems = _state.value.listItems
        val newSelection = _state.value.selectedSlugs.toMutableSet()

        if (shouldSelectAll) {
            currentItems.filterIsInstance<DownloadListItem.SourceSelection>()
                .filter { !it.isDownloaded }
                .forEach { newSelection.add(it.id) }
        } else {
            currentItems.filterIsInstance<DownloadListItem.SourceSelection>()
                .forEach { newSelection.remove(it.id) }
        }

        _state.update {
            it.copy(
                selectedSlugs = newSelection,
                selectAllChecked = shouldSelectAll,
                isDownloadEnabled = newSelection.isNotEmpty()
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

        val categoryMap = when (categoryResId) {
            R.string.old_testament_label -> result.otBooks
            R.string.new_testament_label -> result.ntBooks
            else -> result.otherBooks
        }

        val languageIndices = result.byLanguage[selectedLanguage] ?: return false
        return languageIndices.any { index ->
            val source = result.sources.getOrNull(index)
            categoryMap.containsKey(source?.project?.slug)
        }
    }
}