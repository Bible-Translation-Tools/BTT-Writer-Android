package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.door43.translationstudio.ui.publish.ValidationItem
import com.door43.usecases.ValidateProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ValidationViewModel(
    application: Application,
    private val validateProject: ValidateProject
) : AndroidViewModel(application) {

    private val _validations = MutableLiveData<List<ValidationItem>?>()
    val validations: LiveData<List<ValidationItem>?> = _validations

    fun validateProject(targetTranslationId: String, sourceTranslationId: String) {
        viewModelScope.launch {
            _validations.value = withContext(Dispatchers.IO) {
                validateProject.execute(targetTranslationId, sourceTranslationId)
            }
        }
    }
}