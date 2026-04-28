package com.door43.translationstudio.core

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.IntegrationTest
import com.door43.translationstudio.Platform
import com.door43.translationstudio.rendering.spannables.USFMVerseSpan
import com.door43.util.FileUtilities
import junit.framework.TestCase.assertFalse
import org.bibletranslationtools.logger.Logger
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.test.KoinTest
import org.koin.test.inject
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.ChunkMarker
import org.unfoldingword.door43client.models.TargetLanguage
import java.io.File
import java.io.IOException
import java.util.regex.Pattern
import kotlin.test.DefaultAsserter.assertEquals
import kotlin.test.DefaultAsserter.assertTrue
import kotlin.test.DefaultAsserter.fail
import kotlin.test.assertNotNull

/**
 * Created by blm on 4/19/16.
 */
@RunWith(AndroidJUnit4::class)
@IntegrationTest
class ImportUsfmTest : KoinTest {

    private val appContext: Context by inject()
    private val library: Door43Client by inject()
    private val directoryProvider: IDirectoryProvider by inject()
    private val profile: Profile by inject()
    private val assetsProvider: AssetsProvider by inject()
    private val platform: Platform by inject()

    private lateinit var expectedBooks: JSONArray
    private lateinit var targetLanguage: TargetLanguage
    private var usfm: ProcessUSFM? = null
    private var chunks: HashMap<String, MutableList<String>> = HashMap()
    private var chapters: Array<String>? = null

    @Before
    fun setUp() {
        expectedBooks = JSONArray()

        Logger.flush()
        targetLanguage = library.index.getTargetLanguage("es")!!
    }

    @After
    fun tearDown() {
        usfm?.cleanup()
    }

