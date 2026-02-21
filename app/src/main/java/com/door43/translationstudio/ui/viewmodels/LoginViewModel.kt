package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.usecases.GogsLogin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginViewModel(
    application: Application,
    private val gogsLogin: GogsLogin
) : AndroidViewModel(application) {
    private val _progress = MutableLiveData<ProgressHelper.Progress?>()
    val progress: LiveData<ProgressHelper.Progress?> = _progress

    private val _loginResult = MutableLiveData<GogsLogin.LoginResult?>()
    val loginResult: LiveData<GogsLogin.LoginResult?> = _loginResult

    fun login(username: String, password: String, fullName: String?) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress()
            _loginResult.value = withContext(Dispatchers.IO) {
                gogsLogin.execute(username, password, fullName)
            }
            _progress.value = null
        }
    }
}