package com.door43.translationstudio.ui.publish

import android.app.Application
import androidx.compose.ui.text.AnnotatedString
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.activate
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.decompose.router.slot.dismiss
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.door43.translationstudio.App.Companion.deviceLanguageCode
import com.door43.translationstudio.R
import com.door43.translationstudio.core.NativeSpeaker
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.core.Validation
import com.door43.translationstudio.rendering.RenderingGroup
import com.door43.translationstudio.rendering.RenderingProvider
import com.door43.translationstudio.ui.dialogs.export.DefaultExportComponent
import com.door43.translationstudio.ui.dialogs.feedback.DefaultFeedbackComponent
import com.door43.translationstudio.ui.dialogs.export.ExportComponent
import com.door43.translationstudio.ui.dialogs.feedback.FeedbackComponent
import com.door43.translationstudio.core.ComponentScope
import com.door43.translationstudio.ui.textadapters.ComposeTextAdapter
import com.door43.usecases.ValidateProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.resourcecontainer.Project
import java.io.File

data class ValidationItem(
    val validation: Validation,
    val rendered: AnnotatedString = AnnotatedString("")
)

interface PublishComponent {

    val state: StateFlow<State>
    val dialogSlot: Value<ChildSlot<*, DialogChild>>

    val targetTranslation: TargetTranslation

    data class State(
        val isLoading: Boolean = false,
        val validations: List<ValidationItem> = emptyList(),
        val translators: List<NativeSpeaker> = emptyList()
    )

    fun openReview(item: Validation.InvalidFrame)
    fun refreshContributors()

    fun showExportDialog()
    fun dismissDialog()

    fun navigateBack()

    @Serializable
    sealed interface DialogConfig {
        @Serializable
        data object Export : DialogConfig

        @Serializable
        data class Feedback(val message: String = "") : DialogConfig
    }

    sealed interface DialogChild {
        data class Export(val component: ExportComponent) : DialogChild
        data class Feedback(val component: FeedbackComponent) : DialogChild
    }

    sealed interface Result {
        data class Error(val message: String) : Result
        data class OpenReview(val translationId: String) : Result
        data class ExportToApp(val file: File) : Result
        data object Login : Result
        data object Logout : Result
        data class MergeConflict(val translationId: String) : Result
        data object NavigateBack : Result
    }
}

class DefaultPublishComponent(
    componentContext: ComponentContext,
    translationId: String,
    private val onResult: (PublishComponent.Result) -> Unit
) : PublishComponent,
    ComponentContext by componentContext,
    KoinComponent, ComponentScope {

    private val application: Application by inject()
    private val translator: Translator by inject()
    private val library: Door43Client by inject()
    private val validateProject: ValidateProject by inject()
    private val profile: Profile by inject()

    private lateinit var sourceTranslationId: String

    override lateinit var targetTranslation: TargetTranslation
        private set

    override val coroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val _state = MutableStateFlow(PublishComponent.State())
    override val state: StateFlow<PublishComponent.State> = _state.asStateFlow()

    private val dialogNavigation = SlotNavigation<PublishComponent.DialogConfig>()

    override val dialogSlot = childSlot(
        source = dialogNavigation,
        serializer = PublishComponent.DialogConfig.serializer(),
        handleBackButton = true,
        childFactory = ::createDialogChild
    )

    init {
        translator.getTargetTranslation(translationId)?.let { translation ->
            targetTranslation = translation

            (getSelectedSourceTranslationId() ?: getDefaultSourceTranslation())?.let { sourceId ->
                sourceTranslationId = sourceId

                coroutineScope.launch {
                    validateProject(sourceId)
                    loadTranslators()
                }
            } ?: run {
                val error = application.getString(R.string.choose_source_translations)
                onResult(PublishComponent.Result.Error(error))
            }
        } ?: run {
            val error = application.getString(R.string.target_translation_not_found, translationId)
            onResult(PublishComponent.Result.Error(error))
        }

        lifecycle.doOnDestroy {
            coroutineScope.cancel()
        }
    }

    override fun openReview(item: Validation.InvalidFrame) {
        coroutineScope.launch {
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
            onResult(PublishComponent.Result.OpenReview(item.targetTranslationId))
        }
    }

    override fun refreshContributors() {
        coroutineScope.launch {
            loadTranslators()
        }
    }

    override fun showExportDialog() {
        dialogNavigation.activate(PublishComponent.DialogConfig.Export)
    }

    override fun dismissDialog() {
        dialogNavigation.dismiss()
    }

    override fun navigateBack() {
        onResult(PublishComponent.Result.NavigateBack)
    }

    private fun createDialogChild(
        config: PublishComponent.DialogConfig,
        componentContext: ComponentContext
    ): PublishComponent.DialogChild = when (config) {
        is PublishComponent.DialogConfig.Export -> PublishComponent.DialogChild.Export(
            DefaultExportComponent(
                componentContext = componentContext,
                translationId = targetTranslation.id,
                showPrint = false,
                onResult = ::onExportResult
            )
        )
        is PublishComponent.DialogConfig.Feedback -> PublishComponent.DialogChild.Feedback(
            DefaultFeedbackComponent(
                componentContext = componentContext,
                initialMessage = config.message
            )
        )
    }

    private fun onExportResult(result: ExportComponent.Result) {
        when (result) {
            is ExportComponent.Result.Error -> {}
            is ExportComponent.Result.ExportToApp -> {
                dismissDialog()
                onResult(PublishComponent.Result.ExportToApp(result.file))
            }
            is ExportComponent.Result.OpenLogin -> {
                dismissDialog()
                onResult(PublishComponent.Result.Login)
            }
            is ExportComponent.Result.Logout -> {
                dismissDialog()
                onResult(PublishComponent.Result.Logout)
            }
            is ExportComponent.Result.MergeConflict -> {
                dismissDialog()
                onResult(PublishComponent.Result.MergeConflict(result.translationId))
            }
            is ExportComponent.Result.OpenFeedback -> {
                dialogNavigation.activate(
                    PublishComponent.DialogConfig.Feedback(result.message)
                )
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
                    translators = targetTranslation.contributors.sortedBy {
                        it.name.lowercase()
                    }
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
}