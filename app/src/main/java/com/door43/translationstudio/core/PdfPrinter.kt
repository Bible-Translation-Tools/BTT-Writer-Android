package com.door43.translationstudio.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.R
import com.door43.usecases.ExportProjects.Companion.sortFrameTranslations
import com.itextpdf.text.Anchor
import com.itextpdf.text.BaseColor
import com.itextpdf.text.Chapter
import com.itextpdf.text.Chunk
import com.itextpdf.text.Document
import com.itextpdf.text.DocumentException
import com.itextpdf.text.Element
import com.itextpdf.text.Font
import com.itextpdf.text.Image
import com.itextpdf.text.PageSize
import com.itextpdf.text.Paragraph
import com.itextpdf.text.Phrase
import com.itextpdf.text.Rectangle
import com.itextpdf.text.pdf.BaseFont
import com.itextpdf.text.pdf.ColumnText
import com.itextpdf.text.pdf.PdfContentByte
import com.itextpdf.text.pdf.PdfPCell
import com.itextpdf.text.pdf.PdfPCellEvent
import com.itextpdf.text.pdf.PdfPTable
import com.itextpdf.text.pdf.PdfPageEventHelper
import com.itextpdf.text.pdf.PdfTemplate
import com.itextpdf.text.pdf.PdfWriter
import com.itextpdf.text.pdf.draw.LineSeparator
import com.itextpdf.text.pdf.draw.VerticalPositionMark
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.resourcecontainer.ResourceContainer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Created by joel on 11/12/2015.
 */
