package com.door43.translationstudio.usecases

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.OnProgressListener
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.setDefaultPref
import com.door43.translationstudio.TestUtils
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.SettingsActivity
import com.door43.usecases.CreateRepository
import com.door43.usecases.GogsLogin
import com.door43.usecases.ImportProjects
import com.door43.usecases.SearchGogsUsers
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.unfoldingword.door43client.Door43Client
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CreateRepositoryTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject
    @ApplicationContext lateinit var appContext: Context
    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var library: Door43Client
    @Inject lateinit var profile: Profile
    @Inject lateinit var assetsProvider: AssetsProvider
    @Inject lateinit var createRepository: CreateRepository
    @Inject lateinit var searchGogsUsers: SearchGogsUsers
    @Inject lateinit var importProjects: ImportProjects
    @Inject lateinit var translator: Translator
    @Inject lateinit var prefRepo: IPreferenceRepository
    @Inject lateinit var gogsLogin: GogsLogin

    private lateinit var targetTranslation: TargetTranslation

    private val server = MockWebServer()

    @Before
    fun setUp() {
        hiltRule.inject()

        prefRepo.setDefaultPref(
            SettingsActivity.KEY_PREF_GOGS_API,
            server.url("/api").toString()
        )

        targetTranslation = TestUtils.importTargetTranslation(
            library,
            appContext,
            directoryProvider,
            profile,
            assetsProvider,
            importProjects,
            translator,
            "aae",
            "usfm/mrk.usfm"
        )!!
    }

    @After
    fun tearDown() {
        directoryProvider.clearCache()
        directoryProvider.deleteTranslations()
        profile.logout()
    }

    @Test
    fun createRepositoryWithAuthenticationSucceeds() {
        loginGogsUser()

        createRepoResponse(201)

        val created = createRepository.execute(targetTranslation)

        assertTrue("Repository should be created when authenticated", created)
    }

    @Test
    fun createRepositoryThatAlreadyExistsSucceeds() {
        loginGogsUser()

        createRepoResponse(409)

        val created = createRepository.execute(targetTranslation)

        assertTrue("Repository should be created when authenticated", created)
    }

    @Test
    fun createRepositoryServerError() {
        loginGogsUser()

        createRepoResponse(500)

        val created = createRepository.execute(targetTranslation)

        assertFalse("Repository should not be created", created)
    }

    @Test
    fun createRepositoryWithoutAuthenticationFails() {
        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }
        val created = createRepository.execute(targetTranslation, progressListener)

        assertFalse("Repository should not be created when not authenticated", created)
        assertNotNull("Progress message should not be null", progressMessage)
    }

    private fun loginGogsUser() {
        profile.gogsUser = TestUtils.simulateLoginGogsUser(
            appContext,
            server,
            gogsLogin,
            "test"
        )
    }

    private fun createRepoResponse(responseCode: Int) {
        val body = """
            {
                "name": "${targetTranslation.id}",
                "ssh_url": "http://example.com/repo.git",
                "owner": {
                    "username": "${profile.gogsUser!!.username}"
                }
            }
        """.trimIndent()

        server.enqueue(MockResponse().setBody(body).setResponseCode(responseCode))
    }
}