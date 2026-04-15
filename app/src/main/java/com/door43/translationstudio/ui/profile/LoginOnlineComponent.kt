package com.door43.translationstudio.ui.profile

import com.door43.translationstudio.core.Progress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

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
