package com.door43.translationstudio.ui.translate.components.read

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.door43.translationstudio.ui.translate.ReadListItem
import com.door43.translationstudio.ui.translate.components.StackedCardFlipper

@Composable
fun ReadCard(
    chapter: ReadListItem,
    modifier: Modifier = Modifier
) {
    val sourceText = chapter.sourceText
    val targetText = chapter.targetText

    StackedCardFlipper(
        modifier = modifier,
        containerPadding = 8.dp,
        stackOffset = 32.dp,
        onAnimationEnd = { isFrontOnTop ->
            println("Animation finished, fron is on top: $isFrontOnTop")
        },
        frontCard = {
            ReadSourceCard(sourceText)
        },
        backCard = {
            ReadTargetCard(
                title = getTargetTitle(chapter),
                text = targetText
            )
        }
    )


}

private fun getTargetTitle(item: ReadListItem): String {
    var targetCardTitle = ""

    // look for translated chapter title first
    val chapterTranslation = item.target.getChapterTranslation(item.chapterSlug)
    targetCardTitle = chapterTranslation.title.trim()

    // if no target chapter title translation, fall back to source chapter title
    if (targetCardTitle.isEmpty() && item.chapterTitle.trim().isNotEmpty()) {
        targetCardTitle = item.chapterTitle.trim()
    }

    // if no chapter titles, fall back to project title, try translated title first
    if (targetCardTitle.isEmpty()) {
        val projTrans = item.target.projectTranslation
        if (projTrans.title.trim().isNotEmpty()) {
            targetCardTitle = try {
                "${projTrans.title.trim()} ${item.chapterSlug.toInt()}"
            } catch (_: Exception) {
                "${projTrans.title.trim()} ${item.chapterSlug}"
            }
        }
    }

    // fall back to project source title
    if (targetCardTitle.isEmpty()) {
        targetCardTitle = item.source.readChunk("front", "title").trim()
        if (item.chapterSlug != "front") {
            targetCardTitle += try {
                " ${item.chapterSlug.toInt()}"
            } catch (_: Exception) {
                " ${item.chapterSlug}"
            }
        }
    }

    return "$targetCardTitle - ${item.target.targetLanguage.name}"
}
