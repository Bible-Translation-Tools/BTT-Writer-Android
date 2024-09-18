package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.door43.usecases.CheckForLatestRelease
import com.door43.usecases.UploadFeedback
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class FeedbackViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    @Inject lateinit var checkForLatestRelease: CheckForLatestRelease
    @Inject lateinit var uploadFeedback: UploadFeedback

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _latestRelease = MutableLiveData<CheckForLatestRelease.Result>()
    val latestRelease: LiveData<CheckForLatestRelease.Result> = _latestRelease

    private val _success = MutableLiveData<Boolean?>()
    val success: LiveData<Boolean?> = _success

    private val jobs = mutableListOf<Job>()

    fun checkForLatestRelease() {
        viewModelScope.launch {
            _loading.value = true
            _latestRelease.value = withContext(Dispatchers.IO) {
                checkForLatestRelease.execute()
            }
        }.also(jobs::add)
    }

    fun uploadFeedback(message: String) {
        viewModelScope.launch {
            _success.value = withContext(Dispatchers.IO) {
                uploadFeedback.execute(message)
            }
            _loading.value = false
        }.also(jobs::add)
    }

    fun clearResults() {
        _latestRelease.value = null
        _success.value = null
    }

    fun cancelJobs() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

}