package com.door43.translationstudio.ui.splash

import android.net.Uri
import com.door43.translationstudio.core.Progress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface SplashComponent {
    val state: StateFlow<State>
    val progress: StateFlow<Progress?>
    val event: Flow<Event>

    fun onHardwareWarningContinued()
    fun onHardwareWarningDismissedAndSaved()
    fun onMigrationAccepted()
    fun onMigrationDeclined()
    fun performMigrate(uri: Uri?)

    sealed interface Result {
        data object NavigateToProfile : Result
        data object NavigateToCrashReporter : Result
    }

    sealed interface Event {
        data object OpenDirToMigrate : Event
    }

    data class State(
        val showHardwareWarning: Boolean = false,
        val showMigrationDialog: Boolean = false
    )
}
