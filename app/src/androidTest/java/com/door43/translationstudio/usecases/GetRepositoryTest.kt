package com.door43.translationstudio.usecases

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.setDefaultPref
import com.door43.translationstudio.IntegrationTest
import com.door43.translationstudio.KoinAndroidTest
import com.door43.translationstudio.Platform
import com.door43.translationstudio.TestUtils
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import com.door43.usecases.GetRepository
import com.door43.usecases.GogsLogin
import com.door43.usecases.ImportProjects
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.inject
import org.unfoldingword.door43client.Door43Client


@RunWith(AndroidJUnit4::class)
@IntegrationTest
class GetRepositoryTest : KoinAndroidTest() {

    private val appContext: Context by inject()
    private val directoryProvider: IDirectoryProvider by inject()
    private val library: Door43Client by inject()
    private val profile: Profile by inject()
    private val assetsProvider: AssetsProvider by inject()
    private val getRepository: GetRepository by inject()
    private val gogsLogin: GogsLogin by inject()
    private val prefRepository: IPreferenceRepository by inject()
    private val importProjects: ImportProjects by inject()
    private val translator: Translator by inject()
    private val platform: Platform by inject()

    private lateinit var targetTranslation: TargetTranslation

    private val server = MockWebServer()

    @Before
    fun setUp() {
        prefRepository.setDefaultPref(IPreferenceRepository.KEY_PREF_GOGS_API, server.url("/api/").toString())

        targetTranslation = TestUtils.importTargetTranslation(
            library,
            appContext,
            platform,
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
    }

    @Test
    fun getRepositorySucceeds() = runTest {
        loginGogsUser()
        processRepoResponse(targetTranslation.id)

        val repo = getRepository.execute(targetTranslation)

        assertNotNull("Repository should not be null", repo)

        assertEquals(targetTranslation.id, repo!!.name)
    }

    @Test
    fun getRepositoryThatIsNotExactNameFails() = runTest {
        loginGogsUser()
        processRepoResponse("${targetTranslation.id}_L3")

        val repo = getRepository.execute(targetTranslation)

        assertNull("Repository should be null", repo)
    }

    @Test
    fun getRepositoryNotAuthorizedFails() = runTest {
        val repo = getRepository.execute(targetTranslation)

        assertNull("Repository should be null", repo)
    }

    private fun loginGogsUser() = runTest {
        profile.gogsUser = TestUtils.simulateLoginGogsUser(
            appContext,
            platform,
            server,
            gogsLogin,
            "test"
        )
    }

    private fun processRepoResponse(id: String) {
        val repoResponse = """
            {
                "name": "$id",
                "ssh_url": "http://example.com/repo.git",
                "owner": {
                    "username": "${profile.gogsUser!!.username}"
                }
            }
        """.trimIndent()
        val reposResponse = """
            {"data": [$repoResponse], "ok": true}
        """.trimIndent()

        server.enqueue(MockResponse()) // create repo response
        server.enqueue(MockResponse()
            .setBody(reposResponse)
            .addHeader("Content-Type", "application/json")
        ) // fetch repos response
        server.enqueue(MockResponse()
            .setBody(repoResponse)
            .addHeader("Content-Type", "application/json")
        ) // fetch extra repo
    }
}