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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.door43.translationstudio.ui.dialogs.ActionDialog
import com.door43.translationstudio.ui.dialogs.ProgressDialog
import kotlinx.coroutines.launch
import org.unfoldingword.tools.logger.Logger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperToolsScreen(
    component: DevToolsComponent
) {
    val state by component.state.collectAsStateWithLifecycle()
    val progress by component.progress.collectAsStateWithLifecycle()

    val clipboardManager = LocalClipboard.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.copied_to_clipboard)

    val copyToClipboard: (String) -> Unit = { text ->
        val clipData = ClipData.newPlainText("text", AnnotatedString(text))
        coroutineScope.launch {
            clipboardManager.setClipEntry(clipData.toClipEntry())
            snackbarHostState.showSnackbar(copiedMessage)
        }
    }

    val noLogsString = stringResource(R.string.no_logs)

    var showLogDialog by rememberSaveable { mutableStateOf(false) }
    var systemResourcesMessage by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        component.loadTools()
    }

    LaunchedEffect(Unit) {
        component.event.collect { event ->
            when (event) {
                is DevToolsComponent.DeveloperEvent.ReadLog -> {
                    showLogDialog = true
                    component.readErrorLog()
                }
                is DevToolsComponent.DeveloperEvent.CheckSystemResources -> {
                    systemResourcesMessage = component.calculateSystemResources()
                }
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
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.title_activity_developer))
                },
                navigationIcon = {
                    IconButton(onClick = component::navigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "back",
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(16.dp)
            ) {
                Row(modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(
                        text = stringResource(R.string.app_version_name, component.versionName),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { copyToClipboard(component.versionName) }
                    )
                    Text(
                        text = stringResource(R.string.app_version_code, component.versionCode),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { copyToClipboard(component.versionCode.toString()) }
                    )
                }
                Text(
                    text = stringResource(R.string.app_udid, component.udid),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.clickable { copyToClipboard(component.udid) }
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
        ActionDialog(
            onDismiss = component::clearKeysRegenerated,
            title = stringResource(R.string.success),
            message = stringResource(R.string.ssh_keys_generated)
        ) {
            TextButton(onClick = component::clearKeysRegenerated) {
                Text(stringResource(R.string.dismiss))
            }
        }
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
        ActionDialog(
            onDismiss = { systemResourcesMessage = null },
            title = stringResource(R.string.system_resources_check),
            message = message
        ) { onActionDismiss ->
            TextButton(onClick = onActionDismiss) {
                Text(stringResource(R.string.label_close))
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