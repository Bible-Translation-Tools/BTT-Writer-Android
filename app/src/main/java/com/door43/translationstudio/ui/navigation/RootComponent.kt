package com.door43.translationstudio.ui.navigation

import android.net.Uri
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.door43.translationstudio.ui.devtools.DevToolsComponent
import com.door43.translationstudio.ui.draft.DraftComponent
import com.door43.translationstudio.ui.home.HomeComponent
import com.door43.translationstudio.ui.newtranslation.NewTranslationComponent
import com.door43.translationstudio.ui.profile.ProfileComponent
import com.door43.translationstudio.ui.publish.PublishComponent
import com.door43.translationstudio.ui.settings.SettingsComponent
import com.door43.translationstudio.ui.splash.SplashComponent
import com.door43.translationstudio.ui.translate.TranslateComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

interface RootComponent {

    val stack: Value<ChildStack<*, Child>>
    val event: Flow<Event>
    val sharedFlow: SharedFlow<SharedEvent>
    val currentTheme: StateFlow<String>

    fun onBackPressed()
    fun onDeepLink(uri: Uri)

    fun openTranslate(translationId: String, startWithMergeFilter: Boolean)
    fun openProfile(thenLogin: Boolean)

    sealed interface Child {
        data object Placeholder : Child
        data class Splash(val component: SplashComponent) : Child
        data class Home(val component: HomeComponent) : Child
        data class Translate(val component: TranslateComponent) : Child
        data class Profile(val component: ProfileComponent) : Child
        data class Settings(val component: SettingsComponent) : Child
        data class DevTools(val component: DevToolsComponent) : Child
        data class NewTranslation(val component: NewTranslationComponent) : Child
        data class Draft(val component: DraftComponent) : Child
        data class Publish(val component: PublishComponent) : Child
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
            val disabledLanguages: List<String> = emptyList()
        ) : Config

        @Serializable
        data class Publish(val translationId: String) : Config

        @Serializable
        data class Draft(val translationId: String) : Config

        @Serializable
        data object Settings : Config

        @Serializable
        data object DevTools : Config

        @Serializable
        data class Profile(val thenLogin: Boolean = false) : Config

        @Serializable
        data object CrashReporter : Config
    }

    sealed interface Event {
        data object OpenCrashReporter : Event
    }

    sealed interface SharedEvent {
        data object LoadProjects : SharedEvent
        data class SnackbarMessage(val message: String) : SharedEvent
        data class DuplicateProject(val translationId: String) : SharedEvent
        data object RequestLibraryUpdate : SharedEvent
        data class ImportProject(val uri: Uri) : SharedEvent
    }
}
