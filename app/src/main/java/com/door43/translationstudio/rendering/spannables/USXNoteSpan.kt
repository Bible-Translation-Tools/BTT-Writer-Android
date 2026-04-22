package com.door43.translationstudio.rendering.spannables

import org.bibletranslationtools.logger.Logger
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.xml.sax.InputSource

/**
 * Class to create NoteSpans from USX format text
 */
class USXNoteSpan(
    override val style: String,
    override val caller: String,
    val chars: List<USXChar>
) : NoteSpan() {

    override val notes: String
    override val passage: String

    var isHighlight: Boolean = false

    companion object {
        private const val DEFAULT_CALLER = "+"
        const val PATTERN = "<note\\s+(((?!>).)*)\\s*>\\s*(((?!(<\\/note>)).)*)\\s*<\\/note>"

        /**
         * Generates the passage note tag with additional attributes
         */
        fun generateTag(style: String, caller: String, title: String, chars: List<USXChar>): String {
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
                element.textContent = c.value.replace("\n", "\\n")
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
                Logger.e(USXNoteSpan::class.java.name, "failed to transform the span text", e)
                return title
            }

            return output.toString()
        }

        /**
         * Generates a footnote span
         * @param note the note
         */
        fun generateFootnote(note: String): USXNoteSpan {
            val chars = listOf(USXChar(USXChar.Companion.STYLE_FOOTNOTE_TEXT, note))
            return USXNoteSpan("f", DEFAULT_CALLER, chars)
        }

        /**
         * Generates a new note span from the supplied XML and returns it.
         * Don't forget to set the click listener!
         * we are using usx for footnotes and our own variant for user notes
         * http://dbl.ubs-icap.org:8090/display/DBLDOCS/USX#USX-note(Footnote)
         *
         * Uses javax.xml DOM parsing (JVM standard, no Android SDK dependency).
         */
        fun parseNote(usx: String): USXNoteSpan? {
            return try {
                val dbf = DocumentBuilderFactory.newInstance()
                val db = dbf.newDocumentBuilder()
                val document = db.parse(InputSource(StringReader(usx)))
                val root = document.documentElement ?: return null
                if (root.tagName != "note") return null

                val style = root.getAttribute("style") ?: return null
                val caller = root.getAttribute("caller").takeIf { it.isNotEmpty() } ?: DEFAULT_CALLER

                val chars = ArrayList<USXChar>()
                // Iterate child nodes in document order to preserve interleaving
                // of <char> elements and bare text nodes
                val childNodes = root.childNodes
                for (i in 0 until childNodes.length) {
                    val child = childNodes.item(i)
                    when (child.nodeType) {
                        Node.ELEMENT_NODE -> {
                            val charEl = child as? Element ?: continue
                            if (charEl.tagName != "char") continue
                            val charStyle = charEl.getAttribute("style") ?: continue
                            val charText = charEl.textContent?.trim() ?: ""
                            if (charText.isNotEmpty()) {
                                chars.add(USXChar(charStyle, charText))
                            }
                        }
                        Node.TEXT_NODE -> {
                            val text = child.textContent?.trim() ?: ""
                            if (text.isNotEmpty()) {
                                chars.add(USXChar("ft", text))
                            }
                        }
                    }
                }

                USXNoteSpan(style, caller.trim(), chars)
            } catch (e: Exception) {
                Logger.e(USXNoteSpan::class.java.name, "Failed to parse note", e)
                null
            }
        }
    }

    init {
        var spanTitle = ""
        var quotation = ""
        var passageText = ""
        val noteBuilder = StringBuilder()

        for (c in chars) {
            when (c.style) {
                USXChar.Companion.STYLE_PASSAGE_TEXT -> {
                    passageText = "$passageText${c.value}"
                }
                USXChar.Companion.STYLE_FOOTNOTE_QUOTATION -> {
                    quotation = "$quotation${c.value}"
                }
                USXChar.Companion.STYLE_FOOTNOTE_ALT_QUOTATION -> {
                    if (noteBuilder.isNotEmpty()) noteBuilder.append(" ")
                    noteBuilder.append("\"").append(c.value).append("\"")
                }
                else -> {
                    // TRICKY: this could add extra white space between the quote and a , but
                    // without fixing the usx converter on the server this is the best we can do.
                    if (noteBuilder.isNotEmpty()) noteBuilder.append(" ")
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

        init(spanTitle, generateTag(style, caller, spanTitle, chars))

        passage = spanTitle
        notes = noteBuilder.toString()
    }

    /**
     * Generates a custom Doku Wiki footnote tag.
     * TODO: I think this will just be used for footnotes, however if footnotes are to be treated normally we won't have the span text.
     */
    fun generateDokuWikiTag(): String {
        return "((ref:\"$passage\",note:\"$notes\"))"
    }
}
