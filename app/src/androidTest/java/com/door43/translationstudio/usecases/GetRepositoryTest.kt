package com.door43.translationstudio.usecases

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.setDefaultPref
import com.door43.translationstudio.TestUtils
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.SettingsActivity
import com.door43.usecases.GetRepository
import com.door43.usecases.GogsLogin
import com.door43.usecases.ImportProjects
import com.door43.usecases.SearchGogsUsers
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.unfoldingword.door43client.Door43Client
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class GetRepositoryTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject
    @ApplicationContext lateinit var appContext: Context
    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var library: Door43Client
    @Inject lateinit var profile: Profile
    @Inject lateinit var assetsProvider: AssetsProvider
    @Inject lateinit var getRepository: GetRepository
    @Inject lateinit var searchGogsUsers: SearchGogsUsers
    @Inject lateinit var importProjects: ImportProjects
    @Inject lateinit var translator: Translator
    @Inject lateinit var gogsLogin: GogsLogin
    @Inject lateinit var prefRepo: IPreferenceRepository

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
    }

    @Test
    fun getRepositorySucceeds() {
        loginGogsUser()
        processRepoResponse(targetTranslation.id)

        val repo = getRepository.execute(targetTranslation)

        assertNotNull("Repository should not be null", repo)

        assertEquals(targetTranslation.id, repo!!.name)
    }

    @Test
    fun getRepositoryThatIsNotExactNameFails() {
        loginGogsUser()
        processRepoResponse("${targetTranslation.id}_L3")

        val repo = getRepository.execute(targetTranslation)

        assertNull("Repository should be null", repo)
    }

    @Test
    fun getRepositoryNotAuthorizedFails() {
        val repo = getRepository.execute(targetTranslation)

        assertNull("Repository should be null", repo)
    }

    private fun loginGogsUser() {
        profile.gogsUser = TestUtils.simulateLoginGogsUser(
            appContext,
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
        server.enqueue(MockResponse().setBody(reposResponse)) // fetch repos response
        server.enqueue(MockResponse().setBody(repoResponse)) // fetch extra repo
    }
}