package com.door43.translationstudio.usecases

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.data.IPreferenceRepository
import com.door43.data.setDefaultPref
import com.door43.translationstudio.IntegrationTest
import com.door43.translationstudio.KoinAndroidTest
import com.door43.translationstudio.Platform
import com.door43.translationstudio.TestUtils
import com.door43.translationstudio.TestUtils.getTokenStub
import com.door43.translationstudio.core.Profile
import com.door43.usecases.GogsLogin
import com.door43.usecases.GogsLogout
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.bibletranslationtools.gogsclient.User
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.inject


@RunWith(AndroidJUnit4::class)
@IntegrationTest
class GogsLoginLogoutTest : KoinAndroidTest() {

    private val appContext: Context by inject()
    private val gogsLogin: GogsLogin by inject()
    private val gogsLogout: GogsLogout by inject()
    private val profile: Profile by inject()
    private val prefRepository: IPreferenceRepository by inject()
    private val platform: Platform by inject()

    private val username = "test"
    private val server = MockWebServer()

    @Before
    fun setUp() {
        server.start()
        prefRepository.setDefaultPref(IPreferenceRepository.KEY_PREF_GOGS_API, server.url("/api/").toString())
    }

    @Test
    fun testGogsLogin() = runTest {
        val user = loginUserWithPassword("Test User")
        assertEquals("Test User", user.fullName)
    }

    @Test
    fun testGogsLoginWithoutFullName() = runTest {
        val user = loginUserWithPassword()
        assertEquals("", user.fullName)
    }

    @Test
    fun testGogsLoginWithWrongCredentials() = runTest {
        val result = gogsLogin.execute(
            "btt-test",
            "incorrect_password"
        )

        assertNull("User should be null", result.user)
    }

    @Test
    fun testGogsLogout() = runTest {
        val userBefore = loginUserWithPassword()
        profile.gogsUser = userBefore

        server.enqueue(MockResponse().setResponseCode(204)) // delete token response

        gogsLogout.execute()

        val userAfter = loginUserWithPassword()

        println(userBefore.token)
        println(userAfter.token)

        assertFalse(
            "Token should be updated after logout",
            userBefore.token == userAfter.token
        )
        assertEquals("User should be the same", userBefore.username, userAfter.username)
    }

    private suspend fun loginUserWithPassword(fullName: String? = null): User {
        server.enqueue(createLoginResponse(fullName))
        server.enqueue(createGetTokenResponse())
        server.enqueue(MockResponse().setResponseCode(204)) // Delete token response
        server.enqueue(createTokenResponse())

        val result = gogsLogin.execute("username", "password", fullName)

        assertNotNull("User should not be null", result.user)
        assertEquals(username, result.user!!.username)
        assertNotNull("Token should not be null", result.user.token)
        assertTrue(
            "Token name should contain build model",
            result.user.token?.name?.contains(platform.udid) == true
        )

        return result.user
    }

    private fun createLoginResponse(fullName: String? = null): MockResponse {
        val body = """
            {"id": 1, "username": "$username", "full_name": "${fullName ?: ""}"}
        """.trimIndent()

        return MockResponse()
            .setBody(body)
            .addHeader("Content-Type", "application/json")
            .setResponseCode(200)
    }

    private fun createGetTokenResponse(): MockResponse {
        val body = """
            [{"id": 1, "name": "${getTokenStub(appContext, platform)}", "sha1": "${TestUtils.generateHash()}"}]
        """.trimIndent()

        return MockResponse()
            .setBody(body)
            .addHeader("Content-Type", "application/json")
            .setResponseCode(200)
    }

    private fun createTokenResponse(): MockResponse {
        val body = """
            {"id": 1, "name": "${getTokenStub(appContext, platform)}", "sha1": "${TestUtils.generateHash()}"}
        """.trimIndent()

        return MockResponse()
            .setBody(body)
            .addHeader("Content-Type", "application/json")
            .setResponseCode(201)
    }
}