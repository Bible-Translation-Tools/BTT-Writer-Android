package com.door43.translationstudio.ui.profile

import android.app.Application
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.bringToFront
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.replaceAll
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
    private val goLogin: Boolean,
    private val result: (ProfileComponent.Result) -> Unit
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
        initialStack = {
            buildList {
                add(ProfileComponent.Config.Index)
                if (goLogin) {
                    add(ProfileComponent.Config.LoginOnline)
                }
            }
        },
        handleBackButton = true,
        childFactory = ::child,
    )

    init {
        if (profile.loggedIn) {
            result(ProfileComponent.Result.LoggedIn)
        }
    }

    private fun child(
        config: ProfileComponent.Config,
        componentContext: ComponentContext,
    ): ProfileComponent.Child = when (config) {
        ProfileComponent.Config.Index -> ProfileComponent.Child.Index(
            component = DefaultProfileIndexComponent(
                componentContext = componentContext,
                onResult = ::onProfileIndexResult
            )
        )
        ProfileComponent.Config.LoginOnline -> ProfileComponent.Child.LoginOnline(
            component = DefaultLoginOnlineComponent(
                componentContext = componentContext,
                onResult = ::onLoginOnlineResult,
            )
        )
        ProfileComponent.Config.LoginOffline -> ProfileComponent.Child.LoginOffline(
            component = DefaultLoginOfflineComponent(
                componentContext = componentContext,
                onResult = ::onLoginOfflineResult,
            )
        )
        ProfileComponent.Config.TermsOfUse -> ProfileComponent.Child.TermsOfUse(
            component = DefaultTermsOfUseComponent(
                componentContext = componentContext,
                onResult = ::onTermsOfUseResult,
            )
        )
    }

    private fun onProfileIndexResult(result: ProfileIndexComponent.Result) {
        when (result) {
            ProfileIndexComponent.Result.LoginOnline -> {
                navigation.bringToFront(ProfileComponent.Config.LoginOnline)
            }
            ProfileIndexComponent.Result.LoginOffline -> {
                navigation.bringToFront(ProfileComponent.Config.LoginOffline)
            }
            ProfileIndexComponent.Result.Settings -> {
                result(ProfileComponent.Result.OpenSettings)
            }
            ProfileIndexComponent.Result.Cancel -> {
                result(ProfileComponent.Result.Back)
            }
        }
    }

    private fun onLoginOnlineResult(result: LoginOnlineComponent.Result) {
        when (result) {
            LoginOnlineComponent.Result.Back -> navigation.pop()
            LoginOnlineComponent.Result.LoggedIn -> {
                navigation.bringToFront(ProfileComponent.Config.TermsOfUse)
            }
        }
    }

    private fun onLoginOfflineResult(result: LoginOfflineComponent.Result) {
        when (result) {
            LoginOfflineComponent.Result.Back -> navigation.pop()
            LoginOfflineComponent.Result.LoggedIn -> {
                navigation.bringToFront(ProfileComponent.Config.TermsOfUse)
            }
        }
    }

    private fun onTermsOfUseResult(result: TermsOfUseComponent.Result) {
        when (result) {
            TermsOfUseComponent.Result.Rejected -> {
                navigation.replaceAll(ProfileComponent.Config.Index)
            }
            TermsOfUseComponent.Result.Accepted -> {
                result(ProfileComponent.Result.LoggedIn)
            }
        }
    }
}
