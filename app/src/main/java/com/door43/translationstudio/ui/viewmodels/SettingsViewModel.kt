package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.usecases.CheckForLatestRelease
import com.door43.usecases.DownloadLatestRelease
import com.door43.usecases.GogsLogout
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.unfoldingword.door43client.Door43Client
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: Application
) : AndroidViewModel(application) {

    @Inject lateinit var checkForLatestRelease: CheckForLatestRelease
    @Inject lateinit var downloadLatestRelease: DownloadLatestRelease
    @Inject lateinit var prefRepository: IPreferenceRepository
    @Inject lateinit var library: Door43Client
    @Inject lateinit var profile: Profile
    @Inject lateinit var logout: GogsLogout

    private val _progress = MutableLiveData<ProgressHelper.Progress?>()
    val progress: LiveData<ProgressHelper.Progress?> = _progress

    private val _latestRelease = MutableLiveData<CheckForLatestRelease.Result?>()
    val latestRelease: LiveData<CheckForLatestRelease.Result?> = _latestRelease

    private val _settings = MutableLiveData<Settings>()
    val settings: LiveData<Settings> get() = _settings

    private val _loggedOut = MutableLiveData<Boolean?>()
    val loggedOut: LiveData<Boolean?> get() = _loggedOut

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