package com.door43.translationstudio.ui.dialogs

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
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
    ActionDialog(
        onDismiss = onDismiss,
        modifier = modifier,
        title = title,
        message = message
    ) {
        TextButton(onClick = onDismiss) {
            Text(text = dismissText)
        }
        TextButton(onClick = onConfirm) {
            Text(text = confirmText)
        }
    }
}