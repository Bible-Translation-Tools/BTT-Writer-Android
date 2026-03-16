package com.door43.translationstudio.ui.translate.chunk

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TextStyleType
import com.door43.translationstudio.core.TranslationType
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.getComposeTextStyle
import com.door43.translationstudio.ui.translate.ChunkItem
import com.door43.translationstudio.ui.translate.components.RichEditText


@Composable
fun ChunkTargetCard(
    item: ChunkItem,
    targetTranslation: TargetTranslation,
    typography: Typography,
    onTextChange: (String) -> Unit,
    onCompleteItemClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val titleStyle = typography.getComposeTextStyle(
        translationType = TranslationType.TARGET,
        style = TextStyleType.SUB,
        languageCode = targetTranslation.targetLanguage.slug,
        direction = targetTranslation.targetLanguage.direction
    )

    val bodyStyle = typography.getComposeTextStyle(
        translationType = TranslationType.TARGET,
        style = TextStyleType.NORMAL,
        languageCode = targetTranslation.targetLanguage.slug,
        direction = targetTranslation.targetLanguage.direction
    )

    var waitingForFocus by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxSize(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = item.targetTitle,
                style = titleStyle,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(bottom = 16.dp, end = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                RichEditText(
                    rawText = item.targetText,
                    displayText = item.renderedTargetText.text,
                    onRawTextChange = {
                        onTextChange(it)
                    },
                    textStyle = bodyStyle,
                    shouldFocus = waitingForFocus && !item.isComplete,
                    onFocusConsumed = { waitingForFocus = false },
                    modifier = Modifier.fillMaxSize()
                )

                if (item.isComplete) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .clickable {
                                if (!item.sourceOnTop) {
                                    waitingForFocus = true
                                    onCompleteItemClick()
                                }
                            }
                    )
                }
            }
        }
    }
}
