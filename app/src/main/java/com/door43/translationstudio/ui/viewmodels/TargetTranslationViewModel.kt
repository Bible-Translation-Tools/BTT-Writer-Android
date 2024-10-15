package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import android.content.ContentValues
import android.graphics.Typeface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.data.setDefaultPref
import com.door43.translationstudio.App.Companion.deviceLanguageCode
import com.door43.translationstudio.core.ContainerCache
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.SlugSorter
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TranslationType
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.translationstudio.ui.translate.ListItem
import com.door43.translationstudio.ui.translate.TargetTranslationActivity.SEARCH_SOURCE
import com.door43.translationstudio.ui.translate.review.SearchSubject
import com.door43.usecases.RenderHelps
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.Translation
import org.unfoldingword.resourcecontainer.Language
import org.unfoldingword.resourcecontainer.Project
import org.unfoldingword.resourcecontainer.ResourceContainer
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class TargetTranslationViewModel @Inject constructor(
    private val application: Application
) : AndroidViewModel(application) {

    @Inject lateinit var translator: Translator
    @Inject lateinit var profile: Profile
    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var prefRepository: IPreferenceRepository
    @Inject lateinit var renderHelps: RenderHelps
    @Inject lateinit var library: Door43Client

    @Inject lateinit var typography: Typography

    private val generalJobs = arrayListOf<Job>()
    private val renderHelpJobs = arrayListOf<Job>()

    private var _targetTranslation: TargetTranslation? = null
    val targetTranslation get() = _targetTranslation!!

    private var _resourceContainer: ResourceContainer? = null
    val resourceContainer get() = _resourceContainer!!

    private var _listItems = MutableLiveData<List<ListItem>>()
    val listItems: LiveData<List<ListItem>> = _listItems

    private var _renderHelpsResult = MutableLiveData<RenderHelps.RenderHelpsResult?>(null)
    val renderHelpsResult: LiveData<RenderHelps.RenderHelpsResult?> = _renderHelpsResult

    private val _progress = MutableLiveData<ProgressHelper.Progress?>(null)
    val progress: LiveData<ProgressHelper.Progress?> = _progress

    fun cancelGeneralJobs() {
        generalJobs.forEach { it.cancel() }
        generalJobs.clear()
    }

    fun getTargetTranslation(translationID: String?): TargetTranslation? {
        _targetTranslation = translator.getTargetTranslation(translationID)
        return _targetTranslation
    }

    fun openUsedSourceTranslations() {
        if (prefRepository.getOpenSourceTranslations(targetTranslation.id).isEmpty()) {
            val resourceContainerSlugs = targetTranslation.getSourceTranslations()
            for (slug in resourceContainerSlugs) {
                prefRepository.addOpenSourceTranslation(targetTranslation.id, slug)
            }
        }
    }

    /**
     * Checks if a draft is available
     * @return
     */
    fun draftIsAvailable(): Boolean {
        return library.index().findTranslations(
            targetTranslation.targetLanguage.slug,
            targetTranslation.projectId,
            null,
            "book",
            null,
            0,
            -1
        ).isNotEmpty()
    }

    fun getLastViewMode(): TranslationViewMode {
        return translator.getLastViewMode(targetTranslation.id)
    }

    fun setLastViewMode(modeIndex: Int) {
        if (modeIndex > 0 && modeIndex < TranslationViewMode.entries.size) {
            setLastViewMode(TranslationViewMode.entries[modeIndex])
        }
    }

    fun setLastViewMode(mode: TranslationViewMode) {
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
        getSelectedSourceTranslationId()?.let { sourceTranslationSlug ->
            setSelectedResourceContainer(sourceTranslationSlug)
        } ?: run {
            _listItems.value = listOf()
        }
    }

    /**
     * Selects the source translation by id
     */
    fun setSelectedResourceContainer(sourceTranslationId: String) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress()

            translator.setSelectedSourceTranslation(targetTranslation.id, sourceTranslationId)
            _resourceContainer = library.index.getTranslation(sourceTranslationId)?.let { sourceTranslation ->
                ContainerCache.cache(
                    library,
                    sourceTranslation.resourceContainerSlug
                )
            }
            loadListItems()

            _progress.value = null
        }.also(generalJobs::add)
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

    fun getSourceLanguage(): Language? {
        return _resourceContainer?.language
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

    fun getProject(): Project {
        return library.index.getProject(
            deviceLanguageCode,
            targetTranslation.projectId,
            true
        )
    }

    private fun loadListItems() {
        val items = mutableListOf<ListItem>()
        _resourceContainer?.let { source ->
            val sorter = SlugSorter()
            val chapterSlugs: List<String> = sorter.sort(source.chapters())
            for (chapterSlug: String in chapterSlugs) {
                val chunkSlugs: List<String> = sorter.sort(source.chunks(chapterSlug))
                for (chunkSlug in chunkSlugs) {
                    val item = createItem(chapterSlug, chunkSlug, source, targetTranslation)
                    items.add(item)
                }
            }
        }
        _listItems.value = items
    }

    fun renderHelps(item: ListItem) {
        viewModelScope.launch {
            _renderHelpsResult.value = withContext(Dispatchers.IO) {
                renderHelps.execute(item)
            }
        }.also(renderHelpJobs::add)
    }

    fun cancelRenderJobs() {
        renderHelpJobs.forEach { it.cancel() }
        renderHelpJobs.clear()
    }

    private fun createItem(
        chapterSlug: String,
        chunkSlug: String,
        source: ResourceContainer,
        targetTranslation: TargetTranslation
    ): ListItem {
        return object: ListItem(
            chapterSlug,
            chunkSlug,
            source,
            targetTranslation
        ) {
            override fun fetchTabs(): List<ContentValues> {
                return getSourceTranslations()
            }

            override fun getSourceText(chapterSlug: String, chunkSlug: String?): String {
                return fetchSourceText(source, chapterSlug, chunkSlug)
            }

            override fun getTargetText(chapterSlug: String, chunkSlug: String?): String {
                return fetchTargetText(source, targetTranslation, chapterSlug, chunkSlug)
            }
        }
    }

    fun getDefaultSourceTranslation(): String? {
        val project = getProject()
        val resources = library.index().getResources(project.languageSlug, project.slug)
        val resourceContainer = try {
            library.open(project.languageSlug, project.slug, resources[0].slug)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
        return resourceContainer?.slug
    }

    private fun getSourceTranslations(): List<ContentValues> {
        val tabContents = arrayListOf<ContentValues>()
        val sourceTranslationSlugs = prefRepository.getOpenSourceTranslations(targetTranslation.id)
        for (slug in sourceTranslationSlugs) {
            val st: Translation? = library.index().getTranslation(slug)
            if (st != null) {
                val values = ContentValues()
                val title = st.language.name + " " + st.resource.slug.uppercase(Locale.getDefault())
                values.put("title", title)
                // include the resource id if there are more than one
                if (library.index().getResources(st.language.slug, st.project.slug).size > 1) {
                    values.put("title", title)
                } else {
                    values.put("title", st.language.name)
                }
                values.put("tag", st.resourceContainerSlug)

                getFontForLanguageTab(st, values)
                tabContents.add(values)
            }
        }
        return tabContents
    }

    /**
     * if better font for language, save language info in values
     * @param st
     * @param values
     */
    private fun getFontForLanguageTab(translation: Translation, values: ContentValues) {
        //see if there is a special font for tab
        val typeface = typography.getBestFontForLanguage(
            TranslationType.SOURCE,
            translation.language.slug,
            translation.language.direction
        )
        if (typeface != Typeface.DEFAULT) {
            values.put("language", translation.language.slug);
            values.put("direction", translation.language.direction)
        }
    }

    private fun fetchSourceText(source: ResourceContainer, chapterSlug: String, chunkSlug: String?): String {
        return if (chunkSlug != null) {
            source.readChunk(chapterSlug, chunkSlug)
        } else {
            var chapterBody = ""
            val sorter = SlugSorter()
            val chunks = sorter.sort(source.chunks(chapterSlug))
            for (chunk in chunks) {
                if(!chunk.equals("title")) {
                    chapterBody += source.readChunk(chapterSlug, chunk);
                }
            }
            chapterBody
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

    fun saveSearchSource(source: String) {
        prefRepository.setDefaultPref<String>(
            SEARCH_SOURCE,
            source
        )
    }

}