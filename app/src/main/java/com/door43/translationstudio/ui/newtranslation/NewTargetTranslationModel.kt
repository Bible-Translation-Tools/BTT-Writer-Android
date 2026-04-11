package com.door43.translationstudio.ui.newtranslation

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.door43.data.ILanguageRequestRepository
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import com.door43.translationstudio.core.MergeConflictsHandler
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.ProgressManager
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.ResourceType
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TaskHandle
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.launchWithProgress
import com.door43.usecases.MergeTargetTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.CategoryEntry
import org.unfoldingword.door43client.models.TargetLanguage
import org.unfoldingword.resourcecontainer.Project
import org.unfoldingword.tools.logger.Logger
import java.util.Locale

enum class ScreenStep {
    LANGUAGE,
    PROJECT
}

data class MergeConflict(
    val sourceTranslation: TargetTranslation,
    val destinationTranslation: TargetTranslation,
    val message: String
)

data class NewTranslationState(
    val screenStep: ScreenStep = ScreenStep.LANGUAGE,
    val searchQuery: String = "",
    val languages: List<TargetLanguage> = emptyList(),
    val filteredLanguages: List<TargetLanguage> = emptyList(),
    val disabledLanguages: List<String> = emptyList(),
    val categories: List<CategoryEntry> = emptyList(),
    val filteredCategories: List<CategoryEntry> = emptyList(),
    val categoryStack: List<Long> = listOf(0L),
    val navigatingForward: Boolean = true,
    val mergeConflict: MergeConflict? = null
)

sealed interface NewTranslationAction {
    data class LanguageSelected(val targetLanguage: TargetLanguage) : NewTranslationAction
    data class ProjectSelected(val projectId: String) : NewTranslationAction
    data class CategorySelected(val categoryId: Long) : NewTranslationAction
    data object CategoryBack : NewTranslationAction
    data class OnSearch(val query: String) : NewTranslationAction
    data class MergeTranslation(val mergeConflict: MergeConflict) : NewTranslationAction
    data object ClearMergeConflict : NewTranslationAction
}

sealed interface NewTranslationEvent {
    data object FinishOk : NewTranslationEvent
    data object FinishCanceled : NewTranslationEvent
    data class FinishDuplicate(val targetTranslationId: String) : NewTranslationEvent
    data object FinishError : NewTranslationEvent
    data class OnMergeConflict(val translationId: String) : NewTranslationEvent
    data object OnMergeSuccess : NewTranslationEvent
    data class OnMergeError(val translationId: String) : NewTranslationEvent
}

