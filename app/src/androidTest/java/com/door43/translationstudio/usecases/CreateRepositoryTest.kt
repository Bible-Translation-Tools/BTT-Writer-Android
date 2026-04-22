package com.door43.translationstudio.usecases

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.OnProgressListener
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.setDefaultPref
import com.door43.translationstudio.IntegrationTest
import com.door43.translationstudio.KoinAndroidTest
import com.door43.translationstudio.TestUtils
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import com.door43.usecases.CreateRepository
import com.door43.usecases.GogsLogin
import com.door43.usecases.ImportProjects
import com.door43.usecases.SearchGogsUsers
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.inject
import org.unfoldingword.door43client.Door43Client


@RunWith(AndroidJUnit4::class)
@IntegrationTest
class CreateRepositoryTest : KoinAndroidTest() {

    private val appContext: Context by inject()
    private val directoryProvider: IDirectoryProvider by inject()
    private val library: Door43Client by inject()
    private val profile: Profile by inject()
    private val assetsProvider: AssetsProvider by inject()
    private val createRepository: CreateRepository by inject()
    private val searchGogsUsers: SearchGogsUsers by inject()
    private val gogsLogin: GogsLogin by inject()
    private val prefRepository: IPreferenceRepository by inject()
    private val importProjects: ImportProjects by inject()
    private val translator: Translator by inject()

    private lateinit var targetTranslation: TargetTranslation

    private val server = MockWebServer()

    @Before
    fun setUp() {
        prefRepository.setDefaultPref(IPreferenceRepository.KEY_PREF_GOGS_API, server.url("/api/").toString())

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
    fun createRepositoryWithAuthenticationSucceeds() = runTest {
        loginGogsUser()

        createRepoResponse(201)

        val created = createRepository.execute(targetTranslation)

        assertTrue("Repository should be created when authenticated", created)
    }

    @Test
    fun createRepositoryThatAlreadyExistsSucceeds() = runTest {
        loginGogsUser()

        createRepoResponse(409)

        val created = createRepository.execute(targetTranslation)

        assertTrue("Repository should be created when authenticated", created)
    }

    @Test
    fun createRepositoryServerError() = runTest {
        loginGogsUser()

        createRepoResponse(500)

        val created = createRepository.execute(targetTranslation)

        assertFalse("Repository should not be created", created)
    }

    @Test
    fun createRepositoryWithoutAuthenticationFails() = runTest {
        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, message ->
            progressMessage = message
        }
        val created = createRepository.execute(targetTranslation, progressListener)

        assertFalse("Repository should not be created when not authenticated", created)
        assertNotNull("Progress message should not be null", progressMessage)
    }

    private fun loginGogsUser()  = runTest{
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