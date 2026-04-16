package com.door43.translationstudio.ui.navigation

import android.net.Uri
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.door43.translationstudio.ui.home.HomeComponent
import com.door43.translationstudio.ui.profile.ProfileComponent
import com.door43.translationstudio.ui.splash.SplashComponent
import com.door43.translationstudio.ui.translate.TranslateComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import java.io.File

interface RootComponent {

    val stack: Value<ChildStack<*, Child>>

    val events: Flow<Event>

    fun onBackPressed()
    fun onDeepLink(uri: Uri)

    fun openTranslate(translationId: String, startWithMergeFilter: Boolean)
    fun openProfile(thenLogin: Boolean)
    fun openSettings()

    fun exportToApp(file: File)

    sealed interface Child {
        data object Placeholder : Child
        data class Splash(val component: SplashComponent) : Child
        data class Home(val component: HomeComponent) : Child
        data class Translate(val component: TranslateComponent) : Child
        data class Profile(val component: ProfileComponent) : Child
        // Real children land in later milestones:
        // data class NewTranslation(val component: NewTranslationComponent) : Child
        // ...
    }

    @Serializable
    sealed interface Config {
        @Serializable
        data object Splash : Config

        @Serializable
        data class Home(val importUri: String? = null) : Config

        @Serializable
        data class Translate(
            val translationId: String,
            val startWithMergeFilter: Boolean = false,
        ) : Config

        @Serializable
        data class NewTranslation(
            val translationId: String? = null,
            val disabledLanguages: List<String> = emptyList(),
            val changeTargetLanguageOnly: Boolean = false,
        ) : Config

        @Serializable
        data class Publish(val translationId: String) : Config

        @Serializable
        data class Draft(val translationId: String) : Config

        @Serializable
        data object Settings : Config

        @Serializable
        data class Profile(val thenLogin: Boolean = false) : Config

        @Serializable
        data object LoginDoor43 : Config

        @Serializable
        data object LoginOffline : Config

        @Serializable
        data class LegalDocument(val kind: String) : Config

        @Serializable
        data object TermsOfUse : Config

        @Serializable
        data object CrashReporter : Config

        @Serializable
        data object DevTools : Config
    }

    sealed interface Event {
        data object OpenCrashReporter : Event
        data object OpenSettings : Event
        data class OpenDraft(val translationId: String) : Event
        data class PublishProject(val translationId: String) : Event
    }
}
