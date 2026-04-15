package com.door43.translationstudio.ui.navigation

import android.net.Uri
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DelicateDecomposeApi
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.value.Value
import com.door43.translationstudio.ui.home.DefaultHomeComponent
import com.door43.translationstudio.ui.home.HomeComponent
import com.door43.translationstudio.ui.navigation.RootComponent.Config
import com.door43.translationstudio.ui.profile.DefaultProfileComponent
import com.door43.translationstudio.ui.profile.ProfileComponent
import com.door43.translationstudio.ui.splash.DefaultSplashComponent
import com.door43.translationstudio.ui.splash.SplashComponent
import com.door43.translationstudio.ui.translate.DefaultTranslateComponent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.File

class DefaultRootComponent(
    componentContext: ComponentContext,
) : RootComponent, ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()

    private val _events = Channel<RootComponent.Event>(capacity = Channel.BUFFERED)
    override val events: Flow<RootComponent.Event> = _events.receiveAsFlow()

    override val stack: Value<ChildStack<*, RootComponent.Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = Config.Splash,
        handleBackButton = true,
        childFactory = ::child,
    )

    override fun onBackPressed() {
        navigation.pop()
    }

    override fun onDeepLink(uri: Uri) {
        val active = stack.value.active.instance
        if (active is RootComponent.Child.Home) {
            active.component.onAction(HomeComponent.Action.ImportProject(uri))
        }
    }

    private fun child(
        config: Config,
        componentContext: ComponentContext,
    ): RootComponent.Child = when (config) {
        is Config.Splash -> RootComponent.Child.Splash(
            component = DefaultSplashComponent(
                componentContext = componentContext,
                result = ::onSplashResult,
            ),
        )
        is Config.Home -> RootComponent.Child.Home(
            component = DefaultHomeComponent(componentContext),
        )
        is Config.Translate -> RootComponent.Child.Translate(
            component = DefaultTranslateComponent(
                componentContext = componentContext,
                targetTranslationId = config.translationId,
                startWithMergeFilter = config.startWithMergeFilter
            ),
        )
        is Config.Profile -> RootComponent.Child.Profile(
            component = DefaultProfileComponent(
                componentContext = componentContext,
                result = ::onProfileResult,
            ),
        )
        else -> RootComponent.Child.Placeholder
    }

    private fun onSplashResult(result: SplashComponent.Result) {
        when (result) {
            SplashComponent.Result.NavigateToProfile -> {
                navigation.replaceAll(Config.Profile)
            }
            SplashComponent.Result.NavigateToCrashReporter -> {
                navigation.replaceAll(Config.Home())
                _events.trySend(RootComponent.Event.OpenCrashReporter)
            }
        }
    }

    private fun onProfileResult(result: ProfileComponent.Result) {
        when (result) {
            is ProfileComponent.Result.Back -> {
                if (stack.value.backStack.isEmpty()) {
                    _events.trySend(RootComponent.Event.ExitApp)
                } else {
                    navigation.pop()
                }
            }
            is ProfileComponent.Result.OpenSettings -> {
                _events.trySend(RootComponent.Event.OpenSettings)
            }
            is ProfileComponent.Result.LoggedIn -> {
                navigation.replaceAll(Config.Home())

                if (result.showTerms) {
                    _events.trySend(RootComponent.Event.OpenTermsOfUse)
                }
            }
        }
    }

    private fun signalHomeUpdateLibrary() {
        val active = stack.value.active.instance
        if (active is RootComponent.Child.Home) {
            active.component.onAction(HomeComponent.Action.RequestUpdateLibrary)
        }
    }

    override fun openHome(withUpdate: Boolean) {
        navigation.pop {
            if (withUpdate) signalHomeUpdateLibrary()
        }
    }

    @OptIn(DelicateDecomposeApi::class)
    override fun openTranslate(translationId: String, startWithMergeFilter: Boolean) {
        navigation.push(
            Config.Translate(
                translationId = translationId,
                startWithMergeFilter = startWithMergeFilter,
            )
        )
    }

    @OptIn(DelicateDecomposeApi::class)
    override fun openProfile() {
        navigation.push(Config.Profile)
    }

    override fun openDraft(translationId: String) {
        // TODO Replace with navigation
        _events.trySend(RootComponent.Event.OpenDraft(translationId))
    }

    override fun openPublishPreview(translationId: String) {
        // TODO Replace with navigation
        _events.trySend(RootComponent.Event.OpenPublishFromTranslate(translationId))
        navigation.pop()
    }

    override fun openSettings() {
        // TODO Replace with navigation
        _events.trySend(RootComponent.Event.OpenSettings)
    }

    override fun exportToApp(file: File) {
        // TODO Replace with navigation
        _events.trySend(RootComponent.Event.ExportFile(file))
    }
}
