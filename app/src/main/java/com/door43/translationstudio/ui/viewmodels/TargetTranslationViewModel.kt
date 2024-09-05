package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.core.ContainerCache
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.translate.TargetTranslationActivity.SEARCH_SOURCE
import com.door43.translationstudio.ui.translate.review.SearchSubject
import dagger.hilt.android.lifecycle.HiltViewModel
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.SourceLanguage
import org.unfoldingword.door43client.models.Translation
import org.unfoldingword.resourcecontainer.ResourceContainer
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class TargetTranslationViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    @Inject lateinit var translator: Translator
    @Inject lateinit var profile: Profile
    @Inject lateinit var library: Door43Client
    @Inject lateinit var prefRepository: IPreferenceRepository

    private var _targetTranslation: TargetTranslation? = null
    val targetTranslation get() = _targetTranslation!!

    private var _sourceTranslation: Translation? = null
    val sourceTranslation get() = _sourceTranslation

    private var _resourceContainer: ResourceContainer? = null
    val resourceContainer get() = _resourceContainer

    fun loadTargetTranslation(translationID: String?): TargetTranslation? {
        _targetTranslation = translator.getTargetTranslation(translationID)
        return _targetTranslation
    }

    fun openUsedSourceTranslations() {
        if (translator.getOpenSourceTranslations(targetTranslation.id).isEmpty()) {
            val resourceContainerSlugs: Array<String> =
                targetTranslation.getSourceTranslations()
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

    fun getSelectedSourceTranslationId(): String {
        return translator.getSelectedSourceTranslationId(targetTranslation.id)
    }

    fun setSelectedSourceTranslation(sourceTranslationId: String) {
        translator.setSelectedSourceTranslation(targetTranslation.id, sourceTranslationId)
    }

    fun removeOpenSourceTranslation(sourceTranslationId: String) {
        translator.removeOpenSourceTranslation(targetTranslation.id, sourceTranslationId)
    }

    fun setSourceTranslation(sourceTranslationId: String) {
        _sourceTranslation = library.index.getTranslation(sourceTranslationId)
    }

    fun getOpenSourceTranslations(): Array<String?> {
        return translator.getOpenSourceTranslations(targetTranslation.id)
    }

    fun addOpenSourceTranslation(slug: String) {
        translator.addOpenSourceTranslation(targetTranslation.id, slug)
    }

    fun getResourceContainer(slug: String? = null): ResourceContainer? {
        return slug?.let {
            ContainerCache.cache(library, it)
        } ?: sourceTranslation?.let {
            ContainerCache.cache(library, it.resourceContainerSlug)
        }
    }

    fun setResourceContainer(rc: ResourceContainer? = null) {
        _resourceContainer = rc ?: sourceTranslation?.let {
            ContainerCache.cache(library, it.resourceContainerSlug)
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

    fun getSourceLanguage(): SourceLanguage? {
        return sourceTranslation?.let { library.index.getSourceLanguage(it.language.slug) }
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

}