class PdfPrinter(
    private val context: Context,
    private val translation: TargetTranslation,
    private val format: TranslationFormat,
    fontPath: String?,
    fontSize: Float,
    private val rtl: Boolean,
    licenseFontPath: String?,
    private val imagesDir: File?,
    private val directoryProvider: IDirectoryProvider,
    private val library: Door43Client
) : PdfPageEventHelper() {
    private val titleFont: Font
    private val chapterFont: Font
    private val bodyFont: Font
    private val boldBodyFont: Font
    private val underlineBodyFont: Font
    private val subFont: Font
    private val headingFont: Font
    private val licenseFont: Font
    private val sourceContainer: ResourceContainer?
    private val superScriptFont: Font
    private val footnoteFont: Font
    private val baseFont: BaseFont
    private val licenseBaseFont: BaseFont
    private var includeMedia = true
    private var includeIncomplete = true
    private val tocPlaceholder: MutableMap<String, PdfTemplate> = HashMap()
    private val pageByTitle: MutableMap<String, Int> = HashMap()
    private var writer: PdfWriter? = null
    private var mCurrentParagraph: Paragraph? = null
    private val targetLanguageFontSize: Float

    init {
        var rc: ResourceContainer? = null
        val p = library.index.getProject(
            "en",
            translation.projectId,
            true
        )
        p?.let { project ->
            val resources = library.index.getResources(project.languageSlug, project.slug)
            try {
                rc = library.open(
                    "en",
                    translation.projectId,
                    resources[0].slug
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        this.sourceContainer = rc

        targetLanguageFontSize = fontSize / RATIO_OF_SP_TO_PT

        baseFont = BaseFont.createFont(fontPath, BaseFont.IDENTITY_H, true)
        titleFont = Font(baseFont, targetLanguageFontSize * 2.5f, Font.BOLD)
        chapterFont = Font(baseFont, targetLanguageFontSize * 2)
        bodyFont = Font(baseFont, targetLanguageFontSize)
        boldBodyFont = Font(baseFont, targetLanguageFontSize, Font.BOLD)
        headingFont = Font(baseFont, targetLanguageFontSize * 1.4f, Font.BOLD)
        underlineBodyFont = Font(baseFont, targetLanguageFontSize, Font.UNDERLINE)
        subFont = Font(baseFont, targetLanguageFontSize, Font.ITALIC)
        superScriptFont = Font(baseFont, targetLanguageFontSize * 0.9f, Font.BOLD)
        superScriptFont.setColor(94, 94, 94)
        footnoteFont = Font(
            baseFont,
            targetLanguageFontSize * 0.7f,
            Font.NORMAL,
            BaseColor.BLUE
        )

        licenseBaseFont =
            BaseFont.createFont(licenseFontPath, BaseFont.IDENTITY_H, true)
        licenseFont = Font(licenseBaseFont, 20f)
    }

    /**
     * Include media (images) in the pdf
     * @param include
     */
    fun includeMedia(include: Boolean) {
        this.includeMedia = include
    }

    /**
     * Include incomplete translations
     * @param include
     */
    fun includeIncomplete(include: Boolean) {
        this.includeIncomplete = include
    }

    @Throws(Exception::class)
    fun print(): File {
        val tempFile = directoryProvider.createTempFile(translation.id, ".pdf")

        val document = Document(
            PageSize.LETTER,
            HORIZONTAL_PADDING,
            HORIZONTAL_PADDING,
            VERTICAL_PADDING,
            VERTICAL_PADDING
        )

        FileOutputStream(tempFile).use { output ->
            writer = PdfWriter.getInstance(document, output).apply {
                pageEvent = this@PdfPrinter
            }
            document.open()
            addMetaData(document)
            addTitlePage(document)
            addLicensePage(document)
            addTOC(document)
            addContent(document)
            document.close()
        }

        return tempFile
    }

    @Throws(DocumentException::class)
    private fun addTOC(document: Document) {
        document.newPage()
        document.resetPageCount() // disable page numbering for this page (TOC)

        val toc = context.resources.getString(R.string.table_of_contents)
        val intro = Chapter(Paragraph(toc, chapterFont), 0)
        intro.numberDepth = 0
        document.add(intro)
        document.add(Paragraph(" "))

        val table = PdfPTable(2)
        table.widthPercentage = 100f
        table.horizontalAlignment = Element.ALIGN_CENTER

        for (c in translation.chapterTranslations) {
            if (!includeIncomplete && !c.titleFinished && !sourceContainer?.readChunk(
                    c.id,
                    "title"
                ).isNullOrEmpty()
            ) {
                continue
            }

            if (!"front".equals(
                    c.id,
                    ignoreCase = true
                )
            ) { // ignore front text as not human-readable
                // write chapter title
                val title = chapterTitle(c)
                val chunk = Chunk(title, headingFont).setLocalGoto(title)

                // put in chapter title in cell
                val titleCell = PdfPCell()
                val element = Paragraph(targetLanguageFontSize * 1.6f) // set leading
                element.alignment = Element.ALIGN_LEFT
                element.add(chunk)
                titleCell.addElement(element)
                titleCell.runDirection =
                    if (rtl) PdfWriter.RUN_DIRECTION_RTL else PdfWriter.RUN_DIRECTION_LTR // need to set predominant language direction in case first character runs other direction
                titleCell.border = Rectangle.NO_BORDER
                titleCell.verticalAlignment = Element.ALIGN_MIDDLE

                // put in page number in cell
                val pageNumberCell = PdfPCell()
                pageNumberCell.horizontalAlignment = Element.ALIGN_RIGHT
                pageNumberCell.border = Rectangle.NO_BORDER

                // add placeholder for page reference
                pageNumberCell.addElement(object : VerticalPositionMark() {
                    override fun draw(
                        canvas: PdfContentByte,
                        llx: Float,
                        lly: Float,
                        urx: Float,
                        ury: Float,
                        y: Float
                    ) {
                        val createTemplate = canvas.createTemplate(50f, 50f)
                        tocPlaceholder[title] = createTemplate
                        val shift = targetLanguageFontSize * 1.25f
                        canvas.addTemplate(createTemplate, urx - 50, y - shift)
                    }
                })

                if (!rtl) { // on LTR put page numbers on right
                    table.addCell(titleCell)
                    table.addCell(pageNumberCell)
                    table.setWidths(
                        intArrayOf(
                            20,
                            1
                        )
                    ) // title column is 20 times as wide as the page number column
                } else { // on RTL put page numbers on left
                    pageNumberCell.horizontalAlignment = Element.ALIGN_LEFT
                    pageNumberCell.runDirection = PdfWriter.RUN_DIRECTION_LTR
                    table.addCell(pageNumberCell)
                    table.addCell(titleCell)
                    table.setWidths(
                        intArrayOf(
                            1,
                            20
                        )
                    ) // title column is 20 times as wide as the page number column
                }
            }
        }
        document.add(table)
    }

    /**
     * Adds file meta data
     * @param document
     */
    private fun addMetaData(document: Document) {
        val projectTranslation = translation.projectTranslation
        document.addTitle(projectTranslation.title)
        document.addSubject(projectTranslation.description)
        for (ns in translation.contributors) {
            document.addAuthor(ns.name)
            document.addCreator(ns.name)
        }
        document.addCreationDate()
        document.addLanguage(translation.targetLanguageName)
        document.addKeywords("format=" + format.title)
    }

    /**
     * Adds the title page
     * @param document
     * @throws DocumentException
     */
    @Throws(DocumentException::class)
    private fun addTitlePage(document: Document) {
        document.resetPageCount() // disable page numbering for this page (title)

        // table for vertical alignment
        val table = PdfPTable(1)
        table.widthPercentage = 100f
        val spacerCell = PdfPCell()
        spacerCell.border = Rectangle.NO_BORDER
        spacerCell.fixedHeight = document.pageSize.height / 2 - VERTICAL_PADDING * 2
        table.addCell(spacerCell)

        // book title
        val projectTranslation = translation.projectTranslation
        var title = projectTranslation.title
        if (title.isEmpty()) {
            val project = library.index.getProject(
                translation.targetLanguageId,
                translation.projectId,
                true
            )
            if ((project != null) && (project.name != null)) {
                title = project.name
            }
        }
        val titleParagraph = (Paragraph(title, titleFont))
        titleParagraph.alignment = Element.ALIGN_CENTER
        addBidiParagraphToTable(table, titleParagraph)

        // book description
        val description = Paragraph(projectTranslation.description, subFont)
        description.alignment = Element.ALIGN_CENTER
        addBidiParagraphToTable(table, description)

        document.add(table)
    }

    private fun chapterTitle(c: ChapterTranslation): String {
        val title: String
        if (c.title.isEmpty()) {
            val chapterNumber = Util.strToInt(c.id, 0)
            title = if (chapterNumber > 0) {
                context.resources.getString(
                    R.string.label_chapter_title_detailed,
                    "" + chapterNumber
                )
            } else {
                "" // not regular chapter, may be chapter 0 with id of "front"
            }
        } else {
            title = c.title
        }
        return title
    }

    @Throws(DocumentException::class)
    private fun addChapterPage(document: Document, c: ChapterTranslation) {
        // title
        val title = chapterTitle(c)
        val anchor = Anchor(title, chapterFont)
        anchor.name = c.title

        val cell = PdfPCell()
        val element = Paragraph()
        element.alignment = Element.ALIGN_CENTER
        element.add(anchor)
        cell.addElement(element)
        // need to set predominant language direction in case first character runs other direction
        cell.runDirection =
            if (rtl) PdfWriter.RUN_DIRECTION_RTL else PdfWriter.RUN_DIRECTION_LTR
        cell.border = Rectangle.NO_BORDER
        val table = PdfPTable(1)
        table.addCell(cell)

        val chapterParagraph = Paragraph()
        chapterParagraph.add(table)
        chapterParagraph.alignment = Element.ALIGN_CENTER
        val chapter = Chapter(chapterParagraph, Util.strToInt(c.id, 0))
        chapter.numberDepth = 0

        document.add(chapter)
        document.add(Paragraph(" ")) // put whitespace between chapter title and text

        // update TOC
        val template = tocPlaceholder[title]
        template!!.beginText()
        template.setFontAndSize(baseFont, PAGE_NUMBER_FONT_SIZE)
        template.setTextMatrix(
            50 - baseFont.getWidthPoint(
                writer!!.pageNumber.toString(),
                PAGE_NUMBER_FONT_SIZE
            ), 0f
        )
        template.showText(writer!!.pageNumber.toString())
        template.endText()
    }

    /**
     * Adds the content of the book
     * @param document
     */
    @Throws(DocumentException::class, IOException::class)
    private fun addContent(document: Document) {
        val chapterTranslations = translation.chapterTranslations
//        val chapterCount = chapterTranslations.size + 1
//        val increments = 1.0 / chapterCount
//        var progress = 0.0

        for (c in chapterTranslations) {
            val table = PdfPTable(1)
            table.widthPercentage = 100f

            val chapterFootnotes = mutableListOf<String>()

            // TODO send progress via listener or callback
            // task?.updateProgress(increments.let { progress += it; progress })

            // if chapter 00, then skip title since that was already printed as first page.
            val chapter0 = (Util.strToInt(c.id, 0) == 0)
            if (!chapter0) {
                if (includeIncomplete || c.titleFinished || sourceContainer?.readChunk(
                        c.id,
                        "title"
                    ).isNullOrEmpty()
                ) {
                    addChapterPage(document, c)
                }
            }

            // get chapter body
            val frames = translation.getFrameTranslations(
                c.id,
                this.format
            )
            val frameList = sortFrameTranslations(frames)
            for (i in frameList.indices) {
                val f = frameList[i]
                if (includeIncomplete || f.finished) {
                    if (includeMedia
                        && this.format == TranslationFormat.MARKDOWN
                        && imagesDir != null
                    ) {
                        // TODO: 11/13/2015 insert frame images if we have them.
                        // TODO: 11/13/2015 eventually we need to provide the directory
                        //  where to find these images which will be downloaded not in assets
                        try {
                            val imageFile = File(
                                imagesDir,
                                translation.projectId + "-" + f.complexId + ".jpg"
                            )
                            if (imageFile.exists()) {
                                if (i != 0) {
                                    addBidiTextToTable(
                                        10,
                                        " ",
                                        subFont,
                                        table
                                    ) // add space between text above and image below
                                }
                                addImage(document, table, imageFile.absolutePath)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    // TODO: 11/13/2015 render body according to the format
                    val body = f.body
                    if (format == TranslationFormat.USFM) {
                        addUSFM(f.body, table, c.id, chapterFootnotes)
                    } else {
                        addBidiTextToTable(16, body, this.bodyFont, table)
                    }
                }
            }

            // chapter reference
            if ((includeIncomplete || c.referenceFinished) && c.reference.isNotEmpty()) {
                addBidiTextToTable(16, " ", this.bodyFont, table)
                addBidiTextToTable(16, c.reference, subFont, table)
            }

            // Render footnote section
            if (chapterFootnotes.isNotEmpty()) {
                addSolidLine(table)

                chapterFootnotes.forEachIndexed { index, content ->
                    val fnIndex = index + 1
                    val fnId = "footnote-${c.id}-$fnIndex"
                    val sourceId = "source-$fnId"

                    val p = Paragraph()
                    p.spacingAfter = 2f
                    p.indentationLeft = DEFAULT_INDENT_SPACING

                    val numChunk = Chunk("$fnIndex. ", footnoteFont).apply {
                        textRise = targetLanguageFontSize / 2.5f
                        setLocalGoto(sourceId)
                        setLocalDestination(fnId)
                    }

                    p.add(numChunk)
                    p.add(Chunk(content, bodyFont))

                    addBidiParagraphToTable(table, p)
                }

                addSolidLine(table)
            }

            document.add(table)
        }
    }

    private fun addUSFM(
        usfm: String,
        table: PdfPTable,
        chapterId: String,
        footnotes: MutableList<String>
    ) {
        val context = ParseContext(table, chapterId, footnotes)

        // Captures: 1=Marker, 2=Closer(*), 3=Argument
        val usfmRegex = Regex("""\\([a-z0-9]+)(\*?)(?:\s+(\d+(?:-\d+)?))?[ \t]?""")
        var lastIndex = 0

        usfmRegex.findAll(usfm).forEach { match ->
            val markerIndex = match.range.first
            val marker = match.groupValues[1]
            val isCloser = match.groupValues[2] == "*"
            val argument = match.groupValues[3]

            // Handle text between markers
            if (markerIndex > lastIndex) {
                val text = usfm.substring(lastIndex, markerIndex)
                context.addText(text)
            }

            // Handle the marker itself
            context.processMarker(marker, isCloser, argument)

            lastIndex = match.range.last + 1
        }

        // Handle Trailing Text & Final Flush
        if (lastIndex < usfm.length) {
            context.addText(usfm.substring(lastIndex), isTrailing = true)
        }
        context.flush()
    }

    /**
     * put text in table cell, set text direction, and add to table
     * @param leading
     * @param text
     * @param font
     * @param table
     * @return
     */
    private fun addBidiTextToTable(
        leading: Int,
        text: String,
        font: Font,
        table: PdfPTable
    ): PdfPCell {
        val paragraph = Paragraph(leading.toFloat(), text, font)
        return addBidiParagraphToTable(table, paragraph)
    }

    /**
     * package paragraph in table cell, set text direction, and add to table
     * @param table
     * @param paragraph
     * @return
     */
    private fun addBidiParagraphToTable(table: PdfPTable, paragraph: Paragraph, verseNumber: String? = null): PdfPCell {
        val cell = PdfPCell()
        cell.addElement(paragraph)
        // need to set predominant language direction in case first character runs other direction
        cell.runDirection = if (rtl) PdfWriter.RUN_DIRECTION_RTL else PdfWriter.RUN_DIRECTION_LTR
        cell.border = Rectangle.NO_BORDER

        // Draw verse number with absolute position
        if (verseNumber != null) {
            cell.cellEvent = VerseNumberEvent(verseNumber, superScriptFont, rtl)
        }

        table.addCell(cell)
        return cell
    }

    override fun onChapter(
        writer: PdfWriter,
        document: Document,
        paragraphPosition: Float,
        title: Paragraph
    ) {
        pageByTitle[title.content] = writer.pageNumber
    }

    override fun onSection(
        writer: PdfWriter,
        document: Document,
        paragraphPosition: Float,
        depth: Int,
        title: Paragraph
    ) {
        pageByTitle[title.content] = writer.pageNumber
    }

    override fun onEndPage(writer: PdfWriter, document: Document) {
        val cb = writer.directContent
        cb.saveState()

        var pageNumberShown: String? = ""
        val pageNumber = writer.pageNumber
        if (pageNumber > 0) { // only add page number if above zero
            pageNumberShown += pageNumber
        }

        // place page number just within the margin
        val textBase = document.bottom() - PAGE_NUMBER_FONT_SIZE

        cb.beginText()
        cb.setFontAndSize(baseFont, PAGE_NUMBER_FONT_SIZE)
        cb.setTextMatrix((document.right() / 2) + HORIZONTAL_PADDING / 2, textBase)
        cb.showText(pageNumberShown)
        cb.endText()
        cb.restoreState()
    }

    /**
     * add the license from resource
     * @param document
     * @throws DocumentException
     */
    @Throws(DocumentException::class)
    private fun addLicensePage(document: Document) {
        // title
        val title = ""
        val anchor = Anchor(title, licenseFont)
        anchor.name = "name"
        val chapterParagraph = Paragraph(anchor)
        chapterParagraph.alignment = Element.ALIGN_CENTER
        val chapter = Chapter(chapterParagraph, 0)
        chapter.numberDepth = 0

        // table for vertical alignment
        val table = PdfPTable(1)
        table.widthPercentage = 100f
        val cell = PdfPCell()
        cell.border = Rectangle.NO_BORDER
        cell.minimumHeight = document.pageSize.height - VERTICAL_PADDING * 2
        cell.verticalAlignment = Element.ALIGN_MIDDLE
        //        cell.addElement(chapter);
        table.addCell(cell)

        // place license title on it's own page
        document.newPage()
        document.resetPageCount() // disable page numbering for this page (license)
        document.add(chapter)

        // translate simple html to paragraphs
        var license = context.resources.getString(R.string.license_pdf)

        if (includeMedia) {
            license += context.resources.getString(R.string.artwork_attribution_pdf)
        }

        license = license.replace("&#8226;", "\u2022")

        mCurrentParagraph = null
        parseHtml(document, license, 0)

        nextParagraph(document)
    }

    /**
     * convert basic html to pdf chunks and add to document
     * @param document
     * @param text
     * @param pos
     * @throws DocumentException
     */
    @Throws(DocumentException::class)
    private fun parseHtml(document: Document, text: String?, pos: Int) {
        var position = pos
        if (text == null) {
            return
        }

        val length = text.length
        var foundHtml: FoundHtml?
        while (position < length) {
            foundHtml = getNextHtml(text, position)
            if (null == foundHtml) {
                break
            }

            if (foundHtml.startPos > position) {
                val beforeText = text.substring(position, foundHtml.startPos)
                addHtmlChunk(beforeText, bodyFont)
            }

            if ("b" == foundHtml.html) { // bold
                addHtmlChunk(foundHtml.enclosed, boldBodyFont)
            } else if ((foundHtml.html.isNotEmpty()) && (foundHtml.html[0] == 'h')) { // header
                nextParagraph(document)
                mCurrentParagraph = Paragraph(foundHtml.enclosed, headingFont)
                nextParagraph(document)
            } else if ((foundHtml.html.isNotEmpty()) && (foundHtml.html[0] == 'a')) { // anchor
                addHtmlChunk(foundHtml.enclosed, underlineBodyFont)
            } else if ("br" == foundHtml.html) { // line break
                nextParagraph(document)
            } else if ("p" == foundHtml.html) { // line break
                blankLine(document)
                parseHtml(document, foundHtml.enclosed, 0)
                nextParagraph(document)
            } else { // anything else just strip off the html tag
                parseHtml(document, foundHtml.enclosed, 0)
            }

            position = foundHtml.htmlFinishPos
        }

        if (position < length) {
            val rest = text.substring(position)
            addHtmlChunk(rest, bodyFont)
        }
    }

    /**
     * add text to current paragraph, trim white space
     * @param text
     * @param font
     * @throws DocumentException
     */
    @Throws(DocumentException::class)
    private fun addHtmlChunk(text: String, font: Font) {
        var txt = text
        if (txt.isNotEmpty()) {
            txt = txt.replace("\n", "")
            while ((txt.length > 1) && ("  " == txt.take(2))) {
                // remove extra leading space
                txt = txt.substring(1)
            }
            while ((txt.length > 1) && ("  " == txt.substring(txt.length - 2))) {
                // remove extra leading space
                txt = txt.dropLast(1)
            }

            val chunk = Chunk(txt, font)
            addChunkToParagraph(chunk)
        }
    }

    /**
     * start a new paragraph
     * @param document
     * @throws DocumentException
     */
    @Throws(DocumentException::class)
    private fun nextParagraph(document: Document) {
        if (mCurrentParagraph != null) {
            document.add(mCurrentParagraph)
        }
        mCurrentParagraph = null
    }

    /**
     * insert a blank paragraph
     * @param document
     * @throws DocumentException
     */
    @Throws(DocumentException::class)
    private fun blankLine(document: Document) {
        nextParagraph(document)
        mCurrentParagraph = Paragraph(" ", bodyFont)
        nextParagraph(document)
    }

    /**
     * add a chunk to current paragraph
     * @param chunk
     * @throws DocumentException
     */
    @Throws(DocumentException::class)
    private fun addChunkToParagraph(chunk: Chunk) {
        if (null == mCurrentParagraph) {
            mCurrentParagraph = Paragraph("", bodyFont)
        }
        mCurrentParagraph!!.add(chunk)
    }

    /**
     * get next HTML tag from start pos
     * @param text
     * @param startPos
     * @return
     */
    private fun getNextHtml(text: String, startPos: Int): FoundHtml? {
        val pos = text.indexOf("<", startPos)
        if (pos < 0) {
            return null
        }

        val end = text.indexOf(">", pos + 1)
        val length = text.length
        if (end < 0) {
            return FoundHtml(text.substring(pos + 1), pos, length, "")
        }

        val token = text.substring(pos + 1, end)
        if (token.isNotEmpty() && (token[token.length - 1] == '/')) {
            return FoundHtml(token.dropLast(1), pos, end + 1, "")
        }

        val parts = token.split(" ".toRegex()) // ignore attributes
        val endToken = "</" + parts[0] + ">"
        val finish = text.indexOf(endToken, end + 1)
        if (finish < 0) { // if end token not found, then stop at next
            val next = text.indexOf("<", end + 1)
            return if (next < 0) {
                FoundHtml(token, pos, length, text.substring(end + 1, length))
            } else {
                FoundHtml(token, pos, next, text.substring(end + 1, next))
            }
        }

        val htmlFinishPos = finish + endToken.length
        return FoundHtml(token, pos, htmlFinishPos, text.substring(end + 1, finish))
    }

    private fun defaultParagraphStyle(
        indent: Float = 0f,
        alignment: Int = Element.ALIGN_LEFT,
        spacingBefore: Float = 0f
    ): ParagraphStyle = ParagraphStyle(
        indent = indent,
        alignment = alignment,
        spacingBefore = spacingBefore
    )

    /**
     * class for keeping track of a HTML tag that was found, it's name, it's contents, and position
     */
    private class FoundHtml(
        var html: String,
        var startPos: Int,
        var htmlFinishPos: Int,
        var enclosed: String
    )

    companion object {
        private const val PAGE_NUMBER_FONT_SIZE = 10f
        private const val DEFAULT_INDENT_SPACING = 16f
        private const val VERTICAL_PADDING = 72.0f // 1 inch
        private const val HORIZONTAL_PADDING = 72.0f // 1 inch
        const val RATIO_OF_SP_TO_PT: Float = 2.5f

        private fun addEmptyLine(paragraph: Paragraph, number: Int) {
            repeat(number) {
                paragraph.add(Paragraph(" "))
            }
        }

        private fun addSolidLine(table: PdfPTable) {
            val line = LineSeparator()
            line.lineWidth = 0.5f
            val lineCell = PdfPCell()
            lineCell.addElement(Chunk(line))
            lineCell.border = Rectangle.NO_BORDER
            table.addCell(lineCell)
        }

        /**
         * Add image from a file path
         *
         * @param document
         * @param path
         * @throws DocumentException
         * @throws IOException
         */
        @Throws(DocumentException::class, IOException::class)
        fun addImage(document: Document, table: PdfPTable, path: String?) {
            val image = Image.getInstance(path)
            image.alignment = Element.ALIGN_CENTER
            if (image.scaledWidth > pageWidth(document) || image.scaledHeight > pageHeight(document)) {
                image.scaleToFit(pageWidth(document), pageHeight(document))
            }

            val paragraph = Paragraph(Chunk(image, 0f, 0f, true))
            val cell = PdfPCell()
            cell.addElement(paragraph)
            cell.border = Rectangle.NO_BORDER
            table.addCell(cell)
        }

        /**
         * Add Image from an input stream
         * @param document
         * @param inputStream
         * @throws DocumentException
         * @throws IOException
         */
        @Throws(DocumentException::class, IOException::class)
        fun addImage(document: Document, inputStream: InputStream?) {
            val bmp = BitmapFactory.decodeStream(inputStream)
            val stream = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val image = Image.getInstance(stream.toByteArray())
            image.alignment = Element.ALIGN_CENTER
            if (image.scaledWidth > pageWidth(document) || image.scaledHeight > pageHeight(document)) {
                image.scaleToFit(pageWidth(document), pageHeight(document))
            }
            document.add(Chunk(image, 0f, 0f, true))
        }

        /**
         * Returns the height of the printable area of the page
         * @param document
         * @return
         */
        private fun pageHeight(document: Document): Float {
            return document.pageSize.height - VERTICAL_PADDING * 2
        }

        /**
         * Returns the width of the printable area of the page
         * @param document
         * @return
         */
        private fun pageWidth(document: Document): Float {
            return document.pageSize.width - HORIZONTAL_PADDING * 2
        }
    }

    private inner class ParseContext(
        val table: PdfPTable,
        val chapterId: String,
        val footnotes: MutableList<String>
    ) {
        val italicFont = Font(baseFont, targetLanguageFontSize, Font.ITALIC)

        private var currentParagraph = createNewParagraph()
        private var currentFont = bodyFont
        private var isParagraphStart = true
        private var inFootnote = false
        private var currentIndentLevel = 0
        private var currentAlignment = Element.ALIGN_LEFT
        private var pendingVerseNumber: String? = null

        private val footnoteBuffer = StringBuilder()

        fun addText(text: String, isTrailing: Boolean = false) {
            if (inFootnote) {
                footnoteBuffer.append(text)
                return
            }

            if (text.isNotBlank()) {
                currentParagraph.add(Chunk(text, currentFont))
                isParagraphStart = false
            } else if (inFootnote && isTrailing) {
                // Edge case: handle unclosed footnote at end of string
                captureFootnote(text)
            }
        }

        fun processMarker(marker: String, isCloser: Boolean, argument: String) {
            if (inFootnote) {
                handleFootnoteMarker(marker, isCloser, argument)
                return
            }

            when {
                // Footnotes
                marker == "f" && !isCloser -> {
                    val chunks = currentParagraph.chunks
                    // Trim text before the footnote
                    if (chunks.isNotEmpty()) {
                        val lastIndex = chunks.lastIndex
                        val lastChunk = chunks[lastIndex]
                        val modifiedChunk = Chunk(lastChunk.content.trim(), lastChunk.font)
                        chunks[lastIndex] = modifiedChunk
                        currentParagraph.clear()
                        chunks.forEach { currentParagraph.add(it) }
                    }
                    startFootnote()
                }

                // Verses
                marker == "v" && argument.isNotEmpty() -> handleVerse(argument)

                // Structure: Spacers & Breaks
                marker == "b" -> addSpacerBlock()
                marker in listOf("p", "m") -> startBlock(Element.ALIGN_LEFT)

                // Alignment
                marker == "qa" || marker == "qc" || marker == "qd" -> {
                    startBlock(Element.ALIGN_CENTER, font = italicFont)
                }
                marker == "qr" -> startBlock(Element.ALIGN_RIGHT)
                marker == "qs" -> if (!isCloser) startBlock(Element.ALIGN_RIGHT) else flush()

                // Inline Styles
                marker == "qac" -> toggleItalic(isCloser)

                // Poetry
                marker.startsWith("qm") -> startPoetryBlock(marker, font = italicFont)
                marker.startsWith("q") -> startPoetryBlock(marker, font = bodyFont)

                // We ignore chapter marker if it's in the chunk file
                marker == "c" -> {}

                // Default / Fallback
                else -> currentParagraph.add(Chunk("\\$marker$argument", currentFont))
            }
        }

        private fun handleVerse(number: String) {
            // Special block is the paragraph with indentation and/or uncommon alignment
            val isSpecialBlock = currentIndentLevel > 0 || currentAlignment != Element.ALIGN_LEFT

            // Auto-flush: If adding a verse to a special block that has content, force a new line
            if (isSpecialBlock && !isParagraphStart) {
                flush()
            }

            if (isParagraphStart && isSpecialBlock) {
                pendingVerseNumber = number
            } else {
                val vChunk = Chunk(number, superScriptFont).apply {
                    textRise = targetLanguageFontSize / 2
                }
                currentParagraph.add(vChunk)
            }
            isParagraphStart = false
        }

        private fun startPoetryBlock(marker: String, font: Font) {
            val level = marker.filter { it.isDigit() }.toIntOrNull() ?: 1
            currentIndentLevel = level
            currentFont = font

            // Poetry implicitly resets alignment to Start/Left
            val style = defaultParagraphStyle(
                indent = level * DEFAULT_INDENT_SPACING,
                alignment = Element.ALIGN_LEFT
            )
            flush(style)
        }

        private fun startBlock(alignment: Int, font: Font = bodyFont) {
            currentIndentLevel = 0
            currentFont = font
            flush(defaultParagraphStyle(alignment = alignment))
        }

        private fun addSpacerBlock() {
            // Commit previous content
            flush(defaultParagraphStyle(alignment = Element.ALIGN_LEFT))

            // Add the physical spacer row
            val spacer = Paragraph(" ", bodyFont).apply {
                leading = targetLanguageFontSize * 1.6f
                spacingAfter = 0f
            }
            addBidiParagraphToTable(table, spacer)

            // Reset State
            currentFont = bodyFont
            currentIndentLevel = 0
        }

        private fun startFootnote() {
            inFootnote = true
            val index = footnotes.size + 1
            val fnId = "footnote-$chapterId-$index"
            val sourceId = "source-$fnId"

            val chunk = Chunk("$index", footnoteFont).apply {
                textRise = targetLanguageFontSize / 2.5f
                setUnderline(0.1f, 2.5f)
                setLocalGoto(fnId)
                setLocalDestination(sourceId)
            }
            val spacer = Chunk(" ")
            currentParagraph.add(chunk)
            currentParagraph.add(spacer)
        }

        private fun handleFootnoteMarker(marker: String, isCloser: Boolean, argument: String) {
            if (marker == "f" && isCloser) {
                captureFootnote(footnoteBuffer.toString())
                footnoteBuffer.clear()
                inFootnote = false
            } else if (argument.isNotEmpty()) {
                footnoteBuffer.append(argument).append(" ")
            }
        }

        private fun captureFootnote(rawContent: String) {
            val clean = rawContent
                .replaceFirst(Regex("^\\s*\\S+\\s*"), "")
                .trim()
            if (clean.isNotEmpty()) {
                footnotes.add(clean)
            }
        }

        private fun toggleItalic(isCloser: Boolean) {
            if (isCloser) currentParagraph.add(Chunk(" "))
            currentFont = if (isCloser) bodyFont else italicFont
        }

        fun flush(newStyle: ParagraphStyle? = null) {
            if (inFootnote) return

            // Commit pending content
            if (!currentParagraph.isEmpty()
                || currentParagraph.chunks.isNotEmpty()
                || pendingVerseNumber != null
            ) {
                addBidiParagraphToTable(table, currentParagraph, pendingVerseNumber)
            }

            // Reset logic
            currentParagraph = createNewParagraph()
            isParagraphStart = true
            pendingVerseNumber = null

            if (newStyle != null) {
                applyStyle(currentParagraph, newStyle)
                currentAlignment = newStyle.alignment // Update Tracker
            } else {
                // Inherit Tracker
                currentParagraph.alignment = currentAlignment
                if (currentIndentLevel > 0) {
                    val indent = currentIndentLevel * DEFAULT_INDENT_SPACING
                    applyIndent(currentParagraph, indent)
                }
            }
        }

        private fun createNewParagraph() =
            Paragraph(targetLanguageFontSize * 1.6f, "", bodyFont)

        private fun applyStyle(p: Paragraph, style: ParagraphStyle) {
            applyIndent(p, style.indent)
            p.alignment = style.alignment
            p.spacingBefore = style.spacingBefore
        }

        private fun applyIndent(p: Paragraph, indent: Float) {
            p.indentationLeft = indent
        }
    }

    private data class ParagraphStyle(
        val indent: Float,
        val alignment: Int,
        val spacingBefore: Float
    )

    private class VerseNumberEvent(
        private val verseNumber: String,
        private val font: Font,
        private val rtl: Boolean
    ) : PdfPCellEvent {

        override fun cellLayout(cell: PdfPCell, position: Rectangle, canvases: Array<PdfContentByte>) {
            val canvas = canvases[PdfPTable.TEXTCANVAS]

            // Calculate Y position
            val yPos = position.top - font.size * 1.6f

            // Calculate X position:
            val xPos = if (rtl) {
                position.right - 2f
            } else {
                position.left + 2f
            }

            ColumnText.showTextAligned(
                canvas,
                if (rtl) Element.ALIGN_RIGHT else Element.ALIGN_LEFT,
                Phrase(verseNumber, font),
                xPos,
                yPos,
                0f
            )
        }
    }
}
