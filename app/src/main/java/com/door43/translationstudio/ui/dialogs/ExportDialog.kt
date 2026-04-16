package com.door43.translationstudio.ui.dialogs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File

private const val EXPORT_GENERIC_MIME_TYPE = "application/octet-stream"
private const val EXPORT_PDF_MIME_TYPE: String = "application/pdf"

@Composable
fun ExportDialog(
    targetTranslation: TargetTranslation,
    openPrint: Boolean,
    onDismiss: () -> Unit,
    onExportToApp: (File) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onMergeConflict: () -> Unit
) {
    val viewModel: ExportViewModel = koinViewModel {
        parametersOf(targetTranslation)
    }
    val profile: Profile = koinInject()

    val state by viewModel.state.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    var showPrintDialog by rememberSaveable { mutableStateOf(openPrint) }
    var showInternetUsageDialog by rememberSaveable { mutableStateOf(false) }
    var showAuthDialog by rememberSaveable { mutableStateOf(false) }
    var showLoginDialog by rememberSaveable { mutableStateOf(false) }
    var showFeedbackDialog by rememberSaveable { mutableStateOf(false) }

    var imagesToInclude by rememberSaveable { mutableStateOf(false) }
    var incompleteToInclude by rememberSaveable { mutableStateOf(false) }

    var profileUser by remember { mutableStateOf(profile.currentUser) }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(EXPORT_PDF_MIME_TYPE),
        onResult = { uri ->
            uri?.let {
                viewModel.onAction(
                    ExportAction.PrintPdf(
                        includeImages = imagesToInclude,
                        includeIncomplete = incompleteToInclude,
                        it
                    )
                )
                showPrintDialog = false
            }
        }
    )

    val usfmPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(EXPORT_GENERIC_MIME_TYPE),
        onResult = { uri ->
            uri?.let {
                viewModel.onAction(
                    ExportAction.ExportUsfm(it)
                )
            }
        }
    )

    val projectPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(EXPORT_GENERIC_MIME_TYPE),
        onResult = { uri ->
            uri?.let {
                viewModel.onAction(
                    ExportAction.ExportProject(it)
                )
            }
        }
    )

    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                is ExportEvent.SnackbarMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is ExportEvent.AppExport -> onExportToApp(event.file)
                is ExportEvent.OnLogin -> onLogin()
                is ExportEvent.OnLogout -> onLogout()
                is ExportEvent.AuthRequested -> showAuthDialog = true
            }
        }
    }

    LifecycleResumeEffect(Unit) {
        profileUser = profile.currentUser
        onPauseOrDispose {}
    }

    OverlayDialog(
        snackbarHostState = snackbarHostState,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp)
        ) {
            Text(
                text = stringResource(id = R.string.title_upload_export),
                fontSize = 24.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            ExportOptionRow(
                title = stringResource(id = R.string.backup_to_door43),
                tip = stringResource(id = R.string.tip_backup_to_door43),
                icon = Icons.Default.CloudUpload,
                onClick = {
                    if (profile.gogsUser != null) {
                        viewModel.onAction(ExportAction.ExportToCloud)
                    } else {
                        showLoginDialog = true
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = "wifi",
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.current_user, profileUser),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    fontSize = 14.sp,
                    textAlign = TextAlign.End
                )
                TextButton(
                    onClick = {
                        viewModel.onAction(ExportAction.Logout(false))
                    },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.log_out),
                        fontSize = 14.sp
                    )
                }
            }

            HorizontalDivider()

            if (!viewModel.targetTranslation.isObsProject) {
                ExportOptionRow(
                    title = stringResource(id = R.string.export_to_usfm),
                    tip = stringResource(id = R.string.tip_export_to_usfm),
                    icon = Icons.Default.SdCard,
                    onClick = {
                        usfmPickerLauncher.launch(
                            "${targetTranslation.id}.${Translator.USFM_EXTENSION}"
                        )
                    }
                )

                HorizontalDivider()
            }

            ExportOptionRow(
                title = stringResource(id = R.string.export_to_pdf),
                tip = stringResource(id = R.string.tip_export_to_pdf),
                icon = Icons.Default.SdCard,
                onClick = { showPrintDialog = true }
            )

            HorizontalDivider()

            ExportOptionRow(
                title = stringResource(id = R.string.backup_to_sd),
                tip = stringResource(id = R.string.tip_backup_to_sd),
                icon = Icons.Default.SdCard,
                onClick = {
                    projectPickerLauncher.launch(
                        "${targetTranslation.id}.${Translator.TSTUDIO_EXTENSION}"
                    )
                }
            )

            HorizontalDivider()

            ExportOptionRow(
                title = stringResource(id = R.string.backup_to_app),
                icon = Icons.Default.Share,
                onClick = {
                    viewModel.onAction(ExportAction.ExportToApp)
                }
            )

            HorizontalDivider()
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.dismiss))
            }
        }
    }

    state.info?.let {
        ActionDialog(
            title = it.title,
            message = it.message,
            onDismiss = {
                viewModel.onAction(ExportAction.ClearInfoMessage)
            },
            buttons = { onDismiss ->
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        )
    }

    state.uploadError?.let {
        ActionDialog(
            title = it.title,
            message = it.message,
            onDismiss = {
                viewModel.onAction(ExportAction.ClearErrorMessage)
            },
            buttons = { onDismiss ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.dismiss))
                    }
                    TextButton(
                        onClick = {
                            onDismiss()
                            showFeedbackDialog = true
                        }
                    ) {
                        Text(stringResource(R.string.menu_bug))
                    }
                }
            }
        )
    }

    state.uploadSuccess?.let { info ->
        UploadSuccessDialog(
            info = info,
            onDismiss = {
                viewModel.onAction(ExportAction.ClearUploadSuccess)
            }
        )
    }

    if (showPrintDialog) {
        PrintDialog(
            projectTitle = viewModel.projectTitle,
            isObs = viewModel.targetTranslation.isObsProject,
            onDismiss = {
                showPrintDialog = false
                if (openPrint) onDismiss()
            },
            onPrint = { includeImages, includeIncomplete ->
                incompleteToInclude = includeIncomplete
                imagesToInclude = includeImages

                if (includeImages) {
                    showInternetUsageDialog = true
                } else {
                    pdfPickerLauncher.launch(
                        "${targetTranslation.id}.${Translator.PDF_EXTENSION}"
                    )
                }
            }
        )
    }

    if (showInternetUsageDialog) {
        ConfirmDialog(
            title = stringResource(R.string.use_internet_confirmation),
            message = stringResource(R.string.image_large_download),
            onDismiss = { showInternetUsageDialog = false },
            onConfirm = {
                pdfPickerLauncher.launch(
                    "${targetTranslation.id}.${Translator.PDF_EXTENSION}"
                )
            }
        )
    }

    if (showAuthDialog) {
        ConfirmDialog(
            title = stringResource(R.string.upload_failed),
            message = stringResource(R.string.auth_failure_retry),
            onDismiss = { showAuthDialog = false },
            onConfirm = {
                showAuthDialog = false
                viewModel.onAction(ExportAction.RegisterKeys)
            }
        )
    }

    if (showLoginDialog) {
        LoginOnlineDialog(
            onLogin = {
                viewModel.onAction(ExportAction.Logout(true))
            },
            onDismiss = { showLoginDialog = false }
        )
    }

    if (showFeedbackDialog) {
        val message = "Failed to upload the translation of ${viewModel.projectName}" +
                "into ${targetTranslation.targetLanguageName}.\n" +
                "targetTranslation: ${targetTranslation.id}" +
                "\n--------\n\n"

        FeedbackDialog(
            feedbackText = message,
            onDismiss = { showFeedbackDialog = false }
        )
    }

    state.mergeConflict?.let { conflict ->
        ActionDialog(
            title = conflict.title,
            message = conflict.message,
            onDismiss = { viewModel.onAction(ExportAction.ClearMergeConflict) },
            buttons = { onInfoDismiss ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            onInfoDismiss()
                            onDismiss()
                            onMergeConflict()
                        }
                    ) {
                        Text(stringResource(R.string.yes))
                    }
                    TextButton(
                        onClick = {
                            onInfoDismiss()
                            viewModel.onAction(ExportAction.ResetToMaster)
                        }
                    ) {
                        Text(stringResource(R.string.no))
                    }
                }
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

