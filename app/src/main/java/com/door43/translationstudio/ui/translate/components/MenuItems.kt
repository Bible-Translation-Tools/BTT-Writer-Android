package com.door43.translationstudio.ui.translate.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Upload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.door43.translationstudio.R
import com.door43.translationstudio.core.TranslationViewMode

@Composable
fun rememberMenuItems(
    viewMode: TranslationViewMode,
    draftAvailable: Boolean,
    onHomeClick: () -> Unit,
    onNavigateToDraft: () -> Unit,
    onProjectPreview: () -> Unit,
    onUploadExport: () -> Unit,
    onPrint: () -> Unit,
    onFeedback: () -> Unit,
    onChunksDone: () -> Unit,
    onSettings: () -> Unit,
    onSearchRequested: () -> Unit
): List<TranslateSideBarAction> {
    val translations = stringResource(R.string.action_translations)
    val viewDrafts = stringResource(R.string.view_available_drafts)
    val review = stringResource(R.string.title_review)
    val uploadExport = stringResource(R.string.menu_upload_export)
    val print = stringResource(R.string.print)
    val feedback = stringResource(R.string.feedback)
    val search = stringResource(R.string.action_search)
    val markDone = stringResource(R.string.mark_chunks_done)
    val settings = stringResource(R.string.action_settings)

    return remember(viewMode, draftAvailable) {
        buildList {
            add(
                TranslateSideBarAction(
                    translations,
                    Icons.AutoMirrored.Filled.LibraryBooks,
                    onHomeClick
                )
            )
            if (draftAvailable) {
                add(
                    TranslateSideBarAction(
                        viewDrafts, Icons.Default.Translate,
                        onNavigateToDraft
                    )
                )
            }
            add(
                TranslateSideBarAction(
                    review,
                    Icons.Default.DoneAll,
                    onProjectPreview
                )
            )
            add(
                TranslateSideBarAction(
                    uploadExport,
                    Icons.Default.Upload,
                    onUploadExport
                )
            )
            add(
                TranslateSideBarAction(
                    print,
                    Icons.Default.Print,
                    onPrint
                )
            )
            add(
                TranslateSideBarAction(
                    feedback,
                    Icons.Default.Feedback,
                    onFeedback
                )
            )
            if (viewMode == TranslationViewMode.REVIEW) {
                add(
                    TranslateSideBarAction(
                        search,
                        Icons.Default.Search,
                        onSearchRequested
                    )
                )
                add(
                    TranslateSideBarAction(
                        markDone,
                        Icons.Default.Check,
                        onChunksDone
                    )
                )
            }
            add(
                TranslateSideBarAction(
                    settings,
                    Icons.Default.Settings,
                    onSettings
                )
            )
        }
    }
}
