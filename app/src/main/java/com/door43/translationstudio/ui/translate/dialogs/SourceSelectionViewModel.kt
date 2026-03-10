package com.door43.translationstudio.ui.translate.dialogs

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import com.door43.translationstudio.core.ContainerCache
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.usecases.DownloadResourceContainers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.Translation

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

data class SourceModel(
    val sources: List<RCItem> = emptyList(),
    val snackBarMessage: String? = null,
    val progress: ProgressHelper.Progress? = null
)

class SourceSelectionViewModel(
    private val application: Application,
    private val library: Door43Client,
    private val prefRepository: IPreferenceRepository,
    private val downloadResourceContainers: DownloadResourceContainers,
    private val targetTranslation: TargetTranslation
) : AndroidViewModel(application) {

    private val _model = MutableStateFlow(SourceModel())
    val model: StateFlow<SourceModel> = _model.asStateFlow()

    fun clearSnackBar() {
        _model.value = _model.value.copy(snackBarMessage = null)
    }

    fun loadAvailableSources() {
        viewModelScope.launch {
            _model.update { it.copy(progress = ProgressHelper.Progress()) }

            val rcItems = withContext(Dispatchers.IO) {
                val items = arrayListOf<RCItem>()
                // add selected source translations
                val sourceTranslationSlugs = getOpenSourceTranslations()
                for (slug in sourceTranslationSlugs) {
                    val st = library.index.getTranslation(slug)
                    if (st != null) {
                        _model.update {
                            it.copy(
                                progress = ProgressHelper.Progress(st.resourceContainerSlug)
                            )
                        }
                        items.add(addSourceTranslation(st, true))
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
                    _model.update {
                        it.copy(
                            progress = ProgressHelper.Progress(sourceTranslation.resourceContainerSlug)
                        )
                    }
                    if (!items.map { it.containerSlug }.contains(sourceTranslation.resourceContainerSlug)) {
                        items.add(addSourceTranslation(sourceTranslation, false))
                    }
                }
                items
            }
            _model.update {
                it.copy(progress = null, sources = rcItems)
            }
        }
    }

    fun getOpenSourceTranslations(): List<String> {
        return prefRepository.getOpenSourceTranslations(targetTranslation.id)
    }

    fun toggleSourceSelection(source: RCItem) {
        val stackFull = model.value.sources.filter { it.selected }.size == 3

        if (!stackFull || source.selected) {
            _model.update { state ->
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

    fun downloadResourceContainer(source: RCItem) {
        val translation = source.sourceTranslation ?: return

        viewModelScope.launch {
            _model.update { it.copy(progress = ProgressHelper.Progress()) }
            val result = withContext(Dispatchers.IO) {
                downloadResourceContainers.download(translation) { progress, max, message ->
                    _model.update {
                        it.copy(
                            progress = ProgressHelper.Progress(
                                message,
                                progress,
                                max
                            )
                        )
                    }
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

            _model.update { it.copy(progress = null, snackBarMessage = snackBarMessage) }

            loadAvailableSources()
        }
    }

    fun deleteResourceContainer(source: RCItem) {
        viewModelScope.launch {
            source.containerSlug?.let {
                library.delete(it)
                loadAvailableSources()
            }
        }
    }

    private fun addSourceTranslation(sourceTranslation: Translation, selected: Boolean): RCItem {
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