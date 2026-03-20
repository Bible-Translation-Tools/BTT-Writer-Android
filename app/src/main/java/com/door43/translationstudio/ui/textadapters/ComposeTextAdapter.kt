package com.door43.translationstudio.ui.textadapters

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import com.door43.translationstudio.rendering.HtmlRenderer
import com.door43.translationstudio.rendering.model.LinkData
import com.door43.translationstudio.rendering.model.NodeStyle
import com.door43.translationstudio.rendering.model.TextNode

/**
 * Converts a List<TextNode> to Compose AnnotatedString.
 * All Compose-specific styling lives here — the core renderers have no UI imports.
 */
object ComposeTextAdapter {

    /**
     * Convert a list of TextNodes to an AnnotatedString.
     *
     * @param nodes                The platform-agnostic node list from a renderer.
     * @param searchHighlightColor Color for search highlight nodes.
     * @param verseColor           Color for regular verse markers.
     * @param onVerseClick         Optional click handler for verse markers.
     * @param onNoteClick          Optional click handler for note markers.
     */
    fun convert(
        nodes: List<TextNode>,
        searchHighlightColor: Color = Color.Yellow,
        verseColor: Color = Color.Gray,
        onVerseClick: ((TextNode.VerseMarker) -> Unit)? = null,
        onNoteClick: (TextNode.NoteMarker, Int, Int) -> Unit = {_, _, _ ->},
        onLinkClick: (TextNode.Link) -> Unit = {}
    ): AnnotatedString = buildAnnotatedString {

        var currentPoeticalLineIndent = 0  // Track current poetic line indent level
        var isFirstElementOfPoeticLine = true  // Track if next element is first child of poetic line
        var verseMarkerAddedIndentation = false  // Track if verse marker already added indentation
        var lastWasPoeticLineMarker = false  // Track if previous node was a PoeticLine marker

        for (node in nodes) {
            // Update poetic line context when we encounter a PoeticLine marker
            if (node is TextNode.PoeticLine && node.content.isEmpty()) {
                currentPoeticalLineIndent = node.indentLevel
                isFirstElementOfPoeticLine = true
                verseMarkerAddedIndentation = false
                lastWasPoeticLineMarker = true
            } else if (node is TextNode.LineBreak || node is TextNode.Paragraph) {
                // Reset context at line/paragraph boundaries
                currentPoeticalLineIndent = 0
                isFirstElementOfPoeticLine = false
                verseMarkerAddedIndentation = false
                lastWasPoeticLineMarker = false
            } else if (node is TextNode.VerseMarker
                && isFirstElementOfPoeticLine
                && currentPoeticalLineIndent > 0) {
                // Verse marker as first element in poetic line will add indentation
                verseMarkerAddedIndentation = true
                lastWasPoeticLineMarker = false
            } else if (node !is TextNode.PoeticLine) {
                // Mark that we've processed a non-poetic-marker element
                if (node !is TextNode.VerseMarker) {
                    isFirstElementOfPoeticLine = false
                }
                lastWasPoeticLineMarker = false
            }

            // Pass isFirstElementOfPoetic=true only for verse markers that are first, not for text nodes
            val isFirstForNode = if (node is TextNode.VerseMarker) {
                isFirstElementOfPoeticLine
            } else false

            appendNode(
                node = node,
                searchHighlightColor = searchHighlightColor,
                verseColor = verseColor,
                onVerseClick = onVerseClick,
                onNoteClick = onNoteClick,
                onLinkClick = onLinkClick,
                poeticalLineIndent = currentPoeticalLineIndent,
                isFirstElementOfPoetic = isFirstForNode,
                verseMarkerAddedIndentation = verseMarkerAddedIndentation
            )
        }
    }

