package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.usecases.CheckForLatestRelease
import com.door43.usecases.DownloadLatestRelease
import com.door43.usecases.UploadCrashReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CrashModel(
    val result: CheckForLatestRelease.Result? = null,
    val progress: ProgressHelper.Progress? = null,
    val crashReportUploaded: Boolean? = null
)

class CrashReporterViewModel(
    private val application: Application,
    private val checkForLatestRelease: CheckForLatestRelease,
    private val downloadLatestRelease: DownloadLatestRelease,
    private val uploadCrashReport: UploadCrashReport
) : AndroidViewModel(application) {

    private val _model = MutableStateFlow(CrashModel())
    val model: StateFlow<CrashModel> = _model.asStateFlow()

    fun checkForLatestRelease() {
        viewModelScope.launch {
            _model.update {
                it.copy(progress = ProgressHelper.Progress(
                    application.resources.getString(R.string.checking_for_updates)
                ))
            }
            val result = withContext(Dispatchers.IO) {
                checkForLatestRelease.execute()
            }
            _model.update {
                it.copy(result = result, progress = null)
            }
        }
    }

    fun uploadCrashReport(message: String) {
        viewModelScope.launch {
            _model.update {
                it.copy(progress = ProgressHelper.Progress(
                    application.resources.getString(R.string.uploading)
                ))
            }
            val uploaded = withContext(Dispatchers.IO) {
                uploadCrashReport.execute(message)
            }
            _model.update {
                it.copy(crashReportUploaded = uploaded, progress = null)
            }
        }
    }

    fun downloadLatestRelease() {
        _model.value.result?.release?.let { release ->
            viewModelScope.launch {
                _model.update {
                    it.copy(progress = ProgressHelper.Progress(
                        application.resources.getString(R.string.downloading)
                    ))
                }
                withContext(Dispatchers.IO) {
                    downloadLatestRelease.execute(release)
                }
                _model.update { it.copy(progress = null) }
            }
        }
    }
}