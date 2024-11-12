package com.door43.translationstudio.usecases

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.OnProgressListener
import com.door43.data.IPreferenceRepository
import com.door43.data.setDefaultPref
import com.door43.translationstudio.ui.SettingsActivity
import com.door43.usecases.SearchGogsRepositories
import com.door43.usecases.SearchGogsUsers
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SearchGogsRepositoriesTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject lateinit var prefRepository: IPreferenceRepository
    @Inject lateinit var searchGogsRepositories: SearchGogsRepositories
    @Inject lateinit var searchGogsUsers: SearchGogsUsers

    private val server = MockWebServer()

    @Before
    fun setUp() {
        hiltRule.inject()

        prefRepository.setDefaultPref(
            SettingsActivity.KEY_PREF_GOGS_API,
            server.url("/search").toString()
        )
    }

    @Test
    fun searchReposByUser() {
        val userResponse = """
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
        server.enqueue(MockResponse().setBody(userResponse).setResponseCode(200))

        val user = "test"
        val gogsUser = searchGogsUsers.execute(user, 1).singleOrNull()

        assertNotNull("Gogs user should not be null", gogsUser)
        assertEquals("Gogs user id should match", gogsUser?.id, 222)
        assertEquals("Gogs username should match", gogsUser?.username, user)

        val owner = gogsUser?.toJSON()?.toString()?.let { "owner: $it" } ?: ""
        val repoResponse = """
            {
                "id": 222,
                "name": "test_repo",
                "html_url": "http://example.com/test_repo",
                "clone_url": "http://example.com/test_repo.git",
                "ssh_url": "ssh://example.com/test_repo.git",
                "private": false,
                $owner
            }
        """.trimIndent()

        val reposResponse = """
            {
                "data": [$repoResponse],
                "ok": true
            }
        """.trimIndent()

        server.enqueue(MockResponse().setBody(reposResponse).setResponseCode(200))
        server.enqueue(MockResponse().setBody(repoResponse).setResponseCode(200))

        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }
        val repos = searchGogsRepositories.execute(gogsUser!!.id, "", 3, progressListener)

        assertTrue("Repos should not be empty", repos.isNotEmpty())
        assertFalse("Progress message should not be empty", progressMessage.isNullOrEmpty())

        val repo = repos.first()
        assertEquals("Repo name should match","test_repo", repo.name)
        assertEquals("Repo htmlUrl should match","http://example.com/test_repo", repo.htmlUrl)
        assertEquals("Repo cloneUrl should match","http://example.com/test_repo.git", repo.cloneUrl)
        assertEquals("Repo sshUrl should match","ssh://example.com/test_repo.git", repo.sshUrl)
        assertFalse("Repo should not be private", repo.isPrivate)
        assertEquals("Repo owner should match", gogsUser.username, repo.owner.username)
    }

    @Test
    fun searchReposByRepoName() {
        val repo1Response = """
            {
                "id": 222,
                "name": "aa_gen_text_reg",
                "html_url": "http://example.com/aa_gen_text_reg",
                "clone_url": "http://example.com/aa_gen_text_reg.git",
                "ssh_url": "ssh://example.com/aa_gen_text_reg.git",
                "isPrivate": false
            }
        """.trimIndent()

        val repo2Response = """
            {
                "id": 222,
                "name": "fr_gen_text_reg",
                "html_url": "http://example.com/fr_gen_text_reg",
                "clone_url": "http://example.com/fr_gen_text_reg.git",
                "ssh_url": "ssh://example.com/fr_gen_text_reg.git",
                "isPrivate": false
            }
        """.trimIndent()

        val reposResponse = """
            {
                "data": [
                    $repo1Response,
                    $repo2Response
                ],
                "ok": true
            }
        """.trimIndent()

        // There should be 3 request done
        // First request is to fetch repos by query
        // Second and third requests are to fetch additional data for found repos in the first request
        server.enqueue(MockResponse().setBody(reposResponse).setResponseCode(200))
        server.enqueue(MockResponse().setBody(repo1Response).setResponseCode(200))
        server.enqueue(MockResponse().setBody(repo2Response).setResponseCode(200))

        val query = "_gen_"
        val repos = searchGogsRepositories.execute(0, query, 3)
        assertTrue("Repos should not be empty", repos.isNotEmpty())

        assertEquals("Repos size should match", 2, repos.size)
        val genRepos = repos.filter { it.name.contains(query) }
        assertEquals("Gen repos size should match", 2, genRepos.size)
    }

    @Test
    fun searchNonExistentRepo() {
        val reposResponse = """
            {
                "data": [],
                "ok": true
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(reposResponse).setResponseCode(200))

        val query = "non-existent-repo"
        val repos = searchGogsRepositories.execute(0, query, 3)
        assertTrue("Repos should be empty", repos.isEmpty())
    }
}