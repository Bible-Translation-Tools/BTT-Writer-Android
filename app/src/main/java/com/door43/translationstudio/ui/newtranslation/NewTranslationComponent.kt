package com.door43.translationstudio.ui.newtranslation

import android.app.Application
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import com.door43.translationstudio.core.ComponentScope
import com.door43.translationstudio.core.MergeConflictsHandler
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.Progress
import com.door43.translationstudio.core.ProgressManager
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.ResourceType
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TaskHandle
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.core.launchWithProgress
import com.door43.usecases.MergeTargetTranslation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.CategoryEntry
import org.unfoldingword.door43client.models.TargetLanguage
import org.unfoldingword.resourcecontainer.Project
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

interface NewTranslationComponent {

    val state: StateFlow<State>
    val progress: StateFlow<Progress?>

    fun onLanguageSelected(targetLanguage: TargetLanguage)
    fun onProjectSelected(projectId: String)
    fun onCategorySelected(categoryId: Long)
    fun onCategoryBack()
    fun onSearch(query: String)
    fun mergeTranslation(mergeConflict: MergeConflict)
    fun clearMergeConflict()

    fun navigateBack()

    data class State(
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

    sealed interface Result {
        data object NavigateBack : Result
        data object Success : Result
        data class Error(val text: String) : Result
        data class Duplicate(val translationId: String) : Result
        data class MergeConflict(val translationId: String) : Result
    }
}

class DefaultNewTranslationComponent(
    componentContext: ComponentContext,
    private val disabledLanguages: List<String>,
    private val translationId: String?,
    private val onResult: (NewTranslationComponent.Result) -> Unit
) : NewTranslationComponent,
    ComponentContext by componentContext,
    KoinComponent, ComponentScope, ProgressOwner {

    private val application: Application by inject()
    private val mergeTargetTranslation: MergeTargetTranslation by inject()
    private val prefRepository: IPreferenceRepository by inject()
    private val library: Door43Client by inject()
    private val translator: Translator by inject()
    private val profile: Profile by inject()

    var selectedTargetLanguage: TargetLanguage? = null
        private set

    override val coroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val progressManager = ProgressManager(coroutineScope)
    override val progress get() = progressManager.progress

    private val _state = MutableStateFlow(NewTranslationComponent.State())
    override val state: StateFlow<NewTranslationComponent.State> = _state

    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        progressManager.runTask(message, block)
    }

    init {
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

        lifecycle.doOnDestroy {
            coroutineScope.cancel()
        }
    }

    override fun navigateBack() {
        onResult(NewTranslationComponent.Result.NavigateBack)
    }

    override fun onLanguageSelected(targetLanguage: TargetLanguage) {
        selectedTargetLanguage = targetLanguage

        if (translationId == null) {
            showProjectStep()
            return
        }

        launchWithProgress(
            application.getString(R.string.loading)
        ) {
            withContext(Dispatchers.IO) {
                val sourceTranslation = translator.getTargetTranslation(translationId)
                if (sourceTranslation == null) {
                    withContext(Dispatchers.Main) {
                        val error = application.getString(R.string.target_translation_not_found, translationId)
                        onResult(NewTranslationComponent.Result.Error(error))
                    }
                    return@withContext
                }

                if (targetLanguage.slug == sourceTranslation.targetLanguage.slug) {
                    withContext(Dispatchers.Main) {
                        onResult(NewTranslationComponent.Result.Success)
                    }
                    return@withContext
                }

                val projectId = sourceTranslation.projectId
                val resourceSlug = sourceTranslation.resourceSlug
                val existingTranslation = getTargetTranslation(
                    TargetTranslation.generateTargetTranslationId(
                        targetLanguage.slug, projectId, ResourceType.TEXT, resourceSlug
                    )
                )

                if (existingTranslation != null) {
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
                    withContext(Dispatchers.Main) {
                        onResult(NewTranslationComponent.Result.Success)
                    }
                }
            }
        }
    }

    override fun onProjectSelected(projectId: String) {
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
                onResult(NewTranslationComponent.Result.Success)
            } else {
                val error = application.getString(R.string.failed_to_create_target_translation)
                deleteTargetTranslation(projectId, resourceSlug)
                onResult(NewTranslationComponent.Result.Error(error))
            }
        } else {
            onResult(NewTranslationComponent.Result.Duplicate(existingTranslation.id))
        }
    }

    override fun onCategorySelected(categoryId: Long) {
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

    override fun onCategoryBack() {
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
            return
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
    }

    override fun onSearch(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        when (_state.value.screenStep) {
            ScreenStep.LANGUAGE -> filterLanguages(query)
            ScreenStep.PROJECT -> filterCategories(query)
        }
    }

    override fun mergeTranslation(mergeConflict: MergeConflict) {
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
                    translator.clearTargetTranslationSettings(
                        result.sourceTranslation.id
                    )
                    val hasConflicts = MergeConflictsHandler.isTranslationMergeConflicted(
                        result.destinationTranslation.id,
                        translator
                    )
                    if (hasConflicts) {
                        onResult(NewTranslationComponent.Result.MergeConflict(
                            result.destinationTranslation.id
                        ))
                    } else {
                        onResult(NewTranslationComponent.Result.Success)
                    }
                }
                MergeTargetTranslation.Status.SUCCESS -> {
                    translator.clearTargetTranslationSettings(
                        result.sourceTranslation.id
                    )
                    onResult(NewTranslationComponent.Result.Success)
                }
                else -> {
                    val error = application.getString(R.string.error)
                    onResult(NewTranslationComponent.Result.Error(error))
                }
            }
        }
    }

    override fun clearMergeConflict() {
        _state.update { it.copy(mergeConflict = null) }
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

    private fun getProject(targetTranslation: TargetTranslation): Project? {
        return library.index.getProject(
            App.deviceLanguageCode,
            targetTranslation.projectId
        )
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