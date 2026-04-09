package com.door43.translationstudio.ui.dialogs

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OverlayDialog(
    onDismiss: () -> Unit,
    contentPadding: Dp = 16.dp,
    maxWidth: Dp = 700.dp,
    maxHeight: Dp = 700.dp,
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable ColumnScope.(dismissWithKeyboard: (() -> Unit) -> Unit) -> Unit
) {
    var pendingDismiss by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { pendingDismiss = true },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        val focusManager = LocalFocusManager.current
        val scope = rememberCoroutineScope()

        val dismissWithKeyboard: (() -> Unit) -> Unit = { action ->
            focusManager.clearFocus()
            scope.launch {
                delay(100)
                action()
            }
        }

        Scaffold(
            containerColor = Color.Black.copy(alpha = 0.6f),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures {
                        dismissWithKeyboard(onDismiss)
                    }
                },
            snackbarHost = {
                snackbarHostState?.let {
                    SnackbarHost(hostState = it)
                }
            },
            contentWindowInsets = WindowInsets.ime
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .widthIn(max = maxWidth)
                        .fillMaxWidth(0.9f)
                        .heightIn(max = maxHeight)
                        .fillMaxHeight(0.9f)
                        .wrapContentHeight()
                        .pointerInput(Unit) {
                            detectTapGestures {}
                        },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(contentPadding)) {
                        content(dismissWithKeyboard)
                    }
                }
            }
        }
    }
}