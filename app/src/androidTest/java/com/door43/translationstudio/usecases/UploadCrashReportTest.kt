package com.door43.translationstudio.usecases

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.IntegrationTest
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.Reporter
import com.door43.usecases.UploadCrashReport
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
import org.unfoldingword.tools.logger.Logger
import java.io.File
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@IntegrationTest
class UploadCrashReportTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var prefRepository: IPreferenceRepository
    @Inject lateinit var profile: Profile

    private val server = MockWebServer()

    private lateinit var uploadCrashReport: UploadCrashReport
    private lateinit var crashDir: File

    @Before
    fun setUp() {
        hiltRule.inject()
        server.start()

        crashDir = directoryProvider.createTempDir("crashes")
        Logger.registerGlobalExceptionHandler(crashDir)

        val prefRepoMock = spyk(prefRepository)
        every { prefRepoMock.helpdeskWebhookUrl } answers { server.url("/").toString() }

        val reporter = Reporter(context, directoryProvider, prefRepoMock, profile)
        uploadCrashReport = UploadCrashReport(reporter)
    }

    @After
    fun tearDown() {
        server.shutdown()
        directoryProvider.clearCache()
    }

    @Test
    fun testUploadCrashReport() {
        server.enqueue(MockResponse().setBody("{success: true}").setResponseCode(200))

        val crash = directoryProvider.createTempFile("crash", ".stacktrace", crashDir)
        crash.writeText("NullPointerException at MainActivity.kt:99")

        val message = "Test crash report"
        val reported = uploadCrashReport.execute(message, "tester@example.com")

        assertTrue("Upload success when response code 200", reported)

        val body = server.takeRequest().body.readString(Charsets.UTF_8)

        assertTrue("Body contains message", body.contains(message))
        assertTrue("Body contains stack trace section", body.contains("## Stack Trace"))
        assertTrue("Body contains stacktrace content", body.contains("NullPointerException at MainActivity.kt:99"))
        assertTrue("Body contains environment section", body.contains("## Environment"))
        assertTrue("Body contains log section", body.contains("## Recent Log"))
        assertTrue("Body contains sender email", body.contains("tester@example.com"))

        assertTrue("Crash dir empty after successful upload", crashDir.listFiles()?.isEmpty() ?: true)
    }

    @Test
    fun testUploadCrashReportFailsWhenNoCrashes() {
        Logger.flush()

        server.enqueue(MockResponse().setBody("{success: true}").setResponseCode(200))

        val reported = uploadCrashReport.execute("Test crash report", "")

        assertFalse("Upload fails when no crash files", reported)
    }

    @Test
    fun testUploadCrashServerDown() {
        server.enqueue(MockResponse().setResponseCode(500))

        val crash = directoryProvider.createTempFile("crash", ".stacktrace", crashDir)
        crash.writeText("NullPointerException at MainActivity.kt:99")

        val reported = uploadCrashReport.execute("Test crash report", "")

        assertFalse("Upload fails when response code 500", reported)
        assertTrue("Crash files preserved on failure", crashDir.listFiles()?.isNotEmpty() ?: false)
    }
}
