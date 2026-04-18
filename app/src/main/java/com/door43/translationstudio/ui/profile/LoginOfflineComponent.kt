package com.door43.translationstudio.ui.profile

import com.arkivanov.decompose.ComponentContext
import com.door43.translationstudio.core.Profile
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

interface LoginOfflineComponent {
    fun onContinue(fullName: String)
    fun onCancel()

    sealed interface Result {
        data object Back : Result
        data object LoggedIn : Result
    }
}

class DefaultLoginOfflineComponent(
    componentContext: ComponentContext,
    private val onResult: (LoginOfflineComponent.Result) -> Unit,
) : LoginOfflineComponent,
    ComponentContext by componentContext,
    KoinComponent {

    private val profile: Profile by inject()

    override fun onContinue(fullName: String) {
        profile.login(fullName)
        onResult(LoginOfflineComponent.Result.LoggedIn)
    }

    override fun onCancel() {
        onResult(LoginOfflineComponent.Result.Back)
    }
}
