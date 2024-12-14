package com.door43.translationstudio.usecases

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.data.IPreferenceRepository
import com.door43.data.setDefaultPref
import com.door43.translationstudio.App
import com.door43.translationstudio.IntegrationTest
import com.door43.translationstudio.TestUtils
import com.door43.translationstudio.TestUtils.getTokenStub
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.ui.SettingsActivity
import com.door43.usecases.GogsLogin
import com.door43.usecases.GogsLogout
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.unfoldingword.gogsclient.User
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@IntegrationTest
class GogsLoginLogoutTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject @ApplicationContext lateinit var context: Context
    @Inject lateinit var gogsLogin: GogsLogin
    @Inject lateinit var gogsLogout: GogsLogout
    @Inject lateinit var profile: Profile
    @Inject lateinit var prefRepository: IPreferenceRepository

    private val username = "test"
    private val server = MockWebServer()

    @Before
    fun setUp() {
        hiltRule.inject()

        prefRepository.setDefaultPref(
            SettingsActivity.KEY_PREF_GOGS_API,
            server.url("/api").toString()
        )
    }

    @Test
    fun testGogsLogin() {
        val user = loginUserWithPassword("Test User")
        assertEquals("Test User", user.fullName)
    }

    @Test
    fun testGogsLoginWithoutFullName() {
        val user = loginUserWithPassword()
        assertEquals("", user.fullName)
    }

    @Test
    fun testGogsLoginWithWrongCredentials() {
        val result = gogsLogin.execute(
            "btt-test",
            "incorrect_password"
        )

        assertNull("User should be null", result.user)
    }

    @Test
    fun testGogsLogout() {
        val userBefore = loginUserWithPassword()
        profile.gogsUser = userBefore

        server.enqueue(MockResponse().setResponseCode(204)) // delete token response

        gogsLogout.execute()

        val userAfter = loginUserWithPassword()

        assertFalse(
            "Token should be updated after logout",
            userBefore.token == userAfter.token
        )
        assertEquals("User should be the same", userBefore.username, userAfter.username)
    }

    private fun loginUserWithPassword(fullName: String? = null): User {
        server.enqueue(createLoginResponse(fullName))
        server.enqueue(createGetTokenResponse())
        server.enqueue(MockResponse().setResponseCode(204)) // Delete token response
        server.enqueue(createTokenResponse())

        val result = gogsLogin.execute("username", "password", fullName)

        assertNotNull("User should not be null", result.user)
        assertEquals(username, result.user!!.username)
        assertNotNull("Token should not be null", result.user!!.token)
        assertTrue(
            "Token name should contain build model",
            result.user!!.token.name.contains(App.udid())
        )

        return result.user!!
    }

    private fun createLoginResponse(fullName: String? = null): MockResponse {
        val body = """
            {"id": 1, "username": "$username", "full_name": "${fullName ?: ""}"}
        """.trimIndent()

        return MockResponse().setBody(body).setResponseCode(200)
    }

    private fun createGetTokenResponse(): MockResponse {
        val body = """
            [{"id": 1, "name": "${getTokenStub(context)}", "sha1": "${TestUtils.generateHash()}"}]
        """.trimIndent()

        return MockResponse().setBody(body).setResponseCode(200)
    }

    private fun createTokenResponse(): MockResponse {
        val body = """
            {"id": 1, "name": "${getTokenStub(context)}", "sha1": "${TestUtils.generateHash()}"}
        """.trimIndent()

        return MockResponse().setBody(body).setResponseCode(201)
    }
}