package com.door43.translationstudio.ui.crash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.dialogs.ActionDialog
import com.door43.translationstudio.ui.dialogs.ConfirmDialog
import com.door43.translationstudio.ui.dialogs.ProgressDialog

@Composable
fun CrashReporterScreen(
    component: CrashComponent
) {
    var showConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showUpdateAvailableDialog by rememberSaveable { mutableStateOf(false) }
    var showUploadErrorDialog by rememberSaveable { mutableStateOf(false) }

    val state by component.state.collectAsStateWithLifecycle()
    val progress by component.progress.collectAsStateWithLifecycle()

    LaunchedEffect(component) {
        component.event.collect { event ->
            when (event) {
                is CrashComponent.Event.UpdateAvailable -> {
                    showUpdateAvailableDialog = true
                }
                is CrashComponent.Event.UploadError -> {
                    showUploadErrorDialog = true
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(5.dp)
            .imePadding()
    ) {
        OutlinedTextField(
            value = state.notes,
            onValueChange = component::updateNotes,
            placeholder = {
                Text(stringResource(R.string.crash_details))
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = component::flushAndRestart,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(stringResource(R.string.title_cancel))
            }

            Button(
                onClick = { showConfirmDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            ) {
                Text(stringResource(R.string.title_upload))
            }
        }
    }

    if (showConfirmDialog) {
        ConfirmDialog(
            title = stringResource(R.string.title_upload),
            message = stringResource(R.string.use_internet_confirmation),
            onConfirm = {
                showConfirmDialog = false
                component.checkForLatestRelease()
            },
            onDismiss = {
                showConfirmDialog = false
                component.flushAndRestart()
            },
            confirmText = stringResource(R.string.label_continue),
            dismissText = stringResource(R.string.label_close)
        )
    }

    if (showUpdateAvailableDialog) {
        ActionDialog(
            title = stringResource(R.string.apk_update_available),
            message = stringResource(R.string.upload_report_or_download_latest_apk),
            onDismiss = { showUpdateAvailableDialog = false }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = {
                    showUpdateAvailableDialog = false
                    component.flushAndRestart()
                }) {
                    Text(stringResource(R.string.title_cancel))
                }
                TextButton(onClick = {
                    showUpdateAvailableDialog = false
                    component.downloadLatestRelease()
                }) {
                    Text(stringResource(R.string.download_update))
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(onClick = {
                    showUpdateAvailableDialog = false
                    component.uploadCrashReport()
                }) {
                    Text(stringResource(R.string.label_continue))
                }
            }
        }
    }

    if (showUploadErrorDialog) {
        val messageId = if (component.isNetworkAvailable) {
            R.string.upload_crash_report_failed
        } else {
            R.string.internet_not_available
        }
        
        ActionDialog(
            onDismiss = { showUploadErrorDialog = false },
            title = stringResource(R.string.upload_failed),
            message = stringResource(messageId)
        ) {
            Button(onClick = { showUploadErrorDialog = false }) {
                Text(stringResource(R.string.label_ok))
            }
        }
    }

    progress?.let { progress ->
        ProgressDialog(
            message = progress.message,
            progress = progress.value
        )
    }
}