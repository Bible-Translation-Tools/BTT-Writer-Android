package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import android.content.ContentValues
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.App.Companion.deviceLanguageCode
import com.door43.translationstudio.core.ContainerCache
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.SlugSorter
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.translationstudio.ui.translate.ListItem
import com.door43.translationstudio.ui.translate.TargetTranslationActivity.SEARCH_SOURCE
import com.door43.translationstudio.ui.translate.ViewModeAdapter
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
    @Inject lateinit var library: Door43Client
    @Inject lateinit var prefRepository: IPreferenceRepository
    @Inject lateinit var renderHelps: RenderHelps

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

    fun loadTargetTranslation(translationID: String?): TargetTranslation? {
        _targetTranslation = translator.getTargetTranslation(translationID)
        return _targetTranslation
    }

    fun openUsedSourceTranslations() {
        if (translator.getOpenSourceTranslations(targetTranslation.id).isEmpty()) {
            val resourceContainerSlugs = targetTranslation.getSourceTranslations()
            for (slug in resourceContainerSlugs) {
                translator.addOpenSourceTranslation(targetTranslation.id, slug)
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
            SEARCH_SOURCE, defaultSource
        ) ?: defaultSource
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

    fun getOpenSourceTranslations(): Array<String?> {
        return translator.getOpenSourceTranslations(targetTranslation.id)
    }

    fun removeOpenSourceTranslation(sourceTranslationId: String) {
        translator.removeOpenSourceTranslation(targetTranslation.id, sourceTranslationId)
    }

    fun addOpenSourceTranslation(slug: String) {
        translator.addOpenSourceTranslation(targetTranslation.id, slug)
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
            _resourceContainer = withContext(Dispatchers.IO) {
                translator.setSelectedSourceTranslation(targetTranslation.id, sourceTranslationId)
                library.index.getTranslation(sourceTranslationId)?.let { sourceTranslation ->
                    ContainerCache.cache(
                        library,
                        sourceTranslation.resourceContainerSlug
                    )
                }
            }
            loadListItems()
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
        viewModelScope.launch {
            val items = mutableListOf<ListItem>()
            withContext(Dispatchers.IO) {
                _resourceContainer?.let { source ->
                    val sorter = SlugSorter()
                    val chapterSlugs: List<String> = sorter.sort(source.chapters())
                    for (chapterSlug: String in chapterSlugs) {
                        val chunkSlugs: List<String> = sorter.sort(source.chunks(chapterSlug))
                        for (chunkSlug in chunkSlugs) {
                            val item = object: ListItem(
                                chapterSlug,
                                chunkSlug,
                                source,
                                targetTranslation
                            ){
                                override val tabs: () -> List<ContentValues>
                                    get() = { getSourceTranslations() }
                            }
                            items.add(item)
                        }
                    }
                }
            }
            _listItems.value = items
            _progress.value = null
        }.also(generalJobs::add)
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

    private fun getSourceTranslations(): List<ContentValues> {
        val tabContents = arrayListOf<ContentValues>()
        val sourceTranslationSlugs = translator.getOpenSourceTranslations(targetTranslation.id)
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

                ViewModeAdapter.getFontForLanguageTab(application, st, values)
                tabContents.add(values)
            }
        }
        return tabContents
    }

}