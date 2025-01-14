package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.data.setDefaultPref
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.SettingsActivity
import com.door43.translationstudio.ui.SettingsActivity.Companion.KEY_PREF_CHECK_HARDWARE
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.usecases.MigrateTranslations
import com.door43.usecases.UpdateApp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.unfoldingword.door43client.Door43Client
import javax.inject.Inject

@HiltViewModel
class SplashScreenViewModel @Inject constructor(
    private val application: Application
) : AndroidViewModel(application) {

    @Inject
    lateinit var updateApp: UpdateApp
    @Inject
    lateinit var migrateTranslations: MigrateTranslations
    @Inject
    lateinit var prefRepository: IPreferenceRepository
    @Inject
    lateinit var library: Door43Client

    private val _progress = MutableLiveData<ProgressHelper.Progress?>()
    val progress: LiveData<ProgressHelper.Progress?> = _progress

    private val _updateFinished = MutableLiveData(false)
    val updateFinished: LiveData<Boolean> = _updateFinished

    private val _migrationFinished = MutableLiveData(false)
    val migrationFinished: LiveData<Boolean> = _migrationFinished

    fun updateApp() {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(
                application.getString(R.string.updating_app)
            )
            withContext(Dispatchers.IO) {
                updateApp.execute { progress, max, message ->
                    _progress.postValue(
                        ProgressHelper.Progress(
                            message,
                            progress,
                            max
                        )
                    )
                }
            }
            _updateFinished.value = true
            _progress.value = null
        }
    }

    fun migrateOldAppdataFolder(appDataFolder: Uri) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(
                application.getString(R.string.migrating_translations)
            )
            withContext(Dispatchers.IO) {
                migrateTranslations.execute(appDataFolder) { progress, max, message ->
                    _progress.postValue(
                        ProgressHelper.Progress(
                            message,
                            progress,
                            max
                        )
                    )
                }
            }
            _progress.value = null
            _migrationFinished.value = true
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