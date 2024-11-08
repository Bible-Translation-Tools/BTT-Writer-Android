package com.door43.translationstudio.usecases

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.translationstudio.App
import com.door43.translationstudio.BuildConfig
import com.door43.translationstudio.core.Profile
import com.door43.usecases.GogsLogin
import com.door43.usecases.GogsLogout
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.unfoldingword.gogsclient.User
import org.unfoldingword.tools.logger.Logger
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class GogsLoginLogoutTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject lateinit var gogsLogin: GogsLogin
    @Inject lateinit var gogsLogout: GogsLogout
    @Inject lateinit var profile: Profile

    @Before
    fun setUp() {
        hiltRule.inject()
        Logger.flush()
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

        gogsLogout.execute()

        val userAfter = loginUserWithPassword()

        assertFalse(
            "Token should be updated after logout",
            userBefore.token == userAfter.token
        )
        assertEquals("User should be the same", userBefore.username, userAfter.username)
    }

    private fun loginUserWithPassword(fullName: String? = null): User {
        val result = gogsLogin.execute(
            BuildConfig.TEST_USER,
            BuildConfig.TEST_PASS,
            fullName
        )

        assertNotNull("User should not be null", result.user)
        assertEquals(BuildConfig.TEST_USER, result.user!!.username)
        assertNotNull("Token should not be null", result.user!!.token)
        assertTrue(
            "Token name should contain build model",
            result.user!!.token.name.contains(App.udid())
        )

        return result.user!!
    }
}