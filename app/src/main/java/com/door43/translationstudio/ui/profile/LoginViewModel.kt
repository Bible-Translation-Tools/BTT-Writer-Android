package com.door43.translationstudio.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.door43.translationstudio.core.ProgressManager
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.TaskHandle
import com.door43.translationstudio.ui.launchWithProgress
import com.door43.usecases.GogsLogin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

data class LoginState(
    val result: GogsLogin.LoginResult? = null
)

class LoginViewModel(
    private val gogsLogin: GogsLogin
) : ViewModel(), ProgressOwner {

    private val progressManager = ProgressManager(viewModelScope)
    override val progress get() = progressManager.progress

    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        progressManager.runTask(message, block)
    }

    fun login(username: String, password: String, fullName: String?) {
        launchWithProgress {
            val result = withContext(Dispatchers.IO) {
                gogsLogin.execute(username, password, fullName)
            }
            _state.update { it.copy(result = result) }
        }
    }
}