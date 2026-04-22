package com.door43.translationstudio.core

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.data.AssetsProvider
import com.door43.translationstudio.IntegrationTest
import com.door43.usecases.ParseMergeConflicts.execute
import com.door43.util.FileUtilities.readStreamToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.test.KoinTest
import org.koin.test.inject
import org.bibletranslationtools.logger.Logger
import java.io.IOException
import java.io.InputStream
import java.util.Collections.emptyList


/**
 * Created by blm on 7/25/16.
 */
@RunWith(AndroidJUnit4::class)
@IntegrationTest
class MergeConflictsParseTest : KoinTest {

    private val assetsProvider: AssetsProvider by inject()

    private lateinit var testText: String
    private lateinit var expectedText: String
    private val expectedTexts = mutableListOf<String>()
    private val parsedText = mutableListOf<String>()
    private var expectedConflictCount: Int = 0
    private var foundConflictCount: Int = 0
    private var lastMergeConflictCards: List<CharSequence> = emptyList()

    val TAG: String = MergeConflictsParseTest::class.java.getSimpleName()

    @Before
    fun setUp() {
        Logger.flush()
    }

    @Test
    @Throws(Exception::class)
    fun test01ProcessFullConflict() {
        //given
        val testId = "merge/full_conflict"
        expectedConflictCount = 2

        //when
        doRenderMergeConflicts(testId)

        //then
        verifyRenderText("test01ProcessFullConflict")
    }

    @Test
    @Throws(Exception::class)
    fun test02ProcessTwoConflict() {
        //given
        val testId = "merge/two_conflict"
        expectedConflictCount = 2

        //when
        doRenderMergeConflicts(testId)

        //then
        verifyRenderText("test02ProcessTwoConflict")
    }

    @Test
    @Throws(Exception::class)
    fun test03ProcessPartialConflict() {
        //given
        val testId = "merge/partial_conflict"
        expectedConflictCount = 2

        //when
        doRenderMergeConflicts(testId)

        //then
        verifyRenderText("test03ProcessPartialConflict")
    }

    @Test
    @Throws(Exception::class)
    fun test04ProcessNestedHeadConflict() {
        //given
        val testId = "merge/head_nested_conflict"
        expectedConflictCount = 3

        //when
        doRenderMergeConflicts(testId)

        //then
        verifyRenderText("test04ProcessNestedHeadConflict")
    }

    @Test
    @Throws(Exception::class)
    fun test05ProcessNestedTailConflict() {
        //given
        val testId = "merge/tail_nested_conflict"
        expectedConflictCount = 3

        //when
        doRenderMergeConflicts(testId)

        //then
        verifyRenderText("test05ProcessNestedTailConflict")
    }

    @Test
    @Throws(Exception::class)
    fun test06ProcessNotFullNestedConflict() {
        //given
        val testId = "merge/not_full_double_nested_conflict"
        expectedConflictCount = 4

        //when
        doRenderMergeConflicts(testId)

        //then
        verifyRenderText("test06ProcessNotFullNestedConflict")
    }

    @Test
    @Throws(Exception::class)
    fun test07ProcessNotFullEndNestedConflict() {
        //given
        val testId = "merge/not_full_double_nested_conflict_end"
        expectedConflictCount = 4

        //when
        doRenderMergeConflicts(testId)

        //then
        verifyRenderText("test07ProcessNotFullEndNestedConflict")
    }

    @Test
    @Throws(Exception::class)
    fun test08ProcessNoConflict() {
        //given
        val testTextFile = "merge/partial_conflict_part1.data"
        val expectTextFile = "merge/partial_conflict_part1.data"
        expectedConflictCount = 1

        //when
        doRenderMergeConflicts(testTextFile, expectTextFile)

        //then
        verifyRenderText("test08ProcessNoConflict")
    }

    @Test
    @Throws(Exception::class)
    fun test09DetectTwoMergeConflict() {
        //given
        val testFile = "merge/two_conflict_raw.data"
        val expectedConflict = true

        //when
        val conflicted = doDetectMergeConflict(testFile)

        //then
        assertEquals(expectedConflict, conflicted)
    }

    @Test
    @Throws(Exception::class)
    fun test10DetectFullMergeConflict() {
        //given
        val testFile = "merge/full_conflict_raw.data"
        val expectedConflict = true

        //when
        val conflicted = doDetectMergeConflict(testFile)

        //then
        assertEquals(expectedConflict, conflicted)
    }

    @Test
    @Throws(Exception::class)
    fun test11DetectPartialMergeConflict() {
        //given
        val testFile = "merge/partial_conflict_raw.data"
        val expectedConflict = true

        //when
        val conflicted = doDetectMergeConflict(testFile)

        //then
        assertEquals(expectedConflict, conflicted)
    }

    @Test
    @Throws(Exception::class)
    fun test12DetectNoMergeConflict() {
        //given
        val testFile = "merge/two_conflict_part1.data"
        val expectedConflict = false

        //when
        val conflicted = doDetectMergeConflict(testFile)

        //then
        assertEquals(expectedConflict, conflicted)
    }

    @Test
    @Throws(Exception::class)
    fun test13ProcessFullFiveWayNestedConflict() {
        //given
        val testId = "merge/full_five_way_nested"
        expectedConflictCount = 5

        //when
        doRenderMergeConflicts(testId)

        //then
        verifyRenderText("test13ProcessFullFiveWayNestedConflict")
    }

    @Test
    @Throws(Exception::class)
    fun test14ProcessNotFullFiveWayNestedConflict() {
        //given
        val testId = "merge/not_full_five_way_nested"
        expectedConflictCount = 5

        //when
        doRenderMergeConflicts(testId)

        //then
        verifyRenderText("test14ProcessNotFullFiveWayNestedConflict")
    }

