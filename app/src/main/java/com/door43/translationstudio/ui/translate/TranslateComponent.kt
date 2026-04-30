package com.door43.translationstudio.ui.translate

import android.app.Application
import android.graphics.Typeface
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.activate
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.decompose.router.slot.dismiss
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.operator.map
import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.getOrCreate
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.arkivanov.essenty.lifecycle.doOnResume
import com.door43.data.AssetsProvider
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.Platform
import com.door43.translationstudio.R
import com.door43.translationstudio.core.ComponentScope
import com.door43.translationstudio.core.ContainerCache
import com.door43.translationstudio.core.Progress
import com.door43.translationstudio.core.ProgressManager
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TaskHandle
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.core.entity.SourceTranslation
import com.door43.translationstudio.core.entity.toSourceTranslation
import com.door43.translationstudio.core.launchWithProgress
import com.door43.translationstudio.getBestFontForLanguage
import com.door43.translationstudio.ui.dialogs.export.DefaultExportComponent
import com.door43.translationstudio.ui.dialogs.export.ExportComponent
import com.door43.translationstudio.ui.dialogs.feedback.DefaultFeedbackComponent
import com.door43.translationstudio.ui.dialogs.feedback.FeedbackComponent
import com.door43.translationstudio.ui.dialogs.source.DefaultSelectSourcesComponent
import com.door43.translationstudio.ui.dialogs.source.MAX_SOURCE_ITEMS
import com.door43.translationstudio.ui.dialogs.source.SelectSourcesComponent
import com.door43.translationstudio.ui.dialogs.source.SourceTabItem
import com.door43.translationstudio.ui.navigation.RootComponent
import com.door43.translationstudio.ui.translate.chunk.ChunkModeComponent
import com.door43.translationstudio.ui.translate.chunk.DefaultChunkModeComponent
import com.door43.translationstudio.ui.translate.read.DefaultReadModeComponent
import com.door43.translationstudio.ui.translate.read.ReadModeComponent
import com.door43.translationstudio.ui.translate.review.DefaultReviewModeComponent
import com.door43.translationstudio.ui.translate.review.ReviewModeComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.bibletranslationtools.logger.Logger
import org.bibletranslationtools.resourcecatalog.ResourceCatalogClient
import org.bibletranslationtools.resourcecatalog.library.models.Translation
import org.bibletranslationtools.resourcecontainer.Project
import org.bibletranslationtools.resourcecontainer.ResourceContainer
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

interface TranslateComponent {

    val stack: Value<ChildStack<*, Child>>
    val dialogSlot: Value<ChildSlot<*, DialogChild>>

    val state: StateFlow<State>
    val sharedState: StateFlow<SharedState>
    val progress: StateFlow<Progress?>
    val event: Flow<Event>
    val eventSender: SendChannel<Event>

    val currentViewMode: Value<TranslationViewMode>

    val targetTranslation: TargetTranslation

    fun openViewMode(viewMode: TranslationViewMode)
    fun openReadMode()
    fun openChunkMode()
    fun openReviewMode(conflictFilterOn: Boolean = false)

    fun restartAutoCommitTimer()
    fun updateMergeFilter(on: Boolean)
    fun removeSource(sourceId: String)
    fun selectSource(sourceId: String)
    fun saveLastFocus(chapterId: String, frameId: String?)

    fun openHome(withUpdate: Boolean = false)
    fun openDraft(translationId: String)
    fun openPublishProject(translationId: String)
    fun openSettings()

    fun showFeedbackDialog()
    fun showSelectSourcesDialog()
    fun showExportDialog(startFromPrint: Boolean = false)
    fun dismissDialog()

    companion object {
        const val SEARCH_SOURCE = "search_source"
    }

    data class State(
        val conflictFilterOn: Boolean = false,
        val draftAvailable: Boolean = false,
        val showDraftAvailable: Boolean = false,
        val lastFocusChapterId: String? = null,
        val lastFocusFrameId: String? = null,
        val projectTitle: String? = null,
    )

