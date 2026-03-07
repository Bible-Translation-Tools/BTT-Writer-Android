package com.door43.translationstudio.ui.translate.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.viewmodels.RCItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSelectionDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    onUpdate: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sortedData: List<RCItem>,
    sectionHeaders: Set<Int>,
    onToggleSelection: (Int) -> Unit,
    onTriggerDownload: (RCItem, Int) -> Unit,
    onTriggerDelete: (String, Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                SearchBar(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = searchQuery,
                            onQueryChange = onSearchQueryChange,
                            onSearch = { expanded = false },
                            expanded = expanded,
                            onExpandedChange = { expanded = it },
                            placeholder = { Text("Search sources...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                        )
                    },
                    content = { /* Suggestions could go here */ }
                )

                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    itemsIndexed(sortedData) { index, item ->
                        if (sectionHeaders.contains(index)) {
                            // Header Row
                            SourceHeaderRow(item.title)
                        } else {
                            // Item Row
                            SourceItemRow(
                                item = item,
                                onClick = {
                                    if (!item.downloaded || item.hasUpdates) {
                                        onTriggerDownload(item, index)
                                    } else {
                                        onToggleSelection(index)
                                    }
                                },
                                onLongClick = {
                                    if (item.downloaded) {
                                        onTriggerDelete(item.containerSlug ?: "", index)
                                    }
                                }
                            )
                        }
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onUpdate, modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.update_sources_label),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    TextButton(onClick = onDismissRequest) {
                        Text(
                            stringResource(R.string.title_cancel),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    TextButton(onClick = onConfirm) {
                        Text(
                            stringResource(R.string.confirm),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}