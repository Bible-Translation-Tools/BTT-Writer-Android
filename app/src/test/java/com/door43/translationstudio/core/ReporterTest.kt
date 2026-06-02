package com.door43.translationstudio.core

import android.content.Context
import android.content.res.Resources
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.SettingsActivity
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockkConstructor
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
import org.unfoldingword.gogsclient.User
import org.unfoldingword.tools.http.PostRequestMultipart
import java.io.File

class ReporterTest {

    @MockK private lateinit var context: Context
    @MockK private lateinit var resources: Resources
    @MockK private lateinit var directoryProvider: IDirectoryProvider
    @MockK private lateinit var prefRepository: IPreferenceRepository
    @MockK private lateinit var profile: Profile
    @MockK private lateinit var gogsUser: User

    @JvmField
    @Rule
    var tempDir: TemporaryFolder = TemporaryFolder()

    private lateinit var logFile: File

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        mockkConstructor(PostRequestMultipart::class)

        logFile = tempDir.newFile("test.log")
        logFile.writeText("log line 1\nlog line 2")
        every { directoryProvider.logFile } returns logFile

        every { context.getString(R.string.helpdesk_token) } returns "test-token"
        every { context.getString(R.string.gogs_user_agent) } returns "btt-writer-test"
        every { context.resources } returns resources
        every { resources.getStringArray(R.array.content_server_values_array) } returns arrayOf("prod", "dev")
        every { resources.getStringArray(R.array.content_server_names_array) } returns arrayOf("Production", "Development")

        every { prefRepository.helpdeskWebhookUrl } returns "https://helpdesk.example.com/webhook/"
        every { prefRepository.defaultHelpdeskEmail } returns "feedback@example.com"
        every { prefRepository.getDefaultPref(SettingsActivity.KEY_PREF_CONTENT_SERVER, "") } returns "prod"

        every { profile.gogsUser } returns gogsUser
        every { gogsUser.username } returns "testuser"