    @Test
    @Throws(Exception::class)
    fun test01ValidImportMark() {
        //given
        val source = "mrk.usfm"
        addExpectedBook(source, "mrk", success = true, missingName = false)
        val expectSuccess = true
        val expectNoEmptyChunks = true
        val expectAllVerses = true
        val expectedVerseCount = 678

        usfm = ProcessUSFM.Builder(
            appContext,
            platform,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromRc(targetLanguage, "usfm/$source")
            .build()

        assertNotNull(usfm)

        //when
        val success = usfm!!.isProcessSuccess

        //then
        verifyResults(
            success,
            expectSuccess,
            expectedBooks,
            expectNoEmptyChunks,
            expectAllVerses,
            expectedVerseCount
        )
    }

    @Test
    @Throws(Exception::class)
    fun test02ImportMarkMissingName() {
        //given
        val source = "mrk_no_id.usfm"
        addExpectedBook(source, "", success = false, missingName = true) // not expecting any books to be found
        val expectNoEmptyChunks = true
        val expectSuccess = true
        val expectAllVerses = true
        val expectedVerseCount = 0

        usfm = ProcessUSFM.Builder(
            appContext,
            platform,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromRc(targetLanguage, "usfm/$source")
            .build()

        assertNotNull(usfm)

        //when
        val success = usfm!!.isProcessSuccess

        //then
        verifyResults(
            success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses, expectedVerseCount
        )
    }

    @Test
    @Throws(Exception::class)
    fun test03ImportMarkMissingNameForce() {
        //given
        val source = "mrk_no_id.usfm"
        val useName = "Mrk"
        addExpectedBook(source, useName, success = true, missingName = false)
        val expectSuccess = true
        val expectNoEmptyChunks = true
        val expectAllVerses = true
        val expectedVerseCount = 678
        usfm = ProcessUSFM.Builder(
            appContext,
            platform,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromRc(targetLanguage, "usfm/$source")
            .build()

        val usfmStream = assetsProvider.open("usfm/$source")
        val text = FileUtilities.readStreamToString(usfmStream)

        //when
        val success = usfm!!.processText(text, source, false, useName)

        //then
        verifyResults(
            success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses, expectedVerseCount
        )
    }

    @Test
    @Throws(Exception::class)
    fun test04ValidImportPsalms() {
        //given
        val source = "19-PSA.usfm" // psalms has a verse range
        addExpectedBook(source, "psa", success = true, missingName = false)
        val expectNoEmptyChunks = true
        val expectSuccess = true
        val expectAllVerses = false
        val expectedVerseCount = 2461
        usfm = ProcessUSFM.Builder(
            appContext,
            platform,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromRc(targetLanguage, "usfm/$source")
            .build()

        assertNotNull(usfm)

        //when
        val success = usfm!!.isProcessSuccess

        //then
        verifyResults(
            success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses, expectedVerseCount
        )
    }

    @Test
    @Throws(Exception::class)
    fun test05ImportMarkNoChapters() {
        //given
        val source = "mrk_no_chapter.usfm"
        addExpectedBook(source, "mrk", success = false, missingName = false)
        val expectNoEmptyChunks = true
        val expectSuccess = false
        val expectAllVerses = true
        usfm = ProcessUSFM.Builder(
            appContext,
            platform,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromRc(targetLanguage, "usfm/$source")
            .build()

        assertNotNull(usfm)

        //when
        val success = usfm!!.isProcessSuccess

        //then
        verifyResults(success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses)
    }

    @Test
    @Throws(Exception::class)
    fun test06ImportMarkMissingChapters() {
        //given
        val source = "mrk_one_chapter.usfm"
        addExpectedBook(source, "mrk", success = false, missingName = false)
        val expectSuccess = true
        val expectNoEmptyChunks = false
        val expectAllVerses = true
        val expectedVerseCount = 45
        usfm = ProcessUSFM.Builder(
            appContext,
            platform,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromRc(targetLanguage, "usfm/$source")
            .build()

        assertNotNull(usfm)

        //when
        val success = usfm!!.isProcessSuccess

        //then
        verifyResults(
            success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses, expectedVerseCount
        )
    }

    @Test
    @Throws(Exception::class)
    fun test07ImportMarkNoVerses() {
        //given
        val source = "mrk_no_verses.usfm"
        addExpectedBook(source, "mrk", success = false, missingName = false)
        val expectSuccess = false
        val expectNoEmptyChunks = false
        val expectAllVerses = true
        val expectedVerseCount = 0
        usfm = ProcessUSFM.Builder(
            appContext,
            platform,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromRc(targetLanguage, "usfm/$source")
            .build()

        assertNotNull(usfm)

        //when
        val success = usfm!!.isProcessSuccess

        //then
        verifyResults(
            success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses, expectedVerseCount
        )
    }

    @Test
    @Throws(Exception::class)
    fun test08ImportMarkMissingVerse() {
        //given
        val source = "mrk_missing_verse.usfm"
        addExpectedBook(source, "mrk", success = false, missingName = false)
        val expectSuccess = true
        val expectNoEmptyChunks = true
        val expectAllVerses = false
        val expectedVerseCount = 677
        usfm = ProcessUSFM.Builder(
            appContext,
            platform,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromRc(targetLanguage, "usfm/$source")
            .build()

        assertNotNull(usfm)

        //when
        val success = usfm!!.isProcessSuccess

        //then
        verifyResults(
            success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses, expectedVerseCount
        )
    }

    @Test
    @Throws(Exception::class)
    fun test09ImportMarkEmptyChapter() {
        //given
        val source = "mrk_empty_chapter.usfm"
        addExpectedBook(source, "mrk", success = false, missingName = false)
        val expectSuccess = true
        val expectNoEmptyChunks = false
        val expectAllVerses = true
        val expectedVerseCount = 633
        usfm = ProcessUSFM.Builder(
            appContext,
            platform,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromRc(targetLanguage, "usfm/$source")
            .build()

        assertNotNull(usfm)

        //when
        val success = usfm!!.isProcessSuccess

        //then
        verifyResults(
            success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses, expectedVerseCount
        )
    }

    @Test
    @Throws(Exception::class)
    fun test10ImportJudeNoVerses() {
        //given
        val source = "jude.no_verses.usfm"
        addExpectedBook(source, "jud", success = false, missingName = false)
        val expectSuccess = false
        val expectNoEmptyChunks = true
        val expectAllVerses = true
        val expectedVerseCount = 0
        usfm = ProcessUSFM.Builder(
            appContext,
            platform,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromRc(targetLanguage, "usfm/$source")
            .build()

        assertNotNull(usfm)

        //when
        val success = usfm!!.isProcessSuccess

        //then
        verifyResults(
            success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses, expectedVerseCount
        )
    }

    @Test
    @Throws(Exception::class)
    fun test11ImportJudeNoChapter() {
        //given
        val source = "jude.no_chapter_or_verses.usfm"
        addExpectedBook(source, "jud", success = false, missingName = false)
        val expectSuccess = false
        val expectNoEmptyChunks = true
        val expectAllVerses = true
        val expectedVerseCount = 0
        usfm = ProcessUSFM.Builder(
            appContext,
            platform,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromRc(targetLanguage, "usfm/$source")
            .build()

        assertNotNull(usfm)

        //when
        val success = usfm!!.isProcessSuccess

        //then
        verifyResults(
            success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses, expectedVerseCount
        )
    }

    @Test
    @Throws(Exception::class)
    fun test12ImportPhpNoChapter1() {
        //given
        val source = "php_usfm_NoC1.usfm"
        addExpectedBook(source, "php", success = false, missingName = false)
        val expectSuccess = true
        val expectNoEmptyChunks = false
        val expectAllVerses = true
        val expectedVerseCount = 74
        usfm = ProcessUSFM.Builder(
            appContext,
            platform,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromRc(targetLanguage, "usfm/$source")
            .build()

        assertNotNull(usfm)

        //when
        val success = usfm!!.isProcessSuccess

        //then
        verifyResults(
            success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses, expectedVerseCount
        )
    }

    @Test
    @Throws(Exception::class)
    fun test13ImportPhpNoChapter2() {
        //given
        val source = "php_usfm_NoC2.usfm"
        addExpectedBook(source, "php", success = false, missingName = false)
        val expectSuccess = true
        val expectNoEmptyChunks = false
        val expectAllVerses = true
        val expectedVerseCount = 74
        usfm = ProcessUSFM.Builder(
            appContext,
            platform,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromRc(targetLanguage, "usfm/$source")
            .build()

        assertNotNull(usfm)

        //when
        val success = usfm!!.isProcessSuccess

        //then
        verifyResults(
            success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses, expectedVerseCount
        )
    }

    @Test
    @Throws(Exception::class)
    fun test14ImportPhpChapter3OutOfOrder() {
        //given
        val source = "php_usfm_C3_out_of_order.usfm"
        addExpectedBook(source, "php", success = false, missingName = false)
        val expectSuccess = false
        val expectNoEmptyChunks = true
        val expectAllVerses = true
        usfm = ProcessUSFM.Builder(
            appContext,
            platform,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromRc(targetLanguage, "usfm/$source")
            .build()

        assertNotNull(usfm)

        //when
        val success = usfm!!.isProcessSuccess

        //then
        verifyResults(success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses)
    }

    @Test
    @Throws(Exception::class)
    fun test15ImportPhpMissingLastChapter() {
        //given
        val source = "php_usfm_missing_last_chapter.usfm"
        addExpectedBook(source, "php", success = false, missingName = false)
        val expectSuccess = true
        val expectNoEmptyChunks = false
        val expectAllVerses = true
        val expectedVerseCount = 81
        usfm = ProcessUSFM.Builder(
            appContext,
            platform,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromRc(targetLanguage, "usfm/$source")
            .build()

        assertNotNull(usfm)

        //when
        val success = usfm!!.isProcessSuccess

        //then
        verifyResults(
            success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses, expectedVerseCount
        )
    }

    @Test
    @Throws(Exception::class)
    fun test16ImportPhpNoChapter1Marker() {
        //given
        val source = "php_usfm_NoC1_marker.usfm"
        addExpectedBook(source, "php", success = false, missingName = false)
        val expectSuccess = true
        val expectNoEmptyChunks = true
        val expectAllVerses = true
        val expectedVerseCount = 104
        usfm = ProcessUSFM.Builder(
            appContext,
            platform,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromRc(targetLanguage, "usfm/$source")
            .build()

        assertNotNull(usfm)

        //when
        val success = usfm!!.isProcessSuccess

        //then
        verifyResults(
            success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses, expectedVerseCount
        )
    }

    @Test
    @Throws(Exception::class)
    fun test17ImportPhpNoChapter2Marker() {
        //given
        val source = "php_usfm_NoC2_marker.usfm"
        addExpectedBook(source, "php", success = false, missingName = false)
        val expectSuccess = true
        val expectNoEmptyChunks = false
        val expectAllVerses = true
        val expectedVerseCount = 104
        usfm = ProcessUSFM.Builder(
            appContext,
            platform,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromRc(targetLanguage, "usfm/$source")
            .build()

        assertNotNull(usfm)

        //when
        val success = usfm!!.isProcessSuccess

        //then
        verifyResults(
            success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses, expectedVerseCount
        )
    }

    @Test
    @Throws(Exception::class)
    fun test18ImportPhpMissingLastChapterMarker() {
        //given
        val source = "php_usfm_missing_last_chapter_marker.usfm"
        addExpectedBook(source, "php", success = true, missingName = false)
        val expectSuccess = true
        val expectNoEmptyChunks = false
        val expectAllVerses = true
        val expectedVerseCount = 104
        usfm = ProcessUSFM.Builder(
            appContext,
            platform,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromRc(targetLanguage, "usfm/$source")
            .build()

        assertNotNull(usfm)

        //when
        val success = usfm!!.isProcessSuccess

        //then
        verifyResults(
            success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses, expectedVerseCount
        )
    }

    @Test
    @Throws(Exception::class)
    fun test19ImportJudeOutOfOrderVerses() {
        //given
        val source = "jude.out_order_verses.usfm"
        addExpectedBook(source, "jud", success = false, missingName = false)
        val expectSuccess = true
        val expectNoEmptyChunks = true
        val expectAllVerses = false
        val expectedVerseCount = 25
        usfm = ProcessUSFM.Builder(
            appContext,
            platform,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromRc(targetLanguage, "usfm/$source")
            .build()

        assertNotNull(usfm)

        //when
        val success = usfm!!.isProcessSuccess

        //then
        verifyResults(
            success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses, expectedVerseCount
        )
    }

    @Test
    @Throws(Exception::class)
    fun test20ImportPhpMissingInitialAndFinalVerses() {
        //given
        val source = "php_usfm_missing_initial_and_final_vs.usfm"
        addExpectedBook(source, "php", success = false, missingName = false)
        val expectSuccess = true
        val expectNoEmptyChunks = false
        val expectAllVerses = false
        val expectedVerseCount = 6
        usfm = ProcessUSFM.Builder(
            appContext,
            platform,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromRc(targetLanguage, "usfm/$source")
            .build()

        assertNotNull(usfm)

        //when
        val success = usfm!!.isProcessSuccess

        //then
        verifyResults(
            success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses, expectedVerseCount
        )
    }

    @Throws(JSONException::class)
    fun addExpectedBook(filename: String, book: String, success: Boolean, missingName: Boolean) {
        val expectedBook = JSONObject()
        expectedBook.put("filename", filename)
        expectedBook.put("book", book)
        expectedBook.put("success", success)
        expectedBook.put("missingName", missingName)
        expectedBooks.put(expectedBook)
    }

    @Throws(JSONException::class)
    fun getFileName(obj: JSONObject): String {
        return obj.getString("filename")
    }

    @Throws(JSONException::class)
    fun getBook(obj: JSONObject): String {
        return obj.getString("book")
    }

    @Throws(JSONException::class)
    fun getMissingName(obj: JSONObject): Boolean {
        return obj.getBoolean("missingName")
    }

    @Throws(JSONException::class)
    fun getSuccess(obj: JSONObject): Boolean {
        return obj.getBoolean("success")
    }

    @Throws(JSONException::class)
    fun verifyResults(
        success: Boolean,
        expected: Boolean,
        expectedBooks: JSONArray,
        noEmptyChunks: Boolean,
        expectAllVerses: Boolean,
        expectedVerseCount: Int = -1
    ) {
        val results = usfm!!.resultsString
        assertFalse("results text should not be empty", results.isEmpty())
        assertEquals("results", expected, success)
        assertEquals("results", expected, usfm!!.isProcessSuccess)
        val resultLines = results.split("\n").toTypedArray()

        var missingNamesCount = 0

        for (i in 0 until expectedBooks.length()) {
            val obj = expectedBooks.getJSONObject(i)
            val fileName = getFileName(obj)
            val book = getBook(obj)
            val expectedSuccess = getSuccess(obj)
            val missingName = getMissingName(obj)
            verifyBookResults(
                resultLines, fileName, book, expectedSuccess, noEmptyChunks,
                success, expectAllVerses, expectedVerseCount
            )
            if (missingName) {
                findMissingName(fileName)
                missingNamesCount++
            }
        }
        val missingNameItems = usfm!!.booksMissingNames
        assertEquals("Missing name count should equal", missingNamesCount, missingNameItems.size)
    }

    fun findMissingName(filename: String) {
        val missingNameItems = usfm!!.booksMissingNames
        var found = false
        for (missingNameItem in missingNameItems) {
            if (missingNameItem.description != null && missingNameItem.description.contains(filename)) {
                found = true
                break
            }
        }
        assertTrue("$filename should be missing name ", found)
    }

    /**
     * parse chunk markers (contains verses and chapters) into map of verses indexed by chapter
     *
     * @param chunksList
     * @return
     */
    fun parseChunks(chunksList: List<ChunkMarker>): Boolean {
        this.chunks = HashMap() // clear old map
        return try {
            for (chunkMarker in chunksList) {
                val chapter = chunkMarker.chapter
                val firstVerse = chunkMarker.verse

                val verses = this.chunks.getOrPut(chapter) { ArrayList() }
                verses.add(firstVerse)
            }

            //extract chapters
            val foundChapters = this.chunks.keys.sorted()
            chapters = foundChapters.toTypedArray()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun verifyBookResults(
        results: Array<String>, filename: String, book: String,
        noErrorsExpected: Boolean, noEmptyChunks: Boolean,
        success: Boolean, expectAllVerses: Boolean,
        expectedVerseCount: Int
    ) {
        var bookLine = filename
        if (book.isNotEmpty()) {
            bookLine = "${book.lowercase()} = $filename"
        }
        val foundBookMarker = "Found book: "
        val expectLine = foundBookMarker + bookLine
        var bookFound = false
        var verseCount = 0

        for (i in results.indices) {
            val line = results[i]

            if (line.contains(expectLine)) {
                var noErrorsFound = false

                for (j in i + 1 until results.size) {
                    val resultsLine = results[j]

                    var pos = resultsLine.indexOf(foundBookMarker) // if starting next book, then done
                    if (pos >= 0) {
                        break
                    }

                    pos = resultsLine.indexOf("No errors found")
                    if (pos >= 0) {
                        noErrorsFound = true
                        break
                    }
                }
                assertEquals(
                    "$bookLine found, no errors expected $noErrorsExpected",
                    noErrorsExpected, noErrorsFound
                )
                bookFound = true
                break
            }
        }
        assertTrue("$bookLine not found", bookFound)

        var chunk = ""

        // verify chapters and verses
        if (success && book.isNotEmpty()) {
            val projects = usfm!!.importProjects
            assertTrue(
                "Import Projects count should be greater than zero, but is ${projects.size}",
                projects.isNotEmpty()
            )

            for (project in projects) {
                val chunksList = library.index.getChunkMarkers(
                    book.lowercase(),
                    "en-US"
                )
                assertFalse("chunk count should not be empty", chunksList.isEmpty())
                parseChunks(chunksList)

                for (chapter in chapters!!) {
                    // verify chapter
                    val chapterPath = File(project, getRightChapterLength(chapter))
                    val exists = chapterPath.exists()
                    if (!exists) {
                        fail("Chapter missing $chapterPath")
                    }

                    // verify chunks
                    val chapterFrameSlugs = this.chunks[chapter]!!
                    for (i in chapterFrameSlugs.indices) {
                        val chapterFrameSlug = chapterFrameSlugs[i]
                        var expectCount = -1
                        if (i + 1 < chapterFrameSlugs.size) {
                            val nextSlug = chapterFrameSlugs[i + 1]
                            val nextStart = nextSlug.toInt()
                            if (nextStart > 0) {
                                expectCount = nextStart - chapterFrameSlug.toInt()
                            }
                        }

                        val chunkPath = File(
                            chapterPath,
                            ExportUsfmTest.getRightFileNameLength(chapterFrameSlug) + ".txt"
                        )
                        assertTrue("Chunk missing $chunkPath", chunkPath.exists())
                        try {
                            chunk = FileUtilities.readFileToString(chunkPath)
                            val count = getVerseCount(chunk)
                            verseCount += count
                            if (noEmptyChunks) {
                                val emptyChunk = chunk.isEmpty()
                                assertFalse("Chunk is empty $chunkPath", emptyChunk)
                                assertTrue(
                                    "VerseCount should not be zero: $count in chunk $chunkPath",
                                    count > 0
                                )
                                if (expectCount >= 0 && expectAllVerses) {
                                    assertEquals("Verse Count in chunk $chunkPath", expectCount, count)
                                }
                            }
                        } catch (e: IOException) {
                            e.printStackTrace()
                            fail("Could not read chunk $chunkPath")
                        }
                    }
                }
            }
        }

        if (expectedVerseCount >= 0) {
            assertEquals("Verse counts should match", expectedVerseCount, verseCount)
        }
    }

    /**
     * right size the chapter string.  App expects chapter numbers under 100 to be only two digits.
     *
     * @param chapterN
     * @return
     */
    private fun getRightChapterLength(chapterN: String): String {
        var formattedChapter = chapterN
        val chapterNInt = strToInt(formattedChapter, -1)
        if (chapterNInt in 0..99) {
            formattedChapter = formattedChapter.substring(formattedChapter.length - 2)
        }
        return formattedChapter
    }

    /**
     * get verse count
     */
    private fun getVerseCount(text: String): Int {
        var foundVerseCount = 0
        val matcher = PATTERN_USFM_VERSE_SPAN.matcher(text)
        var currentVerse: Int
        var endVerseRange: Int

        while (matcher.find()) {
            val verse = matcher.group(1) ?: continue
            val verseRange = getVerseRange(verse) ?: break
            currentVerse = verseRange[0]
            endVerseRange = verseRange[1]

            if (endVerseRange > 0) {
                foundVerseCount += (endVerseRange - currentVerse + 1)
            } else {
                foundVerseCount++
            }
        }
        return foundVerseCount
    }

    /**
     * parse verse number to get range
     *
     * @param verse
     * @return
     */
    private fun getVerseRange(verse: String): IntArray? {
        return try {
            val currentVers = verse.toInt()
            intArrayOf(currentVers, 0)
        } catch (e: NumberFormatException) { // might be a range in format 12-13
            val range = verse.split("-")
            if (range.size < 2) {
                null
            } else {
                val currentVerse = range[0].toInt()
                val endVerseRange = range[1].toInt()
                intArrayOf(currentVerse, endVerseRange)
            }
        }
    }

    companion object {
        private val PATTERN_USFM_VERSE_SPAN = Pattern.compile(USFMVerseSpan.PATTERN)

        /**
         * do string to integer with default value on conversion error
         *
         * @param value
         * @param defaultValue
         * @return
         */
        fun strToInt(value: String, defaultValue: Int): Int {
            return try {
                value.toInt()
            } catch (e: Exception) {
                Log.d(ImportUsfmTest::class.java.simpleName, "Cannot convert to int: $value")
                defaultValue
            }
        }
    }
}