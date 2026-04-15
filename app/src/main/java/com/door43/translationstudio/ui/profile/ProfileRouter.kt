package com.door43.translationstudio.ui.profile

import androidx.compose.runtime.Composable
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.slide
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation

@Composable
fun ProfileRouter(component: ProfileComponent) {
    Children(
        stack = component.stack,
        animation = stackAnimation(slide()),
    ) { child ->
        when (val instance = child.instance) {
            is ProfileComponent.Child.Index -> ProfileScreen(
                component = instance.component,
                registerUrl = component.registerUrl
            )
            is ProfileComponent.Child.LoginOnline -> LoginScreen(
                component = instance.component
            )
            is ProfileComponent.Child.LoginOffline -> LoginOfflineScreen(
                component = instance.component
            )
            is ProfileComponent.Child.TermsOfUse -> TermsOfUseScreen(
                component = instance.component
            )
        }
    }
}
