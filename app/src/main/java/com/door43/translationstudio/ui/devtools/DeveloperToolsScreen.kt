package com.door43.translationstudio.ui.devtools

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.components.ProgressDialog
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.unfoldingword.tools.logger.Logger

@Composable
fun DeveloperToolsScreen(
    viewModel: DeveloperViewModel = koinViewModel(),
    versionName: String,
    versionCode: Int,
    udid: String,
    systemResourcesMessage: String?,
    onDismissSystemResources: () -> Unit,
    onDeleteLibrary: () -> Unit,
    onCalculateSystemResources: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()

    val clipboardManager = LocalClipboard.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.copied_to_clipboard)

    var showLogDialog by rememberSaveable { mutableStateOf(false) }

    val copyToClipboard: (String) -> Unit = { text ->
        val clipData = ClipData.newPlainText("text", AnnotatedString(text))
        coroutineScope.launch {
            clipboardManager.setClipEntry(clipData.toClipEntry())
            snackbarHostState.showSnackbar(copiedMessage)
        }
    }

    val noLogsString = stringResource(R.string.no_logs)

    LaunchedEffect(Unit) {
        viewModel.loadTools()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DeveloperEvent.ReadLog -> {
                    showLogDialog = true
                    viewModel.readErrorLog()
                }
                is DeveloperEvent.CheckSystemResources -> onCalculateSystemResources()
                is DeveloperEvent.DeleteLibrary -> onDeleteLibrary()
            }
        }
    }

    LaunchedEffect(showLogDialog, state.logs) {
        if (showLogDialog && state.logs.isEmpty()) {
            snackbarHostState.showSnackbar(noLogsString)
            showLogDialog = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(16.dp)
            ) {
                Row(modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(
                        text = stringResource(R.string.app_version_name, versionName),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { copyToClipboard(versionName) }
                    )
                    Text(
                        text = stringResource(R.string.app_version_code, versionCode),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { copyToClipboard(versionCode.toString()) }
                    )
                }
                Text(
                    text = stringResource(R.string.app_udid, udid),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.clickable { copyToClipboard(udid) }
                )
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.tools) { tool ->
                    ToolListItem(tool = tool)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    if (state.keysRegenerated == true) {
        AlertDialog(
            onDismissRequest = viewModel::clearKeysRegenerated,
            title = { Text(stringResource(R.string.success)) },
            text = { Text("The SSH keys have been regenerated") },
            confirmButton = {
                TextButton(onClick = viewModel::clearKeysRegenerated) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        )
    }

    if (showLogDialog && state.logs.isNotEmpty()) {
        ErrorLogDialog(
            logs = state.logs,
            onEmptyLog = {
                Logger.flush()
                showLogDialog = false
            },
            onDismiss = { showLogDialog = false }
        )
    }

    systemResourcesMessage?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissSystemResources,
            title = { Text(stringResource(R.string.system_resources_check)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = onDismissSystemResources) {
                    Text(stringResource(R.string.label_close))
                }
            }
        )
    }

    progress?.let { progress ->
        ProgressDialog(
            message = progress.message,
            progress = progress.value
        )
    }
}

@Composable
fun ToolListItem(tool: ToolItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = tool.isEnabled) { tool.action() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (tool.icon != null) {
            Icon(
                imageVector = tool.icon,
                contentDescription = null,
                tint = if (tool.isEnabled) {
                    MaterialTheme.colorScheme.primary
                } else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.padding(end = 16.dp)
            )
        }
        
        Column {
            Text(
                text = tool.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (tool.isEnabled) {
                    MaterialTheme.colorScheme.onSurface
                } else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            if (tool.description.isNotEmpty()) {
                Text(
                    text = tool.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}