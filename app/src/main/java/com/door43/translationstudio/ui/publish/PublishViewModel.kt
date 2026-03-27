package com.door43.translationstudio.ui.publish

import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.door43.translationstudio.App.Companion.deviceLanguageCode
import com.door43.translationstudio.core.NativeSpeaker
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.core.Validation
import com.door43.translationstudio.rendering.RenderingGroup
import com.door43.translationstudio.rendering.RenderingProvider
import com.door43.translationstudio.ui.textadapters.ComposeTextAdapter
import com.door43.usecases.ValidateProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.resourcecontainer.Project

data class PublishState(
    val isLoading: Boolean = false,
    val validations: List<ValidationItem> = emptyList(),
    val translators: List<NativeSpeaker> = emptyList()
)

data class ValidationItem(
    val validation: Validation,
    val rendered: AnnotatedString = AnnotatedString("")
)

sealed interface PublishAction {
    data class OpenReview(val item: Validation.InvalidFrame) : PublishAction
    object RefreshContributors : PublishAction
}

sealed interface PublishEvent {
    data class OpenReview(val translationId: String) : PublishEvent
}

class PublishViewModel(
    private val translator: Translator,
    private val library: Door43Client,
    private val validateProject: ValidateProject,
    private val profile: Profile
) : ViewModel() {

    lateinit var sourceTranslationId: String
        private set

    lateinit var targetTranslation: TargetTranslation
        private set

    val sourceInitialized: Boolean
        get() = this::sourceTranslationId.isInitialized

    val targetInitialized: Boolean
        get() = this::targetTranslation.isInitialized

    private val _state = MutableStateFlow(PublishState())
    val state: StateFlow<PublishState> = _state.asStateFlow()

    private val _event = Channel<PublishEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    fun initialize(targetTranslationId: String) {
        val translation = translator.getTargetTranslation(targetTranslationId) ?: return
        targetTranslation = translation

        val sourceId = getSelectedSourceTranslationId()
            ?: getDefaultSourceTranslation()
            ?: return
        sourceTranslationId = sourceId

        viewModelScope.launch {
            validateProject(sourceId)
            loadTranslators()
        }
    }

    fun onAction(event: PublishAction) {
        when (event) {
            is PublishAction.OpenReview -> onOpenReview(event.item)
            PublishAction.RefreshContributors -> viewModelScope.launch {
                loadTranslators()
            }
        }
    }

    private suspend fun validateProject(sourceTranslationId: String) {
        _state.update { it.copy(isLoading = true) }

        val items = withContext(Dispatchers.IO) {
            val validations = validateProject.execute(
                targetTranslation.id,
                sourceTranslationId
            )
            validations.chunked(5)
                .flatMap { batch ->
                    batch.map { async { prepareItem(it) } }
                }.awaitAll()
        }

        _state.update { it.copy(isLoading = false, validations = items) }
    }

    private suspend fun loadTranslators() {
        withContext(Dispatchers.IO) {
            targetTranslation.addContributor(profile.nativeSpeaker)
            _state.update { state ->
                state.copy(
                    translators = targetTranslation.contributors.sortedBy { it.name }
                )
            }
        }
    }

    private fun prepareItem(item: Validation): ValidationItem {
        return if (item is Validation.InvalidFrame && item.body.isNotEmpty()) {
            ValidationItem(item, renderTargetText(item.bodyFormat, item.body))
        } else ValidationItem(item)
    }

    private fun getSelectedSourceTranslationId(): String? {
        return translator.getSelectedSourceTranslationId(targetTranslation.id)
    }

    private fun getDefaultSourceTranslation(): String? {
        return getProject()?.let { project ->
            val resources = library.index.getResources(
                project.languageSlug,
                project.slug
            )
                .filter { it.type == "book" && it.slug != "udb" }

            val resourceContainer = try {
                library.open(
                    project.languageSlug,
                    project.slug,
                    resources[0].slug
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }

            return resourceContainer?.slug
        }
    }

    private fun getProject(): Project? {
        return library.index.getProject(
            deviceLanguageCode,
            targetTranslation.projectId,
            true
        )
    }

    private fun renderTargetText(format: TranslationFormat, text: String, ): AnnotatedString {
        return try {
            val renderingGroup = RenderingGroup()
            renderingGroup.init(text)
            RenderingProvider().setupRenderingGroup(
                format,
                renderingGroup
            )
            val renderNodes = renderingGroup.start()
            ComposeTextAdapter.convert(
                nodes = renderNodes
            )
        } catch (_: Exception) {
            AnnotatedString(text)
        }
    }

    private fun onOpenReview(item: Validation.InvalidFrame) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                translator.setLastViewMode(
                    targetTranslationId = item.targetTranslationId,
                    viewMode = TranslationViewMode.REVIEW
                )
                translator.setLastFocus(
                    targetTranslationId = item.targetTranslationId,
                    chapterId = item.chapterId,
                    frameId = item.frameId
                )
            }
            _event.trySend(PublishEvent.OpenReview(item.targetTranslationId))
        }
    }
}