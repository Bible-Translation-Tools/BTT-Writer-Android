package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import android.graphics.Typeface
import android.util.Log
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.door43.data.AssetsProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.data.setDefaultPref
import com.door43.translationstudio.App
import com.door43.translationstudio.App.Companion.deviceLanguageCode
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Chunk
import com.door43.translationstudio.core.ContainerCache
import com.door43.translationstudio.core.SlugSorter
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.core.entity.SourceTranslation
import com.door43.translationstudio.getBestFontForLanguage
import com.door43.translationstudio.rendering.RenderNodeConverter
import com.door43.translationstudio.rendering.RenderingGroup
import com.door43.translationstudio.rendering.RenderingProvider
import com.door43.translationstudio.rendering.adapter.ComposeTextAdapter
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.translationstudio.ui.translate.ListItemOld
import com.door43.translationstudio.ui.translate.TargetTranslationActivity.Companion.SEARCH_SOURCE
import com.door43.translationstudio.ui.translate.review.SearchSubject
import com.door43.usecases.DownloadResourceContainers
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
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.Translation
import org.unfoldingword.resourcecontainer.Project
import org.unfoldingword.resourcecontainer.ResourceContainer
import org.unfoldingword.tools.logger.Logger
import java.util.Locale

data class TargetTranslationModel(
    val items: List<Chunk> = emptyList(),
    val renderHelpsResult: RenderHelps.RenderHelpsResult? = null,
    val progress: ProgressHelper.Progress? = null,
    val viewMode: TranslationViewMode = TranslationViewMode.READ,
    val draftAvailable: Boolean = false,
    val showDraftAvailable: Boolean = false,
    val sourceTabs: List<SourceTabItem> = emptyList(),
    val resourceContainer: ResourceContainer? = null,
    val availableSources: List<RCItem> = emptyList(),
    val snackBarMessage: String? = null
)

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

