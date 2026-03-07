package com.door43.translationstudio.ui.translate.read

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.door43.translationstudio.R
import com.door43.translationstudio.rendering.model.TextNode
import com.door43.translationstudio.ui.translate.ChunkItem
import com.door43.translationstudio.ui.translate.ReadListItem3
import com.door43.translationstudio.ui.translate.components.StackedCardFlipper

@Composable
fun ReadCard(
    chapter: ChunkItem.ReadMode,
    modifier: Modifier = Modifier
) {
    val targetText = chapter.meta.targetText

    var noteDialogText by remember { mutableStateOf<String?>(null) }
    val onNoteClick: (TextNode.NoteMarker) -> Unit = remember {
        {
            println(it)
            noteDialogText = it.notes
        }
    }

    StackedCardFlipper(
        modifier = modifier,
        containerPadding = 8.dp,
        stackOffset = 32.dp,
        onAnimationEnd = { isFrontOnTop ->
            println("Animation finished, fron is on top: $isFrontOnTop")
        },
        frontCard = {
            ReadSourceCard(
                chapter = chapter,
                onNoteClick = onNoteClick
            )
        },
        backCard = {
            ReadTargetCard(
                title = chapter.targetTitle,
                text = targetText
            )
        }
    )

    noteDialogText?.let { notes ->
        AlertDialog(
            onDismissRequest = { noteDialogText = null },
            title = { Text(stringResource(R.string.footnote_label)) },
            text = { Text(notes) },
            confirmButton = {
                TextButton(onClick = { noteDialogText = null }) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        )
    }
}
