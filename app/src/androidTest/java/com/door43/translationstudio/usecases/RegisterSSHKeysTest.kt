package com.door43.translationstudio.usecases

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.OnProgressListener
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.BuildConfig
import com.door43.translationstudio.core.Profile
import com.door43.usecases.GogsLogin
import com.door43.usecases.RegisterSSHKeys
import com.door43.util.FileUtilities
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.unfoldingword.tools.logger.Logger
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

    @Before
    fun setUp() {
        hiltRule.inject()
        Logger.flush()
        deleteSSHKeys()
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
        val login = gogsLogin.execute(
            BuildConfig.TEST_USER,
            BuildConfig.TEST_PASS
        )

        assertNotNull(login.user)

        profile.gogsUser = login.user
    }

    private fun deleteSSHKeys() {
        val keysDir = directoryProvider.sshKeysDir.listFiles()
        if (keysDir != null) {
            for (file in keysDir) {
                FileUtilities.deleteQuietly(file)
            }
        }
    }
}