package com.door43.translationstudio.core

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.data.AssetsProvider
import com.door43.translationstudio.IntegrationTest
import com.door43.translationstudio.rendering.RenderingGroup
import com.door43.translationstudio.rendering.RenderingProvider
import com.door43.translationstudio.ui.textadapters.ComposeTextAdapter
import com.door43.util.FileUtilities
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.test.KoinTest
import org.koin.test.inject
import org.unfoldingword.tools.logger.Logger
import java.io.IOException

// End-to-end integration tests that verify the full rendering pipeline:
// USX input -> USXRenderer.render() -> ComposeTextAdapter.convert() -> AnnotatedString.text
// Expected output is stored in androidTest/assets/usx/ _processed.data files.
@RunWith(AndroidJUnit4::class)
@IntegrationTest
class UsxBrokenRenderTest : KoinTest {

    private val assetsProvider: AssetsProvider by inject()
    private val renderingProvider: RenderingProvider by inject()

    private var expectedText: String? = null

    @Before
    fun setUp() {
        Logger.flush()
    }

    @Test
    @Throws(Exception::class)
    fun test01ProcessMk_1_1() {
        val out = doRender(search = null, testId = "usx/mk_1_1")
        verifyProcessedText(expectedText!!, out)
    }

    @Test
    @Throws(Exception::class)
    fun test02ProcessMk_7_6() {
        val out = doRender(search = null, testId = "usx/mk_7_6")
        verifyProcessedText(expectedText!!, out)
    }

    @Test
    @Throws(Exception::class)
    fun test03ProcessMk_7_14() {
        val out = doRender(search = null, testId = "usx/mk_7_14")
        verifyProcessedText(expectedText!!, out)
    }

    @Test
    @Throws(Exception::class)
    fun test04ProcessMk_11_24() {
        val out = doRender(search = null, testId = "usx/mk_11_24")
        verifyProcessedText(expectedText!!, out)
    }

    @Test
    @Throws(Exception::class)
    fun test05ProcessMk_16_19() {
        val out = doRender(search = null, testId = "usx/mk_16_19")
        verifyProcessedText(expectedText!!, out)
    }

    @Test
    @Throws(Exception::class)
    fun test06ProcessMk_1_1Search() {
        val out = doRender(search = "</", testId = "usx/mk_1_1")
        verifyProcessedText(expectedText!!, out)
    }

    @Test
    @Throws(Exception::class)
    fun test07ProcessMk_7_6Search() {
        val out = doRender(search = "</", testId = "usx/mk_7_6")
        verifyProcessedText(expectedText!!, out)
    }

    @Test
    @Throws(Exception::class)
    fun test08ProcessMk_7_14Search() {
        val out = doRender(search = "</", testId = "usx/mk_7_14")
        verifyProcessedText(expectedText!!, out)
    }

    @Test
    @Throws(Exception::class)
    fun test09ProcessMk_11_24Search() {
        val out = doRender(search = "</", testId = "usx/mk_11_24")
        verifyProcessedText(expectedText!!, out)
    }

    @Test
    @Throws(Exception::class)
    fun test10ProcessMk_16_19Search() {
        val out = doRender(search = "</", testId = "usx/mk_16_19")
        verifyProcessedText(expectedText!!, out)
    }

    @Throws(IOException::class)
    private fun doRender(search: String?, testId: String): String {
        val testTextFile = testId + "_raw.data"
        val expectTextFile = testId + "_processed.data"
        val testTextStream = assetsProvider.open(testTextFile)
        val testText: String = FileUtilities.readStreamToString(testTextStream)
        Assert.assertNotNull(testText)
        Assert.assertFalse(testText.isEmpty())
        val testExpectedStream = assetsProvider.open(expectTextFile)
        expectedText = FileUtilities.readStreamToString(testExpectedStream)
        Assert.assertNotNull(expectedText)
        Assert.assertFalse(expectedText!!.isEmpty())

        val renderingGroup = RenderingGroup()
        val format = TranslationFormat.USX

        renderingProvider.setupRenderingGroup(format, renderingGroup)

        if (search != null) {
            renderingGroup.setSearchString(search)
        }
        renderingGroup.init(testText)
        val nodes = renderingGroup.start()

        val annotatedString = ComposeTextAdapter.convert(nodes)
        return annotatedString.text
    }

    private fun verifyProcessedText(expectedText: String, out: String?) {
        Assert.assertNotNull(out)
        Assert.assertFalse(out!!.isEmpty())
        if (out != expectedText) {
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
                        "expected extra text at position $ptr: '" + expectedText.substring(ptr) + "'"
                    )
                    if (ptr < expectedText.length) {
                        Log.e(
                            TAG,
                            "character: '" + expectedText[ptr] + "', " + Character.codePointAt(
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
                        "character: '" + out[ptr] + "', " + Character.codePointAt(out, ptr)
                    )
                    break
                }

                val cOut = out[ptr]
                val cExpect = expectedText[ptr]
                if (cOut != cExpect) {
                    Log.e(TAG, "expected different at position $ptr")
                    Log.e(TAG, "expected: '" + expectedText.substring(ptr) + "'")
                    Log.e(TAG, "but got: '" + out.substring(ptr) + "'")
                    Log.e(
                        TAG,
                        "expected character: '" + expectedText[ptr] + "', " + Character.codePointAt(
                            expectedText,
                            ptr
                        )
                    )
                    Log.e(
                        TAG,
                        "but got character: '" + out[ptr] + "', " + Character.codePointAt(out, ptr)
                    )
                    break
                }
                ptr++
            }
        }
        Assert.assertEquals(out, expectedText)
    }

    companion object {
        val TAG: String = UsxBrokenRenderTest::class.java.simpleName
    }
}
