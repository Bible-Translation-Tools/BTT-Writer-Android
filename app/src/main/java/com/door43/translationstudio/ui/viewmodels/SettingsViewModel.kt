package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.usecases.CheckForLatestRelease
import com.door43.usecases.DownloadLatestRelease
import com.door43.usecases.GogsLogout
import com.door43.usecases.MigrateTranslations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.unfoldingword.door43client.Door43Client

class SettingsViewModel(
    private val application: Application,
    private val checkForLatestRelease: CheckForLatestRelease,
    private val downloadLatestRelease: DownloadLatestRelease,
    private val library: Door43Client,
    private val profile: Profile,
    private val logout: GogsLogout,
    private val migrateTranslations: MigrateTranslations
) : AndroidViewModel(application) {

    private val _progress = MutableLiveData<ProgressHelper.Progress?>()
    val progress: LiveData<ProgressHelper.Progress?> = _progress

    private val _latestRelease = MutableLiveData<CheckForLatestRelease.Result?>()
    val latestRelease: LiveData<CheckForLatestRelease.Result?> = _latestRelease

    private val _settings = MutableLiveData<Settings>()
    val settings: LiveData<Settings> get() = _settings

    private val _loggedOut = MutableLiveData<Boolean?>()
    val loggedOut: LiveData<Boolean?> get() = _loggedOut

    private val _migrationFinished = MutableLiveData(false)
    val migrationFinished: LiveData<Boolean> = _migrationFinished

    fun checkForLatestRelease() {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(
                application.resources.getString(R.string.checking_for_updates)
            )
            _latestRelease.value = withContext(Dispatchers.IO) {
                checkForLatestRelease.execute()
            }
            _progress.value = null
        }
    }

    fun migrateOldAppData(appDataFolder: Uri) {
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

    fun updateLanguageUrl(url: String) {
        library.updateLanguageUrl(url)
    }

    fun downloadLatestRelease(release: CheckForLatestRelease.Release) {
        downloadLatestRelease.execute(release)
    }

    fun logout() {
        if (profile.gogsUser != null) {
            viewModelScope.launch {
                _progress.value = ProgressHelper.Progress(
                    application.resources.getString(R.string.log_out)
                )
                withContext(Dispatchers.IO) {
                    logout.execute()
                    profile.logout()
                    _loggedOut.postValue(true)
                }
                _progress.value = null
            }
        }
    }
}