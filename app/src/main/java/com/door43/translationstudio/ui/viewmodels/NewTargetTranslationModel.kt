package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.door43.data.ILanguageRequestRepository
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.App.Companion.deviceLanguageCode
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.ResourceType
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.core.Translator
import com.door43.usecases.MergeTargetTranslation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.CategoryEntry
import org.unfoldingword.door43client.models.TargetLanguage
import org.unfoldingword.resourcecontainer.Project
import org.unfoldingword.tools.logger.Logger
import javax.inject.Inject

@HiltViewModel
class NewTargetTranslationModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    @Inject lateinit var library: Door43Client
    @Inject lateinit var translator: Translator
    @Inject lateinit var profile: Profile
    @Inject lateinit var mergeTargetTranslation: MergeTargetTranslation
    @Inject lateinit var languageRequestRepository: ILanguageRequestRepository
    @Inject lateinit var prefRepository: IPreferenceRepository

    var selectedTargetLanguage: TargetLanguage? = null
    var newTargetTranslationId: String? = null
    var createdNewLanguage = false
    var changeTargetLanguageOnly = false
    var targetTranslationId: String? = null

    private val _mergeTranslationResult = MutableLiveData<MergeTargetTranslation.Result?>()
    val mergeTranslationResult: LiveData<MergeTargetTranslation.Result?> = _mergeTranslationResult

    fun getProject(targetTranslation: TargetTranslation): Project {
        return library.index.getProject(deviceLanguageCode, targetTranslation.projectId)
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

    fun getTargetLanguage(languageId: String?): TargetLanguage? {
        return library.index.getTargetLanguage(languageId)
    }

    fun getTargetTranslation(translationId: String?): TargetTranslation? {
        return translator.getTargetTranslation(translationId)
    }

    fun normalizeTargetTranslationPath(targetTranslation: TargetTranslation) {
        targetTranslation.normalizePath()
    }

    fun getOpenSourceTranslations(translationId: String): Array<String> {
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
            translator.createTargetTranslation(
                profile.nativeSpeaker,
                targetLanguage,
                projectId,
                resourceType,
                resourceSlug,
                format
            )?.let { targetTranslation ->
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
    }

    fun deleteTargetTranslation(projectId: String, resourceSlug: String) {
        selectedTargetLanguage?.let { selected ->
            translator.deleteTargetTranslation(
                TargetTranslation.generateTargetTranslationId(
                    selected.slug, projectId, ResourceType.TEXT, resourceSlug
                )
            )
        }
    }

    fun getTargetLanguages(): List<TargetLanguage> {
        return library.index.targetLanguages
    }

    fun getCategories(categoryId: Long = 0): List<CategoryEntry> {
        return library.index.getProjectCategories(categoryId, deviceLanguageCode, "all")
    }
}