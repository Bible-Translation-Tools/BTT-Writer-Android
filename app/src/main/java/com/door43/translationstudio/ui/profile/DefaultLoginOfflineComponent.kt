package com.door43.translationstudio.ui.profile

import com.arkivanov.decompose.ComponentContext
import com.door43.translationstudio.core.Profile
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DefaultLoginOfflineComponent(
    componentContext: ComponentContext,
    private val result: (LoginOfflineComponent.Result) -> Unit,
) : LoginOfflineComponent,
    ComponentContext by componentContext,
    KoinComponent {

    private val profile: Profile by inject()

    override fun onContinue(fullName: String) {
        profile.login(fullName)
        result(LoginOfflineComponent.Result.LoggedIn)
    }

    override fun onCancel() {
        result(LoginOfflineComponent.Result.Back)
    }
}
