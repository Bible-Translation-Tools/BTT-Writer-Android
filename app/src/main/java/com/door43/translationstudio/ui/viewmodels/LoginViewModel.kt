package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.usecases.GogsLogin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LoginModel(
    val result: GogsLogin.LoginResult? = null,
    val progress: ProgressHelper.Progress? = null
)

class LoginViewModel(
    application: Application,
    private val gogsLogin: GogsLogin
) : AndroidViewModel(application) {

    private val _model = MutableStateFlow(LoginModel())
    val model: StateFlow<LoginModel> = _model.asStateFlow()

    fun login(username: String, password: String, fullName: String?) {
        viewModelScope.launch {
            _model.update {
                it.copy(progress = ProgressHelper.Progress())
            }
            val result = withContext(Dispatchers.IO) {
                gogsLogin.execute(username, password, fullName)
            }
            _model.update {
                it.copy(result = result, progress = null)
            }
        }
    }
}