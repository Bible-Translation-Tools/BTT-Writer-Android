package com.door43.translationstudio.usecases

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.IntegrationTest
import com.door43.translationstudio.KoinAndroidTest
import com.door43.usecases.UploadCrashReport
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.inject
import org.unfoldingword.tools.logger.Logger
import java.io.File

@RunWith(AndroidJUnit4::class)
@IntegrationTest
class UploadCrashReportTest : KoinAndroidTest() {

    private val context: Context by inject()
    private val directoryProvider: IDirectoryProvider by inject()
    private val prefRepository: IPreferenceRepository by inject()

    private val server = MockWebServer()

    private lateinit var prefRepoMock: IPreferenceRepository
    private lateinit var crashDir: File

    private lateinit var testContext: Context
    private val mockResources = mockk<Resources>()

    @Before
    fun setUp() {
        testContext = object : ContextWrapper(context) {
            override fun getResources(): Resources {
                return mockResources
            }
        }

        crashDir = directoryProvider.createTempDir("crashes")
        Logger.registerGlobalExceptionHandler(crashDir)

        prefRepoMock = spyk(prefRepository)
        every {
            prefRepoMock.getGithubBugReportRepo()
        } answers {
            server.url("/issues").toString()
        }

        every {
            mockResources.getIdentifier("github_oauth2", "string", any())
        } returns 12345
        every { mockResources.getString(12345) } returns "fake_token"

        mockkStatic(Settings.Secure::class)
        every { Settings.Secure.getString(any(), any()) }.returns("test")
    }

    @After
    fun tearDown() {
        directoryProvider.clearCache()
    }

    @Test
    fun testUploadCrashReport() {
        createStackTraces()

        server.enqueue(MockResponse().setBody("{success: true}").setResponseCode(200))

        val message = "Test crash report"
        val uploadCrashReport = UploadCrashReport(testContext, directoryProvider, prefRepoMock)
        val reported = uploadCrashReport.execute(message)

        assertTrue("Upload success when response code 200", reported)

        val request = server.takeRequest().body.readString(Charsets.UTF_8)

        assertTrue(
            "Upload request body contains message",
            request.contains(message)
        )
        assertTrue(
            "Upload request body contains stacktrace",
            request.contains("This is a crash")
        )
        assertTrue(
            "Upload request body contains environment",
            request.contains("Environment")
        )

        assertTrue(
            "Crash dir is empty after successful upload",
            crashDir.listFiles()?.isEmpty() ?: true
        )
    }

    @Test
    fun crashReportFailsWhenNoCrashes() {
        deleteStackTraces()

        server.enqueue(MockResponse().setBody("{success: true}").setResponseCode(200))

        val message = "Test crash report"
        val uploadCrashReport = UploadCrashReport(testContext, directoryProvider, prefRepoMock)
        val reported = uploadCrashReport.execute(message)

        assertFalse("Upload failed when no crash files", reported)
    }

    @Test
    fun testUploadCrashServerDown() {
        createStackTraces()

        server.enqueue(MockResponse().setResponseCode(500))

        val message = "Test crash report"
        val uploadCrashReport = UploadCrashReport(testContext, directoryProvider, prefRepoMock)
        val reported = uploadCrashReport.execute(message)

        assertFalse("Upload fails when response code 500", reported)
    }

    private fun createStackTraces() {
        val crash = directoryProvider.createTempFile("crash", ".stacktrace", crashDir)
        crash.writeText("This is a crash")
    }

    private fun deleteStackTraces() {
        Logger.flush()
    }
}
