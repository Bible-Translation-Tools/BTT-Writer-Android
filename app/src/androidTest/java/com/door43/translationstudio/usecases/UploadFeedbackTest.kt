package com.door43.translationstudio.usecases

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.IntegrationTest
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.Reporter
import com.door43.usecases.UploadFeedback
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.spyk
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.unfoldingword.tools.logger.LogLevel
import org.unfoldingword.tools.logger.Logger
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@IntegrationTest
class UploadFeedbackTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var prefRepository: IPreferenceRepository
    @Inject lateinit var profile: Profile

    private val server = MockWebServer()

    private lateinit var uploadFeedback: UploadFeedback

    @Before
    fun setUp() {
        hiltRule.inject()
        server.start()

        Logger.configure(directoryProvider.logFile, LogLevel.getLevel(0))

        val prefRepoMock = spyk(prefRepository)
        every { prefRepoMock.helpdeskWebhookUrl } answers { server.url("/").toString() }

        val reporter = Reporter(context, directoryProvider, prefRepoMock, profile)
        uploadFeedback = UploadFeedback(directoryProvider, reporter)
    }

    @After
    fun tearDown() {
        server.shutdown()
        directoryProvider.clearCache()
    }

    @Test
    fun testUploadFeedback() {
        server.enqueue(MockResponse().setBody("{success: true}").setResponseCode(200))

        Logger.i("UploadFeedbackTest", "This is an info log.")
        Logger.w("UploadFeedbackTest", "This is a warning log.")
        Logger.e("UploadFeedbackTest", "This is an error log.")

        assertTrue("Log file should not be empty", directoryProvider.logFile.length() > 0)

        val notes = "This is a test note"
        val uploaded = uploadFeedback.execute(notes, "tester@example.com")

        assertTrue("Feedback should be uploaded", uploaded)

        val body = server.takeRequest().body.readString(Charsets.UTF_8)

        assertTrue("Body contains notes", body.contains(notes))
        assertTrue("Body contains info log", body.contains("This is an info log."))
        assertTrue("Body contains warning log", body.contains("This is a warning log."))
        assertTrue("Body contains error log", body.contains("This is an error log."))
        assertTrue("Body contains environment section", body.contains("## Environment"))
        assertTrue("Body contains sender email", body.contains("tester@example.com"))

        assertFalse(
            "Log cleared after successful upload",
            directoryProvider.logFile.readText().contains("This is an error log.")
        )
        assertTrue(
            "Submission logged",
            directoryProvider.logFile.readText().contains("Submitted bug report")
        )
    }

    @Test
    fun testUploadFailsOnServerError() {
        server.enqueue(MockResponse().setResponseCode(500))

        Logger.i("UploadFeedbackTest", "This is an info log.")

        assertTrue("Log file should not be empty", directoryProvider.logFile.length() > 0)

        val uploaded = uploadFeedback.execute("This is a test note", "")

        assertFalse("Upload should fail on 500", uploaded)
        assertTrue("Log preserved on failure", directoryProvider.logFile.length() > 0)
    }
}
