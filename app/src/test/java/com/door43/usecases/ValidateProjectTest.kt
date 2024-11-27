package com.door43.usecases

import android.content.Context
import com.door43.TestUtils
import com.door43.translationstudio.R
import com.door43.translationstudio.core.ChapterTranslation
import com.door43.translationstudio.core.FrameTranslation
import com.door43.translationstudio.core.MergeConflictsHandler
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.json.JSONException
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.Index
import org.unfoldingword.door43client.models.SourceLanguage
import org.unfoldingword.resourcecontainer.ResourceContainer

class ValidateProjectTest {

    @MockK private lateinit var context: Context
    @MockK private lateinit var library: Door43Client
    @MockK private lateinit var translator: Translator
    @MockK private lateinit var index: Index
    @MockK private lateinit var sourceContainer: ResourceContainer
    @MockK private lateinit var sourceLanguage: SourceLanguage

    private val sourceTranslationId = "en_mrk_text_ulb"
    private val targetTranslationId = "id_mrk_text_reg"

    private val sourceTranslation: TargetTranslation = mockk(relaxed = true)
    val targetTranslation: TargetTranslation = mockk(relaxed = true)

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        TestUtils.setPropertyReflection(library, "index", index)

        every { translator.getTargetTranslation(sourceTranslationId) }
            .returns(sourceTranslation)
        every { translator.getTargetTranslation(targetTranslationId) }
            .returns(targetTranslation)

        every { index.getTargetLanguage(any()) }.returns(mockk())
        every { index.getSourceLanguage(any()) }.returns(sourceLanguage)

        val info: JSONObject = mockk {
            every { getString(any()) }.returns("text/usfm")
        }
        TestUtils.setPropertyReflection(sourceContainer, "info", info)
        TestUtils.setPropertyReflection(sourceContainer, "language", sourceLanguage)

        every { library.open(any()) }.returns(sourceContainer)

        mockkStatic(MergeConflictsHandler::class)
        every { MergeConflictsHandler.isMergeConflicted(any()) }.returns(false)

