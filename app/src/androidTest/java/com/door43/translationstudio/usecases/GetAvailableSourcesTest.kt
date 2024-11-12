package com.door43.translationstudio.usecases

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.OnProgressListener
import com.door43.usecases.GetAvailableSources
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class GetAvailableSourcesTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject lateinit var getAvailableSources: GetAvailableSources

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun testAvailableResources() {
        val prefixMessage = "test_prefix"
        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        val result = getAvailableSources.execute(prefixMessage, progressListener)

        assertEquals("Prefix message should be equal to progress message", prefixMessage, progressMessage)

        // Test that some gateway languages exist
        assertTrue("English should be available", result.byLanguage.containsKey("en"))
        assertTrue("Spanish should be available", result.byLanguage.containsKey("es-419"))
        assertTrue("French should be available", result.byLanguage.containsKey("fr"))

        assertTrue("English resources should be available", !result.byLanguage["en"].isNullOrEmpty())
        assertTrue("Spanish resources should be available", !result.byLanguage["es-419"].isNullOrEmpty())
        assertTrue("French resources should be available", !result.byLanguage["fr"].isNullOrEmpty())

        // Test that some books exist
        assertTrue("Genesis should be available", result.otBooks.containsKey("gen"))
        assertTrue("Malachi should be available", result.otBooks.containsKey("mal"))
        assertTrue("Matthew should be available", result.ntBooks.containsKey("mat"))
        assertTrue("Revelation should be available", result.ntBooks.containsKey("rev"))

        assertTrue("Genesis resources should be available", !result.otBooks["gen"].isNullOrEmpty())
        assertTrue("Malachi resources should be available", !result.otBooks["mal"].isNullOrEmpty())
        assertTrue("Matthew resources should be available", !result.ntBooks["mat"].isNullOrEmpty())
        assertTrue("Revelation resources should be available", !result.ntBooks["rev"].isNullOrEmpty())

        assertTrue("tW (bible) resources should be available", result.otherBooks.containsKey("bible"))
        assertTrue("tW (bible) resources should be available", !result.otherBooks["bible"].isNullOrEmpty())

        assertEquals("There should be 39 OT books", 39, result.otBooks.size)
        assertEquals("There should be 27 NT books", 27, result.ntBooks.size)
    }
}