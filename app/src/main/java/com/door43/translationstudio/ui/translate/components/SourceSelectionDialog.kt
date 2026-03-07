package com.door43.translationstudio.ui.translate.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.viewmodels.RCItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSelectionDialog(
    sources: List<RCItem>,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    onUpdate: () -> Unit,
    onToggleSelection: (RCItem) -> Unit,
    onTriggerDownload: (RCItem) -> Unit,
    onTriggerDelete: (RCItem) -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val selectedString = stringResource(R.string.selected)
    val availableString = stringResource(R.string.available)
    val availableOnlineString = stringResource(R.string.available_online)

    val uiState by remember(sources, searchQuery) {
        derivedStateOf {
            prepareSourceState(
                sources = sources,
                searchText = searchQuery,
                selectedString = selectedString,
                availableString = availableString,
                availableOnlineString = availableOnlineString
            )
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text(stringResource(R.string.choose_source_translations)) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(50), // Makes it a pill shape like M3 SearchBar
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )

                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    itemsIndexed(uiState.filteredList) { index, item ->
                        if (uiState.headerIndices.contains(index)) {
                            val isInternetRequiredHeader = item.title.toString().contains("Selected", true) ||
                                    item.title.toString().contains("Online", true)

                            SourceHeaderRow(
                                title = item.title.toString(),
                                showStatusIcons = isInternetRequiredHeader
                            )
                        } else {
                            SourceItemRow(
                                item = item,
                                onTriggerSelected = {
                                    onToggleSelection(item)
                                },
                                onTriggerDownload = {
                                    onTriggerDownload(item)
                                },
                                onTriggerDelete = {
                                    if (item.downloaded) {
                                        onTriggerDelete(item)
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

private fun prepareSourceState(
    sources: List<RCItem>,
    searchText: String,
    selectedString: String,
    availableString: String,
    availableOnlineString: String
): SourceSelectionState {
    val filtered = if (searchText.isBlank()) {
        sources
    } else {
        sources.filter { item ->
            val langCode = item.sourceTranslation?.language?.slug ?: ""
            val langName = item.sourceTranslation?.language?.name ?: ""
            val resourceParts = item.sourceTranslation?.resource?.name
                ?.split("-")?.map { it.trim() } ?: emptyList()

            val matchesCode = langCode.startsWith(searchText, ignoreCase = true)
            val matchesName = langName.startsWith(searchText, ignoreCase = true)
            val matchesResource = resourceParts.any { it.startsWith(searchText, ignoreCase = true) }

            matchesCode || matchesName || matchesResource
        }
    }

    val sortedItems = filtered.sortedBy { it.sourceTranslation?.language?.slug ?: "" }

    val selected = sortedItems.filter { it.selected && it.downloaded }
    val available = sortedItems.filter { !it.selected && it.downloaded }
    val downloadable = sortedItems.filter { !it.downloaded }

    val flatList = mutableListOf<RCItem>()
    val headers = mutableSetOf<Int>()

    // Helper to add sections
    fun addSection(title: String, items: List<RCItem>) {
        if (items.isEmpty()) return
        headers.add(flatList.size)
        flatList.add(RCItem(title, null, selected = false, downloaded = false))
        flatList.addAll(items)
    }

    addSection(selectedString, selected)
    addSection(availableString, available)
    addSection(availableOnlineString, downloadable)

    return SourceSelectionState(flatList, headers)
}

private data class SourceSelectionState(
    val filteredList: List<RCItem> = emptyList(),
    val headerIndices: Set<Int> = emptySet()
)