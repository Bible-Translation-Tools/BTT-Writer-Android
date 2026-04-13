package com.door43.translationstudio.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.door43.translationstudio.R

@Composable
fun PrivacyNoticeDialog(
    onConfirm: (() -> Unit)? = null,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(stringResource(R.string.privacy_notice))
            }
        },
        text = {
            Text(
                stringResource(R.string.publishing_privacy_notice),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        confirmButton = {
            if (onConfirm != null) {
                Button(
                    onClick = {
                        onConfirm()
                        onDismissRequest()
                    }
                ) {
                    Text(stringResource(R.string.label_continue))
                }
            } else {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(R.string.dismiss))
                }
            }
        },
        dismissButton = {
            if (onConfirm != null) {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(R.string.title_cancel))
                }
            }
        }
    )
}