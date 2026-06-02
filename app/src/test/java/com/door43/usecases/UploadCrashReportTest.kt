package com.door43.usecases

import com.door43.translationstudio.core.Reporter
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
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
import org.unfoldingword.tools.logger.Logger

class UploadCrashReportTest {

    @MockK private lateinit var reporter: Reporter

    @JvmField
    @Rule
    var tempDir: TemporaryFolder = TemporaryFolder()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic(Logger::class)
        every { Logger.listStacktraces() } returns arrayOf()
        every { Logger.flush() } just runs
    }

    @After
    fun tearDown() {
        unmockkAll()
        tempDir.delete()
    }

    @Test
    fun `upload crash report successfully flushes log and returns true`() {
        val stacktrace = tempDir.newFile("crash.stacktrace")
        every { Logger.listStacktraces() } returns arrayOf(stacktrace)
        every { reporter.sendCrash(any(), any(), any()) } returns true

        val success = UploadCrashReport(reporter).execute("Crash message", "user@test.com")

        assertTrue(success)
        verify { reporter.sendCrash("Crash message", "user@test.com", stacktrace) }
        verify { Logger.flush() }
    }

    @Test
    fun `upload crash report server error returns false and does not flush`() {
        val stacktrace = tempDir.newFile("crash.stacktrace")
        every { Logger.listStacktraces() } returns arrayOf(stacktrace)
        every { reporter.sendCrash(any(), any(), any()) } returns false

        val success = UploadCrashReport(reporter).execute("Crash message", "")

        assertFalse(success)
        verify(inverse = true) { Logger.flush() }
    }

    @Test
    fun `upload crash report with no stacktraces returns false without calling reporter`() {
        every { Logger.listStacktraces() } returns arrayOf()

        val success = UploadCrashReport(reporter).execute("Crash message", "")

        assertFalse(success)
        verify(inverse = true) { reporter.sendCrash(any(), any(), any()) }
        verify(inverse = true) { Logger.flush() }
    }
}
