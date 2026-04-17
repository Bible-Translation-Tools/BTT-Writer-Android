package com.door43.translationstudio.ui.dialogs

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.translate.FootnoteAction
import kotlinx.coroutines.delay

@Composable
fun FootnoteDialog(
    text: String,
    action: FootnoteAction,
    onDismissRequest: () -> Unit,
    onDeleteNote: () -> Unit = {},
    onSaveText: (String) -> Unit = {}
) {
    OverlayDialog(
        onDismiss = onDismissRequest
    ) { dismissWithKeyboard ->

        var showEditor by rememberSaveable { mutableStateOf(false) }
        val textFieldState = remember { TextFieldState(text) }
        val focusRequester = remember { FocusRequester() }

        LaunchedEffect(showEditor) {
            delay(300)
            focusRequester.requestFocus()
        }

        Text(
            text = if (action == FootnoteAction.EDIT) {
                stringResource(R.string.title_add_footnote)
            } else stringResource(R.string.title_footnote),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        when {
            showEditor || action == FootnoteAction.EDIT -> {
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
                        onClick = { dismissWithKeyboard(onDismissRequest) }
                    ) {
                        Text(stringResource(R.string.title_cancel))
                    }
                    TextButton(onClick = {
                        dismissWithKeyboard {
                            onSaveText(textFieldState.text.toString())
                        }
                    }) {
                        Text(stringResource(R.string.menu_save))
                    }
                }
            }
            action == FootnoteAction.ACTIONS -> {
                Text(text)

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDeleteNote) {
                        Text(stringResource(R.string.label_delete))
                    }
                    TextButton(onClick = { showEditor = true }) {
                        Text(stringResource(R.string.edit))
                    }
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(R.string.dismiss))
                    }
                }
            }
            action == FootnoteAction.VIEW -> {
                Text(text)

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(R.string.dismiss))
                    }
                }
            }
        }
    }
}