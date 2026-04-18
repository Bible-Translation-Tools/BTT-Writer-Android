package com.door43.translationstudio.ui.profile

import android.app.Application
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.Progress
import com.door43.translationstudio.core.ProgressManager
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.TaskHandle
import com.door43.translationstudio.core.ComponentScope
import com.door43.translationstudio.core.launchWithProgress
import com.door43.usecases.GogsLogout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

interface TermsOfUseComponent {

    val progress: StateFlow<Progress?>

    fun acceptTerms()
    fun rejectTerms()

    sealed interface Result {
        data object Rejected : Result
        data object Accepted : Result
    }
}

class DefaultTermsOfUseComponent(
    componentContext: ComponentContext,
    private val onResult: (TermsOfUseComponent.Result) -> Unit
) : TermsOfUseComponent,
    ComponentContext by componentContext,
    KoinComponent, ComponentScope, ProgressOwner {

    private val application: Application by inject()
    private val profile: Profile by inject()
    private val logoutUseCase: GogsLogout by inject()

    override val coroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val progressManager = ProgressManager(coroutineScope)
    override val progress get() = progressManager.progress

    init {
        lifecycle.doOnDestroy {
            coroutineScope.cancel()
        }
    }

    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        progressManager.runTask(message, block)
    }

    override fun acceptTerms() {
        profile.termsOfUseLastAccepted = application.resources.getInteger(R.integer.terms_of_use_version)
        onResult(TermsOfUseComponent.Result.Accepted)
    }

    override fun rejectTerms() {
        launchWithProgress(
            application.getString(R.string.log_out)
        ) {
            withContext(Dispatchers.IO) {
                logoutUseCase.execute()
                profile.logout()
            }
            onResult(TermsOfUseComponent.Result.Rejected)
        }
    }
}