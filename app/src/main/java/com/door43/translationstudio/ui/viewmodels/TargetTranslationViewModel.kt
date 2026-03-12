package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import android.graphics.Typeface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.door43.data.AssetsProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.setDefaultPref
import com.door43.translationstudio.App.Companion.deviceLanguageCode
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Chunk
import com.door43.translationstudio.core.ContainerCache
import com.door43.translationstudio.core.ProgressManager
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.SlugSorter
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TaskHandle
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.core.entity.SourceTranslation
import com.door43.translationstudio.getBestFontForLanguage
import com.door43.translationstudio.ui.launchWithProgress
import com.door43.translationstudio.ui.translate.ListItemOld
import com.door43.translationstudio.ui.translate.TargetTranslationActivity.Companion.SEARCH_SOURCE
import com.door43.translationstudio.ui.translate.dialogs.RCItem
import com.door43.usecases.RenderHelps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.Translation
import org.unfoldingword.resourcecontainer.Project
import org.unfoldingword.resourcecontainer.ResourceContainer
import org.unfoldingword.tools.logger.Logger
import java.util.Locale

data class TargetTranslationState(
    val items: List<Chunk> = emptyList(),
    val renderHelpsResult: RenderHelps.RenderHelpsResult? = null,
    val viewMode: TranslationViewMode = TranslationViewMode.READ,
    val draftAvailable: Boolean = false,
    val showDraftAvailable: Boolean = false,
    val sourceTabs: List<SourceTabItem> = emptyList(),
    val resourceContainer: ResourceContainer? = null,
    val lastFocusChapterId: String? = null,
    val lastFocusFrameId: String? = null,
    val projectTitle: String? = null,
    val snackBarMessage: String? = null
)

data class SourceTabItem(
    val tag: String,
    val title: String,
    val language: String?,
    val direction: String?
)

sealed interface TargetAction {
    object RefreshSelectedSource : TargetAction
    data class RemoveSource(val sourceId: String) : TargetAction
    data class SelectSource(val sourceId: String) : TargetAction
    data class LastViewMode(val viewMode: TranslationViewMode) : TargetAction
    data object OpenSourceTranslations : TargetAction
    data class SaveLastFocus(val chapterId: String, val frameId: String?) : TargetAction
    object InitLastFocus : TargetAction
}

