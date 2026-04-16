package com.door43.translationstudio.ui.navigation

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.slide
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.door43.translationstudio.ui.crash.CrashReporterActivity
import com.door43.translationstudio.ui.devtools.DevToolsScreen
import com.door43.translationstudio.ui.draft.DraftScreen
import com.door43.translationstudio.ui.home.HomeScreen
import com.door43.translationstudio.ui.newtranslation.NewTargetTranslationScreen
import com.door43.translationstudio.ui.profile.ProfileRouter
import com.door43.translationstudio.ui.publish.PublishActivity
import com.door43.translationstudio.ui.settings.SettingsScreen
import com.door43.translationstudio.ui.splash.SplashScreen
import com.door43.translationstudio.ui.translate.TargetTranslationScreen

@Composable
fun RootContent(
    component: RootComponent,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    LaunchedEffect(component) {
        component.event.collect { event ->
            when (event) {
                RootComponent.Event.OpenCrashReporter ->
                    context.startActivity(Intent(context, CrashReporterActivity::class.java))
                is RootComponent.Event.PublishProject -> {
                    val intent = Intent(context, PublishActivity::class.java).apply {
                        putExtra(PublishActivity.EXTRA_TARGET_TRANSLATION_ID, event.translationId)
                        putExtra(
                            PublishActivity.EXTRA_CALLING_ACTIVITY,
                            PublishActivity.ACTIVITY_TRANSLATION,
                        )
                    }
                    context.startActivity(intent)
                }
            }
        }
    }

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
                component = instance.component,
                startWithMergeFilter = false
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
            is RootComponent.Child.Placeholder -> Unit
        }
    }
}
