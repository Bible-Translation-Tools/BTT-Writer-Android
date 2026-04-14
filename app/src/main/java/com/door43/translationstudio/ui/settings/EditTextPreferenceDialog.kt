package com.door43.translationstudio.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.door43.translationstudio.R


@Composable
fun EditTextPreferenceDialog(
    title: String,
    initialValue: String,
    onValueSaved: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    var textInput by remember { mutableStateOf(initialValue) }

    val customSelectionColors = TextSelectionColors(
        handleColor = MaterialTheme.colorScheme.primary,
        backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = title) },
        text = {
            CompositionLocalProvider(
                LocalTextSelectionColors provides customSelectionColors
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onValueSaved(textInput)
                    onDismissRequest()
                }
            ) {
                Text(stringResource(R.string.menu_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.menu_cancel))
            }
        },
        shape = RoundedCornerShape(8.dp)
    )
}