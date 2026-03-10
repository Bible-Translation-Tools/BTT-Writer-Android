package com.door43.translationstudio.ui.crash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.components.ProgressDialog
import com.door43.translationstudio.ui.viewmodels.CrashReporterViewModel
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

    val model by viewModel.model.collectAsStateWithLifecycle()

    LaunchedEffect(model.result) {
        model.result?.let { result ->
            if (result.release != null) {
                showUpdateAvailableDialog = true
            } else {
                viewModel.uploadCrashReport(notes.trim())
            }
        }
    }

    LaunchedEffect(model.crashReportUploaded) {
        model.crashReportUploaded?.let { uploaded ->
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
                    .padding(start = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Text(stringResource(R.string.title_upload))
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(stringResource(R.string.title_upload)) },
            text = { Text(stringResource(R.string.use_internet_confirmation)) },
            confirmButton = {
                Button(onClick = {
                    showConfirmDialog = false
                    viewModel.checkForLatestRelease()
                }) {
                    Text(stringResource(R.string.label_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    onFlushAndSplash()
                }) {
                    Text(stringResource(R.string.label_close))
                }
            }
        )
    }

    if (showUpdateAvailableDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateAvailableDialog = false },
            title = { Text(stringResource(R.string.apk_update_available)) },
            text = { Text(stringResource(R.string.upload_report_or_download_latest_apk)) },
            confirmButton = {
                Button(onClick = {
                    showUpdateAvailableDialog = false
                    viewModel.uploadCrashReport(notes.trim())
                }) {
                    Text(stringResource(R.string.label_continue))
                }
            },
            dismissButton = {
                Row {
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
                }
            }
        )
    }

    if (showUploadErrorDialog) {
        val messageId = if (isNetworkAvailable) {
            R.string.upload_crash_report_failed
        } else {
            R.string.internet_not_available
        }
        
        AlertDialog(
            onDismissRequest = { showUploadErrorDialog = false },
            title = { Text(stringResource(R.string.upload_failed)) },
            text = { Text(stringResource(messageId)) },
            confirmButton = {
                Button(onClick = { showUploadErrorDialog = false }) {
                    Text(stringResource(R.string.label_ok))
                }
            }
        )
    }

    model.progress?.let { progress ->
        ProgressDialog(
            message = progress.message ?: stringResource(R.string.loading),
            progress = progress.progress.toFloat()
        )
    }
}