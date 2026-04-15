package com.door43.translationstudio.ui.profile

import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import kotlinx.serialization.Serializable

interface ProfileComponent {

    val stack: Value<ChildStack<*, Child>>

    val registerUrl: String

    sealed interface Result {
        data object Back : Result
        data object OpenSettings : Result
        data object LoggedIn : Result
    }

    sealed interface Child {
        data class Index(val component: ProfileIndexComponent) : Child
        data class LoginOnline(val component: LoginOnlineComponent) : Child
        data class LoginOffline(val component: LoginOfflineComponent) : Child
        data class TermsOfUse(val component: TermsOfUseComponent) : Child
    }

    @Serializable
    sealed interface Config {
        @Serializable
        data object Index : Config

        @Serializable
        data object LoginOnline : Config

        @Serializable
        data object LoginOffline : Config

        @Serializable
        data object TermsOfUse : Config
    }
}
