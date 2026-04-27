package com.door43.usecases

import android.content.Context
import android.content.res.Resources
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.git.Repo
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.bibletranslationtools.gogsclient.Repository
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
import java.io.IOException

class PushTargetTranslationTest {

    @MockK private lateinit var context: Context
    @MockK private lateinit var profile: Profile
    @MockK private lateinit var getRepository: GetRepository
    @MockK private lateinit var directoryProvider: IDirectoryProvider
    @MockK private lateinit var prefRepository: IPreferenceRepository
    @MockK private lateinit var resources: Resources
    @MockK private lateinit var targetTranslation: TargetTranslation
    @MockK private lateinit var git: Git
    @MockK private lateinit var repo: Repo
    @MockK private lateinit var repository: Repository
    @MockK private lateinit var deleteCommand: DeleteBranchCommand
    @MockK private lateinit var createCommand: CreateBranchCommand
    @MockK private lateinit var pushCommand: PushCommand

    val onProgress = mockk<(Float, String?) -> Unit>(relaxed = true)

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        every { context.resources }.returns(resources)

        every {
            prefRepository.getDefaultPref(
                IPreferenceRepository.KEY_PREF_GIT_SERVER_PORT,
                any(),
                String::class.java
            )
        }.returns("22")

        every { onProgress(any(), any()) }.just(runs)
        every { repository.sshUrl }.returns("ssh://repo.git")
        coEvery { getRepository.execute(targetTranslation, onProgress) }.returns(repository)

        every { targetTranslation.commitSync() }.returns(true)
        every { targetTranslation.repo }.returns(repo)
        every { repo.git }.returns(git)
        every { git.branchDelete() }.returns(deleteCommand)
        every { git.branchCreate() }.returns(createCommand)
        every { deleteCommand.setBranchNames(any<String>()) }.returns(deleteCommand)
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
    fun `test push target translation authorized`() = runTest {
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
        ).execute(targetTranslation, onProgress)

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
    fun `test push target translation not authorized`() = runTest {
        every { profile.gogsUser }.returns(null)

        val result = PushTargetTranslation(
            context,
            profile,
            getRepository,
            directoryProvider,
            prefRepository
        ).execute(targetTranslation, onProgress)

        assertEquals(PushTargetTranslation.Status.AUTH_FAILURE, result.status)
        assertFalse(result.status.isRejected)
        assertNull(result.message)

        verify { profile.gogsUser }
        verify(exactly = 0) { pushCommand.call() }
        verify(exactly = 0) { onProgress(any(), any()) }
        verify(exactly = 0) { repository.sshUrl }
        coVerify(exactly = 0) { getRepository.execute(targetTranslation, onProgress) }
    }

    @Test
    fun `test push target translation, remote repo not found and not created`() = runTest {
        every { profile.gogsUser }.returns(mockk())

        coEvery { getRepository.execute(any(), any()) }.returns(null)

        val result = PushTargetTranslation(
            context,
            profile,
            getRepository,
            directoryProvider,
            prefRepository
        ).execute(targetTranslation, onProgress)

        assertEquals(PushTargetTranslation.Status.UNKNOWN, result.status)
        assertFalse(result.status.isRejected)
        assertNull(result.message)

        verify { profile.gogsUser }
        verify(exactly = 0) { pushCommand.call() }
        verify(exactly = 0) { repository.sshUrl }
        coVerify { getRepository.execute(targetTranslation, onProgress) }
    }

    @Test
    fun `test push target translation, translation commit failed`() = runTest {
        every { profile.gogsUser }.returns(mockk())

        coEvery { getRepository.execute(any(), any()) }.returns(null)
        every { targetTranslation.commitSync() }.throws(Exception("Error committing translation"))

        val result = PushTargetTranslation(
            context,
            profile,
            getRepository,
            directoryProvider,
            prefRepository
        ).execute(targetTranslation, onProgress)

        assertEquals(PushTargetTranslation.Status.UNKNOWN, result.status)
        assertFalse(result.status.isRejected)
        assertNull(result.message)

        verify { profile.gogsUser }
        verify(exactly = 0) { pushCommand.call() }
        verify(exactly = 0) { repository.sshUrl }
        coVerify { getRepository.execute(targetTranslation, onProgress) }
    }

    @Test
    fun `test push target translation, delete origin failed`() = runTest {
        every { profile.gogsUser }.returns(mockk())

        every { repo.deleteRemote(any()) }.throws(IOException("Error deleting remote"))

        val result = PushTargetTranslation(
            context,
            profile,
            getRepository,
            directoryProvider,
            prefRepository
        ).execute(targetTranslation, onProgress)

        assertEquals(PushTargetTranslation.Status.UNKNOWN, result.status)
        assertFalse(result.status.isRejected)
        assertEquals("Error deleting remote", result.message)

        verify { profile.gogsUser }
        verify(exactly = 0) { pushCommand.call() }
        verify { repo.deleteRemote(any()) }
        verify(exactly = 0) { repo.setRemote(any(), any()) }
        verify { onProgress(any(), any()) }
        verify { repository.sshUrl }
        coVerify { getRepository.execute(targetTranslation, onProgress) }
    }

