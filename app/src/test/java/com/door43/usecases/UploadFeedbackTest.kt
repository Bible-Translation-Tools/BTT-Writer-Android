package com.door43.usecases

import android.content.Context
import android.content.res.Resources
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkConstructor
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
import org.unfoldingword.tools.http.Request
import org.unfoldingword.tools.logger.GithubReporter
import org.unfoldingword.tools.logger.Logger
import java.io.File
import java.io.IOException

class UploadFeedbackTest {

    @MockK private lateinit var context: Context
    @MockK private lateinit var prefRepository: IPreferenceRepository
    @MockK private lateinit var resources: Resources
    @MockK private lateinit var directoryProvider: IDirectoryProvider

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

        mockkStatic(Logger::class)
        every { directoryProvider.logFile }.returns(tempDir.newFile("test.log"))

        mockkConstructor(GithubReporter::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
        tempDir.delete()
    }

    @Test
    fun `test upload feedback successfully`() {
        val request: Request = mockk {
            every { responseCode }.returns(200)
        }
        every { anyConstructed<GithubReporter>().reportBug(any(), any<File>()) }
            .returns(request)

        val success = UploadFeedback(
            context,
            prefRepository,
            directoryProvider
        ).execute("Notes")

        assertTrue(success)

        verify { prefRepository.getGithubBugReportRepo() }
        verify { anyConstructed<GithubReporter>().reportBug(any(), any<File>()) }
    }

    @Test
    fun `test upload feedback failed, server error`() {
        val request: Request = mockk {
            every { responseCode }.returns(500)
        }
        every { anyConstructed<GithubReporter>().reportBug(any(), any<File>()) }
            .returns(request)

        val success = UploadFeedback(
            context,
            prefRepository,
            directoryProvider
        ).execute("Notes")

        assertFalse(success)

        verify { prefRepository.getGithubBugReportRepo() }
        verify { anyConstructed<GithubReporter>().reportBug(any(), any<File>()) }
    }

    @Test
    fun `test upload feedback throws exception`() {
        every { anyConstructed<GithubReporter>().reportBug(any(), any<File>()) }
            .throws(IOException("An error occurred."))

        val success = UploadFeedback(
            context,
            prefRepository,
            directoryProvider
        ).execute("Notes")

        assertFalse(success)

        verify { prefRepository.getGithubBugReportRepo() }
        verify { anyConstructed<GithubReporter>().reportBug(any(), any<File>()) }
    }

    @Test
    fun `test upload feedback, no github token`() {
        every { resources.getIdentifier(any(), any(), any()) }.returns(0)

        val success = UploadFeedback(
            context,
            prefRepository,
            directoryProvider
        ).execute("Notes")

        assertFalse(success)

        verify { prefRepository.getGithubBugReportRepo() }
        verify(inverse = true) { anyConstructed<GithubReporter>().reportBug(any(), any<File>()) }
    }
}