package com.door43.translationstudio.ui.dialogs.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.dialogs.BaseDialog
import com.door43.translationstudio.ui.dialogs.ConfirmDialog
import com.door43.translationstudio.ui.dialogs.OverlayDialog
import com.door43.translationstudio.ui.dialogs.ProgressDialog

@Composable
fun FeedbackDialog(
    component: FeedbackComponent,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(component.initialMessage) }
    val progress by component.progress.collectAsStateWithLifecycle()

    val state by component.state.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(component) {
        component.event.collect { event ->
            when (event) {
                is FeedbackComponent.FeedbackEvent.SnackbarMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    OverlayDialog(
        snackbarHostState = snackbarHostState,
        onDismiss = onDismiss
    ) { dismissWithKeyboard ->
        Text(
            text = stringResource(R.string.feedback),
            fontSize = 24.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = "requires internet",
                modifier = Modifier.padding(end = 5.dp)
            )
            Text(
                text = stringResource(R.string.requires_internet),
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = {
                Text(stringResource(R.string.bug_report))
            },
            modifier = Modifier.fillMaxWidth()
                .height(150.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { dismissWithKeyboard(onDismiss) }) {
                Text(
                    text = stringResource(R.string.title_cancel),
                    fontSize = 14.sp
                )
            }
            TextButton(
                onClick = { component.reportBug(text) }
            ) {
                Text(
                    text = stringResource(R.string.confirm),
                    fontSize = 14.sp
                )
            }
        }
    }

    state.uploadError?.let { error ->
        BaseDialog(
            title = stringResource(R.string.upload_failed),
            message = error,
            onDismiss = component::clearError
        ) { onDismiss ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.label_close))
                }
                TextButton(
                    onClick = {
                        onDismiss()
                        component.reportBug(text)
                    }
                ) {
                    Text(stringResource(R.string.retry_label))
                }
            }
        }
    }

    state.release?.let { release ->
        ConfirmDialog(
            title = stringResource(R.string.apk_update_available),
            message = stringResource(R.string.download_latest_apk),
            onDismiss = component::clearRelease,
            onConfirm = {
                component.downloadLatestRelease(release)
            }
        )
    }

    progress?.let {
        ProgressDialog(
            message = it.message,
            progress = it.value
        )
    }
}