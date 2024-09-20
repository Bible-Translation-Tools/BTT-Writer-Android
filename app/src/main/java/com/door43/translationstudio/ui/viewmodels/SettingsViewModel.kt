package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.usecases.CheckForLatestRelease
import com.door43.usecases.DownloadLatestRelease
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

    private val _progress = MutableLiveData<ProgressHelper.Progress?>()
    val progress: LiveData<ProgressHelper.Progress?> = _progress

    private val _latestRelease = MutableLiveData<CheckForLatestRelease.Result?>()
    val latestRelease: LiveData<CheckForLatestRelease.Result?> = _latestRelease

    private val _settings = MutableLiveData<Settings>()
    val settings: LiveData<Settings> get() = _settings

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
}