class TargetTranslationViewModel(
    private val application: Application,
    private val translator: Translator,
    private val renderHelps: RenderHelps,
    private val library: Door43Client,
    private val prefRepository: IPreferenceRepository,
    private val typography: Typography,
    private val assetsProvider: AssetsProvider,
    private val downloadResourceContainers: DownloadResourceContainers
) : AndroidViewModel(application) {

    private val renderHelpJobs = arrayListOf<Job>()

    lateinit var targetTranslation: TargetTranslation
        private set

    private val _model = MutableStateFlow(TargetTranslationModel())
    val model: StateFlow<TargetTranslationModel> = _model.asStateFlow()

    fun initialize(targetTranslationId: String): Boolean {
        val translation = translator.getTargetTranslation(targetTranslationId) ?: return false

        targetTranslation = translation

        val draftAvailable = draftIsAvailable()
        val lastViewMode = translator.getLastViewMode(targetTranslation.id)

        _model.update {
            it.copy(
                viewMode = lastViewMode,
                draftAvailable = draftAvailable,
                showDraftAvailable = draftAvailable && targetTranslation.numTranslated == 0
            )
        }

        return true
    }

    fun openUsedSourceTranslations() {
        if (prefRepository.getOpenSourceTranslations(targetTranslation.id).isEmpty()) {
            val resourceContainerSlugs = targetTranslation.sourceTranslations
            for (slug in resourceContainerSlugs) {
                prefRepository.addOpenSourceTranslation(
                    targetTranslation.id,
                    slug
                )
            }
        }
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

    fun setLastViewMode(modeIndex: Int) {
        if (modeIndex > 0 && modeIndex < TranslationViewMode.entries.size) {
            setLastViewMode(TranslationViewMode.entries[modeIndex])
        }
    }

    fun setLastViewMode(mode: TranslationViewMode) {
        _model.update { it.copy(viewMode = mode) }
        translator.setLastViewMode(targetTranslation.id, mode)
    }

    fun getLastSearchSource(): String {
        val defaultSource = SearchSubject.SOURCE.name.uppercase(
            Locale.getDefault()
        )
        return prefRepository.getDefaultPref(
            SEARCH_SOURCE,
            defaultSource
        )
    }

    fun setLastSearchSource(subject: SearchSubject) {
        prefRepository.setDefaultPref(
            SEARCH_SOURCE,
            subject.name.uppercase(Locale.getDefault())
        )
    }

    fun setLastFocus(chapterId: String, frameId: String?) {
        translator.setLastFocus(targetTranslation.id, chapterId, frameId)
    }

    fun getLastFocusChapterId(): String? {
        return translator.getLastFocusChapterId(targetTranslation.id)
    }

    fun getLastFocusFrameId(): String? {
        return translator.getLastFocusFrameId(targetTranslation.id)
    }

    fun getSelectedSourceTranslationId(): String? {
        return translator.getSelectedSourceTranslationId(targetTranslation.id)
    }

    fun getOpenSourceTranslations(): Array<String> {
        return prefRepository.getOpenSourceTranslations(targetTranslation.id)
    }

    fun removeOpenSourceTranslation(sourceTranslationId: String) {
        prefRepository.removeOpenSourceTranslation(targetTranslation.id, sourceTranslationId)
    }

    fun addOpenSourceTranslation(slug: String) {
        prefRepository.addOpenSourceTranslation(targetTranslation.id, slug)
    }

    fun getResourceContainer(slug: String): ResourceContainer? {
        return ContainerCache.get(slug)
    }

    /**
     * Selects the currently selected source translation or the first available
     * If no source available, sets list items to empty list
     */
    fun setSelectedResourceContainer() {
        viewModelScope.launch {
            getSelectedSourceTranslationId()?.let { sourceTranslationSlug ->
                setSelectedResourceContainer(sourceTranslationSlug)
            } ?: run {
                _model.update { it.copy(items = emptyList()) }
            }
            updateSourceTranslations()
        }
    }

    /**
     * Selects the source translation by id
     */
    fun setSelectedResourceContainer(sourceTranslationId: String) {
       viewModelScope.launch {
            _model.update { it.copy(progress = ProgressHelper.Progress()) }

            withContext(Dispatchers.IO) {
                translator.setSelectedSourceTranslation(targetTranslation.id, sourceTranslationId)
                val resourceContainer = library.index.getTranslation(sourceTranslationId)?.let { sourceTranslation ->
                    ContainerCache.cache(
                        library,
                        sourceTranslation.resourceContainerSlug
                    )
                }
                _model.update { it.copy(resourceContainer = resourceContainer) }
            }

            loadListItems()
            _model.update { it.copy(progress = null) }
        }
    }

    fun getClosestResourceContainer(
        languageSlug: String,
        projectSlug: String,
        resourceSlug: String
    ): ResourceContainer? {
        return ContainerCache.cacheClosest(library, languageSlug, projectSlug, resourceSlug)
    }

    fun getTranslation(slug: String): Translation? {
        return library.index.getTranslation(slug)
    }

    fun getResourceContainerLastModified(translation: Translation?): Int {
        return translation?.let {
            library.getResourceContainerLastModified(
                translation.language.slug,
                translation.project.slug,
                translation.resource.slug
            )
        } ?: -1
    }

    fun findTranslations(
        languageSlug: String,
        projectSlug: String,
        resourceSlug: String,
        resourceType: String,
        translationMode: String? = null,
        minCheckingLevel: Int = 0,
        maxCheckingLevel: Int = -1
    ): List<Translation> {
        return library.index.findTranslations(
            languageSlug,
            projectSlug,
            resourceSlug,
            resourceType,
            translationMode,
            minCheckingLevel,
            maxCheckingLevel
        )
    }

    fun getProject(): Project? {
        return library.index.getProject(
            deviceLanguageCode,
            targetTranslation.projectId,
            true
        )
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
                it.copy(progress = null, availableSources = rcItems)
            }
        }
    }

    fun toggleSourceSelection(source: RCItem) {
        val stackFull = model.value.availableSources.filter { it.selected }.size == 3

        if (!stackFull || source.selected) {
            _model.update { state ->
                state.copy(
                    availableSources = state.availableSources.map {
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

    fun confirmSelectedSources() {
        val selectedItems = model.value.availableSources.filter { it.selected }
        val selectedIds = selectedItems.mapNotNull { it.containerSlug }

        if (selectedItems.size > 3) return

        viewModelScope.launch {
            val oldSourceTranslationIds = getOpenSourceTranslations()
            for (id in oldSourceTranslationIds) {
                removeOpenSourceTranslation(id)
            }
            if (selectedIds.isNotEmpty()) {
                setSelectedSources(selectedIds)
                val selectedSourceId = getSelectedSourceTranslationId()
                if (selectedSourceId != null) {
                    setSelectedResourceContainer(selectedSourceId)
                }
            }

            updateSourceTranslations()
        }
    }

    private fun setSelectedSources(sourceSlugs: List<String>) {
        val sources = ArrayList<SourceTranslation>()
        for (slug in sourceSlugs) {
            try {
               addOpenSourceTranslation(slug)
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

    private suspend fun loadListItems() {
        val items = mutableListOf<Chunk>()
        withContext(Dispatchers.Default) {
            _model.value.resourceContainer?.let { source ->
                val sorter = SlugSorter()
                val chapterSlugs = sorter.sort(source.chapters())
                for (chapterSlug: String in chapterSlugs) {
                    val chunkSlugs = sorter.sort(source.chunks(chapterSlug))
                    for (chunkSlug in chunkSlugs) {
                        items.add(Chunk(chapterSlug, chunkSlug, source, targetTranslation))
                    }
                }
            }
        }
        _model.update { it.copy(items = items) }
    }

    fun renderHelps(item: ListItemOld) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                renderHelps.execute(item)
            }
            _model.update {
                it.copy(renderHelpsResult = result)
            }
        }.also(renderHelpJobs::add)
    }

    fun cancelRenderJobs() {
        renderHelpJobs.forEach { it.cancel() }
        renderHelpJobs.clear()
    }

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

    private fun updateSourceTranslations() {
        val tabs = arrayListOf<SourceTabItem>()
        val sourceTranslationSlugs = prefRepository.getOpenSourceTranslations(targetTranslation.id)
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

        _model.update { it.copy(sourceTabs = tabs) }
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

    private fun fetchSourceText(source: ResourceContainer, chapterSlug: String, chunkSlug: String?): String {
        return if (chunkSlug != null) {
            source.readChunk(chapterSlug, chunkSlug)
        } else {
            var chapterBody = ""
            val sorter = SlugSorter()
            val chunks = sorter.sort(source.chunks(chapterSlug))
            for (chunk in chunks) {
                if(chunk != "title") {
                    chapterBody += source.readChunk(chapterSlug, chunk);
                }
            }
            chapterBody
        }
    }

    private fun renderSourceText(sourceText: String, format: TranslationFormat): AnnotatedString {
        return try {
            val renderingGroup = RenderingGroup()
            renderingGroup.init(sourceText)
            RenderingProvider(application).setupRenderingGroup(
                format,
                renderingGroup,
                pinVerses = false,
                target = false
            )
            val renderNodes = renderingGroup.startNodes()
            val textNodes = RenderNodeConverter.renderNodesToTextNodes(renderNodes)
            ComposeTextAdapter.convert(textNodes/*, onNoteClick = onNoteClick*/)
        } catch (_: Exception) {
            AnnotatedString(sourceText)
        }
    }

    private fun fetchTargetText(
        source: ResourceContainer,
        target: TargetTranslation,
        chapterSlug: String,
        chunkSlug: String?
    ): String {
        return if (chunkSlug != null) {
            when (chapterSlug) {
                "front" -> {
                    // project stuff
                    if (chunkSlug == "title") {
                        target.projectTranslation.title
                    } else ""
                }
                "back" -> ""
                else -> {
                    // chapter stuff
                    when (chunkSlug) {
                        "title" -> target.getChapterTranslation(chapterSlug).title
                        "reference" -> target.getChapterTranslation(chapterSlug).reference
                        else -> target.getFrameTranslation(
                            chapterSlug,
                            chunkSlug,
                            target.format
                        )?.body ?: ""
                    }
                }
            }
        } else {
            var chapterBody = ""
            val sorter = SlugSorter()
            val chunks = sorter.sort(source.chunks(chapterSlug))
            for (chunk in chunks) {
                val translation = target.getFrameTranslation(chapterSlug, chunk, target.format)
                chapterBody += " " + translation.body
            }
            chapterBody
        }
    }

    private fun renderTargetText(targetText: String): AnnotatedString {
        return AnnotatedString(targetText)
    }

    fun saveSearchSource(source: String) {
        prefRepository.setDefaultPref<String>(
            SEARCH_SOURCE,
            source
        )
    }

}