    @Test
    fun `test push target translation, rejected non-fast-forward`() = runTest {
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
        ).execute(targetTranslation, onProgress)

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
    fun `test push target translation, rejected non-delete`() = runTest {
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
        ).execute(targetTranslation, onProgress)

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
    fun `test push target translation, rejected remote changed`() = runTest {
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
        ).execute(targetTranslation, onProgress)

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
    fun `test push target translation, rejected other reason`() = runTest {
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
        ).execute(targetTranslation, onProgress)

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
    fun `test push target translation, auth failed`() = runTest {
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
        ).execute(targetTranslation, onProgress)

        assertEquals(PushTargetTranslation.Status.AUTH_FAILURE, result.status)
        assertFalse(result.status.isRejected)
        assertNull(result.message)

        verifyCommonCalls()
    }

    @Test
    fun `test push target translation, remote repo not found`() = runTest {
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
        ).execute(targetTranslation, onProgress)

        assertEquals(PushTargetTranslation.Status.NO_REMOTE_REPO, result.status)
        assertFalse(result.status.isRejected)
        assertNull(result.message)

        verifyCommonCalls()
    }

    @Test
    fun `test push target translation, push to private repo fails`() = runTest {
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
        ).execute(targetTranslation, onProgress)

        assertEquals(PushTargetTranslation.Status.AUTH_FAILURE, result.status)
        assertFalse(result.status.isRejected)
        assertNull(result.message)

        verifyCommonCalls()
    }

    @Test
    fun `test push target translation, unknown transport exception`() = runTest {
        every { profile.gogsUser }.returns(mockk())

        every { pushCommand.call() }.throws(TransportException("An error occurred."))

        val result = PushTargetTranslation(
            context,
            profile,
            getRepository,
            directoryProvider,
            prefRepository
        ).execute(targetTranslation, onProgress)

        assertEquals(PushTargetTranslation.Status.UNKNOWN, result.status)
        assertFalse(result.status.isRejected)
        assertNull(result.message)

        verifyCommonCalls()
    }

    @Test
    fun `test push target translation, out of memory error`() = runTest {
        every { profile.gogsUser }.returns(mockk())

        every { pushCommand.call() }.throws(OutOfMemoryError("Out of memory"))

        val result = PushTargetTranslation(
            context,
            profile,
            getRepository,
            directoryProvider,
            prefRepository
        ).execute(targetTranslation, onProgress)

        assertEquals(PushTargetTranslation.Status.OUT_OF_MEMORY, result.status)
        assertFalse(result.status.isRejected)
        assertNull(result.message)

        verifyCommonCalls()
    }

    @Test
    fun `test push target translation, generic exception`() = runTest {
        every { profile.gogsUser }.returns(mockk())

        every { pushCommand.call() }.throws(Exception("An error occurred."))

        val result = PushTargetTranslation(
            context,
            profile,
            getRepository,
            directoryProvider,
            prefRepository
        ).execute(targetTranslation, onProgress)

        assertEquals(PushTargetTranslation.Status.UNKNOWN, result.status)
        assertFalse(result.status.isRejected)
        assertNull(result.message)

        verifyCommonCalls()
    }

    @Test
    fun `test push target translation, base exception`() = runTest {
        every { profile.gogsUser }.returns(mockk())

        every { pushCommand.call() }.throws(Throwable("An error occurred."))

        val result = PushTargetTranslation(
            context,
            profile,
            getRepository,
            directoryProvider,
            prefRepository
        ).execute(targetTranslation, onProgress)

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
        verify { onProgress(any(), any()) }
        verify { repository.sshUrl }
        coVerify { getRepository.execute(targetTranslation, onProgress) }
    }

    private fun mockResources() {
        every { resources.getString(R.string.pref_default_git_server_port) }
            .returns("22")
        every { resources.getString(R.string.git_awaiting_report, any()) } answers {
            "Awaiting report %s".format((args[1] as Array<*>)[0])
        }
        every { resources.getString(R.string.git_non_existing, any()) } answers {
            "Non existing %s".format((args[1] as Array<*>)[0])
        }
        every { resources.getString(R.string.git_not_attempted, any()) } answers {
            "Not attempted %s".format((args[1] as Array<*>)[0])
        }
        every { resources.getString(R.string.git_ok, any()) } answers {
            "OK %s".format((args[1] as Array<*>)[0])
        }
        every { resources.getString(R.string.git_rejected_nondelete, any()) } answers {
            "Rejected non-delete %s".format((args[1] as Array<*>)[0])
        }
        every { resources.getString(R.string.git_rejected_nonfastforward, any()) } answers {
            "Rejected non-fast-forward %s".format((args[1] as Array<*>)[0])
        }
        every { resources.getString(R.string.git_rejected_other_reason, any()) } answers {
            "Rejected other reason %s".format((args[1] as Array<*>)[0])
        }
        every { resources.getString(R.string.git_rejected_other_reason_detailed, any(), any()) } answers {
            "Rejected other reason detailed %s".format((args[1] as Array<*>)[0])
        }
        every { resources.getString(R.string.git_rejected_remote_changed, any()) } answers {
            "Rejected remote changed %s".format((args[1] as Array<*>)[0])
        }
        every { resources.getString(R.string.git_uptodate, any()) } answers {
            "Up to date %s".format((args[1] as Array<*>)[0])
        }
        every { resources.getString(R.string.git_server_details, any()) } answers {
            "Server details %s".format((args[1] as Array<*>)[0])
        }
    }
}