    /**
     * Convert an HTML string (pre-processed by [HtmlRenderer.toAnnotatedHtml])
     * into an AnnotatedString using the platform's `fromHtml()` parser.
     *
     * Wiki-style links have already been converted to `<a href="app://...">` tags,
     * so the platform parser handles all HTML tags and entities correctly.
     * This method post-processes the `app://` link annotations to add click handlers.
     *
     * @param html       Output of [HtmlRenderer.toAnnotatedHtml].
     * @param onLinkClick Called when any link (wiki-style or regular `<a>`) is clicked.
     */
    fun convertHtml(
        html: String,
        onLinkClick: (LinkData) -> Unit = {},
        linkFilter: (LinkData) -> Boolean = { true }
    ): AnnotatedString {
        val parsed = AnnotatedString.fromHtml(html)

        return buildAnnotatedString {
            append(parsed.text)

            // Copy all span styles (bold, italic, colors, etc.)
            for (range in parsed.spanStyles) {
                addStyle(range.item, range.start, range.end)
            }

            // Copy all paragraph styles
            for (range in parsed.paragraphStyles) {
                addStyle(range.item, range.start, range.end)
            }

            // Post-process link annotations: add click handlers for app:// links
            val links = parsed.getLinkAnnotations(0, parsed.length)
            for (range in links) {
                when (val annotation = range.item) {
                    is LinkAnnotation.Url -> {
                        val linkData = HtmlRenderer.parseLinkUrl(annotation.url)
                        val data = if (linkData != null) {
                            linkData
                        } else {
                            val url = annotation.url
                            val title = parsed.text.substring(range.start, range.end)
                            when {
                                url.endsWith(".md") -> {
                                    val wordId = url.substringAfterLast('/')
                                        .substringBeforeLast('.')
                                    LinkData.TranslationWord(id = wordId, title = title)
                                }
                                url.startsWith("rc://") -> LinkData.RcLink(address = url, title = title)
                                else -> LinkData.Markdown(address = url, title = title)
                            }
                        }
                        if (linkFilter(data)) {
                            addLink(
                                LinkAnnotation.Clickable(tag = linkTag(data)) {
                                    onLinkClick(data)
                                },
                                range.start, range.end
                            )
                        }
                    }
                    is LinkAnnotation.Clickable -> addLink(annotation, range.start, range.end)
                }
            }
        }
    }

    private fun linkTag(data: LinkData): String = when (data) {
        is LinkData.Article -> "TA"
        is LinkData.TranslationWord -> "TW"
        is LinkData.Passage -> "PASSAGE"
        is LinkData.Markdown -> "MD"
        is LinkData.RcLink -> "RC"
        is LinkData.ShortReference -> "REF"
        is LinkData.AppLink -> data.linkType
    }

