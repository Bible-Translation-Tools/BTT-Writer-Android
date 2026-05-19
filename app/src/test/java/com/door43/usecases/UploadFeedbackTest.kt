package com.door43.usecases

import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.core.Reporter
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkStatic
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

class UploadFeedbackTest {

    @MockK private lateinit var directoryProvider: IDirectoryProvider
    @MockK private lateinit var reporter: Reporter

    @JvmField
    @Rule
    var tempDir: TemporaryFolder = TemporaryFolder()

    private lateinit var logFile: java.io.File

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic(Logger::class)

        logFile = tempDir.newFile("test.log")
        every { directoryProvider.logFile } returns logFile
    }

    @After
    fun tearDown() {
        unmockkAll()
        tempDir.delete()
    }

    @Test
    fun `upload feedback successfully clears log and returns true`() {
        every { reporter.send(any(), any()) } returns true
        logFile.writeText("old logs")

        val success = UploadFeedback(directoryProvider, reporter).execute("Notes", "user@test.com")

        assertTrue(success)
        assertTrue("Log cleared after success", logFile.readText().isEmpty())
        verify { reporter.send("Notes", "user@test.com") }
    }

    @Test
    fun `upload feedback server error preserves log and returns false`() {
        every { reporter.send(any(), any()) } returns false
        logFile.writeText("old logs")

        val success = UploadFeedback(directoryProvider, reporter).execute("Notes", "")

        assertFalse(success)
        assertTrue("Log preserved on failure", logFile.readText().isNotEmpty())
        verify { reporter.send("Notes", "") }
    }
}
