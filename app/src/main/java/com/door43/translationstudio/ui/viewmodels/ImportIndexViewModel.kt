package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.usecases.DownloadIndex
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ImportIndexViewModel @Inject constructor(
    private val application: Application
) : AndroidViewModel(application) {

    @Inject lateinit var downloadIndex: DownloadIndex

    private val _progress = MutableLiveData<ProgressHelper.Progress?>()
    val progress: LiveData<ProgressHelper.Progress?> = _progress

    private val _indexDownloaded = MutableLiveData<Boolean?>()
    val indexDownloaded: LiveData<Boolean?> = _indexDownloaded

    fun downloadIndex() {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress()
            _indexDownloaded.value = withContext(Dispatchers.IO) {
                downloadIndex.download { progress, max, message ->
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
        }
    }

    fun importIndex(index: Uri) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(application.getString(R.string.importing_index))
            _indexDownloaded.value = withContext(Dispatchers.IO) {
                downloadIndex.import(index)
            }
            _progress.value = null
        }
    }
}