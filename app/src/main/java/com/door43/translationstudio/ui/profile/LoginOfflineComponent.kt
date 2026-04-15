package com.door43.translationstudio.ui.profile

interface LoginOfflineComponent {
    fun onContinue(fullName: String)
    fun onCancel()

    sealed interface Result {
        data object Back : Result
        data object LoggedIn : Result
    }
}
