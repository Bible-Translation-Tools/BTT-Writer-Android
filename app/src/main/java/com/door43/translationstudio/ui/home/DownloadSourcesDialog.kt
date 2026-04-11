package com.door43.translationstudio.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.dialogs.OverlayDialog
import com.door43.translationstudio.ui.dialogs.ProgressDialog
import com.door43.translationstudio.ui.viewmodels.DownloadAction
import com.door43.translationstudio.ui.viewmodels.DownloadListItem
import com.door43.translationstudio.ui.viewmodels.DownloadSourcesViewModel
import com.door43.translationstudio.ui.viewmodels.FilterMode
import com.door43.translationstudio.ui.viewmodels.SelectionType
import org.koin.androidx.compose.koinViewModel

@Composable
fun DownloadSourcesDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: DownloadSourcesViewModel = koinViewModel()

    val state by viewModel.state.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    OverlayDialog(
        onDismiss = onDismiss,
        snackbarHostState = snackbarHostState,
        maxWidth = 1000.dp,
        maxHeight = 1000.dp
    ) {
        Column(
            modifier = modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = state.filterMode == FilterMode.ByLanguage,
                        onClick = {
                            viewModel.onAction(DownloadAction.FilterModeChanged(
                                FilterMode.ByLanguage
                            ))
                        }
                    )
                    Text(stringResource(R.string.by_language_label))
                }

                Spacer(modifier = Modifier.width(24.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = state.filterMode == FilterMode.ByBook,
                        onClick = {
                            viewModel.onAction(DownloadAction.FilterModeChanged(
                                FilterMode.ByBook
                            ))
                        }
                    )
                    Text(stringResource(R.string.by_book_label))
                }
            }

            HorizontalDivider()

            NavigationBar(
                breadcrumbs = state.navigationStack.mapNotNull { it.label },
                query = state.searchQuery,
                enableSearch = state.navigationStack.lastOrNull()?.selection == SelectionType.LANGUAGE,
                onQueryChanged = {
                    viewModel.onAction(DownloadAction.Search(it))
                },
                onBackClicked = {
                    viewModel.onAction(DownloadAction.NavigateBack)
                },
                onBreadcrumbClicked = {
                    viewModel.onAction(DownloadAction.NavigateStep(it))
                }
            )

            if (state.navigationStack.last().selection.isDownloadable) {
                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = state.selectAllChecked,
                            onCheckedChange = {
                                viewModel.onAction(DownloadAction.SelectAll(it))
                            }
                        )
                        Text(stringResource(R.string.select_all_label))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = !state.selectAllChecked && state.listItems.none {
                                (it as? DownloadListItem.SourceSelection)?.isSelected == true
                            },
                            onCheckedChange = {
                                if (it) viewModel.onAction(DownloadAction.SelectAll(false))
                            }
                        )
                        Text(stringResource(R.string.unselect_all_label))
                    }

                    Button(
                        onClick = {
                            viewModel.onAction(DownloadAction.DownloadSources)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(stringResource(R.string.download))
                    }
                }
            }

            HorizontalDivider()

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                items(state.listItems) { item ->
                    when (item) {
                        is DownloadListItem.FilterCategory -> {
                            FilterCategoryItemView(
                                item = item,
                                onClick = {
                                    viewModel.onAction(DownloadAction.NavigateForward(item))
                                }
                            )
                        }
                        is DownloadListItem.SourceSelection -> {
                            SourceSelectionItemView(
                                item = item,
                                onClick = {
                                    viewModel.onAction(DownloadAction.ToggleSelection(item.id))
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    progress?.let {
        ProgressDialog(
            message = it.message,
            progress = it.value,
            details = it.details
        )
    }
}

@Composable
private fun NavigationBar(
    breadcrumbs: List<String>,
    query: String,
    onQueryChanged: (String) -> Unit,
    enableSearch: Boolean = true,
    onBackClicked: () -> Unit,
    onBreadcrumbClicked: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClicked) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back"
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            breadcrumbs.forEachIndexed { index, crumb ->
                val arrow = if (index < breadcrumbs.size - 1) " > " else ""
                Text(
                    text = "$crumb $arrow",
                    modifier = Modifier.clickable {
                        if (index < breadcrumbs.size - 1) {
                            onBreadcrumbClicked(index)
                        }
                    }
                )
            }
        }

        if (enableSearch) {
            TextField(
                value = query,
                onValueChange = onQueryChanged,
                singleLine = true,
                placeholder = {
                    Text(stringResource(R.string.search_for_language))
                },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun FilterCategoryItemView(
    item: DownloadListItem.FilterCategory,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item.icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null
            )
        }

        Text(
            text = item.title,
            modifier = Modifier.weight(1f)
        )

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Next"
        )
    }
}

@Composable
private fun SourceSelectionItemView(
    item: DownloadListItem.SourceSelection,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = item.isSelected || item.isDownloaded,
            onCheckedChange = null
        )

        if (item.errorMessage != null) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error
            )
        }

        Text(
            text = item.projectName,
            modifier = Modifier.weight(0.5f),
            textAlign = TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = item.resourceName,
            modifier = Modifier
                .weight(0.5f),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}