        every { anyConstructed<PostRequestMultipart>().userAgent = any() } just runs
        every { anyConstructed<PostRequestMultipart>().addField(any(), any()) } just runs
        every { anyConstructed<PostRequestMultipart>().setTimeout(any()) } just runs
        every { anyConstructed<PostRequestMultipart>().read() } returns ""
        every { anyConstructed<PostRequestMultipart>().responseCode } returns 200
    }

    @After
    fun tearDown() {
        unmockkAll()
        tempDir.delete()
    }

    @Test
    fun `send returns true on 200 response`() {
        assertTrue(makeReporter().send("Test notes", "user@test.com"))
    }

    @Test
    fun `send returns false on 500 response`() {
        every { anyConstructed<PostRequestMultipart>().responseCode } returns 500

        assertFalse(makeReporter().send("Test notes", "user@test.com"))
    }

    @Test
    fun `send returns false on exception`() {
        every { anyConstructed<PostRequestMultipart>().read() } throws Exception("Network error")

        assertFalse(makeReporter().send("Test notes", "user@test.com"))
    }

    @Test
    fun `title uses notes when under 80 chars`() {
        makeReporter().send("Short note", "user@test.com")

        verify { anyConstructed<PostRequestMultipart>().addField("title", "Short note") }
    }

    @Test
    fun `title truncated to 77 chars plus ellipsis when over 80`() {
        val longNote = "A".repeat(100)

        makeReporter().send(longNote, "user@test.com")

        verify { anyConstructed<PostRequestMultipart>().addField("title", "A".repeat(77) + "...") }
    }

    @Test
    fun `title defaults to Bug report when notes blank`() {
        makeReporter().send("", "user@test.com")

        verify { anyConstructed<PostRequestMultipart>().addField("title", "Bug report") }
    }

    @Test
    fun `content includes user notes`() {
        makeReporter().send("My bug notes", "user@test.com")

        verify {
            anyConstructed<PostRequestMultipart>().addField(
                "content",
                match { it.contains("My bug notes") }
            )
        }
    }

    @Test
    fun `content includes environment section`() {
        makeReporter().send("Notes", "user@test.com")

        verify {
            anyConstructed<PostRequestMultipart>().addField(
                "content",
                match { it.contains("## Environment") && it.contains("## Recent Log") }
            )
        }
    }

    @Test
    fun `content includes log tail`() {
        makeReporter().send("Notes", "user@test.com")

        verify {
            anyConstructed<PostRequestMultipart>().addField(
                "content",
                match { it.contains("log line 1") }
            )
        }
    }

    @Test
    fun `sender email uses provided email`() {
        makeReporter().send("Notes", "user@test.com")

        verify { anyConstructed<PostRequestMultipart>().addField("sender[email]", "user@test.com") }
    }

    @Test
    fun `sender email falls back to default when blank`() {
        makeReporter().send("Notes", "")

        verify { anyConstructed<PostRequestMultipart>().addField("sender[email]", "feedback@example.com") }
    }

    @Test
    fun `user agent set on request`() {
        makeReporter().send("Notes", "user@test.com")

        verify { anyConstructed<PostRequestMultipart>().userAgent = "btt-writer-test" }
    }

    // sendCrash tests

    @Test
    fun `sendCrash returns true on 200 response`() {
        val stacktrace = tempDir.newFile("crash.stacktrace").also { it.writeText("NullPointerException at Foo.kt:42") }

        assertTrue(makeReporter().sendCrash("Crashed", "user@test.com", stacktrace))
    }

    @Test
    fun `sendCrash returns false on 500 response`() {
        every { anyConstructed<PostRequestMultipart>().responseCode } returns 500
        val stacktrace = tempDir.newFile("crash.stacktrace")

        assertFalse(makeReporter().sendCrash("Crashed", "", stacktrace))
    }

    @Test
    fun `sendCrash returns false on exception`() {
        every { anyConstructed<PostRequestMultipart>().read() } throws Exception("Network error")

        assertFalse(makeReporter().sendCrash("Crashed", "", null))
    }

    @Test
    fun `sendCrash title defaults to Crash report when message blank`() {
        makeReporter().sendCrash("", "", null)

        verify { anyConstructed<PostRequestMultipart>().addField("title", "Crash report") }
    }

    @Test
    fun `sendCrash content includes stack trace section`() {
        val stacktrace = tempDir.newFile("crash.stacktrace").also { it.writeText("NullPointerException at Foo.kt:42") }

        makeReporter().sendCrash("Crashed", "", stacktrace)

        verify {
            anyConstructed<PostRequestMultipart>().addField(
                "content",
                match { it.contains("## Stack Trace") && it.contains("NullPointerException at Foo.kt:42") }
            )
        }
    }

    @Test
    fun `sendCrash content has no stack trace section when file is null`() {
        makeReporter().sendCrash("Crashed", "", null)

        verify {
            anyConstructed<PostRequestMultipart>().addField(
                "content",
                match { !it.contains("## Stack Trace") }
            )
        }
    }

    @Test
    fun `sendCrash content includes environment and log sections`() {
        makeReporter().sendCrash("Crashed", "", null)

        verify {
            anyConstructed<PostRequestMultipart>().addField(
                "content",
                match { it.contains("## Environment") && it.contains("## Recent Log") }
            )
        }
    }

    @Test
    fun `sendCrash uses provided email as sender`() {
        makeReporter().sendCrash("Crashed", "crasher@test.com", null)

        verify { anyConstructed<PostRequestMultipart>().addField("sender[email]", "crasher@test.com") }
    }

    @Test
    fun `sendCrash falls back to default email when blank`() {
        makeReporter().sendCrash("Crashed", "", null)

        verify { anyConstructed<PostRequestMultipart>().addField("sender[email]", "feedback@example.com") }
    }

    private fun makeReporter() = Reporter(context, directoryProvider, prefRepository, profile)
}
