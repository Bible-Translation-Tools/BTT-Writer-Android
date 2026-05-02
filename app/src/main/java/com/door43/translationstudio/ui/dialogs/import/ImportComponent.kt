package com.door43.translationstudio.ui.dialogs.import

import android.app.Application
import android.net.Uri
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.Platform
import com.door43.translationstudio.R
import com.door43.translationstudio.core.ComponentScope
import com.door43.translationstudio.core.Progress
import com.door43.translationstudio.core.ProgressManager
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TargetTranslationMigrator
import com.door43.translationstudio.core.TaskHandle
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.core.launchWithProgress
import com.door43.translationstudio.ui.home.RepositoryItem
import com.door43.usecases.AdvancedGogsRepoSearch
import com.door43.usecases.CloneRepository
import com.door43.usecases.ImportProjects
import com.door43.usecases.RegisterSSHKeys
import com.door43.util.FileUtilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bibletranslationtools.gogsclient.Repository
import org.bibletranslationtools.logger.Logger
import org.bibletranslationtools.resourcecatalog.ResourceCatalogClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.IOException
import java.security.InvalidParameterException

data class MergeConflict(
    val translation: TargetTranslation,
    val hasMergeConflict: Boolean,
    val isFromServer: Boolean,
    val onResolve: () -> Unit,
    val onOverwrite: () -> Unit,
    val onCancel: () -> Unit
)

interface ImportComponent {
    val state: StateFlow<State>
    val progress: StateFlow<Progress?>
    val event: Flow<Event>

    fun importUsfm(fileUri: Uri)
    fun importProject(uri: Uri, overwrite: Boolean)
    fun importSource(uri: Uri, overwrite: Boolean)
    fun importBackup(backup: File)
    fun importRepo(repo: RepositoryItem, accepted: Boolean, overwrite: Boolean)
    fun searchRepositories(user: String, repo: String)
    fun registerKeys()
    fun clearResult()
    fun clearMergeConflict()
    fun clearSourceConflict()
    fun clearImportRepo()

    data class State(
        val mergeConflict: MergeConflict? = null,
        val sourceConflict: ImportProjects.ImportSourceResult? = null,
        val resultMessage: Pair<String, String>? = null,
        val backups: List<File> = emptyList(),
        val repositories: List<RepositoryItem> = emptyList(),
        val repoToImport: RepositoryItem? = null
    )

    sealed interface Event {
        data object AuthRequested : Event
    }

    sealed interface Result {
        data class MergeConflict(val translationId: String) : Result
        data class ProjectsImported(val translationIds: List<String>) : Result
        data class OpenUsfmImport(val fileUri: String) : Result
    }
}

