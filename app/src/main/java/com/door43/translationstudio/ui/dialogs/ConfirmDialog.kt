package com.door43.translationstudio.ui.dialogs

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.door43.translationstudio.R

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String = stringResource(R.string.confirm),
    dismissText: String = stringResource(R.string.title_cancel)
) {
    ConfirmDialog(
        title = title,
        message = AnnotatedString(message),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        modifier = modifier,
        confirmText = confirmText,
        dismissText = dismissText
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: AnnotatedString,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String = stringResource(R.string.confirm),
    dismissText: String = stringResource(R.string.title_cancel)
) {
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        title = {
            Text(
                text = title
            )
        },
        text = {
            Text(
                text = message,
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(scrollState)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = dismissText)
            }
        }
    )
}