package com.door43.translationstudio.ui.dialogs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

@Composable
fun BaseDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    message: String,
    buttons: @Composable (onDismiss: () -> Unit) -> Unit
) {
    BaseDialog(
        title = title,
        message = AnnotatedString(message),
        onDismiss = onDismiss,
        modifier = modifier,
        buttons = buttons
    )
}

@Composable
fun BaseDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    message: AnnotatedString,
    buttons: @Composable (onDismiss: () -> Unit) -> Unit
) {
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(it) } },
        text = {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(scrollState)
            )
        },
        shape = RoundedCornerShape(8.dp),
        confirmButton = {
            buttons {
                onDismiss()
            }
        },
        modifier = modifier.fillMaxWidth(0.8f)
    )
}