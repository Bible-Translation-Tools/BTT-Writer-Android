package com.door43.translationstudio.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Typography
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun TranslationListScreen(
    viewModel: HomeViewModel = koinViewModel(),
    onProjectSelected: (TranslationItem) -> Unit,
    onChangeLanguage: (TranslationItem) -> Unit,
    onMergeConflict: (String) -> Unit,
    onProjectPublish: (String) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    val typography: Typography = koinInject()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .widthIn(max = 900.dp)
            .fillMaxWidth(0.9f)
            .wrapContentHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SortDropdown(
                label = stringResource(R.string.sort_column),
                options = viewModel.projectSortOptions,
                selectedOption = state.projectSort,
                onOptionSelected = {
                    viewModel.onAction(HomeAction.ProjectSortChanged(it))
                },
                labelTransformer = { it.localize() },
                modifier = Modifier.weight(1f)
            )

            SortDropdown(
                label = stringResource(R.string.sort_projects),
                options = viewModel.bookSortOptions,
                selectedOption = state.bookSort,
                onOptionSelected = {
                    viewModel.onAction(HomeAction.BookSortChanged(it))
                },
                labelTransformer = { it.localize() },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .padding(horizontal = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.Project),
                modifier = Modifier.weight(1f)
            )

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Text(stringResource(R.string.language))

                Spacer(modifier = Modifier.weight(1f))

                Text(stringResource(R.string.progress))

            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(state.translations, key = { it.translation.id }) { project ->
                ProjectCard(
                    item = project,
                    typography = typography,
                    onItemClick = { onProjectSelected(project) },
                    onInfoClick = {
                        viewModel.onAction(HomeAction.ShowProjectInfo(project))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Spacer(modifier = Modifier.height(50.dp))
            }
        }
    }

    state.projectInfo?.let { project ->
        ProjectDetailsDialog(
            project = project,
            onDismiss = {
                viewModel.onAction(HomeAction.HideProjectInfo)
            },
            onChangeLanguage = {
                viewModel.onAction(HomeAction.HideProjectInfo)
                onChangeLanguage(project)
            },
            onDelete = {
                viewModel.onAction(HomeAction.DeleteProject(project))
            },
            onPublish = {
                viewModel.onAction(HomeAction.HideProjectInfo)
                onProjectPublish(project.translation.id)
            },
            onLogin = onLogin,
            onLogout = onLogout,
            onMergeConflict = {
                viewModel.onAction(HomeAction.HideProjectInfo)
                onMergeConflict(project.translation.id)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T : Enum<T>> SortDropdown(
    label: String,
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    labelTransformer: @Composable (T) -> String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            Column(
                modifier = Modifier.menuAnchor(
                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = true
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                        .menuAnchor(
                            type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                            enabled = true
                        )
                        .clickable { expanded = true }
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                ) {
                    Text(
                        text = labelTransformer(selectedOption)
                    )
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }

                HorizontalDivider()
            }

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(text = labelTransformer(option))
                        },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectSort.localize(): String {
    return when (this) {
        ProjectSort.ProjectThenLanguage -> stringResource(R.string.sort_project_then_language)
        ProjectSort.LanguageThenProject -> stringResource(R.string.sort_language_then_project)
        ProjectSort.ProgressThenProject -> stringResource(R.string.sort_progress_then_project)
    }
}

@Composable
private fun BookSort.localize(): String {
    return when (this) {
        BookSort.BibleOrder -> stringResource(R.string.sort_bible_order)
        BookSort.Alphabetical -> stringResource(R.string.sort_alphabetical_order)
    }
}
