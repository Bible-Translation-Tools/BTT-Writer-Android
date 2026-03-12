package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.door43.translationstudio.R
import com.door43.translationstudio.core.ProgressManager
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.TaskHandle
import com.door43.translationstudio.ui.launchWithProgress
import com.door43.usecases.CheckForLatestRelease
import com.door43.usecases.DownloadLatestRelease
import com.door43.usecases.UploadCrashReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

data class CrashState(
    val result: CheckForLatestRelease.Result? = null,
    val crashReportUploaded: Boolean? = null
)

class CrashReporterViewModel(
    private val checkForLatestRelease: CheckForLatestRelease,
    private val downloadLatestRelease: DownloadLatestRelease,
    private val uploadCrashReport: UploadCrashReport
) : ViewModel(), KoinComponent, ProgressOwner {

    private val application: Application by inject()

    private val progressManager = ProgressManager(viewModelScope)
    override val progress get() = progressManager.progress

    private val _state = MutableStateFlow(CrashState())
    val state: StateFlow<CrashState> = _state.asStateFlow()

    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        progressManager.runTask(message, block)
    }

    fun checkForLatestRelease() {
        launchWithProgress(
            application.resources.getString(R.string.checking_for_updates)
        ) {
            val result = withContext(Dispatchers.IO) {
                checkForLatestRelease.execute()
            }
            _state.update {
                it.copy(result = result)
            }
        }
    }

    fun uploadCrashReport(message: String) {
        launchWithProgress(
            application.resources.getString(R.string.uploading)
        ) {
            val uploaded = withContext(Dispatchers.IO) {
                uploadCrashReport.execute(message)
            }
            _state.update {
                it.copy(crashReportUploaded = uploaded)
            }
        }
    }

    fun downloadLatestRelease() {
        _state.value.result?.release?.let { release ->
            launchWithProgress(
                application.resources.getString(R.string.downloading)
            ) {
                withContext(Dispatchers.IO) {
                    downloadLatestRelease.execute(release)
                }
            }
        }
    }
}