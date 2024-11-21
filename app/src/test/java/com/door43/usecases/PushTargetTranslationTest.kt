package com.door43.usecases

import android.content.Context
import android.content.res.Resources
import com.door43.OnProgressListener
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.git.Repo
import com.door43.translationstudio.ui.SettingsActivity
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.eclipse.jgit.api.CreateBranchCommand
import org.eclipse.jgit.api.DeleteBranchCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.PushCommand
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.errors.NoRemoteRepositoryException
import org.eclipse.jgit.transport.PushResult
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.URIish
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.unfoldingword.gogsclient.Repository
import java.io.IOException

class PushTargetTranslationTest {

    @MockK private lateinit var context: Context
    @MockK private lateinit var profile: Profile
    @MockK private lateinit var getRepository: GetRepository
    @MockK private lateinit var directoryProvider: IDirectoryProvider
    @MockK private lateinit var prefRepository: IPreferenceRepository
    @MockK private lateinit var progressListener: OnProgressListener
    @MockK private lateinit var resources: Resources
    @MockK private lateinit var targetTranslation: TargetTranslation
    @MockK private lateinit var git: Git
    @MockK private lateinit var repo: Repo
    @MockK private lateinit var repository: Repository
    @MockK private lateinit var deleteCommand: DeleteBranchCommand
    @MockK private lateinit var createCommand: CreateBranchCommand
    @MockK private lateinit var pushCommand: PushCommand

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        every { context.resources }.returns(resources)

        every {
            prefRepository.getDefaultPref(
                SettingsActivity.KEY_PREF_GIT_SERVER_PORT,
                any(),
                String::class.java
            )
        }.returns("22")

        every { progressListener.onProgress(any(), any(), any()) }.just(runs)
        every { repository.sshUrl }.returns("ssh://repo.git")
        every { getRepository.execute(targetTranslation, progressListener) }.returns(repository)

        every { targetTranslation.commitSync() }.returns(true)
        every { targetTranslation.repo }.returns(repo)
        every { repo.git }.returns(git)
        every { git.branchDelete() }.returns(deleteCommand)
        every { git.branchCreate() }.returns(createCommand)
        every { deleteCommand.setBranchNames(any()) }.returns(deleteCommand)
        every { deleteCommand.setForce(any()) }.returns(deleteCommand)
        every { deleteCommand.call() }.returns(listOf())

        every { createCommand.setName(any()) }.returns(createCommand)
        every { createCommand.setForce(any()) }.returns(createCommand)
        every { createCommand.call() }.returns(mockk())

        every { pushCommand.setTransportConfigCallback(any()) }.returns(pushCommand)
        every { pushCommand.setRemote(any()) }.returns(pushCommand)
        every { pushCommand.setPushTags() }.returns(pushCommand)
        every { pushCommand.setForce(any()) }.returns(pushCommand)
        every { pushCommand.setRefSpecs(any<RefSpec>()) }.returns(pushCommand)

        every { repo.deleteRemote(any()) }.just(runs)
        every { repo.setRemote(any(), any()) }.just(runs)
        every { git.push() }.returns(pushCommand)

