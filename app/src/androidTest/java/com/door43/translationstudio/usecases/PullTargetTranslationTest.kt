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
import com.door43.usecases.GogsLogin
import com.door43.usecases.ImportProjects
import com.door43.usecases.PullTargetTranslation
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.test.runTest
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
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.inject
import org.unfoldingword.door43client.Door43Client


@RunWith(AndroidJUnit4::class)
@IntegrationTest
class PullTargetTranslationTest : KoinAndroidTest() {


    private val appContext: Context by inject()
    private val directoryProvider: IDirectoryProvider by inject()
    private val library: Door43Client by inject()
    private val profile: Profile by inject()
    private val assetsProvider: AssetsProvider by inject()
    private val importProjects: ImportProjects by inject()
    private val translator: Translator by inject()
    private val pullTargetTranslation: PullTargetTranslation by inject()
    private val gogsLogin: GogsLogin by inject()
    private val prefRepo: IPreferenceRepository by inject()
    private val platform: Platform by inject()

    private val server = MockWebServer()
    private lateinit var targetTranslation: TargetTranslation

    @Before
    fun setUp() {
        prefRepo.setDefaultPref(
            IPreferenceRepository.KEY_PREF_GOGS_API,
            server.url("/api").toString()
        )

        mockkConstructor(PullCommand::class)

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
        profile.logout()
    }

    @Test
    fun testPullTargetTranslationAuthorizedNewRepo() = runTest {
        loginGogsUser()

        var progressMessage: String? = null
        val onProgress: (Float, String?) -> Unit = { _, message ->
            progressMessage = message
        }

        processRepoResponse()

        every { anyConstructed<PullCommand>().call() }
            .throws(Exception("New repo doesn't have a branch yet."))

        val result = pullTargetTranslation.execute(
            targetTranslation,
            MergeStrategy.RECURSIVE,
            null,
            onProgress
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
    fun testPullTargetTranslationAuthorizedExistingRepo() = runTest {
        loginGogsUser()

        var progressMessage: String? = null
        val onProgress: (Float, String?) -> Unit = { _, message ->
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
            onProgress
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
    fun testPullTargetTranslationAuthorizedExistingRepoNoMergeConflicts() = runTest {
        loginGogsUser()

        var progressMessage: String? = null
        val onProgress: (Float, String?) -> Unit = { _, message ->
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
            onProgress
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
    fun testPullTargetTranslationAuthorizedConflicts() = runTest {
        loginGogsUser()

        var progressMessage: String? = null
        val onProgress: (Float, String?) -> Unit = { _, message ->
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
            onProgress
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
    fun testPullTargetTranslationUnAuthorizedFails() = runTest {
        var progressMessage: String? = null
        val onProgress: (Float, String?) -> Unit = { _, message ->
            progressMessage = message
        }

        val result = pullTargetTranslation.execute(
            targetTranslation,
            MergeStrategy.RECURSIVE,
            null,
            onProgress
        )

        assertEquals(
            "Pull status should be AUTH_FAILURE",
            PullTargetTranslation.Status.AUTH_FAILURE,
            result.status
        )
        assertNotNull("Message should not be null", result.message)
        assertNull("Progress message should be null", progressMessage)
    }

    @Test
    fun testPullTargetTranslationAuthorizationFailed() = runTest {
        loginGogsUser()

        var progressMessage: String? = null
        val onProgress: (Float, String?) -> Unit = { _, message ->
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
            onProgress
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
    fun testPullTargetTranslationRemoteNotFound() = runTest {
        loginGogsUser()

        var progressMessage: String? = null
        val onProgress: (Float, String?) -> Unit = { _, message ->
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
            onProgress
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
    fun testPullTargetTranslationUnknownTransportException() = runTest {
        loginGogsUser()

        var progressMessage: String? = null
        val onProgress: (Float, String?) -> Unit = { _, message ->
            progressMessage = message
        }

        processRepoResponse()

        every { anyConstructed<PullCommand>().call() }
            .throws(TransportException("An error occurred."))

        val result = pullTargetTranslation.execute(
            targetTranslation,
            MergeStrategy.RECURSIVE,
            null,
            onProgress
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
    fun testPullTargetTranslationOutOfMemoryError() = runTest {
        loginGogsUser()

        var progressMessage: String? = null
        val onProgress: (Float, String?) -> Unit = { _, message ->
            progressMessage = message
        }

        processRepoResponse()

        every { anyConstructed<PullCommand>().call() }
            .throws(OutOfMemoryError("Out of memory."))

        val result = pullTargetTranslation.execute(
            targetTranslation,
            MergeStrategy.RECURSIVE,
            null,
            onProgress
        )

        assertEquals(
            "Pull status should be OUT_OF_MEMORY",
            PullTargetTranslation.Status.OUT_OF_MEMORY,
            result.status
        )
        assertNull("Message should be null", result.message)
        assertNotNull("Progress message should not be null", progressMessage)
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
        server.enqueue(MockResponse()
            .setBody(reposResponse)
            .addHeader("Content-Type", "application/json")) // fetch repos response
        server.enqueue(MockResponse()
            .setBody(repoResponse)
            .addHeader("Content-Type", "application/json")) // fetch extra repo
    }
}
