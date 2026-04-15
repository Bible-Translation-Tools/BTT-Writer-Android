package com.door43.translationstudio.ui.profile

import com.arkivanov.decompose.ComponentContext

interface ProfileIndexComponent {

    fun loginOnline()
    fun loginOffline()
    fun settings()
    fun cancel()

    sealed interface Result {
        data object LoginOnline : Result
        data object LoginOffline : Result
        data object Settings : Result
        data object Cancel : Result
    }
}

class DefaultProfileIndexComponent(
    val componentContext: ComponentContext,
    val onResult: (ProfileIndexComponent.Result) -> Unit
) : ProfileIndexComponent, ComponentContext by componentContext {

    override fun loginOnline() {
        onResult(ProfileIndexComponent.Result.LoginOnline)
    }

    override fun loginOffline() {
        onResult(ProfileIndexComponent.Result.LoginOffline)
    }

    override fun settings() {
        onResult(ProfileIndexComponent.Result.Settings)
    }

    override fun cancel() {
        onResult(ProfileIndexComponent.Result.Cancel)
    }
}