class NewTargetTranslationModel(
    private val mergeTargetTranslation: MergeTargetTranslation,
    private val languageRequestRepository: ILanguageRequestRepository,
    private val prefRepository: IPreferenceRepository,
    private val library: Door43Client,
    private val translator: Translator,
    private val profile: Profile
) : ViewModel(), KoinComponent, ProgressOwner {

    private val application: Application by inject()

    private val progressManager = ProgressManager(viewModelScope)
    override val progress get() = progressManager.progress

    var selectedTargetLanguage: TargetLanguage? = null
        private set
    var newTargetTranslationId: String? = null
        private set
    var createdNewLanguage = false
        private set
    var changeTargetLanguageOnly = false
        private set
    var targetTranslationId: String? = null
        private set

    private val _state = MutableStateFlow(NewTranslationState())
    val state: StateFlow<NewTranslationState> = _state

    private val _event = Channel<NewTranslationEvent>(Channel.BUFFERED)
    val events = _event.receiveAsFlow()

    private var initialized = false

    fun initialize(
        disabledLanguages: List<String>,
        translationId: String?,
        changeLanguageOnly: Boolean
    ) {
        if (initialized) return
        initialized = true

        targetTranslationId = translationId
        changeTargetLanguageOnly = changeLanguageOnly

        launchWithProgress {
            val languages = withContext(Dispatchers.IO) {
                library.index.getTargetLanguages().sorted()
            }
            _state.value = _state.value.copy(
                languages = languages,
                filteredLanguages = languages,
                disabledLanguages = disabledLanguages
            )
        }
    }

    fun onAction(action: NewTranslationAction) {
        when (action) {
            is NewTranslationAction.LanguageSelected -> onLanguageSelected(action.targetLanguage)
            is NewTranslationAction.ProjectSelected -> onProjectSelected(action.projectId)
            is NewTranslationAction.CategorySelected -> navigateToCategory(action.categoryId)
            NewTranslationAction.CategoryBack -> navigateCategoryBack()
            is NewTranslationAction.OnSearch -> onSearch(action.query)
            is NewTranslationAction.MergeTranslation -> mergeTargetTranslation(action.mergeConflict)
            NewTranslationAction.ClearMergeConflict -> _state.update { it.copy(mergeConflict = null) }
        }
    }

    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        progressManager.runTask(message, block)
    }

    private fun onSearch(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        when (_state.value.screenStep) {
            ScreenStep.LANGUAGE -> filterLanguages(query)
            ScreenStep.PROJECT -> filterCategories(query)
        }
    }

    private fun filterLanguages(query: String) {
        val languages = _state.value.languages
        if (query.isEmpty()) {
            _state.value = _state.value.copy(filteredLanguages = languages)
            return
        }
        val lowerQuery = query.lowercase(Locale.getDefault())
        val filtered = languages.filter { language ->
            language.slug.lowercase(Locale.getDefault()).startsWith(lowerQuery) ||
                    language.name.lowercase(Locale.getDefault()).contains(lowerQuery)
        }
        val sorted = filtered.sortedWith(Comparator { lhs, rhs ->
            var lhId = lhs.slug
            var rhId = rhs.slug
            if (lhId.lowercase(Locale.getDefault()).startsWith(lowerQuery)) {
                lhId = "!!$lhId"
            }
            if (rhId.lowercase(Locale.getDefault()).startsWith(lowerQuery)) {
                rhId = "!!$rhId"
            }
            if (lhs.name.lowercase(Locale.getDefault()).startsWith(lowerQuery)) {
                lhId = "!$lhId"
            }
            if (rhs.name.lowercase(Locale.getDefault()).startsWith(lowerQuery)) {
                rhId = "!$rhId"
            }
            lhId.compareTo(rhId, ignoreCase = true)
        })
        _state.value = _state.value.copy(filteredLanguages = sorted)
    }

    private fun filterCategories(query: String) {
        val categories = _state.value.categories
        if (query.isEmpty()) {
            _state.value = _state.value.copy(filteredCategories = categories)
            return
        }
        val lowerQuery = query.lowercase(Locale.getDefault())
        val filtered = categories.filter { category ->
            val slugMatch = category.slug.lowercase(Locale.getDefault()).startsWith(lowerQuery)
            val nameMatch = category.name.lowercase(Locale.getDefault()).startsWith(lowerQuery)
            val langMatch = category.sourceLanguageSlug.lowercase(Locale.getDefault())
                .startsWith(lowerQuery)
            val componentMatch = category.slug.split("-").any {
                it.lowercase(Locale.getDefault()).startsWith(lowerQuery)
            } || category.name.split(" ").any {
                it.lowercase(Locale.getDefault()).startsWith(lowerQuery)
            }
            slugMatch || nameMatch || langMatch || componentMatch
        }
        _state.value = _state.value.copy(filteredCategories = filtered)
    }

    private fun showProjectStep() {
        val categories = library.index.getProjectCategories(
            0L, App.deviceLanguageCode, "all"
        )
        _state.value = _state.value.copy(
            screenStep = ScreenStep.PROJECT,
            searchQuery = "",
            categories = categories,
            filteredCategories = categories,
            categoryStack = listOf(0L),
            navigatingForward = true
        )
    }

    private fun navigateToCategory(categoryId: Long) {
        val categories = library.index.getProjectCategories(
            categoryId, App.deviceLanguageCode, "all"
        )
        _state.value = _state.value.copy(
            searchQuery = "",
            categories = categories,
            filteredCategories = categories,
            categoryStack = _state.value.categoryStack + categoryId,
            navigatingForward = true
        )
    }

    private fun navigateCategoryBack(): Boolean {
        val stack = _state.value.categoryStack
        if (stack.size <= 1) {
            _state.value = _state.value.copy(
                screenStep = ScreenStep.LANGUAGE,
                searchQuery = "",
                categories = emptyList(),
                filteredCategories = emptyList(),
                categoryStack = listOf(0L),
                navigatingForward = false
            )
            return true
        }
        val newStack = stack.dropLast(1)
        val parentId = newStack.last()
        val categories = library.index.getProjectCategories(
            parentId, App.deviceLanguageCode, "all"
        )
        _state.value = _state.value.copy(
            searchQuery = "",
            categories = categories,
            filteredCategories = categories,
            categoryStack = newStack,
            navigatingForward = false
        )
        return false
    }

    private fun onLanguageSelected(targetLanguage: TargetLanguage) {
        selectedTargetLanguage = targetLanguage

        if (!changeTargetLanguageOnly) {
            showProjectStep()
            return
        }

        val translationId = targetTranslationId
        if (translationId == null) {
            _event.trySend(NewTranslationEvent.FinishOk)
            return
        }

        val sourceTranslation = translator.getTargetTranslation(translationId)
        if (sourceTranslation == null) {
            _event.trySend(NewTranslationEvent.FinishOk)
            return
        }

        if (targetLanguage.slug == sourceTranslation.targetLanguage.slug) {
            _event.trySend(NewTranslationEvent.FinishOk)
            return
        }

        val projectId = sourceTranslation.projectId
        val resourceSlug = sourceTranslation.resourceSlug
        val existingTranslation = getTargetTranslation(
            TargetTranslation.generateTargetTranslationId(
                targetLanguage.slug, projectId, ResourceType.TEXT, resourceSlug
            )
        )

        if (existingTranslation != null) {
            newTargetTranslationId = existingTranslation.id
            val message = application.getString(
                R.string.warn_existing_target_translation,
                getProject(existingTranslation)?.name,
                existingTranslation.targetLanguageName
            )
            _state.update {
                it.copy(mergeConflict = MergeConflict(
                    sourceTranslation = sourceTranslation,
                    destinationTranslation = existingTranslation,
                    message = message
                ))
            }
        } else {
            val originalId = sourceTranslation.id
            sourceTranslation.changeTargetLanguage(targetLanguage)
            sourceTranslation.normalizePath()
            val newId = sourceTranslation.id
            moveTargetTranslationAppSettings(originalId, newId)
            _event.trySend(NewTranslationEvent.FinishOk)
        }
    }

    private fun onProjectSelected(projectId: String) {
        val resourceSlug = if (projectId == "obs") "obs" else "reg"
        val existingTranslation = selectedTargetLanguage?.let { selected ->
            getTargetTranslation(
                TargetTranslation.generateTargetTranslationId(
                    selected.slug, projectId, ResourceType.TEXT, resourceSlug
                )
            )
        }

        if (existingTranslation == null) {
            val format = if (projectId == "obs") TranslationFormat.MARKDOWN else TranslationFormat.USFM
            val targetTranslation = createTargetTranslation(
                projectId, ResourceType.TEXT, resourceSlug, format
            )
            if (targetTranslation != null) {
                newTargetTranslationId = targetTranslation.id
                _event.trySend(NewTranslationEvent.FinishOk)
            } else {
                deleteTargetTranslation(projectId, resourceSlug)
                _event.trySend(NewTranslationEvent.FinishError)
            }
        } else {
            _event.trySend(NewTranslationEvent.FinishDuplicate(existingTranslation.id))
        }
    }

    private fun getProject(targetTranslation: TargetTranslation): Project? {
        return library.index.getProject(
            App.deviceLanguageCode,
            targetTranslation.projectId
        )
    }

    private fun mergeTargetTranslation(mergeConflict: MergeConflict) {
        launchWithProgress {
            _state.update { it.copy(mergeConflict = null) }
            val result = withContext(Dispatchers.IO) {
                mergeTargetTranslation.execute(
                    mergeConflict.destinationTranslation,
                    mergeConflict.sourceTranslation,
                    true
                )
            }

            when (result.status) {
                MergeTargetTranslation.Status.MERGE_CONFLICTS -> {
                    translator.clearTargetTranslationSettings(result.sourceTranslation.id)
                    val hasConflicts = MergeConflictsHandler.isTranslationMergeConflicted(
                        result.destinationTranslation.id,
                        translator
                    )
                    if (hasConflicts) {
                        _event.trySend(NewTranslationEvent.OnMergeConflict(
                            result.destinationTranslation.id
                        ))
                    } else {
                        _event.trySend(NewTranslationEvent.OnMergeSuccess)
                    }
                }
                MergeTargetTranslation.Status.SUCCESS -> {
                    translator.clearTargetTranslationSettings(result.sourceTranslation.id)
                    _event.trySend(NewTranslationEvent.OnMergeSuccess)
                }
                else -> {
                    _event.trySend(NewTranslationEvent.OnMergeError(
                        result.destinationTranslation.id
                    ))
                }
            }
        }
    }

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

    private fun getTargetTranslation(translationId: String): TargetTranslation? {
        return translator.getTargetTranslation(translationId)
    }

    private fun moveTargetTranslationAppSettings(
        targetTranslationId: String,
        newTargetTranslationId: String
    ) {
        val sources = prefRepository.getOpenSourceTranslations(targetTranslationId)
        for (source in sources) {
            prefRepository.addOpenSourceTranslation(newTargetTranslationId, source)
        }

        val source = translator.getSelectedSourceTranslationId(targetTranslationId)
        translator.setSelectedSourceTranslation(newTargetTranslationId, source)

        val lastFocusChapterId = translator.getLastFocusChapterId(targetTranslationId)
        val lastFocusFrameId = translator.getLastFocusFrameId(targetTranslationId)
        prefRepository.setLastFocus(newTargetTranslationId, lastFocusChapterId, lastFocusFrameId)

        val lastViewMode = translator.getLastViewMode(targetTranslationId)
        translator.setLastViewMode(newTargetTranslationId, lastViewMode)

        translator.clearTargetTranslationSettings(targetTranslationId)
    }

    private fun createTargetTranslation(
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

    private fun deleteTargetTranslation(projectId: String, resourceSlug: String) {
        selectedTargetLanguage?.let { selected ->
            translator.deleteTargetTranslation(
                TargetTranslation.generateTargetTranslationId(
                    selected.slug, projectId, ResourceType.TEXT, resourceSlug
                )
            )
        }
    }
}
