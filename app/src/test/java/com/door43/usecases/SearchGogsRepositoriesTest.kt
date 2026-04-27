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
import org.bibletranslationtools.gogsclient.Repository
import org.bibletranslationtools.gogsclient.User
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SearchGogsRepositoriesTest {

    @MockK private lateinit var context: Context
    @MockK private lateinit var prefRepository: IPreferenceRepository

    val onProgress = mockk<(Float, String?) -> Unit>(relaxed = true)

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
    fun `test search with default user`() = runTest {
        val repoQuery = "_gen_"
        val limit = 1

        val repository: Repository = mockk()
        every { repository.name }.returns("aa_gen_text_reg")

        server.enqueue(createReposResponse())
        server.enqueue(createRepoResponse())

        val repositories = SearchGogsRepositories(
            context,
            prefRepository
        ).execute(0, repoQuery, limit, onProgress)

        assertEquals(1, repositories.size)

        verify { onProgress(any(), "Searching for repositories") }
    }

    @Test
    fun `test search with auth user`() = runTest {
        val repoQuery = "_gen_"
        val limit = 1

        val user: User = mockk()
        every { user.id }.returns(1)

        val repository: Repository = mockk()
        every { repository.name }.returns("aa_gen_text_reg")

        server.enqueue(createReposResponse())
        server.enqueue(createRepoResponse())

        val repositories = SearchGogsRepositories(
            context,
            prefRepository
        ).execute(user.id, repoQuery, limit, onProgress)

        assertEquals(1, repositories.size)

        verify { user.id }
        verify { onProgress(any(), "Searching for repositories") }
    }

    @Test
    fun `test search with empty query`() = runTest {
        val repoQuery = ""
        val limit = 1

        val repository: Repository = mockk()
        every { repository.name }.returns("aa_gen_text_reg")

        server.enqueue(createReposResponse())
        server.enqueue(createRepoResponse())

        val repositories = SearchGogsRepositories(
            context,
            prefRepository
        ).execute(0, repoQuery, limit, onProgress)

        assertEquals(1, repositories.size)

        verify { onProgress(any(), "Searching for repositories") }
    }

    private fun createRepoResponse(): MockResponse {
        val body = """
            {
                "id": 222,
                "name": "fr_gen_text_reg",
                "html_url": "http://example.com/fr_gen_text_reg",
                "clone_url": "http://example.com/fr_gen_text_reg.git",
                "ssh_url": "ssh://example.com/fr_gen_text_reg.git",
                "isPrivate": false
            }
        """.trimIndent()
        return MockResponse()
            .setBody(body)
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
    }

    private fun createReposResponse(): MockResponse {
        val body = """
            {
                "data": [
                    {
                        "id": 222,
                        "name": "fr_gen_text_reg",
                        "isPrivate": false
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