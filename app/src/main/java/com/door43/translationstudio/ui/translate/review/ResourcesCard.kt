package com.door43.translationstudio.ui.translate.review

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.translate.TranslationHelp
import org.unfoldingword.resourcecontainer.Language
import org.unfoldingword.resourcecontainer.Link

data class HelpTab(val tag: String, val title: String)

sealed class HelpItem {
    abstract val title: String

    data class Note(val data: TranslationHelp) : HelpItem() {
        override val title = data.title
    }
    data class Word(val data: Link, val rcSlug: String) : HelpItem() {
        override val title: String = data.title
    }
    data class Question(val data: TranslationHelp) : HelpItem() {
        override val title = data.title
    }
}


@Composable
fun ResourcesCard(
    helps: Map<String, Any>,
    sourceLanguage: Language,
    resourcesOpen: Boolean,
    onHelpClick: (HelpItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val cornerSize by animateDpAsState(
        targetValue = if (resourcesOpen) 16.dp else 0.dp,
        label = "cornerSize"
    )

    val notesStr = stringResource(R.string.label_translation_notes)
    val wordsStr = stringResource(R.string.translation_words)
    val questionsStr = stringResource(R.string.questions)

    var notes by remember { mutableStateOf(emptyList<TranslationHelp>()) }
    var words by remember { mutableStateOf(emptyList<Link>()) }
    var questions by remember { mutableStateOf(emptyList<TranslationHelp>()) }

    var tabs by remember { mutableStateOf(emptyList<HelpTab>()) }
    var selectedTag by remember { mutableStateOf("") }

    val activeList = remember(selectedTag, notes, words, questions) {
        when (selectedTag) {
            "notes" -> notes.map { HelpItem.Note(it) }
            "words" -> words.map { HelpItem.Word(
                data = it,
                rcSlug = "${sourceLanguage.slug}_${it.project}_${it.resource}")
            }
            "questions" -> questions.map { HelpItem.Question(it) }
            else -> emptyList()
        }
    }

    LaunchedEffect(helps) {
        notes = helps["notes"] as? List<TranslationHelp> ?: emptyList()
        words = helps["words"] as? List<Link> ?: emptyList()
        questions = helps["questions"] as? List<TranslationHelp> ?: emptyList()

        tabs = buildList {
            if (notes.isNotEmpty()) add(HelpTab(tag = "notes", title = notesStr))
            if (words.isNotEmpty()) add(HelpTab(tag = "words", title = wordsStr))
            if (questions.isNotEmpty()) add(HelpTab(tag = "questions", title = questionsStr))
        }

        selectedTag = tabs.firstOrNull()?.tag ?: ""
    }

    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(
            topStart = 16.dp,
            bottomStart = 16.dp,
            topEnd = cornerSize,
            bottomEnd = cornerSize
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (resourcesOpen) {
                HelpTabRow(
                    helpTabs = tabs,
                    selectedTag = selectedTag,
                    onHelpTabClick = { selectedTag = it }
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    activeList.forEach {
                        TextButton(onClick = {
                            onHelpClick(it)
                        }) {
                            Text(text = it.title)
                        }
                    }
                }
            }
        }
    }
}