    data class SharedState(
        val sourceTabs: List<SourceTabItem> = emptyList(),
        val resourceContainer: ResourceContainer? = null
    )

    sealed interface Event {
        data class SnackbarMessage(val message: String) : Event
        data object RestartAutoCommitTimer : Event
    }

    sealed interface Child {
        data class Loading(val component: LoadingComponent) : Child
        data class Read(val component: ReadModeComponent) : Child
        data class Chunk(val component: ChunkModeComponent) : Child
        data class Review(val component: ReviewModeComponent) : Child
    }

    sealed interface Result {
        data class OpenHome(val withUpdate: Boolean) : Result
        data class OpenDraft(val translationId: String) : Result
        data class OpenPublishProject(val translationId: String) : Result
        data object Logout : Result
        data object OpenLogin : Result
        data object OpenSettings : Result
        data class Error(val message: String) : Result
    }

    @Serializable
    sealed interface Config {
        @Serializable
        object Loading : Config

        @Serializable
        data object Read : Config

        @Serializable
        data object Chunk : Config

        @Serializable
        data class Review(val conflictFilterOn: Boolean) : Config
    }

    @Serializable
    sealed interface DialogConfig {
        @Serializable
        data object Feedback : DialogConfig

        @Serializable
        data class SelectSources(val translationId: String) : DialogConfig

        @Serializable
        data class Export(val translationId: String, val startFromPrint: Boolean) : DialogConfig
    }

    sealed interface DialogChild {
        data class Feedback(val component: FeedbackComponent) : DialogChild
        data class SelectSources(val component: SelectSourcesComponent) : DialogChild
        data class Export(val component: ExportComponent) : DialogChild
    }
}

