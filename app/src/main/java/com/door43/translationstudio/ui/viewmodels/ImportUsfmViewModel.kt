package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.door43.OnProgressListener
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.R
import com.door43.translationstudio.core.ProcessUSFM
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.usecases.ImportProjects
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.TargetLanguage
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ImportUsfmViewModel @Inject constructor(
    private val application: Application
) : AndroidViewModel(application) {

    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var profile: Profile
    @Inject lateinit var importProjects: ImportProjects
    @Inject lateinit var translator: Translator
    @Inject lateinit var library: Door43Client
    @Inject lateinit var assetsProvider: AssetsProvider

    private val _progress = MutableLiveData<ProgressHelper.Progress?>()
    val progress: LiveData<ProgressHelper.Progress?> = _progress

    private val _usfm = MutableLiveData<ProcessUSFM?>()
    val usfm: LiveData<ProcessUSFM?> = _usfm

    private val _importResult = MutableLiveData<ImportProjects.ImportUsfmResult?>()
    val importResult: LiveData<ImportProjects.ImportUsfmResult?> = _importResult

    fun processUsfm(targetLanguage: TargetLanguage, file: File) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(application.getString(R.string.reading_usfm))
            _usfm.value = withContext(Dispatchers.IO) {
                ProcessUSFM.Builder(
                    application,
                    directoryProvider,
                    profile,
                    library,
                    assetsProvider
                )
                    .fromFile(targetLanguage, file, progressListener)
                    .build()
            }
            _progress.value = null
        }
    }

    fun processUsfm(targetLanguage: TargetLanguage, uri: Uri) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(application.getString(R.string.reading_usfm))
            _usfm.value = withContext(Dispatchers.IO) {
                ProcessUSFM.Builder(
                    application,
                    directoryProvider,
                    profile,
                    library,
                    assetsProvider
                )
                    .fromUri(targetLanguage, uri, progressListener)
                    .build()
            }
            _progress.value = null
        }
    }

    fun processUsfm(targetLanguage: TargetLanguage, rcPath: String) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(application.getString(R.string.reading_usfm))
            _usfm.value = withContext(Dispatchers.IO) {
                ProcessUSFM.Builder(
                    application,
                    directoryProvider,
                    profile,
                    library,
                    assetsProvider
                )
                    .fromRc(targetLanguage, rcPath, progressListener)
                    .build()
            }
            _progress.value = null
        }
    }

    fun processUsfm(jsonString: String) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(application.getString(R.string.reading_usfm))
            _usfm.value = withContext(Dispatchers.IO) {
                ProcessUSFM.Builder(
                    application,
                    directoryProvider,
                    profile,
                    library,
                    assetsProvider
                )
                    .fromJsonString(jsonString)
                    .build()
            }
            _progress.value = null
        }
    }

    fun processText(
        book: String,
        name: String,
        promptForName: Boolean,
        useName: String?) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(application.getString(R.string.reading_usfm))
            _usfm.value?.let { usfm ->
                usfm.processText(book, name, promptForName, useName)
                _usfm.value = usfm
            }
            _progress.value = null
        }
    }

    fun importProjects(files: List<File>, overwrite: Boolean) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(application.getString(R.string.importing_usfm))
            _importResult.value = withContext(Dispatchers.IO) {
                importProjects.importProjects(files, overwrite, progressListener)
            }
            _progress.value = null
        }
    }

    fun getTargetLanguage(targetLanguageId: String): TargetLanguage? {
        return library.index.getTargetLanguage(targetLanguageId)
    }

    fun checkMergeConflictExists(): TargetTranslation? {
        val imports = _usfm.value?.importProjects ?: listOf()
        for (file in imports) {
            val conflictingTranslation = translator.getConflictingTargetTranslation(file)
            if (conflictingTranslation != null) {
                return conflictingTranslation
            }
        }
        return null
    }

    fun cleanup() {
        _usfm.value?.cleanup()
        _usfm.value = null
    }

    private val progressListener = object : OnProgressListener {
        override fun onProgress(progress: Int, max: Int, message: String?) {
            _progress.postValue(ProgressHelper.Progress(message, progress, max))
        }
        override fun onIndeterminate() {
            _progress.postValue(ProgressHelper.Progress())
        }
    }
}