    @Test
    @Throws(Exception::class)
    fun test15ProcessNotFullFiveWayNestedConflictComplex() {
        //given
        val testId = "merge/not_full_five_way_nested_complex"
        expectedConflictCount = 5

        //when
        doRenderMergeConflicts(testId)

        //then
        verifyRenderText("test15ProcessNotFullFiveWayNestedConflictComplex")
    }

    private fun verifyRenderText(id: String?) {
        assertEquals("merge counts should be the same", expectedTexts.size, parsedText.size)

        //sort to make sure in same order
        parsedText.sort()
        expectedTexts.sort()

        for (i in parsedText.indices) {
            val got: String = parsedText.get(i)!!
            val expected = expectedTexts.get(i)
            verifyProcessedText(id + ": Conflict text " + i, expected, got)
        }
    }

    @Throws(IOException::class)
    private fun doRenderMergeConflicts(testId: String?) {
        for (i in 0..<expectedConflictCount) {
            val text = doRenderMergeConflict(testId, i + 1)
            parsedText.add(text!!)
            expectedTexts.add(expectedText)
        }

        if (expectedConflictCount !== foundConflictCount) {
            for (i in lastMergeConflictCards.indices) {
                val conflict: CharSequence? = lastMergeConflictCards.get(i)
                Log.d(TAG, "conflict card " + i + ":\n" + conflict)
            }
            assertEquals("conflict count", expectedConflictCount, foundConflictCount)
        }
    }

    @Throws(IOException::class)
    private fun doRenderMergeConflicts(testTextFile: String, expectTextFile: String) {
        var text = doRenderMergeConflict(1, testTextFile, expectTextFile)
        parsedText.add(text!!)
        expectedTexts.add(expectedText)

        if (lastMergeConflictCards.size > 1) {
            text = doRenderMergeConflict(2, testTextFile, expectTextFile)
            parsedText.add(text!!)
            expectedTexts.add(expectedText)
        }
    }

    @Throws(IOException::class)
    private fun doDetectMergeConflict(testFile: String): Boolean {
        val testTextStream: InputStream = assetsProvider.open(testFile)
        testText = readStreamToString(testTextStream)
        assertNotNull(testText)
        assertFalse(testText.isEmpty())

        return MergeConflictsHandler.isMergeConflicted(testText)
    }

    @Throws(IOException::class)
    private fun doRenderMergeConflict(testId: String?, sourceGroup: Int): String? {
        val testTextFile = testId + "_raw.data"
        val expectTextFile = testId + "_part" + sourceGroup + ".data"
        return doRenderMergeConflict(sourceGroup, testTextFile, expectTextFile)
    }

    @Throws(IOException::class)
    private fun doRenderMergeConflict(
        sourceGroup: Int,
        testTextFile: String,
        expectTextFile: String
    ): String? {
        val testTextStream: InputStream = assetsProvider.open(testTextFile)
        testText = readStreamToString(testTextStream)
        assertNotNull(testText)
        assertFalse(testText.isEmpty())
        val testExpectedStream: InputStream = assetsProvider.open(expectTextFile)
        expectedText = readStreamToString(testExpectedStream)
        assertNotNull(expectedText)
        assertFalse(expectedText.isEmpty())

        lastMergeConflictCards = execute(testText)
        foundConflictCount = lastMergeConflictCards.size
        if (foundConflictCount >= sourceGroup) {
            return lastMergeConflictCards.get(sourceGroup - 1).toString()
        }
        return null
    }

    private fun verifyProcessedText(id: String?, expectedText: String, out: String) {
        assertNotNull(id, out)
        assertFalse(id, out.isEmpty())
        if (out != expectedText) {
            Log.e(TAG, "error in: " + id)
            if (out.length != expectedText.length) {
                Log.e(
                    TAG,
                    "expected length " + expectedText.length + " but got length " + out.length
                )
            }

            var ptr = 0
            while (true) {
                if (ptr >= out.length) {
                    Log.e(
                        TAG,
                        "expected extra text at position " + ptr + ": '" + expectedText.substring(
                            ptr
                        ) + "'"
                    )
                    if (ptr < expectedText.length) {
                        Log.e(
                            TAG,
                            "character: '" + expectedText.get(ptr) + "', " + Character.codePointAt(
                                expectedText,
                                ptr
                            )
                        )
                    }
                    break
                }
                if (ptr >= expectedText.length) {
                    Log.e(
                        TAG,
                        "not expected extra text at position " + ptr + ": '" + out.substring(ptr) + "'"
                    )
                    Log.e(
                        TAG,
                        "character: '" + out.get(ptr) + "', " + Character.codePointAt(out, ptr)
                    )
                    break
                }

                val cOut = out.get(ptr)
                val cExpect = expectedText.get(ptr)
                if (cOut != cExpect) {
                    Log.e(TAG, "expected different at position " + ptr)
                    Log.e(TAG, "expected sub match: '" + expectedText.substring(ptr) + "'")
                    Log.e(TAG, "but got sub match: '" + out.substring(ptr) + "'")
                    Log.e(
                        TAG,
                        "expected character: '" + expectedText.get(ptr) + "', " + Character.codePointAt(
                            expectedText,
                            ptr
                        )
                    )
                    Log.e(
                        TAG,
                        "but got character: '" + out.get(ptr) + "', " + Character.codePointAt(
                            out,
                            ptr
                        )
                    )
                    Log.e(TAG, "expected: '" + expectedText + "'")
                    Log.e(TAG, "but got: '" + out + "'")
                    break
                }
                ptr++
            }
            Log.e(TAG, "error in: " + id)
        }
        if (out != expectedText) {
            assertEquals(id, out, expectedText)
        }
    }
}