class DefaultTranslateComponent(
    componentContext: ComponentContext,
    translationId: String,
    initialViewMode: TranslationViewMode?,
    conflictFilterOn: Boolean,
    private val sharedFlow: SharedFlow<RootComponent.SharedEvent>,
    private val onResult: (TranslateComponent.Result) -> Unit
) : TranslateComponent,
    ComponentContext by componentContext,
    ComponentScope, ProgressOwner, KoinComponent {

    private val application: Application by inject()
    private val translator: Translator by inject()
    private val catalogClient: ResourceCatalogClient by inject()
    private val prefRepository: IPreferenceRepository by inject()
    private val typography: Typography by inject()
    private val assetsProvider: AssetsProvider by inject()
    private val platform: Platform by inject()

    private val navigation = StackNavigation<TranslateComponent.Config>()

    override val coroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val progressManager = ProgressManager(coroutineScope)
    override val progress get() = progressManager.progress

    private val dialogNavigation = SlotNavigation<TranslateComponent.DialogConfig>()

    override val dialogSlot = childSlot(
        source = dialogNavigation,
        serializer = TranslateComponent.DialogConfig.serializer(),
        handleBackButton = true,
        childFactory = ::createDialogChild
    )

    private val commitOnDestroy = instanceKeeper.getOrCreate {
        CommitOnDestroyInstance()
    }

    override lateinit var targetTranslation: TargetTranslation
        private set

    private val _state = MutableStateFlow(TranslateComponent.State())
    override val state: StateFlow<TranslateComponent.State> = _state.asStateFlow()

    private val _sharedState = MutableStateFlow(TranslateComponent.SharedState())
    override val sharedState: StateFlow<TranslateComponent.SharedState> = _sharedState.asStateFlow()

    private val _event = Channel<TranslateComponent.Event>(Channel.BUFFERED)
    override val event = _event.receiveAsFlow()
    override val eventSender: SendChannel<TranslateComponent.Event> = _event

    val initialized: Boolean
        get() = this::targetTranslation.isInitialized

    override val stack: Value<ChildStack<*, TranslateComponent.Child>> = childStack(
        source = navigation,
        serializer = TranslateComponent.Config.serializer(),
        initialConfiguration = TranslateComponent.Config.Loading,
        handleBackButton = true,
        childFactory = ::child
    )

    override val currentViewMode: Value<TranslationViewMode> = stack
        .map {
            when (it.active.instance) {
                is TranslateComponent.Child.Read -> TranslationViewMode.READ
                is TranslateComponent.Child.Chunk -> TranslationViewMode.CHUNK
                is TranslateComponent.Child.Review -> TranslationViewMode.REVIEW
                else -> TranslationViewMode.LOADING
            }
        }

    init {
        translator.getTargetTranslation(translationId)?.let { translation ->
            targetTranslation = translation

            val draftAvailable = draftIsAvailable()

            val viewMode = initialViewMode ?: translator.getLastViewMode(
                targetTranslation.id
            )
            openViewMode(viewMode)

            val projectTitle = "${getProject()?.name} - ${targetTranslation.targetLanguageName}"

            commitOnDestroy.scheduleAutoCommit()

            _state.update {
                it.copy(
                    conflictFilterOn = conflictFilterOn,
                    draftAvailable = draftAvailable,
                    showDraftAvailable = draftAvailable && targetTranslation.numTranslated == 0,
                    projectTitle = projectTitle
                )
            }

            launchWithProgress {
                ContainerCache.empty()
                openUsedSourceTranslations()
                refreshSelectedResourceContainer()
            }
        } ?: run {
            Logger.e(
                this::javaClass.name,
                "A valid target translation id is required. " +
                        "Received $translationId but the translation could not be found"
            )
            val error = application.getString(R.string.target_translation_not_found, translationId)
            onResult(TranslateComponent.Result.Error(error))
        }

        coroutineScope.launch {
            sharedFlow.collectLatest { event ->
                when (event) {
                    is RootComponent.SharedEvent.SnackbarMessage -> {
                        _event.trySend(TranslateComponent.Event.SnackbarMessage(event.message))
                    }
                    else -> {}
                }
            }
        }

        lifecycle.doOnDestroy {
            coroutineScope.cancel()
        }

        lifecycle.doOnResume {
            refreshLastFocus()
        }
    }

    private fun child(
        config: TranslateComponent.Config,
        componentContext: ComponentContext,
    ): TranslateComponent.Child = when (config) {
        is TranslateComponent.Config.Read -> TranslateComponent.Child.Read(
            component = DefaultReadModeComponent(
                componentContext = componentContext,
                sharedState = sharedState,
                targetTranslation = targetTranslation
            )
        )
        is TranslateComponent.Config.Chunk -> TranslateComponent.Child.Chunk(
            component = DefaultChunkModeComponent(
                componentContext = componentContext,
                sharedState = sharedState,
                targetTranslation = targetTranslation
            )
        )
        is TranslateComponent.Config.Review -> TranslateComponent.Child.Review(
            component = DefaultReviewModeComponent(
                componentContext = componentContext,
                sharedState = sharedState,
                eventSender = eventSender,
                targetTranslation = targetTranslation
            )
        )
        is TranslateComponent.Config.Loading -> TranslateComponent.Child.Loading(
            component = DefaultLoadingComponent()
        )
    }

    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        progressManager.runTask(message, block)
    }

    override fun openViewMode(viewMode: TranslationViewMode) {
        when (viewMode) {
            TranslationViewMode.CHUNK -> openChunkMode()
            TranslationViewMode.REVIEW -> openReviewMode()
            else -> openReadMode()
        }
    }

    override fun openReadMode() {
        saveViewMode(TranslationViewMode.READ)
        updateMergeFilter(false)

        if (currentViewMode.value !== TranslationViewMode.READ) {
            navigation.replaceAll(TranslateComponent.Config.Read)
        }
    }

    override fun openChunkMode() {
        saveViewMode(TranslationViewMode.CHUNK)
        updateMergeFilter(false)

        if (currentViewMode.value !== TranslationViewMode.CHUNK) {
            navigation.replaceAll(TranslateComponent.Config.Chunk)
        }
    }

    override fun openReviewMode(conflictFilterOn: Boolean) {
        saveViewMode(TranslationViewMode.REVIEW)
        updateMergeFilter(conflictFilterOn)

        if (currentViewMode.value !== TranslationViewMode.REVIEW) {
            navigation.replaceAll(TranslateComponent.Config.Review(conflictFilterOn))
        }
    }

    override fun restartAutoCommitTimer() {
        commitOnDestroy.scheduleAutoCommit()
    }

    override fun updateMergeFilter(on: Boolean) {
        _state.update { it.copy(conflictFilterOn = on) }
    }

    override fun removeSource(sourceId: String) {
        launchWithProgress {
            removeOpenSourceTranslation(sourceId)
        }
    }

    override fun selectSource(sourceId: String) {
        launchWithProgress {
            setSelectedResourceContainer(sourceId)
        }
    }

    override fun saveLastFocus(chapterId: String, frameId: String?) {
        translator.setLastFocus(targetTranslation.id, chapterId, frameId)
    }

    override fun openHome(withUpdate: Boolean) {
        onResult(TranslateComponent.Result.OpenHome(withUpdate))
    }

    override fun openDraft(translationId: String) {
        onResult(TranslateComponent.Result.OpenDraft(translationId))
    }

    override fun openPublishProject(translationId: String) {
        onResult(TranslateComponent.Result.OpenPublishProject(translationId))
    }

    override fun openSettings() {
        onResult(TranslateComponent.Result.OpenSettings)
    }

    override fun showFeedbackDialog() {
        dialogNavigation.activate(TranslateComponent.DialogConfig.Feedback)
    }

    override fun showSelectSourcesDialog() {
        dialogNavigation.activate(
            TranslateComponent.DialogConfig.SelectSources(
                translationId = targetTranslation.id
            )
        )
    }

    override fun showExportDialog(startFromPrint: Boolean) {
        dialogNavigation.activate(
            TranslateComponent.DialogConfig.Export(
                translationId = targetTranslation.id,
                startFromPrint = startFromPrint
            )
        )
    }

    private fun openUsedSourceTranslations() {
        val opened = prefRepository.getOpenSourceTranslations(
            targetTranslation.id
        )
        if (opened.isEmpty()) {
            val resourceContainerSlugs = targetTranslation.sourceTranslations
            for (slug in resourceContainerSlugs) {
                prefRepository.addOpenSourceTranslation(
                    targetTranslation.id,
                    slug
                )
            }
        }
    }

    private fun saveViewMode(viewMode: TranslationViewMode) {
        launchWithProgress {
            translator.setLastViewMode(
                targetTranslationId = targetTranslation.id,
                viewMode = viewMode
            )
        }
    }

    private suspend fun refreshSelectedResourceContainer() {
        getSelectedSourceTranslationId()?.let { sourceTranslationSlug ->
            setSelectedResourceContainer(sourceTranslationSlug)
        } ?: run {
            _sharedState.update { it.copy(resourceContainer = null) }
        }
        refreshSourceTranslationTabs()
    }

    private fun draftIsAvailable(): Boolean {
        return catalogClient.library.findTranslations(
            targetTranslation.targetLanguage.slug,
            targetTranslation.projectId,
            null,
            "book",
            null,
            0,
            -1
        ).any { it.resource.slug != "udb" }
    }

    private fun refreshLastFocus() {
        val chapter = translator.getLastFocusChapterId(targetTranslation.id)
        val frame = translator.getLastFocusFrameId(targetTranslation.id)
        _state.update { it.copy(lastFocusChapterId = chapter, lastFocusFrameId = frame) }
    }

    private fun getSelectedSourceTranslationId(): String? {
        return translator.getSelectedSourceTranslationId(targetTranslation.id)
    }

    private fun getOpenSourceTranslations(): List<String> {
        return prefRepository.getOpenSourceTranslations(targetTranslation.id)
    }

    private suspend fun removeOpenSourceTranslation(sourceTranslationId: String) {
        prefRepository.removeOpenSourceTranslation(
            targetTranslation.id,
            sourceTranslationId
        )

        val sourceTranslationIds = getOpenSourceTranslations()
        if (sourceTranslationIds.isNotEmpty()) {
            val availableSourceId = getAvailableOpenTranslation()
            if (availableSourceId != null) {
                setSelectedResourceContainer(availableSourceId)
            }
        }
        refreshSelectedResourceContainer()
    }

    private fun getAvailableOpenTranslation(): String? {
        val openSourceTranslationIds = getOpenSourceTranslations()
        if (openSourceTranslationIds.isNotEmpty()) {
            return openSourceTranslationIds[0]
        }
        return null
    }

    private fun addOpenSourceTranslation(slug: String) {
        prefRepository.addOpenSourceTranslation(
            targetTranslation.id,
            slug
        )
    }

    /**
     * Selects the source translation by id
     * If the selected source is not downloaded,
     * fallback to the first available downloaded source
     */
    private suspend fun setSelectedResourceContainer(sourceTranslationId: String) {
        withContext(Dispatchers.Default) {
            var resourceContainer = ContainerCache.get(sourceTranslationId) ?: ContainerCache.cache(
                catalogClient,
                sourceTranslationId
            )

            if (resourceContainer == null) {
                val availableSources = getOpenSourceTranslations()
                    .filter { it != sourceTranslationId }
                    .toMutableList()

                while (availableSources.isNotEmpty()) {
                    val nextSource = availableSources.first()
                    resourceContainer = ContainerCache.cache(catalogClient, nextSource)
                    if (resourceContainer != null) {
                        break
                    } else {
                        availableSources.removeAt(0)
                    }
                }
            }

            val rc = resourceContainer?.let { rc ->
                translator.setSelectedSourceTranslation(
                    targetTranslation.id,
                    rc.slug
                )
                rc
            }

            _sharedState.update { it.copy(resourceContainer = rc) }
        }
    }

    private fun getTranslation(slug: String): Translation? {
        return catalogClient.library.getTranslation(slug)
    }

    private fun getResourceContainerLastModified(translation: Translation?): Int {
        return translation?.let {
            catalogClient.getResourceContainerLastModified(
                translation.language.slug,
                translation.project.slug,
                translation.resource.slug
            )
        } ?: -1
    }

    private fun getProject(): Project? {
        return catalogClient.library.getProject(
            platform.deviceLanguageCode,
            targetTranslation.projectId,
            true
        )
    }

    private fun confirmSelectedSources(selectedIds: Set<String>) {
        launchWithProgress {
            if (selectedIds.size > MAX_SOURCE_ITEMS) return@launchWithProgress

            val oldSourceTranslationIds = getOpenSourceTranslations().toSet()
            val toDelete = (oldSourceTranslationIds subtract selectedIds)
            val toInsert = (selectedIds subtract oldSourceTranslationIds)

            for (id in toDelete) {
                removeOpenSourceTranslation(id)
            }

            setSelectedSources(selectedIds, toInsert)

            if (selectedIds.isNotEmpty()) {
                val selectedSourceId = getSelectedSourceTranslationId()
                if (selectedSourceId == null) {
                    getAvailableOpenTranslation()?.let {
                        setSelectedResourceContainer(it)
                    }
                }
            }

            ContainerCache.empty()
            refreshSelectedResourceContainer()
        }
    }

    private fun setSelectedSources(sourceSlugs: Set<String>, newSourceSlugs: Set<String>) {
        val sources = ArrayList<SourceTranslation>()
        for (slug in sourceSlugs) {
            try {
                if (slug in newSourceSlugs) {
                    addOpenSourceTranslation(slug)
                }
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
                sources.add(translation.toSourceTranslation(modifiedAt))
            }
        }

        try {
            targetTranslation.setSourceTranslations(sources)
        } catch (e: Exception) {
            Logger.e(
                this.javaClass.name,
                "Failed to set source translations for the target translation ${targetTranslation.id}",
                e
            )
        }
    }

    private fun refreshSourceTranslationTabs() {
        val tabs = arrayListOf<SourceTabItem>()
        val sourceTranslationSlugs = prefRepository.getOpenSourceTranslations(
            targetTranslation.id
        )
        for (slug in sourceTranslationSlugs) {
            val st: Translation? = catalogClient.library.getTranslation(slug)
            if (st != null) {
                var title = st.language.name + " " + st.resource.slug.uppercase(Locale.getDefault())

                // include the resource id if there are more than one
                val resources = catalogClient.library.getResources(
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

        _sharedState.update { it.copy(sourceTabs = tabs) }
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

    override fun dismissDialog() {
        dialogNavigation.dismiss()
    }

    private fun createDialogChild(
        config: TranslateComponent.DialogConfig,
        componentContext: ComponentContext
    ): TranslateComponent.DialogChild = when (config) {
        is TranslateComponent.DialogConfig.Feedback -> TranslateComponent.DialogChild.Feedback(
            component = DefaultFeedbackComponent(
                componentContext = componentContext
            )
        )
        is TranslateComponent.DialogConfig.SelectSources -> TranslateComponent.DialogChild.SelectSources(
            component = DefaultSelectSourcesComponent(
                componentContext = componentContext,
                translationId = config.translationId,
                onResult = ::onSelectSourcesResult
            )
        )
        is TranslateComponent.DialogConfig.Export -> TranslateComponent.DialogChild.Export(
            component = DefaultExportComponent(
                componentContext = componentContext,
                translationId = config.translationId,
                showPrint = config.startFromPrint,
                onResult = ::onExportResult
            )
        )
    }

    private fun onSelectSourcesResult(result: SelectSourcesComponent.Result) {
        when (result) {
            is SelectSourcesComponent.Result.Error -> {
                dismissDialog()
                onResult(TranslateComponent.Result.Error(result.text))
            }
            is SelectSourcesComponent.Result.ConfirmedSources -> {
                dismissDialog()
                confirmSelectedSources(result.sources)
            }
            is SelectSourcesComponent.Result.UpdateSources -> openHome(true)
        }
    }

    private fun onExportResult(result: ExportComponent.Result) {
        when (result) {
            is ExportComponent.Result.Error -> {
                dismissDialog()
                onResult(TranslateComponent.Result.Error(result.text))
            }
            is ExportComponent.Result.ExportToApp -> {
                platform.shareProject(result.file)
            }
            is ExportComponent.Result.OpenLogin -> {
                dismissDialog()
                onResult(TranslateComponent.Result.OpenLogin)
            }
            is ExportComponent.Result.Logout -> {
                dismissDialog()
                onResult(TranslateComponent.Result.Logout)
            }
            is ExportComponent.Result.MergeConflict -> {
                dismissDialog()
                openReviewMode(true)
            }
            is ExportComponent.Result.OpenFeedback -> {
                dismissDialog()
                showFeedbackDialog()
            }
        }
    }

    private inner class CommitOnDestroyInstance : InstanceKeeper.Instance {
        private val commitInterval = 2 * 60 * 1000L
        private var commitTimer: Timer = Timer()

        fun scheduleAutoCommit() {
            commitTimer.cancel()
            commitTimer = Timer()
            commitTimer.schedule(object : TimerTask() {
                override fun run() {
                    if (!initialized) return
                    try {
                        targetTranslation.commit()
                    } catch (e: Exception) {
                        Logger.e(
                            this::javaClass.name,
                            "Failed to commit the latest translation of " +
                                    targetTranslation.id,
                            e
                        )
                    }
                }
            }, commitInterval, commitInterval)
        }

        override fun onDestroy() {
            commitTimer.cancel()
            if (!initialized) return
            try {
                targetTranslation.commit()
            } catch (e: Exception) {
                Logger.e(
                    this::javaClass.name,
                    "Failed to commit changes before closing translation",
                    e
                )
            }
        }
    }
}
