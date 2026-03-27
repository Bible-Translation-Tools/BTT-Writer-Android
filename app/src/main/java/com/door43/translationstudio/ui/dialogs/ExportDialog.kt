package com.door43.translationstudio.ui.dialogs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.viewmodels.ExportAction
import com.door43.translationstudio.ui.viewmodels.ExportEvent
import com.door43.translationstudio.ui.viewmodels.ExportViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File

private const val EXPORT_GENERIC_MIME_TYPE = "application/octet-stream"
private const val EXPORT_PDF_MIME_TYPE: String = "application/pdf"

@Composable
fun ExportDialog(
    targetTranslation: TargetTranslation,
    onDismiss: () -> Unit,
    onExportToApp: (File) -> Unit
) {
    val viewModel: ExportViewModel = koinViewModel {
        parametersOf(targetTranslation)
    }
    val profile: Profile = koinInject()

    val state by viewModel.state.collectAsStateWithLifecycle()

    val snackBarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showPrintDialog by rememberSaveable { mutableStateOf(false) }
    var showInternetUsageDialog by rememberSaveable { mutableStateOf(false) }

    var imagesToInclude by rememberSaveable { mutableStateOf(false) }
    var incompleteToInclude by rememberSaveable { mutableStateOf(false) }

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

    Dialog(
        onDismissRequest = onDismiss
    ) {
        LaunchedEffect(viewModel) {
            viewModel.event.collect { event ->
                when (event) {
                    is ExportEvent.SnackBarMessage -> {
                        snackBarHostState.showSnackbar(event.message)
                    }
                    is ExportEvent.AppExport -> {
                        onExportToApp(event.file)
                    }
                }
            }
        }

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Box(modifier = Modifier) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .animateContentSize()
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
                            onClick = { /*onCloudBackup*/ }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = "wifi",
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(
                                    R.string.current_user,
                                    profile.currentUser
                                ),
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                fontSize = 14.sp,
                                textAlign = TextAlign.End
                            )
                            TextButton(
                                onClick = { /*onLogout*/ },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.secondary,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
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
                        TextButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                contentColor = MaterialTheme.colorScheme.secondary,
                                containerColor = Color.Transparent
                            )
                        ) {
                            Text(text = stringResource(id = R.string.dismiss).uppercase())
                        }
                    }
                }

                SnackbarHost(
                    hostState = snackBarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 40.dp)
                ) { data ->
                    Snackbar(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        containerColor = MaterialTheme.colorScheme.surface,
                        snackbarData = data
                    )
                }
            }
        }

        state.exportMessage?.let { export ->
            AlertDialog(
                onDismissRequest = {
                    viewModel.onAction(ExportAction.ClearExport)
                },
                title = {
                    Text(export.title)
                },
                text = {
                    Text(export.message)
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.onAction(ExportAction.ClearExport)
                        }
                    ) {
                        Text(stringResource(R.string.dismiss))
                    }
                }
            )
        }

        if (showPrintDialog) {
            PrintDialog(
                projectTitle = state.projectTitle,
                isObs = viewModel.targetTranslation.isObsProject,
                onDismiss = { showPrintDialog = false },
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