    private fun AnnotatedString.Builder.appendNode(
        node: TextNode,
        searchHighlightColor: Color,
        verseColor: Color,
        onVerseClick: ((TextNode.VerseMarker) -> Unit)?,
        onNoteClick: (TextNode.NoteMarker, Int, Int) -> Unit,
        onLinkClick: (TextNode.Link) -> Unit,
        poeticalLineIndent: Int = 0,
        isFirstElementOfPoetic: Boolean = false,
        verseMarkerAddedIndentation: Boolean = false
    ) {
        when (node) {
            is TextNode.Text -> {
                if (poeticalLineIndent > 0
                    && !verseMarkerAddedIndentation
                    && node.content.trim().isNotEmpty()) {
                    val padding = "    ".repeat(poeticalLineIndent)
                    append(padding)
                }
                val start = length
                append(node.content)
                val end = length
                if (node.startPos >= 0) {
                    addStringAnnotation(
                        tag = "RAW_POSITION",
                        annotation = "${node.startPos}|${node.endPos}",
                        start = start,
                        end = end
                    )
                }
            }

            is TextNode.Styled -> {
                val start = length
                append(node.content)
                val end = length
                applyNodeStyle(node.style, start, end)
            }

            TextNode.LineBreak -> append("\n")

            TextNode.BlankLine -> append("\n\n")

            is TextNode.Paragraph -> append(if (node.indented) "\n    " else "\n")

            is TextNode.SectionHeading -> {
                val start = length
                val text = if (node.isMajor) node.text.uppercase() else node.text
                append(text)
                val end = length

                addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                addStyle(ParagraphStyle(textAlign = TextAlign.Center), start, end)
                append("\n")
            }

            is TextNode.ChapterLabel -> {
                val start = length
                append(node.text)
                val end = length
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
            }

            is TextNode.PoeticLine -> {
                if (node.content.isEmpty()) {
                    append("\n")
                } else {
                    val padding = "    ".repeat(node.indentLevel)
                    val start = length
                    append(padding)
                    append(node.content)
                    val end = length

                    if (node.rightAligned) {
                        addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                        addStyle(ParagraphStyle(textAlign = TextAlign.Right), start, end)
                    }
                    append("\n")
                }
            }

            is TextNode.VerseMarker -> {
                val label = if (node.endVerse > 0) {
                    "${node.startVerse}-${node.endVerse}"
                } else "${node.startVerse}"
                val start = length

                if (node.pinned) {
                    appendInlineContent("verse_pin", label)
                } else {
                    pushStyle(SpanStyle(color = verseColor))
                    append(label)
                    pop()
                }
                val end = length

                // Verse marker annotation for long-press drag identification
                if (node.pinned) {
                    addStringAnnotation(
                        tag = "VERSE_MARKER",
                        annotation = "${node.startVerse}|${node.endVerse}|${node.machineReadable}",
                        start = start,
                        end = end
                    )
                }

                if (node.startPos >= 0) {
                    addStringAnnotation(
                        tag = "RAW_POSITION",
                        annotation = "${node.startPos}|${node.endPos}",
                        start = start,
                        end = end
                    )
                }

                // Click handler
                if (node.pinned && onVerseClick != null) {
                    addLink(
                        LinkAnnotation.Clickable(
                            tag = "VERSE_${node.startVerse}"
                        ) { onVerseClick(node) },
                        start = start,
                        end = end
                    )
                }

                if (isFirstElementOfPoetic && poeticalLineIndent > 0) {
                    append(" ")
                }
            }

            is TextNode.NoteMarker -> {
                val start = length
                pushStyle(SpanStyle())
                if (node.highlighted && searchHighlightColor != Color.Unspecified) {
                    pushStyle(SpanStyle(background = searchHighlightColor))
                }

                // Placeholder for the note icon using inline content
                appendInlineContent("note_icon", "\uFFFC")

                if (node.highlighted && searchHighlightColor != Color.Unspecified) {
                    pop()
                }
                pop()
                val end = length

                addLink(
                    LinkAnnotation.Clickable(tag = "NOTE") {
                        onNoteClick(node, start, end)
                    },
                    start = start,
                    end = end
                )

                if (node.startPos >= 0) {
                    addStringAnnotation(
                        tag = "RAW_POSITION",
                        annotation = "${node.startPos}|${node.endPos}",
                        start = start,
                        end = end
                    )
                }
            }

            is TextNode.SearchHighlight -> {
                val start = length
                append(node.content)
                val end = length
                if (searchHighlightColor != Color.Unspecified) {
                    addStyle(SpanStyle(background = searchHighlightColor), start, end)
                }
            }

            is TextNode.Link -> {
                val (tag, annotation, title) = when (val d = node.linkData) {
                    is LinkData.Article -> Triple("TA", d.address, d.title)
                    is LinkData.Passage -> Triple("PASSAGE", d.address, d.title)
                    is LinkData.TranslationWord -> Triple("TW", d.id, d.title)
                    is LinkData.Markdown -> Triple("MD", d.address, d.title)
                    is LinkData.RcLink -> Triple("RC", d.address, d.title)
                    is LinkData.ShortReference -> Triple("REF", d.ref, d.ref)
                    is LinkData.AppLink -> Triple(d.linkType, d.href, d.title)
                }

                val start = length
                pushStyle(SpanStyle(color = Color.Blue))
                append(title)
                pop()
                val end = length

                addLink(
                    LinkAnnotation.Clickable(tag = tag) {
                        onLinkClick(node)
                    },
                    start = start,
                    end = end
                )
            }
        }
    }

    private fun AnnotatedString.Builder.applyNodeStyle(style: NodeStyle, start: Int, end: Int) {
        when (style) {
            NodeStyle.BOLD ->
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
            NodeStyle.ITALIC ->
                addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
            NodeStyle.BOLD_CENTER -> {
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                addStyle(ParagraphStyle(textAlign = TextAlign.Center), start, end)
            }
            NodeStyle.ITALIC_RIGHT -> {
                addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                addStyle(ParagraphStyle(textAlign = TextAlign.Right), start, end)
            }
            NodeStyle.NORMAL ->
                addStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Normal,
                        fontStyle = FontStyle.Normal
                    ),
                    start,
                    end
                )
        }
    }
}