package com.door43.translationstudio.ui.profile

import android.app.Application
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import com.door43.translationstudio.core.ComponentScope
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.Progress
import com.door43.translationstudio.core.ProgressManager
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.TaskHandle
import com.door43.translationstudio.core.launchWithProgress
import com.door43.usecases.GogsLogin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

interface LoginOnlineComponent {
    val progress: StateFlow<Progress?>
    val event: Flow<Event>

    val isNetworkAvailable: Boolean

    fun onLogin(username: String, password: String)
    fun onCancel()

    sealed interface Event {
        data class ShowError(val errorResId: Int) : Event
    }

    sealed interface Result {
        data object Back : Result
        data object LoggedIn : Result
    }
}

class DefaultLoginOnlineComponent(
    componentContext: ComponentContext,
    private val onResult: (LoginOnlineComponent.Result) -> Unit,
) : LoginOnlineComponent,
    ComponentContext by componentContext,
    ComponentScope, ProgressOwner, KoinComponent {

    private val application: Application by inject()
    private val profile: Profile by inject()
    private val gogsLogin: GogsLogin by inject()

    private val _event = Channel<LoginOnlineComponent.Event>(Channel.BUFFERED)
    override val event = _event.receiveAsFlow()

    override val coroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val progressManager = ProgressManager(coroutineScope)
    override val progress get() = progressManager.progress

    override val isNetworkAvailable: Boolean
        get() = App.isNetworkAvailable

    init {
        lifecycle.doOnDestroy {
            coroutineScope.cancel()
        }
    }

    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        progressManager.runTask(message, block)
    }

    override fun onLogin(username: String, password: String) {
        launchWithProgress(application.getString(R.string.logging_in)) {
            val loginResult = withContext(Dispatchers.IO) {
                gogsLogin.execute(
                    username.trim(),
                    password,
                    profile.fullName.takeIf { it.isNotEmpty() }
                )
            }
            val user = loginResult.user
            if (user != null) {
                if (user.fullName.isNullOrEmpty()) {
                    user.fullName = user.username
                }
                profile.login(user.fullName, user)
                onResult(LoginOnlineComponent.Result.LoggedIn)
            } else {
                val errorRes = if (App.isNetworkAvailable) {
                    R.string.double_check_credentials
                } else {
                    R.string.internet_not_available
                }
                _event.trySend(LoginOnlineComponent.Event.ShowError(errorRes))
            }
        }
    }

    override fun onCancel() {
        onResult(LoginOnlineComponent.Result.Back)
    }
}
