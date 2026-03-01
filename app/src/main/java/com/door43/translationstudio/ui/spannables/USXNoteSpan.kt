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
import android.util.Xml
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.door43.translationstudio.R
import org.unfoldingword.tools.logger.Logger
import org.w3c.dom.Element
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerException
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Class to create NoteSpans from USX format text
 */
class USXNoteSpan(
    override val style: String,
    override val caller: String,
    val chars: List<USXChar>
) : NoteSpan() {

    override val notes: CharSequence
    override val passage: CharSequence

    var isHighlight: Boolean = false
    private var spannable: SpannableStringBuilder? = null

    companion object {
        private const val DEFAULT_CALLER = "+"
        const val PATTERN = "<note\\s+(((?!>).)*)\\s*>\\s*(((?!(<\\/note>)).)*)\\s*<\\/note>"

        /**
         * Generates the passage note tag with additional attributes
         */
        fun generateTag(style: String, caller: String, title: CharSequence, chars: List<USXChar>): CharSequence {
            val dbf = DocumentBuilderFactory.newInstance()
            val db = try {
                dbf.newDocumentBuilder()
            } catch (e: ParserConfigurationException) {
                return title
            }
            val document = db.newDocument()

            // build root
            val rootElement: Element = document.createElement("note")
            rootElement.setAttribute("style", style)
            rootElement.setAttribute("caller", caller)
            document.appendChild(rootElement)

            // add chars
            for (c in chars) {
                val element = document.createElement("char")
                element.setAttribute("style", c.style)
                element.textContent = c.value.toString().replace("\n", "\\n")
                rootElement.appendChild(element)
            }

            // generate
            val domSource = DOMSource(document.documentElement)
            val output = ByteArrayOutputStream()
            val result = StreamResult(output)

            val factory = TransformerFactory.newInstance()
            try {
                val transformer = factory.newTransformer()
                val outFormat = Properties()
                outFormat.setProperty(OutputKeys.INDENT, "no")
                outFormat.setProperty(OutputKeys.METHOD, "xml")
                outFormat.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
                outFormat.setProperty(OutputKeys.VERSION, "1.0")
                outFormat.setProperty(OutputKeys.ENCODING, "UTF-8")
                transformer.outputProperties = outFormat
                transformer.transform(domSource, result)
            } catch (e: Exception) {
                Logger.e(USXNoteSpan::class.java.name, "failed to transform the the span text", e)
                if (e !is TransformerException) {
                    return title
                }
            }

            return output.toString()
        }

        /**
         * Generates a footnote span
         * @param note the note
         */
        fun generateFootnote(note: CharSequence): USXNoteSpan {
            val chars = listOf(USXChar(USXChar.STYLE_FOOTNOTE_TEXT, note))
            return USXNoteSpan("f", DEFAULT_CALLER, chars)
        }

        /**
         * Generates a new note span from the supplied XML and returns it.
         * Don't forget to set the click listener!
         * we are using usx for footnotes and our own variant for user notes
         * http://dbl.ubs-icap.org:8090/display/DBLDOCS/USX#USX-note(Footnote)
         */
        fun parseNote(usx: CharSequence): USXNoteSpan? {
            val parser = Xml.newPullParser()
            return try {
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(InputStreamReader(getStream(usx)))
                parser.nextTag()
                readXML(parser)
            } catch (e: XmlPullParserException) {
                Logger.e(USXNoteSpan::class.java.name, "Failed to parse note", e)
                null
            } catch (e: IOException) {
                null
            }
        }

        /**
         * Converts charsequence to an input stream
         */
        private fun getStream(charSequence: CharSequence): InputStream {
            return object : InputStream() {
                var index = 0
                val length = charSequence.length
                @Throws(IOException::class)
                override fun read(): Int {
                    return if (index >= length) -1 else charSequence[index++].code
                }
            }
        }

        /**
         * Reads some xml to produce a new note span
         */
        @Throws(XmlPullParserException::class, IOException::class)
        private fun readXML(parser: XmlPullParser): USXNoteSpan {
            parser.require(XmlPullParser.START_TAG, null, "note")

            // load attributes
            val style = parser.getAttributeValue("", "style")
            var caller = parser.getAttributeValue("", "caller")
            if (caller == null) {
                caller = DEFAULT_CALLER
            }

            var eventType = parser.eventType
            parser.nextTag()

            // load char's
            val chars = ArrayList<USXChar>()
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    parser.require(XmlPullParser.START_TAG, null, "char")
                    val charStyle = parser.getAttributeValue("", "style")
                    val charText = parser.nextText().trim()
                    if (charText.isNotEmpty()) {
                        chars.add(USXChar(charStyle, charText))
                    }
                } else if (eventType == XmlPullParser.TEXT) {
                    val text = parser.text.trim()
                    if (text.isNotEmpty()) {
                        chars.add(USXChar("ft", text))
                    }
                }
                eventType = parser.next()
            }

            return USXNoteSpan(style, caller.trim(), chars)
        }
    }

    init {
        var spanTitle: CharSequence = ""
        var quotation: CharSequence = ""
        var passageText: CharSequence = ""
        var noteBuilder: CharSequence = ""

        for (c in chars) {
            when (c.style) {
                USXChar.STYLE_PASSAGE_TEXT -> {
                    passageText = TextUtils.concat(passageText, c.value)
                }
                USXChar.STYLE_FOOTNOTE_QUOTATION -> {
                    quotation = TextUtils.concat(quotation, c.value)
                }
                USXChar.STYLE_FOOTNOTE_ALT_QUOTATION -> {
                    if (noteBuilder.toString() != "") noteBuilder = TextUtils.concat(noteBuilder, " ")
                    noteBuilder = TextUtils.concat(noteBuilder, "\"", c.value, "\"")
                }
                else -> {
                    // TRICKY: this could add extra white space between the quote and a , but
                    // without fixing the usx converter on the server this is the best we can do.
                    if (noteBuilder.toString() != "") noteBuilder = TextUtils.concat(noteBuilder, " ")
                    noteBuilder = TextUtils.concat(noteBuilder, c.value)
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
        notes = noteBuilder
    }

    override fun render(): SpannableStringBuilder {
        if (spannable == null) {
            val s = super.render()
            // apply custom styles
            context?.let { ctx ->
                if (humanReadable.toString().isEmpty()) {
                    val icon = if (isHighlight) R.drawable.ic_description_black_24dp_highlight else R.drawable.ic_description_black_24dp
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