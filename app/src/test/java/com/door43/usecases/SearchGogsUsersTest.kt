package com.door43.usecases

import android.content.Context
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.R
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test


class SearchGogsUsersTest {

    @MockK private lateinit var context: Context
    @MockK private lateinit var prefRepository: IPreferenceRepository

    private val onProgress = mockk<(Float, String?) -> Unit>(relaxed = true)

    private val server = MockWebServer()
    private val apiUrl = server.url("/api").toString()

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        every { onProgress(any(), any()) } just runs
        every { prefRepository.getDefaultPref(any(), any(), String::class.java) }
            .returns(apiUrl)
        every { context.getString(R.string.pref_default_gogs_api) }
            .returns(apiUrl)
        every { context.getString(R.string.gogs_user_agent) }.returns("btt-writer-android")
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test search by user query`() = runTest {
        val userQuery = "test"
        val limit = 1

        server.enqueue(createUsersResponse())

        val users = SearchGogsUsers(
            context,
            prefRepository
        ).execute(userQuery, limit, onProgress)

        assertEquals(1, users.size)

        verify { onProgress(any(), "Searching for users") }
    }

    @Test
    fun `test search users with empty query`() = runTest {
        val userQuery = ""
        val limit = 1

        val users = SearchGogsUsers(
            context,
            prefRepository
        ).execute(userQuery, limit, onProgress)

        assertEquals(0, users.size)

        verify { onProgress(any(), "Searching for users") }
    }

    private fun createUsersResponse(): MockResponse {
        val body = """
            {
                "data": [
                    {
                        "id": 222,
                        "full_name": "Test",
                        "email": "test@noreply.example.org",
                        "username": "test"
                    }
                ],
                "ok": true
            }
        """.trimIndent()

        return MockResponse()
            .setBody(body)
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
    }
}