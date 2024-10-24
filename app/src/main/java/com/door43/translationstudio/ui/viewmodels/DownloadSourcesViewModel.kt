package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.door43.OnProgressListener
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.usecases.DownloadResourceContainers
import com.door43.usecases.GetAvailableSources
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DownloadSourcesViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    @Inject lateinit var getAvailableSources: GetAvailableSources
    @Inject lateinit var downloadResourceContainers: DownloadResourceContainers

    private val _progress = MutableLiveData<ProgressHelper.Progress?>()
    val progress: LiveData<ProgressHelper.Progress?> = _progress

    private val _availableSources = MutableLiveData<GetAvailableSources.Result?>()
    val availableSources: LiveData<GetAvailableSources.Result?> = _availableSources

    private val _downloadedSources = MutableLiveData<DownloadResourceContainers.Result?>()
    val downloadedSources: LiveData<DownloadResourceContainers.Result?> = _downloadedSources

    fun getAvailableSources(prefix: String) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress()
            _availableSources.value = withContext(Dispatchers.IO) {
                getAvailableSources.execute(prefix, object : OnProgressListener {
                    override fun onProgress(progress: Int, max: Int, message: String?) {
                        _progress.postValue(ProgressHelper.Progress(message, progress, max))
                    }
                    override fun onIndeterminate() {
                        _progress.postValue(ProgressHelper.Progress())
                    }
                })
            }
            _progress.value = null
        }
    }

    fun downloadSources(selected: List<String>) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress()
            _downloadedSources.value = withContext(Dispatchers.IO) {
                downloadResourceContainers.execute(selected, object : OnProgressListener {
                    override fun onProgress(progress: Int, max: Int, message: String?) {
                        _progress.postValue(ProgressHelper.Progress(message, progress, max))
                    }
                    override fun onIndeterminate() {
                        _progress.postValue(ProgressHelper.Progress())
                    }
                })
            }
            _progress.value = null
        }
    }
}