        every { context.getString(R.string.has_warnings) }.returns("Has warnings")
        every { context.getString(R.string.title) }.returns("Title")
        every { context.getString(R.string.reference) }.returns("Reference")
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test validate project, passes successfully`() {
        mockTranslation()

        val bookChapter: ChapterTranslation = mockk(relaxed = true) {
            every { isTitleFinished }.returns(true)
        }
        val chapter01: ChapterTranslation = mockk(relaxed = true) {
            every { isTitleFinished }.returns(true)
        }
        val chapter02: ChapterTranslation = mockk(relaxed = true)

        mockChapters(bookChapter, chapter01, chapter02)

        val chunk0101: FrameTranslation = mockk(relaxed = true) {
            every { isFinished }.returns(true)
        }
        val chunk0104: FrameTranslation = mockk(relaxed = true) {
            every { isFinished }.returns(true)
        }
        val chunk0201: FrameTranslation = mockk(relaxed = true) {
            every { isFinished }.returns(true)
        }
        val chunk0203: FrameTranslation = mockk(relaxed = true) {
            every { isFinished }.returns(true)
        }

        mockChunks(chunk0101, chunk0104, chunk0201, chunk0203)

        val items = ValidateProject(
            context,
            library,
            translator
        ).execute(
            targetTranslationId,
            sourceTranslationId
        )

        assertEquals(1, items.size)
        assertTrue(items.first().isRange)
        assertTrue(items.first().isValid)

        verifyCommonStuff()
        verifyChapterAndChunks()
    }

    @Test
    fun `test validate project, invalid project title`() {
        mockTranslation()

        val bookChapter: ChapterTranslation = mockk(relaxed = true) {
            every { isTitleFinished }.returns(false)
        }
        val chapter01: ChapterTranslation = mockk(relaxed = true) {
            every { isTitleFinished }.returns(true)
        }
        val chapter02: ChapterTranslation = mockk(relaxed = true)

        mockChapters(bookChapter, chapter01, chapter02)

        val chunk0101: FrameTranslation = mockk(relaxed = true) {
            every { isFinished }.returns(true)
        }
        val chunk0104: FrameTranslation = mockk(relaxed = true) {
            every { isFinished }.returns(true)
        }
        val chunk0201: FrameTranslation = mockk(relaxed = true) {
            every { isFinished }.returns(true)
        }
        val chunk0203: FrameTranslation = mockk(relaxed = true) {
            every { isFinished }.returns(true)
        }

        mockChunks(chunk0101, chunk0104, chunk0201, chunk0203)

        val items = ValidateProject(
            context,
            library,
            translator
        ).execute(
            targetTranslationId,
            sourceTranslationId
        )

        assertEquals(3, items.size)
        assertEquals("Has warnings", items.first().title)
        assertEquals("front", items[1].chapterId)
        assertFalse(items[1].isValid)
        assertFalse(items[1].isRange)
        assertTrue(items[2].isRange)
        assertTrue(items[2].isValid)

        verifyCommonStuff()
        verifyChapterAndChunks()
    }

    @Test
    fun `test validate project, invalid chapter title`() {
        mockTranslation()

        val bookChapter: ChapterTranslation = mockk(relaxed = true) {
            every { isTitleFinished }.returns(true)
        }
        val chapter01: ChapterTranslation = mockk(relaxed = true) {
            every { isTitleFinished }.returns(false)
        }
        val chapter02: ChapterTranslation = mockk(relaxed = true)

        mockChapters(bookChapter, chapter01, chapter02)

        val chunk0101: FrameTranslation = mockk(relaxed = true) {
            every { isFinished }.returns(true)
        }
        val chunk0104: FrameTranslation = mockk(relaxed = true) {
            every { isFinished }.returns(true)
        }
        val chunk0201: FrameTranslation = mockk(relaxed = true) {
            every { isFinished }.returns(true)
        }
        val chunk0203: FrameTranslation = mockk(relaxed = true) {
            every { isFinished }.returns(true)
        }

        mockChunks(chunk0101, chunk0104, chunk0201, chunk0203)

        val items = ValidateProject(
            context,
            library,
            translator
        ).execute(
            targetTranslationId,
            sourceTranslationId
        )

        assertEquals(4, items.size)
        assertTrue(items.first().isValid)
        assertEquals("Book of Mark front", items.first().title)
        assertEquals("Has warnings", items[1].title)
        assertEquals("01", items[2].chapterId)
        assertEquals("Chapter 1 - Title", items[2].title)
        assertFalse(items[2].isValid)
        assertFalse(items[2].isRange)
        assertTrue(items[3].isRange)
        assertTrue(items[3].isValid)

        verifyCommonStuff()
        verifyChapterAndChunks()
    }

    @Test
    fun `test validate project, invalid first chapter chunk`() {
        mockTranslation()

        val bookChapter: ChapterTranslation = mockk(relaxed = true) {
            every { isTitleFinished }.returns(true)
        }
        val chapter01: ChapterTranslation = mockk(relaxed = true) {
            every { isTitleFinished }.returns(true)
        }
        val chapter02: ChapterTranslation = mockk(relaxed = true)

        mockChapters(bookChapter, chapter01, chapter02)

        val chunk0101: FrameTranslation = mockk(relaxed = true) {
            every { isFinished }.returns(false)
        }
        val chunk0104: FrameTranslation = mockk(relaxed = true) {
            every { isFinished }.returns(true)
        }
        val chunk0201: FrameTranslation = mockk(relaxed = true) {
            every { isFinished }.returns(true)
        }
        val chunk0203: FrameTranslation = mockk(relaxed = true) {
            every { isFinished }.returns(true)
        }

        mockChunks(chunk0101, chunk0104, chunk0201, chunk0203)

        val items = ValidateProject(
            context,
            library,
            translator
        ).execute(
            targetTranslationId,
            sourceTranslationId
        )

        assertEquals(3, items.size)
        assertTrue(items.first().isValid)
        assertEquals("Book of Mark front", items.first().title)
        assertEquals("Has warnings", items[1].title)
        assertEquals("01", items[2].chapterId)
        assertEquals("01", items[2].frameId)
        assertEquals("Book of Mark 1:", items[2].title)
        assertFalse(items[2].isValid)
        assertFalse(items[2].isRange)

        verifyCommonStuff()
        verifyChapterAndChunks()
    }

    @Test
    fun `test validate project, invalid second chapter chunk`() {
        mockTranslation()

        val bookChapter: ChapterTranslation = mockk(relaxed = true) {
            every { isTitleFinished }.returns(true)
        }
        val chapter01: ChapterTranslation = mockk(relaxed = true) {
            every { isTitleFinished }.returns(true)
        }
        val chapter02: ChapterTranslation = mockk(relaxed = true)

        mockChapters(bookChapter, chapter01, chapter02)

        val chunk0101: FrameTranslation = mockk(relaxed = true) {
            every { isFinished }.returns(true)
        }
        val chunk0104: FrameTranslation = mockk(relaxed = true) {
            every { isFinished }.returns(true)
        }
        val chunk0201: FrameTranslation = mockk(relaxed = true) {
            every { isFinished }.returns(true)
        }
        val chunk0203: FrameTranslation = mockk(relaxed = true) {
            every { isFinished }.returns(false)
        }

        mockChunks(chunk0101, chunk0104, chunk0201, chunk0203)

        val items = ValidateProject(
            context,
            library,
            translator
        ).execute(
            targetTranslationId,
            sourceTranslationId
        )

        assertEquals(4, items.size)
        assertTrue(items.first().isValid)
        assertEquals("Book of Mark front-1", items.first().title)
        assertEquals("Has warnings", items[1].title)
        assertEquals("Book of Mark 2:", items[2].title)
        assertTrue(items[2].isValid)
        assertFalse(items[2].isRange)
        assertEquals("02", items[3].chapterId)
        assertEquals("03", items[3].frameId)
        assertEquals("Book of Mark 2:", items[3].title)
        assertFalse(items[3].isValid)
        assertFalse(items[3].isRange)

        verifyCommonStuff()
        verifyChapterAndChunks()
    }

    @Test
    fun `test validate project, target translation not found`() {
        every { translator.getTargetTranslation(targetTranslationId) }.returns(null)

        val items = ValidateProject(
            context,
            library,
            translator
        ).execute(
            targetTranslationId,
            sourceTranslationId
        )

        assertEquals(0, items.size)

        verify { translator.getTargetTranslation(targetTranslationId) }
        // should not be called if there is not target translation
        verify(inverse = true) { index.getTargetLanguage(any()) }
    }

    @Test
    fun `test validate project, source translation not found`() {
        every { library.open(sourceTranslationId) }.throws(Exception("Not found."))

        val items = ValidateProject(
            context,
            library,
            translator
        ).execute(
            targetTranslationId,
            sourceTranslationId
        )

        assertEquals(0, items.size)

        verify { translator.getTargetTranslation(targetTranslationId) }
        verify { index.getTargetLanguage(any()) }
        verify { library.open(sourceTranslationId) }
        // should not be called if there is not source translation
        verify(inverse = true) { sourceContainer.info.getString(any()) }
    }

    @Test
    fun `test validate project, failed to parse format`() {
        every { sourceContainer.info.getString(any()) }.throws(JSONException("Bad format."))

        val items = ValidateProject(
            context,
            library,
            translator
        ).execute(
            targetTranslationId,
            sourceTranslationId
        )

        assertEquals(0, items.size)

        verify { translator.getTargetTranslation(targetTranslationId) }
        verify { index.getTargetLanguage(any()) }
        verify { library.open(sourceTranslationId) }
        verify { sourceContainer.info.getString(any()) }
        // should not be called if failed to parse format
        verify(inverse = true) { sourceContainer.readChunk("front", "title") }
    }

    private fun mockTranslation() {
        every { sourceContainer.readChunk(any(), any()) }.answers {
            val chapterSlug = firstArg<String>()
            val chunkSlug = secondArg<String>()
            when {
                chapterSlug == "front" -> "Book of Mark"
                chunkSlug == "title" -> "Chapter 1"
                chapterSlug == "01" && chunkSlug == "01" -> "Mark 1:1-3"
                chapterSlug == "01" && chunkSlug == "04" -> "Mark 1:4-6"
                chapterSlug == "02" && chunkSlug == "01" -> "Mark 2:1-2"
                chapterSlug == "02" && chunkSlug == "03" -> "Mark 2:3-5"
                else -> ""
            }
        }
        every { sourceContainer.chapters() }.returns(arrayOf("front", "01", "02"))
        every { sourceContainer.chunks(any()) }.answers {
            val chapterSlug = firstArg<String>()
            when (chapterSlug) {
                "front" -> arrayOf("title")
                "01" -> arrayOf("title", "01", "04")
                "02" -> arrayOf("01", "03")
                else -> arrayOf()
            }
        }
    }

    private fun mockChapters(vararg chapters: ChapterTranslation) {
        every { targetTranslation.getChapterTranslation(any()) }.answers {
            val chapterSlug = firstArg<String>()
            when (chapterSlug) {
                "front" -> chapters.getOrNull(0)
                "01" -> chapters.getOrNull(1)
                "02" -> chapters.getOrNull(2)
                else -> mockk()
            }
        }
    }

    private fun mockChunks(vararg chunks: FrameTranslation) {
        every { targetTranslation.getFrameTranslation(any(), any(), any()) }.answers {
            val chapterSlug = firstArg<String>()
            val chunkSlug = secondArg<String>()
            when {
                chapterSlug == "01" && chunkSlug == "01" -> chunks.getOrNull(0)
                chapterSlug == "01" && chunkSlug == "04" -> chunks.getOrNull(1)
                chapterSlug == "02" && chunkSlug == "01" -> chunks.getOrNull(2)
                chapterSlug == "02" && chunkSlug == "03" -> chunks.getOrNull(3)
                else -> mockk()
            }
        }
    }

    private fun verifyChapterAndChunks() {
        verify { targetTranslation.getChapterTranslation("front") }
        verify { targetTranslation.getChapterTranslation("01") }
        verify { targetTranslation.getChapterTranslation("02") }

        verify { targetTranslation.getFrameTranslation("01", "01", any()) }
        verify { targetTranslation.getFrameTranslation("01", "04", any()) }
        verify { targetTranslation.getFrameTranslation("02", "01", any()) }
        verify { targetTranslation.getFrameTranslation("02", "03", any()) }
    }

    private fun verifyCommonStuff() {
        verify { translator.getTargetTranslation(any()) }
        verify { index.getTargetLanguage(any()) }
        verify { library.open(any()) }
        verify { sourceContainer.chapters() }
        verify { sourceContainer.chunks(any()) }
        verify { sourceContainer.info.getString(any()) }
        verify { sourceContainer.readChunk(any(), any()) }
        verify { index.getSourceLanguage(any()) }
        verify { MergeConflictsHandler.isMergeConflicted(any()) }
    }
}