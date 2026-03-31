package com.door43.translationstudio.ui.translate.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.components.OverlayDialog
import kotlinx.coroutines.delay

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
    OverlayDialog(
        onDismiss = onDismissRequest
    ) { dismissWithKeyboard ->
        val focusRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            delay(300)
            focusRequester.requestFocus()
        }

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