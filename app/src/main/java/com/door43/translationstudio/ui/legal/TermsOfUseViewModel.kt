package com.door43.translationstudio.ui.legal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.door43.translationstudio.core.Profile
import com.door43.usecases.GogsLogout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class TermsOfUseViewModel(
    private val profile: Profile,
    private val logoutUseCase: GogsLogout,
    private val termsVersion: Int
) : ViewModel() {

    private val _navigationEvent = Channel<NavigationEvent>()
    val navigationEvent = _navigationEvent.receiveAsFlow()

    enum class InitialState { SHOW_TERMS, GO_HOME, FINISH }

    val initialState: InitialState
        get() = when {
            !profile.loggedIn -> InitialState.FINISH
            profile.termsOfUseLastAccepted == termsVersion -> InitialState.GO_HOME
            else -> InitialState.SHOW_TERMS
        }

    fun acceptTerms() {
        profile.termsOfUseLastAccepted = termsVersion
        emitNavigation(NavigationEvent.NavigateToHome)
    }

    fun rejectTerms() {
        viewModelScope.launch(Dispatchers.IO) {
            logoutUseCase.execute()
            profile.logout()
            emitNavigation(NavigationEvent.NavigateToLogin)
        }
    }

    private fun emitNavigation(event: NavigationEvent) {
        viewModelScope.launch { _navigationEvent.send(event) }
    }

    sealed class NavigationEvent {
        object NavigateToHome : NavigationEvent()
        object NavigateToLogin : NavigationEvent()
    }
}