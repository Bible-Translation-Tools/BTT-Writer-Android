package com.door43.usecases

import android.content.Context
import android.content.res.Resources
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.unfoldingword.tools.http.Request
import org.unfoldingword.tools.logger.GithubReporter
import org.unfoldingword.tools.logger.Logger
import java.io.File
import java.io.IOException

class UploadCrashReportTest {

    @MockK private lateinit var context: Context
    @MockK private lateinit var directoryProvider: IDirectoryProvider
    @MockK private lateinit var prefRepository: IPreferenceRepository
    @MockK private lateinit var resources: Resources

    @JvmField
    @Rule
    var tempDir: TemporaryFolder = TemporaryFolder()

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        every { context.resources }.returns(resources)
        every { context.packageName }.returns("org.example.writer")
        every { resources.getIdentifier(any(), any(), any()) }.returns(1)
        every { resources.getString(1) }.returns("token_stub")

        every { prefRepository.getGithubBugReportRepo() }.returns("/github")

        every { directoryProvider.logFile }.returns(tempDir.newFile("test.log"))

        mockkStatic(Logger::class)
        every { Logger.listStacktraces() }.returns(arrayOf())
        every { Logger.flush() }.just(runs)

        mockkConstructor(GithubReporter::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
        tempDir.delete()
    }

    @Test
    fun `test upload crash report successfully`() {
        val stacktrace = tempDir.newFile("stacktrace.txt")
        every { Logger.listStacktraces() }.returns(arrayOf(stacktrace))

        val request: Request = mockk {
            every { responseCode }.returns(200)
        }
        every { anyConstructed<GithubReporter>().reportCrash(any(), any<File>(), any()) }
            .returns(request)

        val success = UploadCrashReport(
            context,
            directoryProvider,
            prefRepository
        ).execute("test message")

        assertTrue(success)

        verify { prefRepository.getGithubBugReportRepo() }
        verify { anyConstructed<GithubReporter>().reportCrash(any(), any<File>(), any()) }
        verify { Logger.flush() }
    }

    @Test
    fun `test upload crash report failed, server error`() {
        val stacktrace = tempDir.newFile("stacktrace.txt")
        every { Logger.listStacktraces() }.returns(arrayOf(stacktrace))

        val request: Request = mockk {
            every { responseCode }.returns(500)
        }
        every { anyConstructed<GithubReporter>().reportCrash(any(), any<File>(), any()) }
            .returns(request)

        val success = UploadCrashReport(
            context,
            directoryProvider,
            prefRepository
        ).execute("test message")

        assertFalse(success)

        verify { prefRepository.getGithubBugReportRepo() }
        verify { anyConstructed<GithubReporter>().reportCrash(any(), any<File>(), any()) }
        verify(inverse = true) { Logger.flush() }
    }

    @Test
    fun `test upload crash report, no stack traces`() {
        val success = UploadCrashReport(
            context,
            directoryProvider,
            prefRepository
        ).execute("test message")

        assertFalse(success)

        verify { prefRepository.getGithubBugReportRepo() }
        verify(inverse = true) { anyConstructed<GithubReporter>().reportCrash(any(), any<File>(), any()) }
        verify(inverse = true) { Logger.flush() }
    }

    @Test
    fun `test upload crash report throws exception`() {
        val stacktrace = tempDir.newFile("stacktrace.txt")
        every { Logger.listStacktraces() }.returns(arrayOf(stacktrace))

        every { anyConstructed<GithubReporter>().reportCrash(any(), any<File>(), any()) }
            .throws(IOException("An error occurred."))

        val success = UploadCrashReport(
            context,
            directoryProvider,
            prefRepository
        ).execute("test message")

        assertFalse(success)

        verify { prefRepository.getGithubBugReportRepo() }
        verify { anyConstructed<GithubReporter>().reportCrash(any(), any<File>(), any()) }
        verify(inverse = true) { Logger.flush() }
    }

    @Test
    fun `test upload crash report, no github token`() {
        every { resources.getIdentifier(any(), any(), any()) }.returns(0)

        val success = UploadCrashReport(
            context,
            directoryProvider,
            prefRepository
        ).execute("test message")

        assertFalse(success)

        verify { prefRepository.getGithubBugReportRepo() }
        verify(inverse = true) { anyConstructed<GithubReporter>().reportCrash(any(), any<File>(), any()) }
        verify(inverse = true) { Logger.flush() }
    }
}