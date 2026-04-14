package com.door43.translationstudio.ui.splash

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.data.setDefaultPref
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import com.door43.translationstudio.core.ProgressManager
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.TaskHandle
import com.door43.translationstudio.ui.launchWithProgress
import com.door43.translationstudio.ui.settings.SettingsActivity
import com.door43.translationstudio.ui.settings.SettingsActivity.Companion.KEY_PREF_CHECK_HARDWARE
import com.door43.usecases.MigrateTranslations
import com.door43.usecases.UpdateApp
import com.door43.util.RuntimeWrapper
import kotlinx.coroutines.Dispatchers
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

data class SplashState(
    val showHardwareWarning: Boolean = false,
    val showMigrationDialog: Boolean = false
)

sealed interface SplashEvent {
    data object NavigateToProfile : SplashEvent
    data object NavigateToCrashReporter : SplashEvent
    data object OpenDirToMigrate : SplashEvent
}

class SplashScreenViewModel(
    private val updateApp: UpdateApp,
    private val prefRepository: IPreferenceRepository,
    private val migrateTranslations: MigrateTranslations
) : ViewModel(), KoinComponent, ProgressOwner {

    private val application: Application by inject()

    private val _state = MutableStateFlow(SplashState())
    val state: StateFlow<SplashState> = _state.asStateFlow()

    private val _events = Channel<SplashEvent>()
    val events = _events.receiveAsFlow()

    private val progressManager = ProgressManager(viewModelScope)
    override val progress get() = progressManager.progress

    init {
        evaluateStartupPath()
    }

    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        progressManager.runTask(message, block)
    }

    fun onHardwareWarningContinued() {
        _state.update { it.copy(showHardwareWarning = false) }
        checkMigration()
    }

    fun onHardwareWarningDismissedAndSaved() {
        _state.update { it.copy(showHardwareWarning = false) }
        saveHardwareCheck(false)
        checkMigration()
    }

    fun onMigrationAccepted() {
        _state.update { it.copy(showMigrationDialog = false) }
        _events.trySend(SplashEvent.OpenDirToMigrate)
    }

    fun onMigrationDeclined() {
        _state.update { it.copy(showMigrationDialog = false) }
        startAppLogic()
    }

    fun performMigrate(uri: Uri?) {
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

    private fun checkMigration() {
        if (!checkMigrationShown()) {
            setMigrationShown(true)
            _state.update { it.copy(showMigrationDialog = true) }
        } else {
            startAppLogic()
        }
    }

    private fun startAppLogic() {
        val files = Logger.listStacktraces()
        if (files.isNotEmpty()) {
            _events.trySend(SplashEvent.NavigateToCrashReporter)
            return
        }

        updateApp()
    }

    private fun onMigrationFinished() {
        startAppLogic()
    }

    private fun onUpdateFinished() {
        _events.trySend(SplashEvent.NavigateToProfile)
    }

    private fun updateApp() {
        launchWithProgress(
            application.getString(R.string.updating_app)
        ) { handle ->
            withContext(Dispatchers.IO) {
                updateApp.execute { progress, message ->
                    handle.update(progress, message)
                }
            }
            onUpdateFinished()
        }
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

    private fun checkHardware(): Boolean {
        return prefRepository.getDefaultPref(
            KEY_PREF_CHECK_HARDWARE,
            true
        )
    }

    private fun saveHardwareCheck(check: Boolean) {
        prefRepository.setDefaultPref(KEY_PREF_CHECK_HARDWARE, check)
    }

    private fun checkMigrationShown(): Boolean {
        return prefRepository.getDefaultPref(
            SettingsActivity.KEY_PREF_MIGRATE_OLD_APP,
            false
        )
    }

    private fun setMigrationShown(shown: Boolean) {
        prefRepository.setDefaultPref(SettingsActivity.KEY_PREF_MIGRATE_OLD_APP, shown)
    }
}