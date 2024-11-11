package com.door43.translationstudio.usecases

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.usecases.UploadFeedback
import dagger.hilt.android.qualifiers.ApplicationContext
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
class UploadFeedbackTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject @ApplicationContext lateinit var context: Context
    @Inject lateinit var assetsProvider: AssetsProvider
    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var prefRepository: IPreferenceRepository

    private val server = MockWebServer()

    private lateinit var prefRepoMock: IPreferenceRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        server.start()

        Logger.configure(directoryProvider.logFile, LogLevel.getLevel(0))

        prefRepoMock = spyk(prefRepository)
        every {
            prefRepoMock.getGithubBugReportRepo()
        } answers {
            server.url("/issues").toString()
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
        directoryProvider.clearCache()
    }

    @Test
    fun testUploadFeedback() {
        server.enqueue(MockResponse().setBody("{success: true}").setResponseCode(200))

        // create some logs
        Logger.i("UploadFeedbackTest", "This is an info log.")
        Logger.w("UploadFeedbackTest", "This is a warning log.")
        Logger.e("UploadFeedbackTest", "This is an error log.")

        assertTrue(
            "Log file should not be empty",
            directoryProvider.logFile.length() > 0
        )

        val uploadFeedback = UploadFeedback(context, prefRepoMock)
        val notes = "This is a test note"
        val uploaded = uploadFeedback.execute(notes)
        val request = server.takeRequest().body.readString(Charsets.UTF_8)

        assertTrue("Feedback should be uploaded", uploaded)
        assertTrue(
            "Upload request body contains message",
            request.contains(notes)
        )
        assertTrue(
            "Upload request body contains info log",
            request.contains("This is an info log.")
        )
        assertTrue(
            "Upload request body contains warning log",
            request.contains("This is a warning log.")
        )
        assertTrue(
            "Upload request body contains error log",
            request.contains("This is an error log.")
        )
        assertFalse(
            "Log file should not contain old logs after successful upload",
            directoryProvider.logFile.readText().contains("This is an error log.")
        )
        assertTrue(
            "Log file should not contain new log message",
            directoryProvider.logFile.readText().contains("Submitted bug report"))
    }

    @Test
    fun testUploadFailsOnServerDown() {
        server.enqueue(MockResponse().setBody("{success: true}").setResponseCode(500))

        // create some logs
        Logger.i("UploadFeedbackTest", "This is an info log.")
        Logger.w("UploadFeedbackTest", "This is a warning log.")
        Logger.e("UploadFeedbackTest", "This is an error log.")

        assertTrue(
            "Log file should not be empty",
            directoryProvider.logFile.length() > 0
        )

        val uploadFeedback = UploadFeedback(context, prefRepoMock)
        val notes = "This is a test note"
        val uploaded = uploadFeedback.execute(notes)

        assertFalse("Upload should be failed", uploaded)
        assertTrue(
            "Log file should remain after failed upload",
            directoryProvider.logFile.length() > 0
        )
    }
}