class DefaultImportComponent(
    componentContext: ComponentContext,
    projectUri: Uri?,
    private val onResult: (ImportComponent.Result) -> Unit
) : ImportComponent,
    ComponentContext by componentContext,
    ComponentScope, ProgressOwner, KoinComponent {

    private val application: Application by inject()
    private val translator: Translator by inject()
    private val advancedGogsRepoSearch: AdvancedGogsRepoSearch by inject()
    private val cloneRepository: CloneRepository by inject()
    private val registerSSHKeys: RegisterSSHKeys by inject()
    private val importProjects: ImportProjects by inject()
    private val catalogClient: ResourceCatalogClient by inject()
    private val directoryProvider: IDirectoryProvider by inject()
    private val targetTranslationMigrator: TargetTranslationMigrator by inject()
    private val platform: Platform by inject()

    override val coroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val progressManager = ProgressManager(coroutineScope)
    override val progress get() = progressManager.progress

    private val _state = MutableStateFlow(ImportComponent.State())
    override val state = _state.asStateFlow()

    private val _event = Channel<ImportComponent.Event>(Channel.BUFFERED)
    override val event = _event.receiveAsFlow()

    init {
        coroutineScope.launch {
            val backups = getBackupTranslations().sorted()
            _state.update { it.copy(backups = backups) }
        }

        projectUri?.let { uri ->
            importProject(uri, false)
        }

        lifecycle.doOnDestroy {
            coroutineScope.cancel()
        }
    }

    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        progressManager.runTask(message, block)
    }

    override fun importUsfm(fileUri: Uri) {
        onResult(ImportComponent.Result.OpenUsfmImport(fileUri.toString()))
    }

    override fun importProject(uri: Uri, overwrite: Boolean) {
        importProject(uri, overwrite, application.getString(R.string.import_source_text))
    }

    override fun importSource(uri: Uri, overwrite: Boolean) {
        launchWithProgress(
            application.getString(R.string.import_source_text)
        ) {
            val result = withContext(Dispatchers.IO) {
                importProjects.importSource(uri, overwrite)
            }

            when {
                result.success -> {
                    val dirName = FileUtilities.getDirectoryName(application, uri)
                    updateResult(
                        application.getString(R.string.success),
                        application.getString(R.string.import_success) + " $dirName"
                    )
                }
                result.hasConflict -> {
                    _state.update { it.copy(sourceConflict = result) }
                }
                else -> {
                    updateResult(
                        application.getString(R.string.could_not_import),
                        result.error ?: "Unknown error"
                    )
                }
            }
        }
    }

    override fun importBackup(backup: File) {
        val uri = Uri.fromFile(backup)
        val message = application.resources.getString(
            R.string.importing_file,
            backup.name
        )
        importProject(uri, false, message)
    }

    override fun importRepo(repo: RepositoryItem, accepted: Boolean, overwrite: Boolean) {
        launchWithProgress(
            application.getString(R.string.cloning_repository)
        ) { handle ->
            if (repo.isSupported || accepted) {
                cloneRepository(repo, overwrite, handle)
            } else {
                _state.update { it.copy(repoToImport = repo) }
            }
        }
    }

    override fun searchRepositories(user: String, repo: String) {
        launchWithProgress(
            application.getString(R.string.searching_repositories)
        ) { handle ->
            val result = withContext(Dispatchers.IO) {
                advancedGogsRepoSearch.execute(user, repo, 50) { progress, message ->
                    handle.update(progress, message)
                }
            }
            _state.update { it.copy(repositories = result.map(::mapRepository)) }
        }
    }

    override fun registerKeys() {
        forceRegisterSSHKeys()
    }

    override fun clearResult() {
        _state.update { it.copy(resultMessage = null, repositories = emptyList()) }
    }

    override fun clearMergeConflict() {
        _state.update { it.copy(mergeConflict = null) }
    }

    override fun clearSourceConflict() {
        _state.update { it.copy(sourceConflict = null) }
    }

    override fun clearImportRepo() {
        _state.update { it.copy(repoToImport = null) }
    }

    private fun importProject(
        uri: Uri,
        overwrite: Boolean,
        message: String
    ) {
        launchWithProgress(message) { handle ->
            val filename = FileUtilities.getFileName(application, uri)
            val isTstudio = filename.contains(Translator.TSTUDIO_EXTENSION, ignoreCase = true)
            val isZip = filename.contains(Translator.ZIP_EXTENSION, ignoreCase = true)
            if (isTstudio || isZip) {
                if (overwrite) resetToMaster()

                _state.update { it.copy(mergeConflict = null) }

                val result = withContext(Dispatchers.IO) {
                    importProjects.importProject(uri, overwrite) { progress, message ->
                        handle.update(progress, message)
                    }
                }
                when {
                    result.success && result.alreadyExists && !overwrite -> {
                        _state.update {
                            it.copy(mergeConflict = MergeConflict(
                                translation = getTargetTranslation(
                                    result.importedSlug!!
                                ),
                                hasMergeConflict = result.hasMergeConflict,
                                isFromServer = false,
                                onResolve = { resolveMergeConflict() },
                                onOverwrite = {
                                    launchWithProgress {
                                        importProject(result.filePath, true)
                                    }
                                },
                                onCancel = {
                                    coroutineScope.launch {
                                        resetToMaster()
                                    }
                                }
                            ))
                        }
                    }
                    result.success -> {
                        result.importedSlug?.let {
                            onResult(ImportComponent.Result.ProjectsImported(listOf(it)))
                        }
                        updateResult(
                            application.getString(R.string.import_from_storage),
                            application.getString(R.string.import_success) +
                                    "\n${result.readablePath}"
                        )
                    }
                    result.invalidFileName -> {
                        updateResult(
                            application.getString(R.string.import_from_storage),
                            application.getString(R.string.invalid_file) +
                                    "\n${result.readablePath}"
                        )
                    }
                    else -> {
                        updateResult(
                            application.getString(R.string.import_from_storage),
                            application.getString(R.string.import_failed) +
                                    "\n${result.readablePath}"
                        )
                    }
                }
            } else {
                updateResult(
                    application.getString(R.string.import_from_storage),
                    "${application.getString(R.string.invalid_file)}\n$filename"
                )
            }
        }
    }

    private fun mapRepository(repository: Repository): RepositoryItem {
        val repoName = repository.fullName.split("/".toRegex())
        var projectName = ""
        var languageName = ""
        var code = "en"
        var direction = "ltr"
        var unsupportedTag = ""
        var targetTranslationSlug = ""

        if (repoName.isNotEmpty()) {
            targetTranslationSlug = repoName[repoName.size - 1]
            try {
                val projectSlug = TargetTranslation.getProjectSlugFromId(targetTranslationSlug)
                val targetLanguageSlug = TargetTranslation.getTargetLanguageSlugFromId(
                    targetTranslationSlug
                )
                val resourceTypeSlug = TargetTranslation.getResourceTypeFromId(
                    targetTranslationSlug
                )
                if (resourceTypeSlug != "text") {
                    unsupportedTag = when (resourceTypeSlug) {
                        "tw" -> application.getString(R.string.translation_words)
                        "tn" -> application.getString(R.string.label_translation_notes)
                        "tq" -> application.getString(R.string.translation_questions)
                        else -> application.getString(R.string.unsupported)
                    }
                }

                val project = catalogClient.library.getProject(
                    languageSlug = platform.deviceLanguageCode,
                    projectSlug = projectSlug,
                    enableDefaultLanguage = true
                )
                projectName = project?.name ?: targetTranslationSlug
                val targetLanguage = catalogClient.library.getTargetLanguage(targetLanguageSlug)
                if (targetLanguage != null) {
                    languageName = targetLanguage.name
                    direction = targetLanguage.direction
                    code = targetLanguage.slug
                } else {
                    languageName = targetLanguageSlug
                }
            } catch (e: StringIndexOutOfBoundsException) {
                e.printStackTrace()
                projectName = targetTranslationSlug
                unsupportedTag = application.getString(R.string.unsupported)
            }
        }

        return RepositoryItem(
            languageName,
            projectName,
            targetTranslationSlug,
            code,
            direction,
            repository.fullName,
            repository.htmlUrl,
            repository.isPrivate,
            unsupportedTag
        )
    }

    private suspend fun cloneRepository(
        repo: RepositoryItem,
        overwrite: Boolean,
        handle: TaskHandle
    ) {
        if (overwrite) resetToMaster()

        _state.update { it.copy(mergeConflict = null, repoToImport = null) }

        val result = withContext(Dispatchers.IO) {
            cloneRepository.execute(repo.url) { progress, message ->
                handle.update(progress, message)
            }
        }

        when (result.status) {
            CloneRepository.Status.SUCCESS -> {
                result.cloneDir?.let {
                    val clonedDir = targetTranslationMigrator.migrate(it)

                    Logger.i(this.javaClass.name, "Repository cloned from $it")

                    clonedDir?.let { tempRepoDir ->
                        TargetTranslation.open(tempRepoDir)?.let { tempTargetTranslation ->
                            val existingTargetTranslation = translator.getTargetTranslation(
                                tempTargetTranslation.id
                            )

                            if (existingTargetTranslation != null && !overwrite) {
                                try {
                                    val mergedWithoutConflicts = existingTargetTranslation.merge(
                                        tempRepoDir,
                                        null
                                    )
                                    _state.update { state ->
                                        state.copy(
                                            mergeConflict = MergeConflict(
                                                translation = existingTargetTranslation,
                                                hasMergeConflict = !mergedWithoutConflicts,
                                                isFromServer = true,
                                                onResolve = { resolveMergeConflict() },
                                                onOverwrite = {
                                                    launchWithProgress { handle ->
                                                        cloneRepository(
                                                            repo = repo,
                                                            overwrite = true,
                                                            handle = handle
                                                        )
                                                    }
                                                },
                                                onCancel = {
                                                    coroutineScope.launch {
                                                        resetToMaster()
                                                    }
                                                }
                                            )
                                        )
                                    }
                                } catch (e: Exception) {
                                    Logger.e(
                                        this.javaClass.name,
                                        "Failed to merge translation",
                                        e
                                    )
                                    reportImportFailed("Failed to merge translation")
                                }
                            } else {
                                try {
                                    translator.restoreTargetTranslation(tempTargetTranslation)
                                    onResult(
                                        ImportComponent.Result.ProjectsImported(
                                            listOf(tempTargetTranslation.id)
                                        )
                                    )
                                    updateResult(
                                        title = application.getString(R.string.import_from_door43),
                                        message = application.getString(R.string.title_import_success)
                                    )
                                } catch (e: IOException) {
                                    Logger.e(
                                        this.javaClass.name,
                                        "Failed to overite translation",
                                        e
                                    )
                                    reportImportFailed("Failed to overite translation")
                                }
                            }
                        } ?: run {
                            Logger.e(this.javaClass.name, "Failed to open the online backup")
                            reportImportFailed("Failed to open the online backup")
                        }
                    } ?: run {
                        Logger.e(this.javaClass.name, "Failed to migrate project")
                        reportImportFailed("Failed to migrate project")
                    }
                }
            }
            CloneRepository.Status.AUTH_FAILURE -> {
                Logger.i(this.javaClass.name, "Authentication failed")
                if (!directoryProvider.hasSSHKeys()) {
                    registerSSHKeys(false, handle) {
                        cloneRepository(repo, overwrite, handle)
                    }
                } else {
                    _state.update { it.copy(repoToImport = repo) }
                    _event.trySend(ImportComponent.Event.AuthRequested)
                }
            }
            else -> {
                updateResult(
                    title = application.getString(R.string.error),
                    message = application.getString(R.string.restore_failed)
                )
            }
        }
    }

    private suspend fun resetToMaster() {
        val mergeConflict = _state.value.mergeConflict ?: return
        withContext(Dispatchers.IO) {
            mergeConflict.translation.resetToMasterBackup()
        }
    }

    private fun resolveMergeConflict() {
        val result = _state.value.mergeConflict ?: return
        if (result.hasMergeConflict) {
            onResult(ImportComponent.Result.MergeConflict(result.translation.id))
        } else {
            val title = if (result.isFromServer) {
                application.getString(R.string.import_from_door43)
            } else application.getString(R.string.import_from_storage)
            updateResult(
                title,
                application.getString(R.string.import_success) +
                        "\n${result.translation.id}"
            )
        }
    }

    private fun updateResult(title: String, message: String) {
        _state.update { it.copy(resultMessage = title to message) }
    }

    private suspend fun getBackupTranslations(): List<File> {
        return withContext(Dispatchers.IO) {
            directoryProvider
                .backupsDir
                .listFiles()
                ?.asList()
                ?.filter { it.length() > 0 }
                ?: listOf()
        }
    }

    private fun forceRegisterSSHKeys() {
        launchWithProgress { handle ->
            registerSSHKeys(true, handle) {
                _state.value.repoToImport?.let {
                    cloneRepository(it, false, handle)
                }
            }
        }
    }

    private suspend fun registerSSHKeys(
        force: Boolean,
        handle: TaskHandle,
        onSuccess: suspend () -> Unit
    ) {
        handle.update(
            value = -1f,
            message = application.getString(R.string.registering_keys)
        )
        val registered = withContext(Dispatchers.IO) {
            registerSSHKeys.execute(force) { progress, message ->
                handle.update(progress, message)
            }
        }
        if (registered) {
            Logger.i(this.javaClass.name, "SSH keys were registered with the server")
            onSuccess()
        } else {
            _event.trySend(ImportComponent.Event.AuthRequested)
        }
    }

    private fun reportImportFailed(details: String) {
        updateResult(
            title = application.getString(R.string.error),
            message = application.getString(R.string.restore_failed, details)
        )
    }

    private fun getTargetTranslation(translationID: String): TargetTranslation {
        return translator.getTargetTranslation(translationID)
            ?: throw InvalidParameterException("The target translation '$translationID' is invalid")
    }
}
