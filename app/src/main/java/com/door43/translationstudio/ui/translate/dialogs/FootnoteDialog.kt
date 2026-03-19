package com.door43.translationstudio.ui.translate.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.door43.translationstudio.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class FootnoteDialogType {
    VIEW, ACT, EDIT
}

@Composable
fun FootnoteDialog(
    title: String,
    text: String,
    type: FootnoteDialogType,
    onDismissRequest: () -> Unit,
    onDeleteNote: () -> Unit = {},
    onEditNote: () -> Unit = {},
    onSaveText: (String) -> Unit = {}
) {
    var pendingDismiss by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { pendingDismiss = true }
    ) {
        val focusManager = LocalFocusManager.current
        val focusRequester = remember { FocusRequester() }
        val scope = rememberCoroutineScope()

        LaunchedEffect(pendingDismiss) {
            if (pendingDismiss) {
                focusManager.clearFocus()
                delay(100)
                onDismissRequest()
            }
        }

        // Hide keyboard first, then dismiss after it has time to process
        val dismissWithKeyboard: (() -> Unit) -> Unit = { action ->
            focusManager.clearFocus()
            scope.launch {
                delay(100)
                action()
            }
        }

        LaunchedEffect(Unit) {
            delay(300)
            focusRequester.requestFocus()
        }

        Surface(
            modifier = Modifier.fillMaxWidth(0.9f),
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (type == FootnoteDialogType.EDIT) {
                        val textFieldState = remember { TextFieldState(text) }

                        TextField(
                            state = textFieldState,
                            lineLimits = TextFieldLineLimits.SingleLine,
                            onKeyboardAction = {
                                dismissWithKeyboard {
                                    onSaveText(textFieldState.text.toString())
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(
                                onClick = {
                                    dismissWithKeyboard(onDismissRequest)
                                }
                            ) {
                                Text(stringResource(R.string.title_cancel))
                            }
                            TextButton(onClick = {
                                dismissWithKeyboard {
                                    onSaveText(textFieldState.text.toString())
                                }
                            }) {
                                Text(stringResource(R.string.label_ok))
                            }
                        }
                    } else {
                        Text(text)

                        Spacer(modifier = Modifier.height(32.dp))

                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (type == FootnoteDialogType.ACT) {
                                TextButton(onClick = {
                                    dismissWithKeyboard(onDeleteNote)
                                }) {
                                    Text(stringResource(R.string.label_delete))
                                }
                                TextButton(onClick = {
                                    dismissWithKeyboard(onEditNote)
                                }) {
                                    Text(stringResource(R.string.edit))
                                }
                            }
                            TextButton(onClick = {
                                dismissWithKeyboard(onDismissRequest)
                            }) {
                                Text(stringResource(R.string.dismiss))
                            }
                        }
                    }
                }
            }
        }
    }
}