package com.door43.translationstudio.ui.translate.chunk

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.door43.translationstudio.R
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TextStyleType
import com.door43.translationstudio.core.TranslationType
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.getComposeTextStyle

private const val OBJ_REPLACEMENT = '\uFFFC'

class IconVisualTransformation(
    private val iconFont: FontFamily,
    private val iconColor: Color,
    private val fontSize: TextUnit
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder()

        for (char in text.text) {
            if (char == OBJ_REPLACEMENT) {
                builder.pushStyle(
                    SpanStyle(
                        fontFamily = iconFont,
                        fontSize = fontSize,
                        color = iconColor
                    )
                )
                builder.append(char)
                builder.pop()
            } else {
                builder.append(char)
            }
        }

        // Identity mapping — no offset translation needed
        return TransformedText(
            builder.toAnnotatedString(),
            OffsetMapping.Identity
        )
    }
}

fun insertLink(current: TextFieldValue): TextFieldValue {
    val start = current.selection.start
    val newText = current.text.substring(0, start) +
            OBJ_REPLACEMENT +
            current.text.substring(current.selection.end)
    return TextFieldValue(
        text = newText,
        selection = TextRange(start + 1)
    )
}

@Composable
fun ChunkTargetCard(
    title: String,
    text: AnnotatedString,
    targetTranslation: TargetTranslation,
    typography: Typography,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val titleStyle = typography.getComposeTextStyle(
        translationType = TranslationType.SOURCE,
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

    val iconFontFamily = FontFamily(Font(R.font.icons))
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text))
    }

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
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = titleStyle,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(bottom = 16.dp, end = 8.dp)
                    .clickable {
                        textFieldValue = insertLink(textFieldValue)
                    }
            )

            Spacer(modifier = Modifier.height(16.dp))

            BasicTextField(
                value = textFieldValue,
                onValueChange = {
                    textFieldValue = it
                    onTextChange(it.text)
                },
                visualTransformation = IconVisualTransformation(
                    iconFont = iconFontFamily,
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = bodyStyle.fontSize
                ),
                textStyle = bodyStyle
            )

//            Text(
//                text = text,
//                inlineContent = inlineContentMap,
//                style = bodyStyle
//            )
        }
    }
}
