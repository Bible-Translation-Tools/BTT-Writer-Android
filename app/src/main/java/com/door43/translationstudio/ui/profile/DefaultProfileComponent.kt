package com.door43.translationstudio.ui.profile

import android.app.Application
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DelicateDecomposeApi
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.value.Value
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.ui.settings.SettingsActivity
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DefaultProfileComponent(
    componentContext: ComponentContext,
    private val result: (ProfileComponent.Result) -> Unit,
) : ProfileComponent,
    ComponentContext by componentContext, KoinComponent {

    private val application: Application by inject()
    private val profile: Profile by inject()
    private val prefRepository: IPreferenceRepository by inject()

    private val navigation = StackNavigation<ProfileComponent.Config>()

    override val registerUrl: String = prefRepository.getDefaultPref(
        SettingsActivity.KEY_PREF_CREATE_ACCOUNT_URL,
        application.getString(R.string.pref_default_create_account_url)
    )

    override val stack: Value<ChildStack<*, ProfileComponent.Child>> = childStack(
        source = navigation,
        serializer = ProfileComponent.Config.serializer(),
        initialConfiguration = ProfileComponent.Config.Index,
        handleBackButton = true,
        childFactory = ::child,
    )

    init {
        if (profile.loggedIn) {
            result(ProfileComponent.Result.LoggedIn(false))
        }
    }

    private fun child(
        config: ProfileComponent.Config,
        componentContext: ComponentContext,
    ): ProfileComponent.Child = when (config) {
        ProfileComponent.Config.Index -> ProfileComponent.Child.Index(
            component = DefaultProfileIndexComponent()
        )
        ProfileComponent.Config.LoginDoor43 -> ProfileComponent.Child.LoginOnline(
            component = DefaultLoginOnlineComponent(
                componentContext = componentContext,
                result = ::onLoginOnlineResult,
            )
        )
        ProfileComponent.Config.LoginOffline -> ProfileComponent.Child.LoginOffline(
            component = DefaultLoginOfflineComponent(
                componentContext = componentContext,
                result = ::onLoginOfflineResult,
            )
        )
    }

    private fun onLoginOnlineResult(childResult: LoginOnlineComponent.Result) {
        when (childResult) {
            LoginOnlineComponent.Result.Back -> navigation.pop()
            LoginOnlineComponent.Result.LoggedIn -> {
                result(ProfileComponent.Result.LoggedIn(true))
            }
        }
    }

    private fun onLoginOfflineResult(childResult: LoginOfflineComponent.Result) {
        when (childResult) {
            LoginOfflineComponent.Result.Back -> navigation.pop()
            LoginOfflineComponent.Result.LoggedIn -> {
                result(ProfileComponent.Result.LoggedIn(true))
            }
        }
    }

    @OptIn(DelicateDecomposeApi::class)
    override fun onLoginOnline() {
        navigation.push(ProfileComponent.Config.LoginDoor43)
    }

    @OptIn(DelicateDecomposeApi::class)
    override fun onLoginOffline() {
        navigation.push(ProfileComponent.Config.LoginOffline)
    }

    override fun onSettings() {
        result(ProfileComponent.Result.OpenSettings)
    }

    override fun onCancel() {
        result(ProfileComponent.Result.Back)
    }
}
