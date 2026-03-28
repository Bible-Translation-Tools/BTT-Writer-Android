package com.door43.translationstudio.ui.publish

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.ui.PrimaryDarkBlue
import com.door43.translationstudio.ui.components.CardsSkeletonList
import com.door43.translationstudio.ui.dialogs.ExportDialog
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import java.io.File

private enum class PublishSection {
    VALIDATION,
    TRANSLATORS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishScreen(
    viewModel: PublishViewModel = koinViewModel(),
    onOpenReview: () -> Unit,
    onExportToApp: (File) -> Unit,
    onLogout: () -> Unit,
    onMergeConflict: () -> Unit
) {
    val typography: Typography = koinInject()

    val state by viewModel.state.collectAsStateWithLifecycle()

    var publishSection by rememberSaveable {
        mutableStateOf(PublishSection.VALIDATION)
    }
    var showUploadDialog by rememberSaveable {
        mutableStateOf(false)
    }

    val snackBarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val noTranslatorsMessage = stringResource(R.string.need_translator_notice)

    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                is PublishEvent.OpenReview -> onOpenReview()
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackBarHostState) { data ->
                Snackbar(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    containerColor = MaterialTheme.colorScheme.surface,
                    snackbarData = data
                )
            }
        },
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
                    .height(110.dp)
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val buttonModifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)

                    PublishButton(
                        text = stringResource(id = R.string.title_book),
                        selected = publishSection == PublishSection.VALIDATION,
                        modifier = buttonModifier,
                        onClick = { publishSection = PublishSection.VALIDATION }
                    )

                    PublishButton(
                        text = stringResource(id = R.string.translators),
                        selected = publishSection == PublishSection.TRANSLATORS,
                        modifier = buttonModifier,
                        onClick = { publishSection = PublishSection.TRANSLATORS }
                    )

                    PublishButton(
                        text = stringResource(id = R.string.menu_upload_export),
                        selected = false,
                        modifier = buttonModifier,
                        onClick = { showUploadDialog = true }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (state.isLoading) {
                    CardsSkeletonList()
                } else {
                    when (publishSection) {
                        PublishSection.VALIDATION -> ValidationSection(
                            items = state.validations,
                            typography = typography,
                            onNextClick = { publishSection = PublishSection.TRANSLATORS },
                            onReviewClick = {
                                viewModel.onAction(PublishAction.OpenReview(it))
                            }
                        )
                        PublishSection.TRANSLATORS -> TranslatorsSection(
                            translators = state.translators,
                            targetTranslation = viewModel.targetTranslation,
                            onNextClick = {
                                if (state.translators.isNotEmpty()) {
                                    showUploadDialog = true
                                } else {
                                    coroutineScope.launch {
                                        snackBarHostState.showSnackbar(
                                            noTranslatorsMessage
                                        )
                                    }
                                }
                            },
                            onContributorsChanged = {
                                viewModel.onAction(PublishAction.RefreshContributors)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showUploadDialog) {
        ExportDialog(
            targetTranslation = viewModel.targetTranslation,
            onExportToApp = onExportToApp,
            onLogout = onLogout,
            onMergeConflict = onMergeConflict,
            onDismiss = { showUploadDialog = false }
        )
    }
}

@Composable
fun PublishButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) PrimaryDarkBlue else {
                MaterialTheme.colorScheme.secondary
            }
        )
    ) {
        Text(
            text = text.uppercase(),
            color = Color.White,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}