@Composable
private fun ExportOptionRow(
    title: String,
    tip: String? = null,
    icon: ImageVector,
    onClick: () -> Unit,
    extraContent: @Composable (RowScope.() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = title,
                modifier = Modifier.weight(1f),
                fontSize = 20.sp
            )

            extraContent?.invoke(this)
        }

        if (!tip.isNullOrEmpty()) {
            Text(
                text = tip,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 34.dp)
            )
        }
    }
}

@Composable
private fun UploadSuccessDialog(
    info: UploadSuccess,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    var showUploadDetailsDialog by rememberSaveable { mutableStateOf(false) }

    if (!showUploadDetailsDialog) {
        ActionDialog(
            onDismiss = onDismiss,
            title = stringResource(R.string.upload_complete),
            message = stringResource(R.string.project_uploaded_to, info.url)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = {
                        showUploadDetailsDialog = true
                    }
                ) {
                    Text(stringResource(R.string.label_details))
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dismiss))
                }
                TextButton(
                    onClick = {
                        uriHandler.openUri(info.url)
                        onDismiss()
                    }
                ) {
                    Text(stringResource(R.string.view_online))
                }
            }
        }
    } else {
        ActionDialog(
            onDismiss = {
                showUploadDetailsDialog = false
                onDismiss()
            },
            title = stringResource(R.string.project_uploaded),
            message = info.details ?: ""
        ) { onDetailsDismiss ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onDetailsDismiss) {
                    Text(stringResource(R.string.dismiss))
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        uriHandler.openUri(info.url)
                        onDetailsDismiss()
                    }
                ) {
                    Text(stringResource(R.string.view_online))
                }
            }
        }
    }
}