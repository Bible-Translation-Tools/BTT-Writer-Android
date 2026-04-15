package com.door43.translationstudio.ui.navigation

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.crash.CrashReporterActivity
import com.door43.translationstudio.ui.draft.DraftActivity
import com.door43.translationstudio.ui.home.HomeScreen
import com.door43.translationstudio.ui.profile.LoginDoor43Activity
import com.door43.translationstudio.ui.profile.ProfileActivity
import com.door43.translationstudio.ui.publish.PublishActivity
import com.door43.translationstudio.ui.settings.SettingsActivity
import com.door43.translationstudio.ui.splash.SplashScreen
import com.door43.translationstudio.ui.translate.TargetTranslationScreen
import java.io.File

@Composable
fun RootContent(
    component: RootComponent,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = remember(context) { context as? Activity }

    LaunchedEffect(component) {
        component.events.collect { event ->
            when (event) {
                RootComponent.Event.OpenProfile ->
                    context.startActivity(Intent(context, ProfileActivity::class.java))
                RootComponent.Event.OpenCrashReporter ->
                    context.startActivity(Intent(context, CrashReporterActivity::class.java))
                RootComponent.Event.OpenSettings ->
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                RootComponent.Event.OpenLoginDoor43 ->
                    context.startActivity(Intent(context, LoginDoor43Activity::class.java))
                is RootComponent.Event.OpenDraft -> {
                    val intent = Intent(context, DraftActivity::class.java).apply {
                        putExtra(DraftActivity.EXTRA_TARGET_TRANSLATION_ID, event.translationId)
                    }
                    context.startActivity(intent)
                }
                is RootComponent.Event.OpenPublishFromTranslate -> {
                    val intent = Intent(context, PublishActivity::class.java).apply {
                        putExtra(PublishActivity.EXTRA_TARGET_TRANSLATION_ID, event.translationId)
                        putExtra(
                            PublishActivity.EXTRA_CALLING_ACTIVITY,
                            PublishActivity.ACTIVITY_TRANSLATION,
                        )
                    }
                    context.startActivity(intent)
                }
                is RootComponent.Event.ExportFile -> activity?.let {
                    shareApp(it, event.file)
                }
            }
        }
    }

    Children(
        stack = component.stack,
        modifier = modifier.fillMaxSize(),
        animation = stackAnimation(fade()),
    ) { child ->
        when (val instance = child.instance) {
            is RootComponent.Child.Splash -> SplashScreen(component = instance.component)
            is RootComponent.Child.Home -> HomeScreen(
                component = instance.component,
                onSettings = {
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                }, // TODO Use router when migrated
                onShareApp = {},
                onLogin = {
                    context.startActivity(Intent(context, LoginDoor43Activity::class.java))
                },
                onLogout = {
                    context.startActivity(Intent(context, ProfileActivity::class.java))
                    activity?.finish()
                },
                onProjectPublish = {
                    val intent = Intent(context, PublishActivity::class.java).apply {
                        putExtra(PublishActivity.EXTRA_TARGET_TRANSLATION_ID, it)
                        putExtra(PublishActivity.EXTRA_CALLING_ACTIVITY, PublishActivity.ACTIVITY_HOME)
                    }
                    context.startActivity(intent)
                },
                onMergeConflict = {
                    component.openTranslate(it, true)
                },
                onOpenTranslate = component::openTranslate,
                onAppExit = { activity?.finishAffinity() }
            )
            is RootComponent.Child.Translate -> TargetTranslationScreen(
                component = instance.component,
                startWithMergeFilter = false,
                onHomeClick = {},
                onNavigateToDraft = {},
                onProjectPreview = {},
                onSettings = {},
                onRestartAutoCommitTimer = {},
                onUpdateSources = {},
                onExportToApp = {},
                onLoginClick = {},
                onLogout = {}
            )
            is RootComponent.Child.Placeholder -> Unit
        }
    }
}

private fun shareApp(activity: Activity, file: File) {
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(
        activity,
        "${activity.application.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
    }
    activity.startActivity(
        Intent.createChooser(intent, activity.resources.getString(R.string.send_to)),
    )
}
