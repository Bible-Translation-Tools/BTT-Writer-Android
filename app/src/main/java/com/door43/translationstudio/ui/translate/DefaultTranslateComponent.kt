package com.door43.translationstudio.ui.translate

import android.app.Application
import android.graphics.Typeface
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.activate
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.decompose.router.slot.dismiss
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.getOrCreate
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.door43.data.AssetsProvider
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.App.Companion.deviceLanguageCode
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Chunk
import com.door43.translationstudio.core.ContainerCache
import com.door43.translationstudio.core.ProgressManager
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.SlugSorter
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TaskHandle
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.core.entity.SourceTranslation
import com.door43.translationstudio.getBestFontForLanguage
import com.door43.translationstudio.ui.dialogs.export.DefaultExportComponent
import com.door43.translationstudio.ui.dialogs.feedback.DefaultFeedbackComponent
import com.door43.translationstudio.ui.dialogs.export.ExportComponent
import com.door43.translationstudio.ui.dialogs.source.DefaultSelectSourcesComponent
import com.door43.translationstudio.ui.dialogs.source.MAX_SOURCE_ITEMS
import com.door43.translationstudio.ui.dialogs.source.SelectSourcesComponent
import com.door43.translationstudio.ui.dialogs.source.SourceTabItem
import com.door43.translationstudio.ui.launchWithProgress
import com.door43.translationstudio.ui.navigation.ComponentScope
import com.door43.translationstudio.ui.navigation.RootComponent
import com.door43.translationstudio.ui.translate.chunk.DefaultChunkModeComponent
import com.door43.translationstudio.ui.translate.read.DefaultReadModeComponent
import com.door43.translationstudio.ui.translate.review.DefaultReviewModeComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.Translation
import org.unfoldingword.resourcecontainer.Project
import org.unfoldingword.resourcecontainer.ResourceContainer
import org.unfoldingword.tools.logger.Logger
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

