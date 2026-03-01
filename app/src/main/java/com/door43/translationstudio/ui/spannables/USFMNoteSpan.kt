package com.door43.translationstudio.ui.spannables

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.StyleSpan
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.door43.translationstudio.R
import java.util.regex.Pattern

class USFMNoteSpan(
    override val style: String,
    override val caller: String,
    chars: List<USFMChar>
) : NoteSpan() {

    override val notes: CharSequence
    override val passage: CharSequence

    var isHighlight: Boolean = false
    private var spannable: SpannableStringBuilder? = null

    companion object {
        private const val DEFAULT_CALLER = "+"
        const val PATTERN = "\\\\f\\s(\\S)\\s([\\s\\S]+?)\\\\f\\*"
        const val CHAR_PATTERN = "\\\\f([^*\\s]+)\\s([^\\\\]+)(?:\\\\f\\1\\*)?"

        /**
         * Generates the passage note tag with additional attributes
         */
        fun generateTag(style: String, caller: String, title: CharSequence, chars: List<USFMChar>): CharSequence {
            val tag = StringBuilder("\\f $caller ")
            for (c in chars) {
                when (c.style) {
                    USFMChar.STYLE_FOOTNOTE_VERSE -> tag.append("\\fv ").append(c.value).append("\\fv*")
                    else -> tag.append("\\").append(c.style).append(" ").append(c.value).append(" ")
                }
            }
            tag.append("\\f*")
            return tag.toString()
        }

        /**
         * Generates a footnote span
         * @param note the note
         */
        fun generateFootnote(note: CharSequence): USFMNoteSpan {
            val chars = listOf(USFMChar(USFMChar.STYLE_FOOTNOTE_TEXT, note))
            return USFMNoteSpan("f", DEFAULT_CALLER, chars)
        }

        /**
         * Generates a new note span from the enclosed text and returns it.
         * Don't forget to set the click listener!
         * we are using usfm for footnotes and our own variant for user notes
         * http://ubs-icap.org/chm/usfm/2.4/index.html
         */
        fun parseNote(caller: CharSequence, noteText: CharSequence): USFMNoteSpan {
            val chars = mutableListOf<USFMChar>()
            val pattern = Pattern.compile(CHAR_PATTERN)
            val matcher = pattern.matcher(noteText)
            var lastIndex = 0
            var note: CharSequence = ""

            while (matcher.find()) {
                val start = matcher.start()
                if (start > lastIndex) {
                    note = TextUtils.concat(note, noteText.subSequence(lastIndex, start))
                }
                chars.add(USFMChar("f" + matcher.group(1), matcher.group(2)))
                lastIndex = matcher.end()
            }

            if (lastIndex < noteText.length) { // if extra text, add it
                note = TextUtils.concat(note, noteText.subSequence(lastIndex, noteText.length))
                chars.add(USFMChar(USFMChar.STYLE_PASSAGE_TEXT, note))
            }
            return USFMNoteSpan("f", caller.toString(), chars)
        }
    }

    init {
        var spanTitle: CharSequence = ""
        var note: CharSequence = ""
        var quotation: CharSequence = ""
        var altQuotation: CharSequence = ""
        var passageText: CharSequence = ""

        for (c in chars) {
            when (c.style) {
                USFMChar.STYLE_PASSAGE_TEXT -> passageText = c.value
                USFMChar.STYLE_FOOTNOTE_QUOTATION -> quotation = c.value
                USFMChar.STYLE_FOOTNOTE_ALT_QUOTATION -> altQuotation = c.value
                else -> {
                    // TODO: implement better. We may need to format the values
                    note = TextUtils.concat(note, c.value)
                }
            }
        }

        // set the span title
        if (!TextUtils.isEmpty(passageText)) {
            spanTitle = passageText
        } else if (!TextUtils.isEmpty(quotation)) {
            spanTitle = quotation
        }

        init(spanTitle, generateTag(style, caller, spanTitle, chars))

        passage = spanTitle
        notes = TextUtils.concat(note, " ", altQuotation)
    }

    override fun render(): SpannableStringBuilder {
        if (spannable == null) {
            val s = super.render()
            // apply custom styles
            context?.let { ctx ->
                if (humanReadable.toString().isEmpty()) {
                    val icon = if (isHighlight) R.drawable.ic_description_black_24dp_highlight else R.drawable.ic_description_neutral_24dp
                    val image = ResourcesCompat.getDrawable(ctx.resources, icon, ctx.theme)
                    if (image != null) {
                        image.setBounds(0, 0, image.minimumWidth, image.minimumHeight)
                        s.setSpan(ImageSpan(image), 0, s.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                } else {
                    s.setSpan(
                        BackgroundColorSpan(ContextCompat.getColor(ctx, R.color.footnote_yellow)),
                        0,
                        s.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    s.setSpan(StyleSpan(Typeface.ITALIC), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    s.setSpan(
                        ForegroundColorSpan(ContextCompat.getColor(ctx, R.color.dark_gray)),
                        0,
                        s.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            spannable = s
        }
        return spannable!!
    }

    /**
     * Generates a custom Doku Wiki footnote tag.
     * TODO: I think this will just be used for footnotes, however if footnotes are to be treated normally we won't have the span text.
     */
    fun generateDokuWikiTag(): String {
        return "((ref:\"$passage\",note:\"$notes\"))"
    }
}