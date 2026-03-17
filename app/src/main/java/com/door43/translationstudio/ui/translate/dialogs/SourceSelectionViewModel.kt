package com.door43.translationstudio.ui.translate.dialogs

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import com.door43.translationstudio.core.ContainerCache
import com.door43.translationstudio.core.ProgressManager
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TaskHandle
import com.door43.translationstudio.ui.launchWithProgress
import com.door43.usecases.DownloadResourceContainers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.Translation

const val MAX_SOURCE_ITEMS = 3

data class SourceTabItem(
    val tag: String,
    val title: String,
    val language: String?,
    val direction: String?
)

data class RCItem(
    val title: String,
    val sourceTranslation: Translation?,
    val selected: Boolean,
    val downloaded: Boolean,
    val hasUpdates: Boolean = false,
    val checkedUpdates: Boolean = false
) {
    val containerSlug: String? = sourceTranslation?.resourceContainerSlug
}

data class SourceState(
    val sources: List<RCItem> = emptyList(),
    val snackBarMessage: String? = null
)

sealed interface SourceAction {
    object LoadSources : SourceAction
    object ClearSnackBar : SourceAction
    data class ToggleSelection(val source: RCItem) : SourceAction
    data class DownloadSource(val source: RCItem) : SourceAction
    data class DeleteSource(val source: RCItem) : SourceAction
}

class SourceSelectionViewModel(
    private val library: Door43Client,
    private val prefRepository: IPreferenceRepository,
    private val downloadResourceContainers: DownloadResourceContainers,
    private val targetTranslation: TargetTranslation
) : ViewModel(), KoinComponent, ProgressOwner {

    private val application: Application by inject()

    private val progressManager = ProgressManager(viewModelScope)
    override val progress get() = progressManager.progress

    private val _state = MutableStateFlow(SourceState())
    val state: StateFlow<SourceState> = _state.asStateFlow()

    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        progressManager.runTask(message, block)
    }

    fun onAction(action: SourceAction) {
        when (action) {
            is SourceAction.LoadSources -> loadAvailableSources()
            is SourceAction.ClearSnackBar -> clearSnackBar()
            is SourceAction.ToggleSelection -> toggleSourceSelection(action.source)
            is SourceAction.DownloadSource -> downloadSource(action.source)
            is SourceAction.DeleteSource -> deleteSource(action.source)
        }
    }

    private fun clearSnackBar() {
        _state.value = _state.value.copy(snackBarMessage = null)
    }

    private fun loadAvailableSources() {
        launchWithProgress { handle ->
            val rcItems = withContext(Dispatchers.IO) {
                val items = arrayListOf<RCItem>()
                // add selected source translations
                val sourceTranslationSlugs = getOpenSources()
                for (slug in sourceTranslationSlugs) {
                    val st = library.index.getTranslation(slug)
                    if (st != null) {
                        handle.update(-1f, st.resourceContainerSlug)
                        items.add(addSource(st, true))
                    }
                }

                val availableTranslations = library.index.findTranslations(
                    null,
                    targetTranslation.projectId,
                    null,
                    "book",
                    null,
                    App.MIN_CHECKING_LEVEL,
                    -1
                )
                for (sourceTranslation in availableTranslations) {
                    handle.update(-1f, sourceTranslation.resourceContainerSlug)
                    if (!items.map { it.containerSlug }.contains(sourceTranslation.resourceContainerSlug)) {
                        items.add(addSource(sourceTranslation, false))
                    }
                }
                items
            }
            _state.update {
                it.copy(sources = rcItems)
            }
        }
    }

    private fun getOpenSources(): List<String> {
        return prefRepository.getOpenSourceTranslations(targetTranslation.id)
    }

    private fun toggleSourceSelection(source: RCItem) {
        val stackFull = state.value.sources.filter { it.selected }.size == MAX_SOURCE_ITEMS

        if (!stackFull || source.selected) {
            _state.update { state ->
                state.copy(
                    sources = state.sources.map {
                        if (it.containerSlug == source.containerSlug) {
                            it.copy(selected = !it.selected)
                        } else {
                            it
                        }
                    }
                )
            }
        }
    }

    private fun downloadSource(source: RCItem) {
        val translation = source.sourceTranslation ?: return

        launchWithProgress { handle ->
            val result = withContext(Dispatchers.IO) {
                downloadResourceContainers.download(translation) { progress, message ->
                    handle.update(progress, message)
                }
            }

            for (rc in result.containers) {
                // reset cached containers that were downloaded
                ContainerCache.remove(rc.slug)
            }

            val snackBarMessage = if (result.success) {
                application.getString(R.string.download_complete)
            } else {
                application.getString(R.string.download_failed)
            }

            _state.update { it.copy(snackBarMessage = snackBarMessage) }

            loadAvailableSources()
        }
    }

    private fun deleteSource(source: RCItem) {
        viewModelScope.launch {
            source.containerSlug?.let {
                library.delete(it)
                loadAvailableSources()
            }
        }
    }

    private fun addSource(sourceTranslation: Translation, selected: Boolean): RCItem {
        val title = sourceTranslation.language.name + " (" + sourceTranslation.language.slug + ") - " + sourceTranslation.resource.name
        val isDownloaded = library.exists(sourceTranslation.resourceContainerSlug)
        var hasUpdates = false
        var checkedUpdates = false

        if (selected) { // see if there are updates available to download
            Log.i(
                this::class.java.simpleName,
                "Checking for updates on " + sourceTranslation.resourceContainerSlug
            )
            try {
                ContainerCache.cache(library, sourceTranslation.resourceContainerSlug)?.let { container ->
                    val lastModified: Int = library.getResourceContainerLastModified(
                        container.language.slug,
                        container.project.slug,
                        container.resource.slug
                    )
                    hasUpdates = (lastModified > container.modifiedAt)
                    Log.i(
                        this::class.java.simpleName,
                        "Checking for updates on " + sourceTranslation.resourceContainerSlug + " finished, needs updates: " + hasUpdates
                    )
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }

            checkedUpdates = true
        }

        return RCItem(
            title = title,
            sourceTranslation = sourceTranslation,
            selected = selected,
            downloaded = isDownloaded,
            hasUpdates = hasUpdates,
            checkedUpdates = checkedUpdates
        )
    }
}