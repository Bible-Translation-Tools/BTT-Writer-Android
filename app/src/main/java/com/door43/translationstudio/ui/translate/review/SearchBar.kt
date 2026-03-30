package com.door43.translationstudio.ui.translate.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.door43.translationstudio.R

@Composable
fun SearchBar(
    searchState: SearchState,
    onQueryChange: (String) -> Unit,
    onSubjectChange: (SearchSubject) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var showSubjectMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Surface(
        tonalElevation = 3.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            TextButton(onClick = { showSubjectMenu = true }) {
                Text(
                    text = if (searchState.subject == SearchSubject.SOURCE) {
                        stringResource(R.string.search_source)
                    } else {
                        stringResource(R.string.search_translation)
                    },
                    style = MaterialTheme.typography.labelMedium
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                DropdownMenu(
                    expanded = showSubjectMenu,
                    onDismissRequest = { showSubjectMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.search_source)) },
                        onClick = {
                            onSubjectChange(SearchSubject.SOURCE)
                            showSubjectMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.search_translation)) },
                        onClick = {
                            onSubjectChange(SearchSubject.TARGET)
                            showSubjectMenu = false
                        }
                    )
                }
            }

            OutlinedTextField(
                value = searchState.query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus()
                    onNext()
                }),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .focusRequester(focusRequester)
            )

            // Match count
            Text(
                text = if (searchState.matchCount > 0) {
                    "${searchState.currentMatchIndex + 1}/${searchState.matchCount}"
                } else {
                    "0"
                },
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.widthIn(min = 32.dp)
            )

            // Navigation
            IconButton(
                onClick = {
                    focusManager.clearFocus()
                    onPrev()
                },
                enabled = searchState.matchCount > 0
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous")
            }
            IconButton(
                onClick = {
                    focusManager.clearFocus()
                    onNext()
                },
                enabled = searchState.matchCount > 0
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next")
            }

            // Close
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close search")
            }
        }
    }
}
