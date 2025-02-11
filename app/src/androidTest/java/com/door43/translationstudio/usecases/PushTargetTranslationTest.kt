package com.door43.translationstudio.usecases

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.OnProgressListener
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.setDefaultPref
import com.door43.translationstudio.IntegrationTest
import com.door43.translationstudio.TestUtils
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.SettingsActivity
import com.door43.usecases.GogsLogin
import com.door43.usecases.ImportProjects
import com.door43.usecases.PullTargetTranslation
import com.door43.usecases.PushTargetTranslation
import com.door43.usecases.RegisterSSHKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.eclipse.jgit.api.PushCommand
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.errors.NoRemoteRepositoryException
import org.eclipse.jgit.transport.PushResult
import org.eclipse.jgit.transport.RemoteRefUpdate
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
@IntegrationTest
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
    @Inject lateinit var prefRepo: IPreferenceRepository

    private val server = MockWebServer()
    private lateinit var targetTranslation: TargetTranslation

    @Before
    fun setUp() {
        hiltRule.inject()

        mockkConstructor(PushCommand::class)

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
    fun testPushTargetTranslationAuthorized() {
        loginGogsUser()

        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        processRepoResponse()

        val pushResult: PushResult = mockk()
        val refUpdate: RemoteRefUpdate = mockk {
            every { status }.returns(RemoteRefUpdate.Status.OK)
            every { remoteName }.returns("test_repo")
        }
        every { pushResult.remoteUpdates }.returns(listOf(refUpdate))
        every { anyConstructed<PushCommand>().call() }.returns(listOf(pushResult))

        val result = pushTargetTranslation.execute(targetTranslation, progressListener)

        assertEquals(
            "Push should fail, because remote exists but not synced with local",
            PushTargetTranslation.Status.OK,
            result.status
        )
        assertFalse("Push should not be rejected", result.status.isRejected)
        assertNotNull("Result message should not be null", result.message)
        assertNotNull("Progress message should not be null", progressMessage)
    }

    @Test
    fun testPushTargetTranslationNotSynced() {
        loginGogsUser()

        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        processRepoResponse()

        val pushResult: PushResult = mockk()
        val refUpdate: RemoteRefUpdate = mockk {
            every { status }.returns(RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD)
            every { remoteName }.returns("test_repo")
        }
        every { pushResult.remoteUpdates }.returns(listOf(refUpdate))
        every { anyConstructed<PushCommand>().call() }.returns(listOf(pushResult))

        val result = pushTargetTranslation.execute(targetTranslation, progressListener)

        assertEquals(
            "Push should fail, because remote exists but not synced with local",
            PushTargetTranslation.Status.REJECTED_NON_FAST_FORWARD,
            result.status
        )
        assertTrue("Push should be rejected", result.status.isRejected)
        assertNotNull("Result message should not be null", result.message)
        assertNotNull("Progress message should not be null", progressMessage)
    }

    @Test
    fun testPushTargetTranslationRefDeleteNotAllowed() {
        loginGogsUser()

        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        processRepoResponse()

        val pushResult: PushResult = mockk()
        val refUpdate: RemoteRefUpdate = mockk {
            every { status }.returns(RemoteRefUpdate.Status.REJECTED_NODELETE)
            every { remoteName }.returns("test_repo")
        }
        every { pushResult.remoteUpdates }.returns(listOf(refUpdate))
        every { anyConstructed<PushCommand>().call() }.returns(listOf(pushResult))

        val result = pushTargetTranslation.execute(targetTranslation, progressListener)

        assertEquals(
            "Push should fail, because remote doesn't allow deleting refs",
            PushTargetTranslation.Status.REJECTED_NODELETE,
            result.status
        )
        assertTrue("Push should be rejected", result.status.isRejected)
        assertNotNull("Result message should not be null", result.message)
        assertNotNull("Progress message should not be null", progressMessage)
    }

    @Test
    fun testPushTargetTranslationRemoteChanged() {
        loginGogsUser()

        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        processRepoResponse()

        val pushResult: PushResult = mockk()
        val refUpdate: RemoteRefUpdate = mockk {
            every { status }.returns(RemoteRefUpdate.Status.REJECTED_REMOTE_CHANGED)
            every { remoteName }.returns("test_repo")
        }
        every { pushResult.remoteUpdates }.returns(listOf(refUpdate))
        every { anyConstructed<PushCommand>().call() }.returns(listOf(pushResult))

        val result = pushTargetTranslation.execute(targetTranslation, progressListener)

        assertEquals(
            "Push should fail, because remote changed during push",
            PushTargetTranslation.Status.REJECTED_REMOTE_CHANGED,
            result.status
        )
        assertTrue("Push should be rejected", result.status.isRejected)
        assertNotNull("Result message should not be null", result.message)
        assertNotNull("Progress message should not be null", progressMessage)
    }

    @Test
    fun testPushTargetTranslationRejectedByOtherReason() {
        loginGogsUser()

        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        processRepoResponse()

        val pushResult: PushResult = mockk()
        val refUpdate: RemoteRefUpdate = mockk {
            every { status }.returns(RemoteRefUpdate.Status.REJECTED_OTHER_REASON)
            every { remoteName }.returns("test_repo")
            every { message }.returns("Unknown reason.")
        }
        every { pushResult.remoteUpdates }.returns(listOf(refUpdate))
        every { anyConstructed<PushCommand>().call() }.returns(listOf(pushResult))

        val result = pushTargetTranslation.execute(targetTranslation, progressListener)

        assertEquals(
            "Push should fail for other reason",
            PushTargetTranslation.Status.REJECTED_OTHER_REASON,
            result.status
        )
        assertTrue("Push should be rejected", result.status.isRejected)
        assertNotNull("Result message should not be null", result.message)
        assertNotNull("Progress message should not be null", progressMessage)
    }

    @Test
    fun testPushTargetTranslationNotRejected() {
        loginGogsUser()

        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        processRepoResponse()

        val pushResult: PushResult = mockk()
        val refUpdate: RemoteRefUpdate = mockk {
            every { status }.returns(RemoteRefUpdate.Status.NON_EXISTING)
            every { remoteName }.returns("test_repo")
            every { message }.returns("Unknown reason.")
        }
        every { pushResult.remoteUpdates }.returns(listOf(refUpdate))
        every { anyConstructed<PushCommand>().call() }.returns(listOf(pushResult))

        val result = pushTargetTranslation.execute(targetTranslation, progressListener)

        assertEquals(
            "Push should fail for other reason",
            PushTargetTranslation.Status.UNKNOWN,
            result.status
        )
        assertFalse("Push should not be rejected", result.status.isRejected)
        assertNotNull("Result message should not be null", result.message)
        assertNotNull("Progress message should not be null", progressMessage)
    }

    @Test
    fun testPushTargetTranslationUnAuthorized() {
        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        val result = pushTargetTranslation.execute(targetTranslation, progressListener)

        assertEquals(
            "Fails when there is no auth user",
            PushTargetTranslation.Status.AUTH_FAILURE,
            result.status
        )
        assertNull("Result message should be null", result.message)
        assertNull("Progress message should be null", progressMessage)
    }

    @Test
    fun testPushTargetTranslationAuthorizationFails() {
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
        every { anyConstructed<PushCommand>().call() }
            .throws(exception)

        val result = pushTargetTranslation.execute(targetTranslation, progressListener)

        assertEquals(
            "Push should fail",
            PushTargetTranslation.Status.AUTH_FAILURE,
            result.status
        )
        assertNull("Result message should be null", result.message)
        assertNotNull("Progress message should not be null", progressMessage)
    }

    @Test
    fun testPushTargetTranslationToPrivateRepoFails() {
        loginGogsUser()

        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        processRepoResponse()

        val exception = TransportException(
            "An error occurred.",
            Exception("Push to private repo is not permitted")
        )
        every { anyConstructed<PushCommand>().call() }
            .throws(exception)

        val result = pushTargetTranslation.execute(targetTranslation, progressListener)

        assertEquals(
            "Push should fail",
            PushTargetTranslation.Status.AUTH_FAILURE,
            result.status
        )
        assertNull("Result message should be null", result.message)
        assertNotNull("Progress message should not be null", progressMessage)
    }

    @Test
    fun testPushTargetTranslationNoRemoteException() {
        loginGogsUser()

        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        processRepoResponse()

        val exception = TransportException(
            "An error occurred.",
            NoRemoteRepositoryException(URIish(), "Remote doesn't exist")
        )
        every { anyConstructed<PushCommand>().call() }
            .throws(exception)

        val result = pushTargetTranslation.execute(targetTranslation, progressListener)

        assertEquals(
            "Push should fail",
            PushTargetTranslation.Status.NO_REMOTE_REPO,
            result.status
        )
        assertNull("Result message should be null", result.message)
        assertNotNull("Progress message should not be null", progressMessage)
    }

    @Test
    fun testPushTargetTranslationUnknownTransportException() {
        loginGogsUser()

        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        processRepoResponse()

        val exception = TransportException(
            "An error occurred.",
            Exception("Remote doesn't exist")
        )
        every { anyConstructed<PushCommand>().call() }
            .throws(exception)

        val result = pushTargetTranslation.execute(targetTranslation, progressListener)

        assertEquals(
            "Push should fail",
            PushTargetTranslation.Status.UNKNOWN,
            result.status
        )
        assertNull("Result message should be null", result.message)
        assertNotNull("Progress message should not be null", progressMessage)
    }

    @Test
    fun testPushTargetTranslationOutOfMemoryError() {
        loginGogsUser()

        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        processRepoResponse()

        every { anyConstructed<PushCommand>().call() }
            .throws(OutOfMemoryError("An error occurred."))

        val result = pushTargetTranslation.execute(targetTranslation, progressListener)

        assertEquals(
            "Push should fail",
            PushTargetTranslation.Status.OUT_OF_MEMORY,
            result.status
        )
        assertNull("Result message should be null", result.message)
        assertNotNull("Progress message should not be null", progressMessage)
    }

    @Test
    fun testPushTargetTranslationGenericError() {
        loginGogsUser()

        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        processRepoResponse()

        every { anyConstructed<PushCommand>().call() }
            .throws(Exception("An error occurred."))

        val result = pushTargetTranslation.execute(targetTranslation, progressListener)

        assertEquals(
            "Push should fail",
            PushTargetTranslation.Status.UNKNOWN,
            result.status
        )
        assertNull("Result message should be null", result.message)
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