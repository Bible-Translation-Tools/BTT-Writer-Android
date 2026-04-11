package com.door43.translationstudio.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.components.SearchBar
import com.door43.translationstudio.ui.dialogs.OverlayDialog
import com.door43.translationstudio.ui.dialogs.ProgressDialog
import org.koin.androidx.compose.koinViewModel

@Composable
fun DownloadSourcesDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: DownloadSourcesViewModel = koinViewModel()

    LaunchedEffect(Unit) {
        viewModel.onAction(DownloadAction.Initialize)
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()

    OverlayDialog(
        onDismiss = {
            viewModel.onAction(DownloadAction.ClearState)
            onDismiss()
        },
        maxWidth = 1000.dp,
        maxHeight = 1000.dp
    ) {
        Column(
            modifier = modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.clickable {
                        viewModel.onAction(DownloadAction.FilterModeChanged(FilterMode.ByLanguage))
                    }
                ) {
                    RadioButton(
                        selected = state.filterMode == FilterMode.ByLanguage,
                        onClick = null
                    )
                    Text(stringResource(R.string.by_language_label))
                }

                Spacer(modifier = Modifier.width(24.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.clickable {
                        viewModel.onAction(DownloadAction.FilterModeChanged(FilterMode.ByBook))
                    }
                ) {
                    RadioButton(
                        selected = state.filterMode == FilterMode.ByBook,
                        onClick = null
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
                    if (state.navigationStack.size == 1) {
                        viewModel.onAction(DownloadAction.ClearState)
                        onDismiss()
                    } else {
                        viewModel.onAction(DownloadAction.NavigateBack)
                    }
                },
                onBreadcrumbClicked = {
                    viewModel.onAction(DownloadAction.NavigateStep(it))
                }
            )

            val canDownload = state.navigationStack.isNotEmpty()
                    && state.navigationStack.last().selection.isDownloadable

            if (canDownload) {
                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            viewModel.onAction(DownloadAction.SelectAll(true))
                        }
                    ) {
                        Checkbox(
                            checked = state.selectAllChecked,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    viewModel.onAction(DownloadAction.SelectAll(true))
                                }
                            }
                        )
                        Text(stringResource(R.string.select_all_label))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            viewModel.onAction(DownloadAction.SelectAll(false))
                        }
                    ) {
                        Checkbox(
                            checked = !state.selectAllChecked && state.listItems.none {
                                (it as? DownloadListItem.SourceSelection)?.isSelected == true
                            },
                            onCheckedChange = { checked ->
                                if (checked) {
                                    viewModel.onAction(DownloadAction.SelectAll(false))
                                }
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
                        ),
                        enabled = state.selectedSources.isNotEmpty()
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
            .height(70.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClicked) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back"
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            breadcrumbs.forEachIndexed { index, crumb ->
                val isClickable = index < breadcrumbs.size - 1
                if (isClickable) {
                    Text(
                        text = crumb,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            onBreadcrumbClicked(index)
                        }
                    )
                    Text(text = " > ")
                } else {
                    Text(text = crumb)
                }
            }
        }

        if (enableSearch) {
            SearchBar(
                query = query,
                onQueryChanged = onQueryChanged,
                placeholder = stringResource(R.string.search_for_language),
                modifier = Modifier.weight(1f)
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
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
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