package com.door43.translationstudio.ui.translate.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.door43.translationstudio.ui.translate.ListItem
import com.door43.translationstudio.ui.translate.ReadListItem
import com.door43.translationstudio.ui.translate.components.read.ReadCard

@Composable
fun ReadModeScreen(
    items: List<ListItem>
) {

    val chapters = remember { mutableStateListOf<ReadListItem>() }

    LaunchedEffect(items) {
        items.forEach { item ->
            if (!chapters.any { it.chapterSlug == item.chapterSlug }) {
                chapters.add(item.toType(::ReadListItem))
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth()
        ) {
            items(chapters) { chapter ->
                ReadCard(chapter)
            }
        }
    }
}