class TargetTranslationViewModel(
    private val translator: Translator,
    private val renderHelps: RenderHelps,
    private val library: Door43Client,
    private val prefRepository: IPreferenceRepository,
    private val typography: Typography,
    private val assetsProvider: AssetsProvider
) : ViewModel(), KoinComponent, ProgressOwner {

    private val application: Application by inject()

    private val progressManager = ProgressManager(viewModelScope)
    override val progress get() = progressManager.progress

    private val renderHelpJobs = arrayListOf<Job>()

    lateinit var targetTranslation: TargetTranslation
        private set

    private val _state = MutableStateFlow(TargetTranslationState())
    val state: StateFlow<TargetTranslationState> = _state.asStateFlow()

    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        progressManager.runTask(message, block)
    }

    fun onAction(action: TargetAction) {
        when (action) {
            TargetAction.InitLastFocus -> {
                initLastFocus()
            }
            TargetAction.RefreshSelectedSource -> launchWithProgress(
                application.getString(R.string.loading)
            ) {
                refreshSelectedResourceContainer()
            }
            is TargetAction.RemoveSource -> launchWithProgress {
                removeOpenSourceTranslation(action.sourceId)
            }
            is TargetAction.SelectSource -> launchWithProgress {
                setSelectedResourceContainer(action.sourceId)
            }
            is TargetAction.LastViewMode -> setLastViewMode(action.viewMode)
            TargetAction.OpenSourceTranslations -> openUsedSourceTranslations()
            is TargetAction.SaveLastFocus -> saveLastFocus(action.chapterId, action.frameId)
        }
    }

    fun initialize(targetTranslationId: String): Boolean {
        val translation = translator.getTargetTranslation(targetTranslationId) ?: return false

        targetTranslation = translation

        val draftAvailable = draftIsAvailable()
        val lastViewMode = translator.getLastViewMode(targetTranslation.id)

        val projectTitle = "${getProject()?.name} - ${targetTranslation.targetLanguageName}"

        _state.update {
            it.copy(
                viewMode = lastViewMode,
                draftAvailable = draftAvailable,
                showDraftAvailable = draftAvailable && targetTranslation.numTranslated == 0,
                projectTitle = projectTitle
            )
        }

        return true
    }

    private fun openUsedSourceTranslations() {
        viewModelScope.launch {
            val opened = prefRepository.getOpenSourceTranslations(
                targetTranslation.id
            )
            if (opened.isEmpty()) {
                val resourceContainerSlugs = targetTranslation.sourceTranslations
                for (slug in resourceContainerSlugs) {
                    prefRepository.addOpenSourceTranslation(
                        targetTranslation.id,
                        slug
                    )
                }
            }
        }
    }

    /**
     * Selects the currently selected source translation or the first available
     * If no source available, sets list items to empty list
     */
    private suspend fun refreshSelectedResourceContainer() {
        getSelectedSourceTranslationId()?.let { sourceTranslationSlug ->
            setSelectedResourceContainer(sourceTranslationSlug)
        } ?: run {
            _state.update { it.copy(items = emptyList()) }
        }
        refreshSourceTranslationTabs()
    }

    /**
     * Checks if a draft is available
     * @return
     */
    private fun draftIsAvailable(): Boolean {
        return library.index.findTranslations(
            targetTranslation.targetLanguage.slug,
            targetTranslation.projectId,
            null,
            "book",
            null,
            0,
            -1
        ).any { it.resource.slug != "udb" }
    }

    private fun loadListItems() {
        val items = mutableListOf<Chunk>()
        _state.value.resourceContainer?.let { source ->
            val sorter = SlugSorter()
            val chapterSlugs = sorter.sort(source.chapters())
            for (chapterSlug: String in chapterSlugs) {
                val chunkSlugs = sorter.sort(source.chunks(chapterSlug))
                for (chunkSlug in chunkSlugs) {
                    items.add(Chunk(chapterSlug, chunkSlug, source, targetTranslation))
                }
            }
        }
        _state.update { it.copy(items = items) }
    }

    private fun setLastViewMode(mode: TranslationViewMode) {
        viewModelScope.launch {
            _state.update { it.copy(viewMode = mode) }
            translator.setLastViewMode(targetTranslation.id, mode)
        }
    }

    private fun initLastFocus() {
        viewModelScope.launch {
            val chapter = translator.getLastFocusChapterId(targetTranslation.id)
            val frame = translator.getLastFocusFrameId(targetTranslation.id)
            _state.update { it.copy(lastFocusChapterId = chapter, lastFocusFrameId = frame) }
        }
    }

    private fun saveLastFocus(chapterId: String, frameId: String?) {
        translator.setLastFocus(targetTranslation.id, chapterId, frameId)
    }

    private fun getSelectedSourceTranslationId(): String? {
        return translator.getSelectedSourceTranslationId(targetTranslation.id)
    }

    private fun getOpenSourceTranslations(): List<String> {
        return prefRepository.getOpenSourceTranslations(targetTranslation.id)
    }

    private suspend fun removeOpenSourceTranslation(sourceTranslationId: String) {
        prefRepository.removeOpenSourceTranslation(
            targetTranslation.id,
            sourceTranslationId
        )

        val sourceTranslationIds = getOpenSourceTranslations()
        if (sourceTranslationIds.isNotEmpty()) {
            val availableSourceId = getAvailableOpenTranslation()
            if (availableSourceId != null) {
                setSelectedResourceContainer(availableSourceId)
            }
        }
        refreshSelectedResourceContainer()
    }

    private fun getAvailableOpenTranslation(): String? {
        val openSourceTranslationIds = getOpenSourceTranslations()
        if (openSourceTranslationIds.isNotEmpty()) {
            return openSourceTranslationIds[0]
        }
        return null
    }

    private fun addOpenSourceTranslation(slug: String) {
        prefRepository.addOpenSourceTranslation(
            targetTranslation.id,
            slug
        )
    }

    /**
     * Selects the source translation by id
     */
    private suspend fun setSelectedResourceContainer(sourceTranslationId: String) {
        withContext(Dispatchers.Default) {
            translator.setSelectedSourceTranslation(
                targetTranslation.id,
                sourceTranslationId
            )
            val resourceContainer = library.index.getTranslation(
                sourceTranslationId
            )?.let { sourceTranslation ->
                ContainerCache.cache(
                    library,
                    sourceTranslation.resourceContainerSlug
                )
            }
            _state.update { it.copy(resourceContainer = resourceContainer) }

            loadListItems()
        }
    }

    private fun getTranslation(slug: String): Translation? {
        return library.index.getTranslation(slug)
    }

    private fun getResourceContainerLastModified(translation: Translation?): Int {
        return translation?.let {
            library.getResourceContainerLastModified(
                translation.language.slug,
                translation.project.slug,
                translation.resource.slug
            )
        } ?: -1
    }

    private fun getProject(): Project? {
        return library.index.getProject(
            deviceLanguageCode,
            targetTranslation.projectId,
            true
        )
    }

    fun confirmSelectedSources(selectedItems: List<RCItem>) {
        launchWithProgress {
            val selectedIds = selectedItems.mapNotNull { it.containerSlug }.toSet()

            if (selectedItems.size > 3) return@launchWithProgress

            val oldSourceTranslationIds = getOpenSourceTranslations().toSet()
            val toDelete = (oldSourceTranslationIds subtract selectedIds)
            val toInsert = (selectedIds subtract oldSourceTranslationIds)

            for (id in toDelete) {
                removeOpenSourceTranslation(id)
            }

            setSelectedSources(selectedIds, toInsert)

            if (selectedIds.isNotEmpty()) {
                val selectedSourceId = getSelectedSourceTranslationId()
                if (selectedSourceId == null) {
                    getAvailableOpenTranslation()?.let {
                        setSelectedResourceContainer(it)
                    }
                }
            }

            refreshSelectedResourceContainer()
        }
    }

    private fun setSelectedSources(sourceSlugs: Set<String>, newSourceSlugs: Set<String>) {
        val sources = ArrayList<SourceTranslation>()
        for (slug in sourceSlugs) {
            try {
                if (slug in newSourceSlugs) {
                    addOpenSourceTranslation(slug)
                }
            } catch (e: Exception) {
                Logger.e(
                    this.javaClass.name,
                    "Error while adding source $slug for ${targetTranslation.id}"
                )
                e.printStackTrace()
            }

            val translation = getTranslation(slug)
            if (translation != null) {
                val modifiedAt = getResourceContainerLastModified(translation)
                sources.add(SourceTranslation(translation, modifiedAt))
            }
        }

        try {
            targetTranslation.setSourceTranslations(sources)
        } catch (e: JSONException) {
            Logger.e(
                this.javaClass.name,
                "Failed to set source translations for the target translation ${targetTranslation.id}",
                e
            )
        }
    }

    private fun refreshSourceTranslationTabs() {
        val tabs = arrayListOf<SourceTabItem>()
        val sourceTranslationSlugs = prefRepository.getOpenSourceTranslations(
            targetTranslation.id
        )
        for (slug in sourceTranslationSlugs) {
            val st: Translation? = library.index.getTranslation(slug)
            if (st != null) {
                var title = st.language.name + " " + st.resource.slug.uppercase(Locale.getDefault())

                // include the resource id if there are more than one
                val resources = library.index.getResources(
                    st.language.slug,
                    st.project.slug
                )
                if (resources.size <= 1) {
                    title = st.language.name
                }

                val tag = st.resourceContainerSlug
                val (language, direction) = getFontForLanguageTab(st)

                tabs.add(
                    SourceTabItem(tag, title, language, direction)
                )
            }
        }

        _state.update { it.copy(sourceTabs = tabs) }
    }

    /**
     * if better font for language, get language info
     * @param translation
     * @param Pair<String, String> pair of language slug and direction
     */
    private fun getFontForLanguageTab(translation: Translation): Pair<String?, String?> {
        //see if there is a special font for tab
        val typeface = getBestFontForLanguage(
            typography,
            assetsProvider,
            translation.language.slug,
        )
        if (typeface != Typeface.DEFAULT) {
            return translation.language.slug to translation.language.direction
        }

        return null to null
    }

    // Methods to deprecate later

    // TODO Make private after removing PublishActivity
    fun getDefaultSourceTranslation(): String? {
        return getProject()?.let { project ->
            val resources = library.index.getResources(project.languageSlug, project.slug)
                .filter { it.type == "book" && it.slug != "udb" }

            val resourceContainer = try {
                library.open(project.languageSlug, project.slug, resources[0].slug)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }

            return resourceContainer?.slug
        }
    }

    // TODO Make private after removing Fragments
    fun getClosestResourceContainer(
        languageSlug: String,
        projectSlug: String,
        resourceSlug: String
    ): ResourceContainer? {
        return ContainerCache.cacheClosest(library, languageSlug, projectSlug, resourceSlug)
    }

    // TODO Make private after removing Fragments
    fun getResourceContainer(slug: String): ResourceContainer? {
        return ContainerCache.get(slug)
    }

    // TODO Make private after removing Fragments
    fun saveSearchSource(source: String) {
        prefRepository.setDefaultPref<String>(
            SEARCH_SOURCE,
            source
        )
    }

    // TODO Make private after removing Fragments
    fun renderHelps(item: ListItemOld) {
        viewModelScope.launch {
            val result = renderHelps.execute(item)
            _state.update {
                it.copy(renderHelpsResult = result)
            }
        }.also(renderHelpJobs::add)
    }

    // TODO Make private after removing Fragments
    fun cancelRenderJobs() {
        renderHelpJobs.forEach { it.cancel() }
        renderHelpJobs.clear()
    }

    // TODO Removing after refactoring PublishActivity
    fun getSelectedSourceTranslationId2(): String? {
        return translator.getSelectedSourceTranslationId(targetTranslation.id)
    }

}