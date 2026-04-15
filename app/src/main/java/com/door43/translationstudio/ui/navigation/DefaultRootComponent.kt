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
import com.door43.translationstudio.ui.splash.DefaultSplashComponent
import com.door43.translationstudio.ui.splash.SplashComponent
import com.door43.translationstudio.ui.translate.DefaultTranslateComponent
import com.door43.translationstudio.ui.translate.TranslateComponent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

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
                startWithMergeFilter = config.startWithMergeFilter,
                result = ::onTranslateResult,
            ),
        )
        else -> RootComponent.Child.Placeholder
    }

    private fun onSplashResult(result: SplashComponent.Result) {
        when (result) {
            SplashComponent.Result.NavigateToProfile -> {
                navigation.replaceAll(Config.Home())
                _events.trySend(RootComponent.Event.OpenProfile)
            }
            SplashComponent.Result.NavigateToCrashReporter -> {
                navigation.replaceAll(Config.Home())
                _events.trySend(RootComponent.Event.OpenCrashReporter)
            }
        }
    }

    private fun onTranslateResult(result: TranslateComponent.Result) {
        when (result) {
            is TranslateComponent.Result.Back -> {
                navigation.pop {
                    if (result.didUpdate) signalHomeUpdateLibrary()
                }
            }
            is TranslateComponent.Result.OpenDraft ->
                _events.trySend(RootComponent.Event.OpenDraft(result.translationId))
            is TranslateComponent.Result.OpenPublish -> {
                _events.trySend(RootComponent.Event.OpenPublishFromTranslate(result.translationId))
                navigation.pop()
            }
            TranslateComponent.Result.OpenSettings ->
                _events.trySend(RootComponent.Event.OpenSettings)
            TranslateComponent.Result.OpenLogin ->
                _events.trySend(RootComponent.Event.OpenLoginDoor43)
            TranslateComponent.Result.OpenLogout -> {
                _events.trySend(RootComponent.Event.OpenProfile)
                navigation.pop()
            }
            is TranslateComponent.Result.ExportFile ->
                _events.trySend(RootComponent.Event.ExportFile(result.file))
        }
    }

    private fun signalHomeUpdateLibrary() {
        val active = stack.value.active.instance
        if (active is RootComponent.Child.Home) {
            active.component.onAction(HomeComponent.Action.RequestUpdateLibrary)
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
}
