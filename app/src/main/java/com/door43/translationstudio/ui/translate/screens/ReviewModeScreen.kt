package com.door43.translationstudio.ui.translate.screens

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect

@Composable
fun ReviewModeScreen() {

    DisposableEffect(Unit) {
        onDispose {
            println("review closed")
            // Should clear search bar here
        }
    }

    LaunchedEffect(Unit) {
        // Should check if there is a merge conflict
    }

    Text("Review screen")
}