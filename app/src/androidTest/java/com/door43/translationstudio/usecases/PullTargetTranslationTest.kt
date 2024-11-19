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
import com.door43.usecases.GogsLogin
import com.door43.usecases.ImportProjects
import com.door43.usecases.PullTargetTranslation
import com.door43.usecases.RegisterSSHKeys
import com.door43.usecases.SearchGogsUsers
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.api.PullCommand
import org.eclipse.jgit.api.PullResult
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.errors.NoRemoteRepositoryException
import org.eclipse.jgit.merge.MergeStrategy
import org.eclipse.jgit.transport.URIish
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.unfoldingword.door43client.Door43Client
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

    private val server = MockWebServer()
    private lateinit var targetTranslation: TargetTranslation

    @Before
    fun setUp() {
        hiltRule.inject()

        prefRepo.setDefaultPref(
            SettingsActivity.KEY_PREF_GOGS_API,
            server.url("/api").toString()
        )

        mockkConstructor(PullCommand::class)

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
    fun testPullTargetTranslationAuthorizedNewRepo() {
        loginGogsUser()

        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        processRepoResponse()

        every { anyConstructed<PullCommand>().call() }
            .throws(Exception("New repo doesn't have a branch yet."))

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
        loginGogsUser()

        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        processRepoResponse()

        val pullResult: PullResult = mockk()
        val mergeResult: MergeResult = mockk()
        val conflicts = mapOf(
            "01.txt" to arrayOf(intArrayOf(0, 0)),
        )

        every { mergeResult.conflicts }.returns(conflicts)
        every { pullResult.mergeResult }.returns(mergeResult)
        every { anyConstructed<PullCommand>().call() }.returns(pullResult)

        val result = pullTargetTranslation.execute(
            targetTranslation,
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
    fun testPullTargetTranslationAuthorizedExistingRepoNoMergeConflicts() {
        loginGogsUser()

        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        processRepoResponse()

        val pullResult: PullResult = mockk()
        val mergeResult: MergeResult = mockk()

        every { mergeResult.conflicts }.returns(mapOf())
        every { pullResult.mergeResult }.returns(mergeResult)
        every { anyConstructed<PullCommand>().call() }.returns(pullResult)

        val result = pullTargetTranslation.execute(
            targetTranslation,
            MergeStrategy.RECURSIVE,
            null,
            progressListener
        )

        assertEquals(
            "Pull status should be UP_TO_DATE",
            PullTargetTranslation.Status.UP_TO_DATE,
            result.status
        )
        assertNotNull("Message should not be null", result.message)
        assertNotNull("Progress message should not be null", progressMessage)
    }

    @Test
    fun testPullTargetTranslationAuthorizedConflicts() {
        loginGogsUser()

        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        processRepoResponse()

        val pullResult: PullResult = mockk()
        val mergeResult: MergeResult = mockk()
        val conflicts = mapOf(
            "manifest.json" to arrayOf(intArrayOf(0, 0)),
            "LICENSE.md" to arrayOf(intArrayOf(0, 0)),
        )

        every { mergeResult.conflicts }.returns(conflicts)
        every { pullResult.mergeResult }.returns(mergeResult)
        every { anyConstructed<PullCommand>().call() }.returns(pullResult)

        val result = pullTargetTranslation.execute(
            targetTranslation,
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
            "Pull status should be AUTH_FAILURE",
            PullTargetTranslation.Status.AUTH_FAILURE,
            result.status
        )
        assertNotNull("Message should not be null", result.message)
        assertNotNull("Progress message should not be null", progressMessage)
    }

    @Test
    fun testPullTargetTranslationAuthorizationFailed() {
        loginGogsUser()

        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        processRepoResponse()

        val exception = TransportException(
            "An error occurred.",
            Exception(
                Exception("Auth fail")
            )
        )
        every { anyConstructed<PullCommand>().call() }
            .throws(exception)

        val result = pullTargetTranslation.execute(
            targetTranslation,
            MergeStrategy.RECURSIVE,
            null,
            progressListener
        )

        assertEquals(
            "Pull status should be AUTH_FAILURE",
            PullTargetTranslation.Status.AUTH_FAILURE,
            result.status
        )
        assertNull("Message should be null", result.message)
        assertNotNull("Progress message should not be null", progressMessage)
    }

    @Test
    fun testPullTargetTranslationRemoteNotFound() {
        loginGogsUser()

        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        processRepoResponse()

        val exception = TransportException(
            "New repo doesn't have a branch yet.",
            NoRemoteRepositoryException(URIish(), "No remote")
        )
        every { anyConstructed<PullCommand>().call() }
            .throws(exception)

        val result = pullTargetTranslation.execute(
            targetTranslation,
            MergeStrategy.RECURSIVE,
            null,
            progressListener
        )

        assertEquals(
            "Pull status should be NO_REMOTE_REPO",
            PullTargetTranslation.Status.NO_REMOTE_REPO,
            result.status
        )
        assertNull("Message should be null", result.message)
        assertNotNull("Progress message should not be null", progressMessage)
    }

    @Test
    fun testPullTargetTranslationUnknownTransportException() {
        loginGogsUser()

        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        processRepoResponse()

        every { anyConstructed<PullCommand>().call() }
            .throws(TransportException("An error occurred."))

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
    fun testPullTargetTranslationOutOfMemoryError() {
        loginGogsUser()

        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        processRepoResponse()

        every { anyConstructed<PullCommand>().call() }
            .throws(OutOfMemoryError("Out of memory."))

        val result = pullTargetTranslation.execute(
            targetTranslation,
            MergeStrategy.RECURSIVE,
            null,
            progressListener
        )

        assertEquals(
            "Pull status should be OUT_OF_MEMORY",
            PullTargetTranslation.Status.OUT_OF_MEMORY,
            result.status
        )
        assertNull("Message should be null", result.message)
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

    private fun processRepoResponse() {
        val repoResponse = """
            {
                "name": "${targetTranslation.id}",
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