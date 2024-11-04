package com.door43.translationstudio.usecases

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.OnProgressListener
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.BuildConfig
import com.door43.translationstudio.R
import com.door43.translationstudio.TestUtils
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.SettingsActivity
import com.door43.usecases.GogsLogin
import com.door43.usecases.ImportProjects
import com.door43.usecases.PullTargetTranslation
import com.door43.usecases.RegisterSSHKeys
import com.door43.usecases.SearchGogsUsers
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
import org.unfoldingword.gogsclient.GogsAPI
import org.unfoldingword.gogsclient.Repository
import org.unfoldingword.gogsclient.User
import org.unfoldingword.tools.logger.Logger
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PullTargetTranslationTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject
    @ApplicationContext lateinit var appContext: Context

    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var library: Door43Client
    @Inject lateinit var profile: Profile
    @Inject lateinit var assetsProvider: AssetsProvider
    @Inject lateinit var importProjects: ImportProjects
    @Inject lateinit var translator: Translator
    @Inject lateinit var pullTargetTranslation: PullTargetTranslation
    @Inject lateinit var searchGogsUsers: SearchGogsUsers
    @Inject lateinit var gogsLogin: GogsLogin
    @Inject lateinit var registerSSHKeys: RegisterSSHKeys
    @Inject lateinit var prefRepo: IPreferenceRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        Logger.flush()
    }

    @After
    fun tearDown() {
        directoryProvider.clearCache()
        directoryProvider.deleteTranslations()
        profile.logout()
    }

    @Test
    fun testPullTargetTranslationAuthorizedNewRepo() {
        val targetTranslation = importTargetTranslation("fr")

        assertNotNull("Target translation should not be null", targetTranslation)

        loginGogsUser()

        // Should first delete remote repo if it exists
        deleteRepo(targetTranslation!!, profile.gogsUser!!)

        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        val result = pullTargetTranslation.execute(
            targetTranslation,
            MergeStrategy.RECURSIVE,
            null,
            progressListener
        )

        assertEquals(
            "Pull status should be UNKNOWN",
            PullTargetTranslation.Status.UNKNOWN,
            result.status
        )
        assertNull("Message should be null", result.message)
        assertNotNull("Progress message should not be null", progressMessage)
    }

    @Test
    fun testPullTargetTranslationAuthorizedExistingRepo() {
        val targetTranslation = importTargetTranslation("aae")

        assertNotNull("Target translation should not be null", targetTranslation)

        loginGogsUser()

        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        val result = pullTargetTranslation.execute(
            targetTranslation!!,
            MergeStrategy.RECURSIVE,
            null,
            progressListener
        )

        assertEquals(
            "Pull status should be MERGE_CONFLICTS",
            PullTargetTranslation.Status.MERGE_CONFLICTS,
            result.status
        )
        assertNotNull("Message should not be null", result.message)
        assertNotNull("Progress message should not be null", progressMessage)
    }

    @Test
    fun testPullTargetTranslationUnAuthorizedFails() {
        val targetTranslation = importTargetTranslation("aae")

        assertNotNull("Target translation should not be null", targetTranslation)

        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        val result = pullTargetTranslation.execute(
            targetTranslation!!,
            MergeStrategy.RECURSIVE,
            null,
            progressListener
        )

        assertEquals(
            "Pull status should be AUTH_FAILURE",
            PullTargetTranslation.Status.AUTH_FAILURE,
            result.status
        )
        assertNotNull("Message should not be null", result.message)
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

    private fun deleteRepo(targetTranslation: TargetTranslation, user: User) {
        val api = GogsAPI(
            prefRepo.getDefaultPref(
                SettingsActivity.KEY_PREF_GOGS_API,
                appContext.resources.getString(R.string.pref_default_gogs_api)
            )
        )
        val templateRepo = Repository(targetTranslation.id, "", false)
        api.deleteRepo(templateRepo, user)
    }
}