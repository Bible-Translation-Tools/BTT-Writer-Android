package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.data.setDefaultPref
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.SettingsActivity
import com.door43.translationstudio.ui.SettingsActivity.Companion.KEY_PREF_CHECK_HARDWARE
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.usecases.MigrateTranslations
import com.door43.usecases.UpdateApp
import com.door43.util.RuntimeWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.unfoldingword.tools.logger.Logger

data class SplashModel(
    val progress: ProgressHelper.Progress? = null,
    val showHardwareWarning: Boolean = false,
    val showMigrationDialog: Boolean = false
)

sealed interface SplashEvent {
    data object NavigateToProfile : SplashEvent
    data object NavigateToCrashReporter : SplashEvent
    data object LaunchDirectoryPicker : SplashEvent
}

class SplashScreenViewModel(
    private val application: Application,
    private val updateApp: UpdateApp,
    private val prefRepository: IPreferenceRepository,
    private val migrateTranslations: MigrateTranslations
) : AndroidViewModel(application) {

    private val _model = MutableStateFlow(SplashModel())
    val model: StateFlow<SplashModel> = _model.asStateFlow()

    private val _events = Channel<SplashEvent>()
    val events = _events.receiveAsFlow()

    init {
        evaluateStartupPath()
    }

    private fun evaluateStartupPath() {
        if (checkHardware()) {
            val numProcessors = RuntimeWrapper.availableProcessors()
            val maxMem = RuntimeWrapper.maxMemory()

            if (numProcessors < App.MINIMUM_NUMBER_OF_PROCESSORS || maxMem < App.MINIMUM_REQUIRED_RAM) {
                _model.update { it.copy(showHardwareWarning = true) }
            } else {
                checkMigration()
            }
        } else {
            checkMigration()
        }
    }

    fun onHardwareWarningContinued() {
        _model.update { it.copy(showHardwareWarning = false) }
        checkMigration()
    }

    fun onHardwareWarningDismissedAndSaved() {
        saveHardwareCheck(false)
        _model.update { it.copy(showHardwareWarning = false) }
        checkMigration()
    }

    private fun checkMigration() {
        if (!checkMigrationShown()) {
            setMigrationShown(true)
            _model.update { it.copy(showMigrationDialog = true) }
        } else {
            startAppLogic()
        }
    }

    fun onMigrationAccepted() {
        _model.update { it.copy(showMigrationDialog = false) }
        viewModelScope.launch { _events.send(SplashEvent.LaunchDirectoryPicker) }
    }

    fun onMigrationDeclined() {
        _model.update { it.copy(showMigrationDialog = false) }
        startAppLogic()
    }

    fun onDirectoryPicked(uri: Uri?) {
        if (uri != null) {
            migrateOldAppdataFolder(uri)
        } else {
            setMigrationShown(false)
            _model.update { it.copy(showMigrationDialog = true) }
        }
    }

    private fun startAppLogic() {
        val files = Logger.listStacktraces()
        if (files.isNotEmpty()) {
            viewModelScope.launch {
                _events.send(SplashEvent.NavigateToCrashReporter)
            }
            return
        }

        if (_model.value.progress == null) {
            updateApp()
        }
    }

    fun onMigrationFinished() {
        _model.update { it.copy(progress = null) }
        startAppLogic()
    }

    fun onUpdateFinished() {
        viewModelScope.launch { _events.send(SplashEvent.NavigateToProfile) }
    }

    fun updateApp() {
        viewModelScope.launch {
            _model.update {
                it.copy(progress = ProgressHelper.Progress(
                    application.getString(R.string.updating_app)
                ))
            }
            withContext(Dispatchers.IO) {
                updateApp.execute { progress, max, message ->
                    _model.update {
                        it.copy(progress = ProgressHelper.Progress(
                            message,
                            progress,
                            max
                        ))
                    }
                }
            }
            onUpdateFinished()
        }
    }

    fun migrateOldAppdataFolder(appDataFolder: Uri) {
        viewModelScope.launch {
            _model.update {
                it.copy(progress = ProgressHelper.Progress(
                    application.getString(R.string.migrating_translations)
                ))
            }
            withContext(Dispatchers.IO) {
                migrateTranslations.execute(appDataFolder) { progress, max, message ->
                    _model.update {
                        it.copy(progress = ProgressHelper.Progress(
                            message,
                            progress,
                            max
                        ))
                    }
                }
            }
            onMigrationFinished()
        }
    }

    fun checkHardware(): Boolean {
        return prefRepository.getDefaultPref(
            KEY_PREF_CHECK_HARDWARE,
            true
        )
    }

    fun saveHardwareCheck(check: Boolean) {
        prefRepository.setDefaultPref(KEY_PREF_CHECK_HARDWARE, check)
    }

    fun checkMigrationShown(): Boolean {
        return prefRepository.getDefaultPref(
            SettingsActivity.KEY_PREF_MIGRATE_OLD_APP,
            false
        )
    }

    fun setMigrationShown(shown: Boolean) {
        prefRepository.setDefaultPref(SettingsActivity.KEY_PREF_MIGRATE_OLD_APP, shown)
    }
}