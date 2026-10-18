package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.usecases.UpdateTargetLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.TargetLanguage
import org.unfoldingword.tools.logger.Logger
import javax.inject.Inject

@HiltViewModel
class EditLibraryViewModel @Inject constructor(
    private val application: Application,
    private val library: Door43Client,
    private val updateTargetLanguage: UpdateTargetLanguage
) : AndroidViewModel(application) {

    private val _progress = MutableLiveData<ProgressHelper.Progress?>()
    val progress: LiveData<ProgressHelper.Progress?> = _progress

    private val _saveResult = MutableLiveData<Boolean?>()
    val saveResult: LiveData<Boolean?> = _saveResult

    fun getTargetLanguage(slug: String): TargetLanguage? = library.index.getTargetLanguage(slug)

    fun save(language: TargetLanguage) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(application.getString(R.string.edit_library_saving))
            _saveResult.value = withContext(Dispatchers.IO) {
                try {
                    updateTargetLanguage.execute(language)
                } catch (e: Exception) {
                    Logger.e(TAG, "Failed to save target language ${language.slug}", e)
                    false
                }
            }
            _progress.value = null
        }
    }

    fun clearSaveResult() {
        _saveResult.value = null
    }

    companion object {
        private const val TAG = "EditLibraryViewModel"
    }
}
