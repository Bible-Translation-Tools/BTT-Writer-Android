package com.door43.translationstudio.usecases

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.OnProgressListener
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.BuildConfig
import com.door43.translationstudio.TestUtils
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import com.door43.usecases.GogsLogin
import com.door43.usecases.ImportProjects
import com.door43.usecases.PullTargetTranslation
import com.door43.usecases.PushTargetTranslation
import com.door43.usecases.RegisterSSHKeys
import com.door43.util.FileUtilities
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.eclipse.jgit.merge.MergeStrategy
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.tools.logger.Logger
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PushTargetTranslationTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject @ApplicationContext lateinit var appContext: Context
    @Inject lateinit var importProjects: ImportProjects
    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var assetsProvider: AssetsProvider
    @Inject lateinit var library: Door43Client
    @Inject lateinit var translator: Translator
    @Inject lateinit var profile: Profile
    @Inject lateinit var registerSSHKeys: RegisterSSHKeys
    @Inject lateinit var gogsLogin: GogsLogin
    @Inject lateinit var pushTargetTranslation: PushTargetTranslation
    @Inject lateinit var pullTargetTranslation: PullTargetTranslation

    @Before
    fun setUp() {
        hiltRule.inject()
        Logger.flush()
    }

    @After
    fun tearDown() {
        Logger.flush()
        directoryProvider.clearCache()
        directoryProvider.deleteTranslations()
    }

    @Test
    fun testPushTargetTranslationAuthorized() {
        loginGogsUser()

        val targetTranslation = importTargetTranslation("aaa")

        assertNotNull("Target translation should not be null", targetTranslation)

        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        val result = pushTargetTranslation.execute(targetTranslation!!, progressListener)

        assertEquals(
            "Push should fail, because remote exists but not synced with local",
            PushTargetTranslation.Status.REJECTED_NON_FAST_FORWARD,
            result.status
        )
        assertTrue("Push should be rejected", result.status.isRejected)
        assertNotNull("Result message should not be null", result.message)
        assertNotNull("Progress message should not be null", progressMessage)

        // Should pull before pushing to avoid conflict
        val pullResult = pullTargetTranslation.execute(targetTranslation, MergeStrategy.RECURSIVE)

        val okStatus = pullResult.status == PullTargetTranslation.Status.UNKNOWN ||
                pullResult.status == PullTargetTranslation.Status.UP_TO_DATE

        assertTrue("Pull should be successful", okStatus)

        val result2 = pushTargetTranslation.execute(targetTranslation)

        assertEquals("Push should be successful", PushTargetTranslation.Status.OK, result2.status)
        assertNotNull("Result message should not be null", result2.message)
    }

    @Test
    fun testPushTargetTranslationUnAuthorizedFails() {
        val targetTranslation = importTargetTranslation("aaa")

        assertNotNull("Target translation should not be null", targetTranslation)

        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        val result = pushTargetTranslation.execute(targetTranslation!!, progressListener)

        assertEquals(
            "Push should fail",
            PushTargetTranslation.Status.AUTH_FAILURE,
            result.status
        )
        assertNull("Result message should be null", result.message)
        assertNull("Progress message should be null", progressMessage)

        // Try to push while logged in but with no SSH keys
        loginWithoutSshKeys()

        progressMessage = null
        val result2 = pushTargetTranslation.execute(targetTranslation, progressListener)

        assertEquals(
            "Push should fail",
            PushTargetTranslation.Status.AUTH_FAILURE,
            result2.status
        )
        assertNull("Result message should be null", result2.message)
        assertNotNull("Progress message should not be null", progressMessage)
    }

    private fun importTargetTranslation(lang: String): TargetTranslation? {
        return TestUtils.importTargetTranslation(
            library,
            appContext,
            directoryProvider,
            profile,
            assetsProvider,
            importProjects,
            translator,
            lang,
            "usfm/mrk.usfm"
        )
    }

    private fun loginGogsUser() {
        val login = gogsLogin.execute(
            BuildConfig.TEST_USER,
            BuildConfig.TEST_PASS
        )

        assertNotNull(login.user)

        profile.gogsUser = login.user
        val registered = registerSSHKeys.execute(true)

        assertTrue("SSH keys should be registered", registered)
    }

    private fun loginWithoutSshKeys() {
        val keysDir = directoryProvider.sshKeysDir.listFiles()
        if (keysDir != null) {
            for (file in keysDir) {
                FileUtilities.deleteQuietly(file)
            }
        }

        val login = gogsLogin.execute(
            BuildConfig.TEST_USER,
            BuildConfig.TEST_PASS
        )

        assertNotNull(login.user)

        profile.gogsUser = login.user
    }
}