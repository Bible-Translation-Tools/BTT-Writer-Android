package com.door43.translationstudio.ui.newtranslation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.door43.data.ILanguageRequestRepository
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.App
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.ResourceType
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.core.Translator
import com.door43.usecases.MergeTargetTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.CategoryEntry
import org.unfoldingword.door43client.models.TargetLanguage
import org.unfoldingword.resourcecontainer.Project
import org.unfoldingword.tools.logger.Logger

class NewTargetTranslationModel(
    private val mergeTargetTranslation: MergeTargetTranslation,
    private val languageRequestRepository: ILanguageRequestRepository,
    private val prefRepository: IPreferenceRepository,
    private val library: Door43Client,
    private val translator: Translator,
    private val profile: Profile
) : ViewModel() {

    var selectedTargetLanguage: TargetLanguage? = null
    var newTargetTranslationId: String? = null
    var createdNewLanguage = false
    var changeTargetLanguageOnly = false
    var targetTranslationId: String? = null

    private val _mergeTranslationResult = MutableLiveData<MergeTargetTranslation.Result?>()
    val mergeTranslationResult: LiveData<MergeTargetTranslation.Result?> = _mergeTranslationResult

    fun getProject(targetTranslation: TargetTranslation): Project? {
        return library.index.getProject(
            App.Companion.deviceLanguageCode,
            targetTranslation.projectId
        )
    }

    fun mergeTargetTranslation(
        destinationTranslation: TargetTranslation,
        sourceTranslation: TargetTranslation,
        deleteSource: Boolean
    ) {
        viewModelScope.launch {
            _mergeTranslationResult.value = withContext(Dispatchers.IO) {
                mergeTargetTranslation.execute(
                    destinationTranslation,
                    sourceTranslation,
                    deleteSource
                )
            }
        }
    }

    fun clearTargetTranslationSettings(targetTranslationId: String) {
        translator.clearTargetTranslationSettings(targetTranslationId)
    }

    /**
     * Use new language information passed in JSON format string to create a new target language
     * @param jsonString
     */
    fun registerTempLanguage(jsonString: String?): Boolean {
        val request = jsonString?.let { languageRequestRepository.requestFromJson(it) }
        if (request != null) {
            val questionnaire = library.index.getQuestionnaire(request.questionnaireId)
            if (questionnaire != null && languageRequestRepository.addNewLanguageRequest(request)) {
                selectedTargetLanguage = request.tempTargetLanguage
                createdNewLanguage = true
                return true
            }
        }
        return false
    }

    fun getTargetLanguage(languageId: String): TargetLanguage? {
        return library.index.getTargetLanguage(languageId)
    }

    fun getTargetTranslation(translationId: String): TargetTranslation? {
        return translator.getTargetTranslation(translationId)
    }

    fun normalizeTargetTranslationPath(targetTranslation: TargetTranslation) {
        targetTranslation.normalizePath()
    }

    fun getOpenSourceTranslations(translationId: String): List<String> {
        return prefRepository.getOpenSourceTranslations(translationId)
    }

    /**
     * moves all settings for a target translation to new target translation
     * (such as when target language changed)
     * @param targetTranslationId
     */
    fun moveTargetTranslationAppSettings(
        targetTranslationId: String,
        newTargetTranslationId: String
    ) {
        val sources = prefRepository.getOpenSourceTranslations(targetTranslationId)
        for (source in sources) {
            prefRepository.addOpenSourceTranslation(newTargetTranslationId!!, source)
        }

        val source = translator.getSelectedSourceTranslationId(targetTranslationId)
        translator.setSelectedSourceTranslation(newTargetTranslationId, source)

        val lastFocusChapterId = translator.getLastFocusChapterId(targetTranslationId)
        val lastFocusFrameId = translator.getLastFocusFrameId(targetTranslationId)
        prefRepository.setLastFocus(newTargetTranslationId, lastFocusChapterId, lastFocusFrameId)

        val lastViewMode = translator.getLastViewMode(targetTranslationId)
        translator.setLastViewMode(newTargetTranslationId, lastViewMode)

        //remove old settings
        translator.clearTargetTranslationSettings(targetTranslationId)
    }

    fun createTargetTranslation(
        projectId: String,
        resourceType: ResourceType,
        resourceSlug: String,
        format: TranslationFormat
    ): TargetTranslation? {
        return selectedTargetLanguage?.let { targetLanguage ->
            val targetTranslation = translator.createTargetTranslation(
                profile.nativeSpeaker,
                targetLanguage,
                projectId,
                resourceType,
                resourceSlug,
                format
            )

            // deploy custom language code request to the translation
            languageRequestRepository.getNewLanguageRequest(
                targetLanguage.slug
            )?.let { request ->
                try {
                    targetTranslation.setNewLanguageRequest(request)
                } catch (e: Exception) {
                    Logger.e(
                        this.javaClass.name,
                        "Failed to deploy the new language code request",
                        e
                    )
                }
            }

            targetTranslation
        }
    }

    fun deleteTargetTranslation(projectId: String, resourceSlug: String) {
        selectedTargetLanguage?.let { selected ->
            translator.deleteTargetTranslation(
                TargetTranslation.Companion.generateTargetTranslationId(
                    selected.slug, projectId, ResourceType.TEXT, resourceSlug
                )
            )
        }
    }

    fun getTargetLanguages(): List<TargetLanguage> {
        return library.index.getTargetLanguages()
    }

    fun getCategories(categoryId: Long = 0): List<CategoryEntry> {
        return library.index.getProjectCategories(categoryId, App.Companion.deviceLanguageCode, "all")
    }
}