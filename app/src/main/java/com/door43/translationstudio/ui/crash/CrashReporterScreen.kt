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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.dialogs.ConfirmDialog
import com.door43.translationstudio.ui.dialogs.ActionDialog
import com.door43.translationstudio.ui.dialogs.ProgressDialog
import org.koin.androidx.compose.koinViewModel

@Composable
fun CrashReporterScreen(
    viewModel: CrashReporterViewModel = koinViewModel(),
    isNetworkAvailable: Boolean,
    onFlushAndSplash: () -> Unit,
    onDownloadUpdate: () -> Unit
) {
    var notes by rememberSaveable { mutableStateOf("") }
    
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showUpdateAvailableDialog by remember { mutableStateOf(false) }
    var showUploadErrorDialog by remember { mutableStateOf(false) }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()

    LaunchedEffect(state.result) {
        state.result?.let { result ->
            if (result.release != null) {
                showUpdateAvailableDialog = true
            } else {
                viewModel.uploadCrashReport(notes.trim())
            }
        }
    }

    LaunchedEffect(state.crashReportUploaded) {
        state.crashReportUploaded?.let { uploaded ->
            if (uploaded) {
                onFlushAndSplash()
            } else {
                showUploadErrorDialog = true
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
            value = notes,
            onValueChange = { notes = it },
            placeholder = { Text(stringResource(R.string.crash_details)) },
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
                onClick = onFlushAndSplash,
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
                viewModel.checkForLatestRelease()
            },
            onDismiss = {
                showConfirmDialog = false
                onFlushAndSplash()
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
                    onFlushAndSplash()
                }) {
                    Text(stringResource(R.string.title_cancel))
                }
                TextButton(onClick = {
                    showUpdateAvailableDialog = false
                    onDownloadUpdate()
                }) {
                    Text(stringResource(R.string.download_update))
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(onClick = {
                    showUpdateAvailableDialog = false
                    viewModel.uploadCrashReport(notes.trim())
                }) {
                    Text(stringResource(R.string.label_continue))
                }
            }
        }
    }

    if (showUploadErrorDialog) {
        val messageId = if (isNetworkAvailable) {
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