class DefaultTranslateComponent(
    componentContext: ComponentContext,
    translationId: String,
    initialViewMode: TranslationViewMode?,
    mergeFilterOn: Boolean,
    private val sharedFlow: SharedFlow<RootComponent.SharedEvent>,
    private val onResult: (TranslateComponent.Result) -> Unit
) : TranslateComponent,
    ComponentContext by componentContext,
    ComponentScope, ProgressOwner, KoinComponent {

    private val application: Application by inject()
    private val translator: Translator by inject()
    private val library: Door43Client by inject()
    private val prefRepository: IPreferenceRepository by inject()
    private val typography: Typography by inject()
    private val assetsProvider: AssetsProvider by inject()

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

    private val sourceContainer: ResourceContainer?
        get() = _sharedState.value.resourceContainer

    val initialized: Boolean
        get() = this::targetTranslation.isInitialized

    override val stack: Value<ChildStack<*, TranslateComponent.Child>> = childStack(
        source = navigation,
        serializer = TranslateComponent.Config.serializer(),
        initialConfiguration = TranslateComponent.Config.Loading,
        handleBackButton = true,
        childFactory = ::child
    )

    init {
        translator.getTargetTranslation(translationId)?.let { translation ->
            targetTranslation = translation

            val draftAvailable = draftIsAvailable()
            val lastViewMode = initialViewMode?.let {
                translator.setLastViewMode(targetTranslation.id, it)
                it
            } ?: translator.getLastViewMode(targetTranslation.id)

            val projectTitle = "${getProject()?.name} - ${targetTranslation.targetLanguageName}"

            commitOnDestroy.scheduleAutoCommit()

            state
                .map { it.viewMode }
                .distinctUntilChanged()
                .onEach {
                    val config = when (it) {
                        TranslationViewMode.LOADING -> TranslateComponent.Config.Loading
                        TranslationViewMode.READ -> TranslateComponent.Config.Read
                        TranslationViewMode.CHUNK -> TranslateComponent.Config.Chunk
                        TranslationViewMode.REVIEW -> TranslateComponent.Config.Review
                    }
                    navigation.replaceAll(config)
                }
                .launchIn(coroutineScope)

            _state.update {
                it.copy(
                    viewMode = lastViewMode,
                    mergeFilterOn = mergeFilterOn,
                    draftAvailable = draftAvailable,
                    showDraftAvailable = draftAvailable && targetTranslation.numTranslated == 0,
                    projectTitle = projectTitle
                )
            }

            launchWithProgress {
                ContainerCache.empty()
                initLastFocus()
                openUsedSourceTranslations()
                refreshSelectedResourceContainer()
            }
        } ?: run {
            Logger.e(
                this::class.simpleName,
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
    }

    private fun child(
        config: TranslateComponent.Config,
        componentContext: ComponentContext,
    ): TranslateComponent.Child = when (config) {
        is TranslateComponent.Config.Read -> TranslateComponent.Child.Read(
            component = DefaultReadModeComponent(
                componentContext = componentContext,
                sharedState = sharedState,
                loadChunks = ::loadChunks
            )
        )
        is TranslateComponent.Config.Chunk -> TranslateComponent.Child.Chunk(
            component = DefaultChunkModeComponent(
                componentContext = componentContext,
                sharedState = sharedState,
                loadChunks = ::loadChunks
            )
        )
        is TranslateComponent.Config.Review -> TranslateComponent.Child.Review(
            component = DefaultReviewModeComponent(
                componentContext = componentContext,
                sharedState = sharedState,
                eventSender = eventSender,
                loadChunks = ::loadChunks
            )
        )
        is TranslateComponent.Config.Loading -> TranslateComponent.Child.Loading(
            component = DefaultLoadingComponent()
        )
    }

    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        progressManager.runTask(message, block)
    }

    override fun restartAutoCommitTimer() {
        commitOnDestroy.scheduleAutoCommit()
    }

    override fun updateMergeFilter(on: Boolean) {
        _state.update { it.copy(mergeFilterOn = on) }
    }

    override fun onAction(action: TranslateComponent.Action) {
        when (action) {
            is TranslateComponent.Action.RemoveSource -> launchWithProgress {
                removeOpenSourceTranslation(action.sourceId)
            }
            is TranslateComponent.Action.SelectSource -> launchWithProgress {
                setSelectedResourceContainer(action.sourceId)
            }
            is TranslateComponent.Action.SaveLastViewMode -> setLastViewMode(action.viewMode)
            is TranslateComponent.Action.SaveLastFocus -> saveLastFocus(action.chapterId, action.frameId)
        }
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

    private suspend fun refreshSelectedResourceContainer() {
        getSelectedSourceTranslationId()?.let { sourceTranslationSlug ->
            setSelectedResourceContainer(sourceTranslationSlug)
        } ?: run {
            _sharedState.update {
                it.copy(resourceContainer = null, chunks = emptyList())
            }
        }
        refreshSourceTranslationTabs()
    }

    private fun draftIsAvailable(): Boolean {
        return library.index.findTranslations(
            targetTranslation.targetLanguage.slug,
            targetTranslation.projectId,
            null,
            "book",
            null,
            0,
            -1
        ).any { it.resource.slug != "udb" }
    }

    private suspend fun loadChunks(viewMode: TranslationViewMode): List<Chunk> {
        val isReadMode = viewMode == TranslationViewMode.READ
        val chunks = withContext(Dispatchers.IO) {
            val chunks = mutableListOf<Chunk>()
            sourceContainer?.let { source ->
                val sorter = SlugSorter()
                val chapterSlugs = sorter.sort(source.chapters())
                for (chapterSlug: String in chapterSlugs) {
                    val chunkSlugs = sorter.sort(source.chunks(chapterSlug))
                    for (chunkSlug in chunkSlugs) {
                        if (!isReadMode || !chunks.any { it.chapterSlug == chapterSlug }) {
                            chunks.add(Chunk(chapterSlug, chunkSlug, source, targetTranslation))
                        }
                    }
                }
            }
            chunks
        }
        return chunks
    }

    private fun setLastViewMode(mode: TranslationViewMode) {
        launchWithProgress {
            _state.update { it.copy(viewMode = mode) }
            _sharedState.update { it.copy(chunks = emptyList()) }
            translator.setLastViewMode(
                targetTranslationId = targetTranslation.id,
                viewMode = mode
            )
        }
    }

    private fun initLastFocus() {
        val chapter = translator.getLastFocusChapterId(targetTranslation.id)
        val frame = translator.getLastFocusFrameId(targetTranslation.id)
        _state.update { it.copy(lastFocusChapterId = chapter, lastFocusFrameId = frame) }
    }

    private fun saveLastFocus(chapterId: String, frameId: String?) {
        translator.setLastFocus(targetTranslation.id, chapterId, frameId)
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
                library,
                sourceTranslationId
            )

            if (resourceContainer == null) {
                val availableSources = getOpenSourceTranslations()
                    .filter { it != sourceTranslationId }
                    .toMutableList()

                while (availableSources.isNotEmpty()) {
                    val nextSource = availableSources.first()
                    resourceContainer = ContainerCache.cache(library, nextSource)
                    if (resourceContainer != null) {
                        break
                    } else {
                        availableSources.removeAt(0)
                    }
                }
            }

            resourceContainer?.let { rc ->
                translator.setSelectedSourceTranslation(
                    targetTranslation.id,
                    rc.slug
                )
                _sharedState.update { it.copy(resourceContainer = rc) }
            }
        }
    }

    private fun getTranslation(slug: String): Translation? {
        return library.index.getTranslation(slug)
    }

    private fun getResourceContainerLastModified(translation: Translation?): Int {
        return translation?.let {
            library.getResourceContainerLastModified(
                translation.language.slug,
                translation.project.slug,
                translation.resource.slug
            )
        } ?: -1
    }

    private fun getProject(): Project? {
        return library.index.getProject(
            deviceLanguageCode,
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
                sources.add(SourceTranslation(translation, modifiedAt))
            }
        }

        try {
            targetTranslation.setSourceTranslations(sources)
        } catch (e: JSONException) {
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
            val st: Translation? = library.index.getTranslation(slug)
            if (st != null) {
                var title = st.language.name + " " + st.resource.slug.uppercase(Locale.getDefault())

                // include the resource id if there are more than one
                val resources = library.index.getResources(
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
                onResult(TranslateComponent.Result.ExportToApp(result.file))
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
                _state.update {
                    it.copy(viewMode = TranslationViewMode.REVIEW, mergeFilterOn = true) // TODO Revise navigation for merge conflict
                }
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
                            this::class.simpleName,
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
                    this::class.simpleName,
                    "Failed to commit changes before closing translation",
                    e
                )
            }
        }
    }
}
