package com.door43.translationstudio.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Upload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.door43.translationstudio.R
import com.door43.translationstudio.core.TranslationViewMode

data class SidebarAction(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
fun rememberTranslateMenuItems(
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
): List<SidebarAction> {
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
                SidebarAction(translations, Icons.AutoMirrored.Filled.LibraryBooks, onHomeClick)
            )
            if (draftAvailable) {
                add(
                    SidebarAction(viewDrafts, Icons.Default.Translate, onNavigateToDraft)
                )
            }
            add(
                SidebarAction(review, Icons.Default.DoneAll, onProjectPreview)
            )
            add(
                SidebarAction(uploadExport, Icons.Default.Upload, onUploadExport)
            )
            add(
                SidebarAction(print, Icons.Default.Print, onPrint)
            )
            add(
                SidebarAction(feedback, Icons.Default.Feedback, onFeedback)
            )
            if (viewMode == TranslationViewMode.REVIEW) {
                add(
                    SidebarAction(search, Icons.Default.Search, onSearchRequested)
                )
                add(
                    SidebarAction(markDone, Icons.Default.Check, onChunksDone)
                )
            }
            add(
                SidebarAction(settings, Icons.Default.Settings, onSettings)
            )
        }
    }
}

@Composable
fun rememberHomeMenuItems(
    onUpdateClick: () -> Unit,
    onImport: () -> Unit,
    onFeedback: () -> Unit,
    onShareApp: () -> Unit,
    onLogout: () -> Unit,
    onSettings: () -> Unit
): List<SidebarAction> {
    val update = stringResource(R.string.menu_update_library)
    val import = stringResource(R.string.label_import_options)
    val feedback = stringResource(R.string.feedback)
    val shareApp = stringResource(R.string.share_apk)
    val logout = stringResource(R.string.log_out)
    val settings = stringResource(R.string.action_settings)

    return remember {
        buildList {
            add(
                SidebarAction(update, Icons.Default.LocalLibrary, onUpdateClick)
            )
            add(
                SidebarAction(import, Icons.Default.Download, onImport)
            )
            add(
                SidebarAction(feedback, Icons.Default.Feedback, onFeedback)
            )
            add(
                SidebarAction(shareApp, Icons.Default.Android, onShareApp)
            )
            add(
                SidebarAction(logout, Icons.AutoMirrored.Filled.ExitToApp, onLogout)
            )
            add(
                SidebarAction(settings, Icons.Default.Settings, onSettings)
            )
        }
    }
}
