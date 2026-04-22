package com.door43.usecases

import kotlinx.coroutines.test.runTest
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
import io.mockk.mockkObject
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
import org.bibletranslationtools.logger.GithubReporter
import org.bibletranslationtools.logger.Logger
import java.io.File
import java.io.IOException
import io.mockk.coEvery
import io.mockk.coVerify

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

        mockkObject(Logger)
        every { Logger.listStacktraces() }.returns(emptyList())
        every { Logger.flush() }.just(runs)

        mockkConstructor(GithubReporter::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
        tempDir.delete()
    }

    @Test
    fun `test upload crash report successfully`() = runTest {
        val stacktrace = tempDir.newFile("stacktrace.txt")
        every { Logger.listStacktraces() }.returns(listOf(stacktrace))

        coEvery { anyConstructed<GithubReporter>().reportCrash(any(), any<File>(), any()) }
            .returns(true)

        val success = UploadCrashReport(
            context,
            directoryProvider,
            prefRepository
        ).execute("test message")

        assertTrue(success)

        verify { prefRepository.getGithubBugReportRepo() }
        coVerify { anyConstructed<GithubReporter>().reportCrash(any(), any<File>(), any()) }
        verify { Logger.flush() }
    }

    @Test
    fun `test upload crash report failed, server error`() = runTest {
        val stacktrace = tempDir.newFile("stacktrace.txt")
        every { Logger.listStacktraces() }.returns(listOf(stacktrace))

        coEvery { anyConstructed<GithubReporter>().reportCrash(any(), any<File>(), any()) }
            .returns(false)

        val success = UploadCrashReport(
            context,
            directoryProvider,
            prefRepository
        ).execute("test message")

        assertFalse(success)

        verify { prefRepository.getGithubBugReportRepo() }
        coVerify { anyConstructed<GithubReporter>().reportCrash(any(), any<File>(), any()) }
        verify(inverse = true) { Logger.flush() }
    }

    @Test
    fun `test upload crash report, no stack traces`() = runTest {
        val success = UploadCrashReport(
            context,
            directoryProvider,
            prefRepository
        ).execute("test message")

        assertFalse(success)

        verify { prefRepository.getGithubBugReportRepo() }
        coVerify(inverse = true) { anyConstructed<GithubReporter>().reportCrash(any(), any<File>(), any()) }
        verify(inverse = true) { Logger.flush() }
    }

    @Test
    fun `test upload crash report throws exception`() = runTest {
        val stacktrace = tempDir.newFile("stacktrace.txt")
        every { Logger.listStacktraces() }.returns(listOf(stacktrace))

        coEvery { anyConstructed<GithubReporter>().reportCrash(any(), any<File>(), any()) }
            .throws(IOException("An error occurred."))

        val success = UploadCrashReport(
            context,
            directoryProvider,
            prefRepository
        ).execute("test message")

        assertFalse(success)

        verify { prefRepository.getGithubBugReportRepo() }
        coVerify { anyConstructed<GithubReporter>().reportCrash(any(), any<File>(), any()) }
        verify(inverse = true) { Logger.flush() }
    }

    @Test
    fun `test upload crash report, no github token`() = runTest {
        every { resources.getIdentifier(any(), any(), any()) }.returns(0)

        val success = UploadCrashReport(
            context,
            directoryProvider,
            prefRepository
        ).execute("test message")

        assertFalse(success)

        verify { prefRepository.getGithubBugReportRepo() }
        coVerify(inverse = true) { anyConstructed<GithubReporter>().reportCrash(any(), any<File>(), any()) }
        verify(inverse = true) { Logger.flush() }
    }
}