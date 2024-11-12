package com.door43.translationstudio.usecases

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.OnProgressListener
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.setDefaultPref
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.ui.SettingsActivity
import com.door43.usecases.GogsLogin
import com.door43.usecases.RegisterSSHKeys
import com.door43.util.FileUtilities
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.unfoldingword.gogsclient.User
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RegisterSSHKeysTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject lateinit var registerSSHKeys: RegisterSSHKeys
    @Inject lateinit var gogsLogin: GogsLogin
    @Inject lateinit var profile: Profile
    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var prefRepository: IPreferenceRepository

    private val server = MockWebServer()

    @Before
    fun setUp() {
        hiltRule.inject()
        deleteSSHKeys()

        prefRepository.setDefaultPref(
            SettingsActivity.KEY_PREF_GOGS_API,
            server.url("/api").toString()
        )

        val dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    "/api/users/test/keys" -> createResponse("get_keys")
                    "/api/user/keys/test_key" -> createResponse("delete_key")
                    "/api/user/keys" -> createResponse("create_key")
                    else -> createResponse("not_found")
                }
            }
        }
        server.dispatcher = dispatcher
    }

    @Test
    fun testRegisterSSHKeys() {
        loginGogsUser()

        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        val hasSSHKeys = directoryProvider.hasSSHKeys()
        assertFalse("SSH keys should not exist", hasSSHKeys)

        val registered = registerSSHKeys.execute(false, progressListener)

        assertTrue("SSH keys registered ", registered)
        assertNotNull("Progress message should not be null", progressMessage)

        val publicKeyBefore = directoryProvider.publicKey.inputStream().use {
            it.bufferedReader().readText()
        }

        progressMessage = null
        val registered2 = registerSSHKeys.execute(true, progressListener)

        assertTrue("SSH keys registered with force flag", registered2)
        assertNotNull("Progress message should not be null", progressMessage)

        val publicKeyAfter = directoryProvider.publicKey.inputStream().use {
            it.bufferedReader().readText()
        }

        assertNotEquals(
            "Public key should be recreated when force flag used",
            publicKeyBefore,
            publicKeyAfter
        )
    }

    @Test
    fun testRegisterSSHKeys_noUser() {
        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        val registered = registerSSHKeys.execute(false, progressListener)

        assertFalse("SSH keys not registered without user", registered)
        assertNotNull("Progress message should not be null", progressMessage)
    }

    private fun loginGogsUser() {
        val user = User("test", "test")
        profile.gogsUser = user
    }

    private fun deleteSSHKeys() {
        val keysDir = directoryProvider.sshKeysDir.listFiles()
        if (keysDir != null) {
            for (file in keysDir) {
                FileUtilities.deleteQuietly(file)
            }
        }
    }

    private fun createResponse(requestId: String): MockResponse {
        return when (requestId) {
            "get_keys" -> {
                val body = """
                [
                    {
                        "title": "test_title_1",
                        "key": "test_key_1"
                    },
                    {
                        "title": "test_title_2",
                        "key": "test_key_2"
                    }
                ]
                """.trimIndent()
                MockResponse().setBody(body).setResponseCode(200)
            }
            "delete_key" -> MockResponse().setResponseCode(204)
            "create_key" -> {
                val body = """
                {
                    "title": "test_title_1",
                    "key": "test_key_1"
                }
                """.trimIndent()
                MockResponse().setBody(body).setResponseCode(201)
            }
            else -> MockResponse().setResponseCode(404)
        }
    }
}