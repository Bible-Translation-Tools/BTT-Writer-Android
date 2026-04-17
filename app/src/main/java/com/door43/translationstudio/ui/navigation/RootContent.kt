package com.door43.translationstudio.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.slide
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.door43.translationstudio.ui.crash.CrashReporterScreen
import com.door43.translationstudio.ui.devtools.DevToolsScreen
import com.door43.translationstudio.ui.draft.DraftScreen
import com.door43.translationstudio.ui.home.HomeScreen
import com.door43.translationstudio.ui.newtranslation.NewTargetTranslationScreen
import com.door43.translationstudio.ui.profile.ProfileRouter
import com.door43.translationstudio.ui.publish.PublishScreen
import com.door43.translationstudio.ui.settings.SettingsScreen
import com.door43.translationstudio.ui.splash.SplashScreen
import com.door43.translationstudio.ui.translate.TargetTranslationScreen

@Composable
fun RootContent(
    component: RootComponent,
    modifier: Modifier = Modifier,
) {
    Children(
        stack = component.stack,
        modifier = modifier.fillMaxSize(),
        animation = stackAnimation(slide()),
    ) { child ->
        when (val instance = child.instance) {
            is RootComponent.Child.Splash -> SplashScreen(component = instance.component)
            is RootComponent.Child.Home -> HomeScreen(
                component = instance.component
            )
            is RootComponent.Child.NewTranslation -> NewTargetTranslationScreen(
                component = instance.component
            )
            is RootComponent.Child.Translate -> TargetTranslationScreen(
                component = instance.component
            )
            is RootComponent.Child.Profile -> ProfileRouter(
                component = instance.component
            )
            is RootComponent.Child.Settings -> SettingsScreen(
                component = instance.component
            )
            is RootComponent.Child.DevTools -> DevToolsScreen(
                component = instance.component
            )
            is RootComponent.Child.Draft -> DraftScreen(
                component = instance.component
            )
            is RootComponent.Child.Publish -> PublishScreen(
                component = instance.component
            )
            is RootComponent.Child.Crash -> CrashReporterScreen(
                component = instance.component
            )
        }
    }
}
