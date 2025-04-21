package com.door43.translationstudio.usecases

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.OnProgressListener
import com.door43.data.IPreferenceRepository
import com.door43.data.setDefaultPref
import com.door43.translationstudio.IntegrationTest
import com.door43.translationstudio.ui.SettingsActivity
import com.door43.usecases.SearchGogsUsers
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@IntegrationTest
class SearchGogsUsersTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject lateinit var prefRepository: IPreferenceRepository
    @Inject lateinit var searchGogsUsers: SearchGogsUsers

    private val server = MockWebServer()

    @Before
    fun setUp() {
        hiltRule.inject()
        server.start()

        prefRepository.setDefaultPref(
            SettingsActivity.KEY_PREF_GOGS_API,
            server.url("/search").toString()
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun searchParticularUser() {
        val user = "test"
        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        val successResponse = """
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
        server.enqueue(MockResponse().setBody(successResponse).setResponseCode(200))

        val gogsUser = searchGogsUsers.execute(user, 1, progressListener).singleOrNull()

        assertNotNull("Gogs user should not be null", gogsUser)
        assertEquals("Ids should match", gogsUser?.id, 222)
        assertEquals("Usernames should match", gogsUser?.username, user)
        assertTrue("Progress message should not be empty", !progressMessage.isNullOrEmpty())
    }

    @Test
    fun searchMultipleUsersByQuery() {
        val successResponse = """
            {
                "data": [
                    {
                        "id": 222,
                        "full_name": "Test",
                        "email": "test@noreply.example.org",
                        "username": "test"
                    },
                    {
                        "id": 333,
                        "full_name": "Test 2",
                        "email": "test2@noreply.example.org",
                        "username": "test2"
                    }
                ],
                "ok": true
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(successResponse).setResponseCode(200))

        val userQuery = "test"
        val gogsUsers = searchGogsUsers.execute(userQuery, 3)

        assertTrue("Gogs users should not be empty", gogsUsers.isNotEmpty())

        val expectedUsers = gogsUsers.filter { it.username.contains(userQuery) }
        assertEquals("Number of users should match", expectedUsers.size, gogsUsers.size)
    }

    @Test
    fun searchNonExistentUsers() {
        val successResponse = """
            {
                "data": [],
                "ok": true
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(successResponse).setResponseCode(200))

        val userQuery = "non-existent-user"
        val gogsUsers = searchGogsUsers.execute(userQuery, 3)
        assertTrue("Gogs users should be empty", gogsUsers.isEmpty())
    }
}