        mockResources()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test push target translation authorized`() {
        every { profile.gogsUser }.returns(mockk())

        val pushResult: PushResult = mockk()
        val refUpdate: RemoteRefUpdate = mockk {
            every { status }.returns(RemoteRefUpdate.Status.OK)
            every { remoteName }.returns("test_repo")
        }
        every { pushResult.remoteUpdates }.returns(listOf(refUpdate))
        every { pushCommand.call() }.returns(listOf(pushResult))

        val result = PushTargetTranslation(
            context,
            profile,
            getRepository,
            directoryProvider,
            prefRepository
        ).execute(targetTranslation, progressListener)

        val expectedMessage = """
            OK ${refUpdate.remoteName}
            Server details ${repository.sshUrl}
            
        """.trimIndent()

        assertEquals(PushTargetTranslation.Status.OK, result.status)
        assertFalse(result.status.isRejected)
        assertEquals(expectedMessage, result.message)

        verifySuccessCalls(refUpdate, pushResult)
    }

    @Test
    fun `test push target translation not authorized`() {
        every { profile.gogsUser }.returns(null)

        val result = PushTargetTranslation(
            context,
            profile,
            getRepository,
            directoryProvider,
            prefRepository
        ).execute(targetTranslation, progressListener)

        assertEquals(PushTargetTranslation.Status.AUTH_FAILURE, result.status)
        assertFalse(result.status.isRejected)
        assertNull(result.message)

        verify { profile.gogsUser }
        verify(exactly = 0) { pushCommand.call() }
        verify(exactly = 0) { progressListener.onProgress(any(), any(), any()) }
        verify(exactly = 0) { repository.sshUrl }
        verify(exactly = 0) { getRepository.execute(targetTranslation, progressListener) }
    }

    @Test
    fun `test push target translation, remote repo not found and not created`() {
        every { profile.gogsUser }.returns(mockk())

        every { getRepository.execute(any(), any()) }.returns(null)

        val result = PushTargetTranslation(
            context,
            profile,
            getRepository,
            directoryProvider,
            prefRepository
        ).execute(targetTranslation, progressListener)

        assertEquals(PushTargetTranslation.Status.UNKNOWN, result.status)
        assertFalse(result.status.isRejected)
        assertNull(result.message)

        verify { profile.gogsUser }
        verify(exactly = 0) { pushCommand.call() }
        verify { progressListener.onProgress(any(), any(), any()) }
        verify(exactly = 0) { repository.sshUrl }
        verify { getRepository.execute(targetTranslation, progressListener) }
    }

    @Test
    fun `test push target translation, translation commit failed`() {
        every { profile.gogsUser }.returns(mockk())

        every { getRepository.execute(any(), any()) }.returns(null)
        every { targetTranslation.commitSync() }.throws(Exception("Error committing translation"))

        val result = PushTargetTranslation(
            context,
            profile,
            getRepository,
            directoryProvider,
            prefRepository
        ).execute(targetTranslation, progressListener)

        assertEquals(PushTargetTranslation.Status.UNKNOWN, result.status)
        assertFalse(result.status.isRejected)
        assertNull(result.message)

        verify { profile.gogsUser }
        verify(exactly = 0) { pushCommand.call() }
        verify { progressListener.onProgress(any(), any(), any()) }
        verify(exactly = 0) { repository.sshUrl }
        verify { getRepository.execute(targetTranslation, progressListener) }
    }

    @Test
    fun `test push target translation, delete origin failed`() {
        every { profile.gogsUser }.returns(mockk())

        every { repo.deleteRemote(any()) }.throws(IOException("Error deleting remote"))

        val result = PushTargetTranslation(
            context,
            profile,
            getRepository,
            directoryProvider,
            prefRepository
        ).execute(targetTranslation, progressListener)

        assertEquals(PushTargetTranslation.Status.UNKNOWN, result.status)
        assertFalse(result.status.isRejected)
        assertEquals("Error deleting remote", result.message)

        verify { profile.gogsUser }
        verify(exactly = 0) { pushCommand.call() }
        verify { repo.deleteRemote(any()) }
        verify(exactly = 0) { repo.setRemote(any(), any()) }
        verify { progressListener.onProgress(any(), any(), any()) }
        verify { repository.sshUrl }
        verify { getRepository.execute(targetTranslation, progressListener) }
    }

    @Test
    fun `test push target translation, rejected non-fast-forward`() {
        every { profile.gogsUser }.returns(mockk())

        val pushResult: PushResult = mockk()
        val refUpdate: RemoteRefUpdate = mockk {
            every { status }.returns(RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD)
            every { remoteName }.returns("test_repo")
        }
        every { pushResult.remoteUpdates }.returns(listOf(refUpdate))
        every { pushCommand.call() }.returns(listOf(pushResult))

        val result = PushTargetTranslation(
            context,
            profile,
            getRepository,
            directoryProvider,
            prefRepository
        ).execute(targetTranslation, progressListener)

        val expectedMessage = """
            Rejected non-fast-forward ${refUpdate.remoteName}
            Server details ${repository.sshUrl}
            
        """.trimIndent()

        assertEquals(PushTargetTranslation.Status.REJECTED_NON_FAST_FORWARD, result.status)
        assertTrue(result.status.isRejected)
        assertEquals(expectedMessage, result.message)

        verifySuccessCalls(refUpdate, pushResult)
    }

    @Test
    fun `test push target translation, rejected non-delete`() {
        every { profile.gogsUser }.returns(mockk())

        val pushResult: PushResult = mockk()
        val refUpdate: RemoteRefUpdate = mockk {
            every { status }.returns(RemoteRefUpdate.Status.REJECTED_NODELETE)
            every { remoteName }.returns("test_repo")
        }
        every { pushResult.remoteUpdates }.returns(listOf(refUpdate))
        every { pushCommand.call() }.returns(listOf(pushResult))

        val result = PushTargetTranslation(
            context,
            profile,
            getRepository,
            directoryProvider,
            prefRepository
        ).execute(targetTranslation, progressListener)

        val expectedMessage = """
            Rejected non-delete ${refUpdate.remoteName}
            Server details ${repository.sshUrl}
            
        """.trimIndent()

        assertEquals(PushTargetTranslation.Status.REJECTED_NODELETE, result.status)
        assertTrue(result.status.isRejected)
        assertEquals(expectedMessage, result.message)

        verifySuccessCalls(refUpdate, pushResult)
    }

    @Test
    fun `test push target translation, rejected remote changed`() {
        every { profile.gogsUser }.returns(mockk())

        val pushResult: PushResult = mockk()
        val refUpdate: RemoteRefUpdate = mockk {
            every { status }.returns(RemoteRefUpdate.Status.REJECTED_REMOTE_CHANGED)
            every { remoteName }.returns("test_repo")
        }
        every { pushResult.remoteUpdates }.returns(listOf(refUpdate))
        every { pushCommand.call() }.returns(listOf(pushResult))

        val result = PushTargetTranslation(
            context,
            profile,
            getRepository,
            directoryProvider,
            prefRepository
        ).execute(targetTranslation, progressListener)

        val expectedMessage = """
            Rejected remote changed ${refUpdate.remoteName}
            Server details ${repository.sshUrl}
            
        """.trimIndent()

        assertEquals(PushTargetTranslation.Status.REJECTED_REMOTE_CHANGED, result.status)
        assertTrue(result.status.isRejected)
        assertEquals(expectedMessage, result.message)

        verifySuccessCalls(refUpdate, pushResult)
    }

    @Test
    fun `test push target translation, rejected other reason`() {
        every { profile.gogsUser }.returns(mockk())

        val pushResult: PushResult = mockk()
        val refUpdate: RemoteRefUpdate = mockk {
            every { status }.returns(RemoteRefUpdate.Status.REJECTED_OTHER_REASON)
            every { remoteName }.returns("test_repo")
            every { message }.returns("test reason")
        }
        every { pushResult.remoteUpdates }.returns(listOf(refUpdate))
        every { pushCommand.call() }.returns(listOf(pushResult))

        val result = PushTargetTranslation(
            context,
            profile,
            getRepository,
            directoryProvider,
            prefRepository
        ).execute(targetTranslation, progressListener)

        val expectedMessage = """
            Rejected other reason detailed ${refUpdate.remoteName}
            Server details ${repository.sshUrl}
            
        """.trimIndent()

        assertEquals(PushTargetTranslation.Status.REJECTED_OTHER_REASON, result.status)
        assertTrue(result.status.isRejected)
        assertEquals(expectedMessage, result.message)

        verifySuccessCalls(refUpdate, pushResult)
    }

    @Test
    fun `test push target translation, auth failed`() {
        every { profile.gogsUser }.returns(mockk())

        val exception = TransportException(
            "An error occurred.",
            Exception(
                Exception("Auth fail")
            )
        )
        every { pushCommand.call() }.throws(exception)

        val result = PushTargetTranslation(
            context,
            profile,
            getRepository,
            directoryProvider,
            prefRepository
        ).execute(targetTranslation, progressListener)

        assertEquals(PushTargetTranslation.Status.AUTH_FAILURE, result.status)
        assertFalse(result.status.isRejected)
        assertNull(result.message)

        verifyCommonCalls()
    }

    @Test
    fun `test push target translation, remote repo not found`() {
        every { profile.gogsUser }.returns(mockk())

        val exception = TransportException(
            "An error occurred.",
            NoRemoteRepositoryException(URIish(), "No remote repository")
        )
        every { pushCommand.call() }.throws(exception)

        val result = PushTargetTranslation(
            context,
            profile,
            getRepository,
            directoryProvider,
            prefRepository
        ).execute(targetTranslation, progressListener)

        assertEquals(PushTargetTranslation.Status.NO_REMOTE_REPO, result.status)
        assertFalse(result.status.isRejected)
        assertNull(result.message)

        verifyCommonCalls()
    }

    @Test
    fun `test push target translation, push to private repo fails`() {
        every { profile.gogsUser }.returns(mockk())

        val exception = TransportException(
            "An error occurred.",
            Exception("Push to private repo is not permitted")
        )
        every { pushCommand.call() }.throws(exception)

        val result = PushTargetTranslation(
            context,
            profile,
            getRepository,
            directoryProvider,
            prefRepository
        ).execute(targetTranslation, progressListener)

        assertEquals(PushTargetTranslation.Status.AUTH_FAILURE, result.status)
        assertFalse(result.status.isRejected)
        assertNull(result.message)

        verifyCommonCalls()
    }

    @Test
    fun `test push target translation, unknown transport exception`() {
        every { profile.gogsUser }.returns(mockk())

        every { pushCommand.call() }.throws(TransportException("An error occurred."))

        val result = PushTargetTranslation(
            context,
            profile,
            getRepository,
            directoryProvider,
            prefRepository
        ).execute(targetTranslation, progressListener)

        assertEquals(PushTargetTranslation.Status.UNKNOWN, result.status)
        assertFalse(result.status.isRejected)
        assertNull(result.message)

        verifyCommonCalls()
    }

    @Test
    fun `test push target translation, out of memory error`() {
        every { profile.gogsUser }.returns(mockk())

        every { pushCommand.call() }.throws(OutOfMemoryError("Out of memory"))

        val result = PushTargetTranslation(
            context,
            profile,
            getRepository,
            directoryProvider,
            prefRepository
        ).execute(targetTranslation, progressListener)

        assertEquals(PushTargetTranslation.Status.OUT_OF_MEMORY, result.status)
        assertFalse(result.status.isRejected)
        assertNull(result.message)

        verifyCommonCalls()
    }

    @Test
    fun `test push target translation, generic exception`() {
        every { profile.gogsUser }.returns(mockk())

        every { pushCommand.call() }.throws(Exception("An error occurred."))

        val result = PushTargetTranslation(
            context,
            profile,
            getRepository,
            directoryProvider,
            prefRepository
        ).execute(targetTranslation, progressListener)

        assertEquals(PushTargetTranslation.Status.UNKNOWN, result.status)
        assertFalse(result.status.isRejected)
        assertNull(result.message)

        verifyCommonCalls()
    }

    @Test
    fun `test push target translation, base exception`() {
        every { profile.gogsUser }.returns(mockk())

        every { pushCommand.call() }.throws(Throwable("An error occurred."))

        val result = PushTargetTranslation(
            context,
            profile,
            getRepository,
            directoryProvider,
            prefRepository
        ).execute(targetTranslation, progressListener)

        assertEquals(PushTargetTranslation.Status.UNKNOWN, result.status)
        assertFalse(result.status.isRejected)
        assertNull(result.message)

        verifyCommonCalls()
    }

    private fun verifySuccessCalls(refUpdate: RemoteRefUpdate, pushResult: PushResult) {
        verifyCommonCalls()

        verify { refUpdate.status }
        verify { refUpdate.remoteName }
        verify { pushResult.remoteUpdates }
    }

    private fun verifyCommonCalls() {
        verify { profile.gogsUser }
        verify { pushCommand.call() }
        verify { repo.deleteRemote(any()) }
        verify { repo.setRemote(any(), any()) }
        verify { progressListener.onProgress(any(), any(), any()) }
        verify { repository.sshUrl }
        verify { getRepository.execute(targetTranslation, progressListener) }
    }

    private fun mockResources() {
        every { resources.getString(R.string.pref_default_git_server_port) }
            .returns("22")
        every { resources.getString(R.string.git_awaiting_report) }
            .returns("Awaiting report %s")
        every { resources.getString(R.string.git_non_existing) }
            .returns("Non existing %s")
        every { resources.getString(R.string.git_not_attempted) }
            .returns("Not attempted %s")
        every { resources.getString(R.string.git_ok) }
            .returns("OK %s")
        every { resources.getString(R.string.git_rejected_nondelete) }
            .returns("Rejected non-delete %s")
        every { resources.getString(R.string.git_rejected_nonfastforward) }
            .returns("Rejected non-fast-forward %s")
        every { resources.getString(R.string.git_rejected_other_reason) }
            .returns("Rejected other reason %s")
        every { resources.getString(R.string.git_rejected_other_reason_detailed) }
            .returns("Rejected other reason detailed %s")
        every { resources.getString(R.string.git_rejected_remote_changed) }
            .returns("Rejected remote changed %s")
        every { resources.getString(R.string.git_uptodate) }
            .returns("Up to date %s")
        every { resources.getString(R.string.git_server_details) }
            .returns("Server details %s")
        every { resources.getString(R.string.git_rejected_other_reason_detailed) }
            .returns("Rejected other reason detailed %s")
    }
}