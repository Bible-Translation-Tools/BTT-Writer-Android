package com.door43.translationstudio.rendering.spannables

import java.util.regex.Pattern

class USFMNoteSpan(
    override val style: String,
    override val caller: String,
    chars: List<USFMChar>
) : NoteSpan() {

    override val notes: CharSequence
    override val passage: CharSequence

    var isHighlight: Boolean = false

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
                    USFMChar.Companion.STYLE_FOOTNOTE_VERSE -> tag.append("\\fv ").append(c.value).append("\\fv*")
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
            val chars = listOf(USFMChar(USFMChar.Companion.STYLE_FOOTNOTE_TEXT, note))
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
            val noteBuilder = StringBuilder()

            while (matcher.find()) {
                val start = matcher.start()
                if (start > lastIndex) {
                    noteBuilder.append(noteText.subSequence(lastIndex, start))
                }
                chars.add(USFMChar("f" + matcher.group(1), matcher.group(2) ?: ""))
                lastIndex = matcher.end()
            }

            if (lastIndex < noteText.length) { // if extra text, add it
                noteBuilder.append(noteText.subSequence(lastIndex, noteText.length))
                chars.add(USFMChar(USFMChar.Companion.STYLE_PASSAGE_TEXT, noteBuilder.toString()))
            }
            return USFMNoteSpan("f", caller.toString(), chars)
        }
    }

    init {
        var spanTitle: CharSequence = ""
        val noteBuilder = StringBuilder()
        var quotation: CharSequence = ""
        var altQuotation: CharSequence = ""
        var passageText: CharSequence = ""

        for (c in chars) {
            when (c.style) {
                USFMChar.Companion.STYLE_PASSAGE_TEXT -> passageText = c.value
                USFMChar.Companion.STYLE_FOOTNOTE_QUOTATION -> quotation = c.value
                USFMChar.Companion.STYLE_FOOTNOTE_ALT_QUOTATION -> altQuotation = c.value
                else -> {
                    // TODO: implement better. We may need to format the values
                    noteBuilder.append(c.value)
                }
            }
        }

        // set the span title
        if (passageText.isNotEmpty()) {
            spanTitle = passageText
        } else if (quotation.isNotEmpty()) {
            spanTitle = quotation
        }

        init(spanTitle.toString(), generateTag(style, caller, spanTitle, chars).toString())

        passage = spanTitle
        notes = "$noteBuilder $altQuotation"
    }

    /**
     * Generates a custom Doku Wiki footnote tag.
     * TODO: I think this will just be used for footnotes, however if footnotes are to be treated normally we won't have the span text.
     */
    fun generateDokuWikiTag(): String {
        return "((ref:\"$passage\",note:\"$notes\"))"
    }
}
