package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.usecases.CheckForLatestRelease
import com.door43.usecases.DownloadLatestRelease
import com.door43.usecases.UploadCrashReport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class CrashReporterViewModel @Inject constructor(
    private val application: Application
) : AndroidViewModel(application) {

    @Inject lateinit var checkForLatestRelease: CheckForLatestRelease
    @Inject lateinit var downloadLatestRelease: DownloadLatestRelease
    @Inject lateinit var uploadCrashReport: UploadCrashReport

    private val _progress = MutableLiveData<ProgressHelper.Progress?>()
    val progress: LiveData<ProgressHelper.Progress?> = _progress

    private val _latestRelease = MutableLiveData<CheckForLatestRelease.Result?>()
    val latestRelease: LiveData<CheckForLatestRelease.Result?> = _latestRelease

    private val _crashReportUploaded = MutableLiveData<Boolean?>()
    val crashReportUploaded: LiveData<Boolean?> = _crashReportUploaded

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

    fun uploadCrashReport(message: String) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(
                application.resources.getString(R.string.uploading)
            )
            _crashReportUploaded.value = withContext(Dispatchers.IO) {
                uploadCrashReport.execute(message)
            }
            _progress.value = null
        }
    }

    fun downloadLatestRelease() {
        _latestRelease.value?.release?.let { release ->
            viewModelScope.launch {
                _progress.value = ProgressHelper.Progress(
                    application.resources.getString(R.string.downloading)
                )
                withContext(Dispatchers.IO) {
                    downloadLatestRelease.execute(release)
                }
                _progress.value = null
            }
        }
    }
}