package com.door43.translationstudio.ui.navigation

import android.net.Uri
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.bringToFront
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
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
import com.door43.translationstudio.ui.translate.TranslateComponent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.File

class DefaultRootComponent(
    componentContext: ComponentContext,
    private val onExportToApp: (File) -> Unit,
    private val onShareApp: () -> Unit,
    private val onExitApp: () -> Unit
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
            component = DefaultHomeComponent(
                componentContext = componentContext,
                onResult = ::onHomeResult
            )
        )
        is Config.Translate -> RootComponent.Child.Translate(
            component = DefaultTranslateComponent(
                componentContext = componentContext,
                targetTranslationId = config.translationId,
                startWithMergeFilter = config.startWithMergeFilter,
                onResult = ::onTranslateResult
            ),
        )
        is Config.Profile -> RootComponent.Child.Profile(
            component = DefaultProfileComponent(
                componentContext = componentContext,
                goLogin = config.thenLogin,
                result = ::onProfileResult,
            ),
        )
        else -> RootComponent.Child.Placeholder
    }

    private fun onSplashResult(result: SplashComponent.Result) {
        when (result) {
            SplashComponent.Result.NavigateToProfile -> openProfile(false)
            SplashComponent.Result.NavigateToCrashReporter -> {
                navigation.replaceAll(Config.Home())
                _events.trySend(RootComponent.Event.OpenCrashReporter)
            }
        }
    }

    private fun onHomeResult(result: HomeComponent.Result) {
        when (result) {
            is HomeComponent.Result.OpenLogin -> openProfile(true)
            is HomeComponent.Result.Logout -> openProfile(false)
            is HomeComponent.Result.OpenSettings -> openSettings()
            is HomeComponent.Result.PublishProject -> openPublishPreview(result.translationId)
            is HomeComponent.Result.OpenProject -> {
                openTranslate(result.translationId, result.mergeConflictFilterOn)
            }
            is HomeComponent.Result.ExitApp -> onExitApp()
            is HomeComponent.Result.ShareApp -> onShareApp()
            is HomeComponent.Result.ExportToApp -> onExportToApp(result.file)
        }
    }

    private fun onTranslateResult(result: TranslateComponent.Result) {
        when (result) {
            is TranslateComponent.Result.OpenHome -> openHome(result.withUpdate)
            is TranslateComponent.Result.OpenDraft -> openDraft(result.translationId)
            is TranslateComponent.Result.OpenPublishProject -> openPublishPreview(result.translationId)
            is TranslateComponent.Result.OpenLogin -> openProfile(true)
            is TranslateComponent.Result.Logout -> openProfile(false)
            is TranslateComponent.Result.OpenSettings -> openSettings()
            is TranslateComponent.Result.ExportToApp -> exportToApp(result.file)
        }
    }

    private fun onProfileResult(result: ProfileComponent.Result) {
        when (result) {
            is ProfileComponent.Result.Back -> {
                if (stack.value.backStack.isEmpty()) {
                    onExitApp()
                } else {
                    navigation.pop()
                }
            }
            is ProfileComponent.Result.OpenSettings -> openSettings()
            is ProfileComponent.Result.LoggedIn -> openHome()
        }
    }

    override fun openTranslate(translationId: String, startWithMergeFilter: Boolean) {
        navigation.bringToFront(
            Config.Translate(
                translationId = translationId,
                startWithMergeFilter = startWithMergeFilter,
            )
        )
    }

    override fun openProfile(thenLogin: Boolean) {
        navigation.replaceAll(Config.Profile(thenLogin))
    }

    private fun exportToApp(file: File) {
        onExportToApp(file)
    }

    private fun openHome(withUpdate: Boolean = false) {
        navigation.replaceAll(Config.Home()) {
            if (withUpdate) signalHomeUpdateLibrary()
        }
    }

    private fun openDraft(translationId: String) {
        // TODO Replace with navigation
        _events.trySend(RootComponent.Event.OpenDraft(translationId))
    }

    private fun openPublishPreview(translationId: String) {
        // TODO Replace with navigation
        _events.trySend(RootComponent.Event.PublishProject(translationId))
        navigation.pop()
    }

    private fun openSettings() {
        // TODO Replace with navigation
        _events.trySend(RootComponent.Event.OpenSettings)
    }

    private fun signalHomeUpdateLibrary() {
        val active = stack.value.active.instance
        if (active is RootComponent.Child.Home) {
            active.component.onAction(HomeComponent.Action.RequestUpdateLibrary)
        }
    }
}
