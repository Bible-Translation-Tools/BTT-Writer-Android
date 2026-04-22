package com.door43.translationstudio.usecases

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.OnProgressListener
import com.door43.data.IPreferenceRepository
import com.door43.data.setDefaultPref
import com.door43.translationstudio.IntegrationTest
import com.door43.translationstudio.KoinAndroidTest
import com.door43.usecases.AdvancedGogsRepoSearch
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.inject


@RunWith(AndroidJUnit4::class)
@IntegrationTest
class AdvancedGogsRepoSearchTest : KoinAndroidTest() {

    private val advancedGogsRepoSearch: AdvancedGogsRepoSearch by inject()
    private val prefRepository: IPreferenceRepository by inject()

    private val server = MockWebServer()

    @Before
    fun setUp() {
        server.start()

        prefRepository.setDefaultPref(
            IPreferenceRepository.KEY_PREF_GOGS_API,
            server.url("/search").toString()
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun searchReposByUser() = runTest {
        val user = "test"

        server.enqueue(createUsersResponse())
        server.enqueue(createReposResponse())
        server.enqueue(createRepoResponse())

        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, message ->
            progressMessage = message
        }
        val repos = advancedGogsRepoSearch.execute(user, "", 5, progressListener)

        assertTrue(repos.isNotEmpty())
        assertFalse(progressMessage.isNullOrEmpty())
    }

    @Test
    fun searchReposByRepoName() = runTest {
        val repo = "_gen_"

        server.enqueue(createReposResponse())
        server.enqueue(createRepoResponse())

        val repos = advancedGogsRepoSearch.execute("", repo, 5)
        assertTrue(repos.isNotEmpty())
    }

    @Test
    fun searchReposByUserAndRepoName() = runTest {
        val user = "mxaln"
        val repo = "_gen_"

        server.enqueue(createUsersResponse())
        server.enqueue(createReposResponse())
        server.enqueue(createRepoResponse())

        val repos = advancedGogsRepoSearch.execute(user, repo, 5)
        assertTrue(repos.isNotEmpty())
    }

    @Test
    fun searchNonExistentUser() = runTest {
        val user = "non-existent-user"

        server.enqueue(createEmptyDataResponse())

        val repos = advancedGogsRepoSearch.execute(user, "", 5)
        assertTrue(repos.isEmpty())
    }

    @Test
    fun searchNonExistentRepo() = runTest {
        val repo = "non-existent-repo"

        server.enqueue(createEmptyDataResponse())

        val repos = advancedGogsRepoSearch.execute("", repo, 5)
        assertTrue(repos.isEmpty())
    }

    private fun createRepoResponse(): MockResponse {
        val body = """
            {
                "id": 111,
                "name": "fr_gen_text_reg",
                "html_url": "http://example.com/fr_gen_text_reg",
                "clone_url": "http://example.com/fr_gen_text_reg.git",
                "ssh_url": "ssh://example.com/fr_gen_text_reg.git",
                "isPrivate": false
            }
        """.trimIndent()

        return MockResponse().setBody(body).setResponseCode(200)
    }

    private fun createReposResponse(): MockResponse {
        val body = """
            {
                data: [
                    {
                        "id": 111,
                        "name": "fr_gen_text_reg",
                        "isPrivate": false
                    }
                ],
                "ok": true
            }
        """.trimIndent()

        return MockResponse().setBody(body).setResponseCode(200)
    }

    private fun createUsersResponse(): MockResponse {
        val body = """
            {
                data: [
                    {
                        "id": 111,
                        "full_name": "Test",
                        "email": "test@noreply.example.org",
                        "username": "test"
                    }
                ],
                "ok": true
            }
        """.trimIndent()

        return MockResponse().setBody(body).setResponseCode(200)
    }

    private fun createEmptyDataResponse(): MockResponse {
        val body = """
            {
                data: [],
                "ok": true
            }
        """.trimIndent()

        return MockResponse().setBody(body).setResponseCode(200)
    }
}
