package com.door43.translationstudio

import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.ImageSpan
import android.widget.TextView
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import com.door43.translationstudio.core.TextStyleType
import com.door43.translationstudio.core.TranslationType
import com.door43.translationstudio.core.Typography

@Composable
fun Typography.getComposeTextStyle(
    translationType: TranslationType,
    style: TextStyleType = TextStyleType.NORMAL,
    languageCode: String? = null,
    direction: String? = null,
    isCenterAligned: Boolean = false
): TextStyle {
    val config = this.getFormatConfig(translationType, style, languageCode, direction)
    val context = LocalContext.current

    val fontFamily = remember(config.fontAssetPath) {
        try {
            FontFamily(Font(path = config.fontAssetPath, assetManager = context.assets))
        } catch (e: Exception) {
            e.printStackTrace()
            FontFamily.Default
        }
    }

    val safeSizeSp = if (config.fontSizeSp > 0f) config.fontSizeSp else 18f

    return TextStyle(
        fontFamily = fontFamily,
        fontSize = safeSizeSp.sp,
        fontWeight = if (config.isBold) FontWeight.Bold else FontWeight.Normal,
        textDirection = if (config.isRtl) TextDirection.Rtl else TextDirection.Ltr,
        textAlign = if (isCenterAligned) TextAlign.Center else TextAlign.Start
    )
}

@Deprecated("use ComposeTextAdapter instead")
fun CharSequence.toComposeAnnotatedString(
    dummyView: TextView,
    clickableColor: Color
): AnnotatedString {
    val originalText = this as? Spanned ?: return AnnotatedString(this.toString())

    return buildAnnotatedString {
        var i = 0
        while (i < originalText.length) {
            val next = originalText.nextSpanTransition(i, originalText.length, Any::class.java)

            val imageSpans = originalText.getSpans(i, next, ImageSpan::class.java)
            val clickSpans = originalText.getSpans(i, next, ClickableSpan::class.java)
            val bgSpans = originalText.getSpans(i, next, BackgroundColorSpan::class.java)

            if (clickSpans.isNotEmpty()) {
                val link = LinkAnnotation.Clickable(
                    tag = "LINK_$i",
                    styles = TextLinkStyles(style = SpanStyle(color = clickableColor)),
                    linkInteractionListener = {
                        clickSpans[0].onClick(dummyView)
                    }
                )
                pushLink(link)
            }

            if (bgSpans.isNotEmpty()) {
                pushStyle(SpanStyle(background = Color(bgSpans[0].backgroundColor)))
            }

            if (imageSpans.isNotEmpty()) {
                appendInlineContent(id = "footnote_icon", alternateText = "[*]")
            } else {
                append(originalText.subSequence(i, next).toString())
            }

            if (bgSpans.isNotEmpty()) pop()
            if (clickSpans.isNotEmpty()) pop()

            i = next
        }
    }
}