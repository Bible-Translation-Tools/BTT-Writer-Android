package com.door43.translationstudio.ui.splash

import android.app.Application
import android.net.Uri
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.data.setDefaultPref
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import com.door43.translationstudio.core.ProgressManager
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.TaskHandle
import com.door43.translationstudio.ui.launchWithProgress
import com.door43.translationstudio.ui.navigation.ComponentScope
import com.door43.usecases.MigrateTranslations
import com.door43.usecases.UpdateApp
import com.door43.util.RuntimeWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.unfoldingword.tools.logger.Logger

class DefaultSplashComponent(
    componentContext: ComponentContext,
    private val result: (SplashComponent.Result) -> Unit,
) : SplashComponent,
    ComponentContext by componentContext,
    ComponentScope, ProgressOwner, KoinComponent {

    override val coroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val application: Application by inject()
    private val prefRepository: IPreferenceRepository by inject()
    private val migrateTranslations: MigrateTranslations by inject()
    private val updateApp: UpdateApp by inject()

    private val _state = MutableStateFlow(SplashComponent.State())
    override val state: StateFlow<SplashComponent.State> = _state.asStateFlow()

    private val _event = Channel<SplashComponent.Event>()
    override val event = _event.receiveAsFlow()

    private val progressManager = ProgressManager(coroutineScope)
    override val progress get() = progressManager.progress

    init {
        evaluateStartupPath()

        lifecycle.doOnDestroy {
            coroutineScope.cancel()
        }
    }

    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        progressManager.runTask(message, block)
    }

    override fun onHardwareWarningContinued() {
        _state.update { it.copy(showHardwareWarning = false) }
        checkMigration()
    }

    override fun onHardwareWarningDismissedAndSaved() {
        _state.update { it.copy(showHardwareWarning = false) }
        saveHardwareCheck(false)
        checkMigration()
    }

    override fun onMigrationAccepted() {
        _state.update { it.copy(showMigrationDialog = false) }
        _event.trySend(SplashComponent.Event.OpenDirToMigrate)
    }

    override fun onMigrationDeclined() {
        _state.update { it.copy(showMigrationDialog = false) }
        startAppLogic()
    }

    override fun performMigrate(uri: Uri?) {
        if (uri != null) {
            migrateOldAppdataFolder(uri)
        } else {
            setMigrationShown(false)
            _state.update { it.copy(showMigrationDialog = true) }
        }
    }

    private fun evaluateStartupPath() {
        if (checkHardware()) {
            val numProcessors = RuntimeWrapper.availableProcessors
            val maxMem = RuntimeWrapper.maxMemory

            if (numProcessors < App.MINIMUM_NUMBER_OF_PROCESSORS || maxMem < App.MINIMUM_REQUIRED_RAM) {
                _state.update { it.copy(showHardwareWarning = true) }
            } else {
                checkMigration()
            }
        } else {
            checkMigration()
        }
    }

    private fun checkHardware(): Boolean {
        return prefRepository.getDefaultPref(
            IPreferenceRepository.KEY_PREF_CHECK_HARDWARE,
            true
        )
    }

    private fun checkMigration() {
        if (!checkMigrationShown()) {
            setMigrationShown(true)
            _state.update { it.copy(showMigrationDialog = true) }
        } else {
            startAppLogic()
        }
    }

    private fun checkMigrationShown(): Boolean {
        return prefRepository.getDefaultPref(
            IPreferenceRepository.KEY_PREF_MIGRATE_OLD_APP,
            false
        )
    }

    private fun setMigrationShown(shown: Boolean) {
        prefRepository.setDefaultPref(IPreferenceRepository.KEY_PREF_MIGRATE_OLD_APP, shown)
    }

    private fun saveHardwareCheck(check: Boolean) {
        prefRepository.setDefaultPref(IPreferenceRepository.KEY_PREF_CHECK_HARDWARE, check)
    }

    private fun startAppLogic() {
        val files = Logger.listStacktraces()
        if (files.isNotEmpty()) {
            result(SplashComponent.Result.NavigateToCrashReporter)
            return
        }
        updateApp()
    }

    private fun updateApp() {
        launchWithProgress(
            application.getString(R.string.updating_app)
        ) { handle ->
            withContext(Dispatchers.IO) {
                updateApp.execute { progress, message ->
                    println("Update in progress $progress")
                    handle.update(progress, message)
                }
            }
            onUpdateFinished()
        }
    }

    private fun onUpdateFinished() {
        result(SplashComponent.Result.NavigateToProfile)
    }

    private fun onMigrationFinished() {
        startAppLogic()
    }

    private fun migrateOldAppdataFolder(appDataFolder: Uri) {
        launchWithProgress(
            application.getString(R.string.migrating_translations)
        ) { handle ->
            withContext(Dispatchers.IO) {
                migrateTranslations.execute(appDataFolder) { progress, message ->
                    handle.update(progress, message)
                }
            }
            onMigrationFinished()
        }
    }
}
