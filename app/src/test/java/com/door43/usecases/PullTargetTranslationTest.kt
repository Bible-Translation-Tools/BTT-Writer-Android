package com.door43.usecases

import android.content.Context
import android.content.res.Resources
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.manifest.Manifest
import com.door43.translationstudio.core.manifest.ManifestAccessor
import com.door43.translationstudio.git.Repo
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.bibletranslationtools.gogsclient.Repository
import org.eclipse.jgit.api.CheckoutCommand
import org.eclipse.jgit.api.CreateBranchCommand
import org.eclipse.jgit.api.DeleteBranchCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.api.PullCommand
import org.eclipse.jgit.api.PullResult
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.errors.NoRemoteRepositoryException
import org.eclipse.jgit.merge.MergeStrategy
import org.eclipse.jgit.transport.URIish
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException

class PullTargetTranslationTest {

    @MockK private lateinit var context: Context
    @MockK private lateinit var getRepository: GetRepository
    @MockK private lateinit var profile: Profile
    @MockK private lateinit var prefRepository: IPreferenceRepository
    @MockK private lateinit var directoryProvider: IDirectoryProvider
    @MockK private lateinit var targetTranslation: TargetTranslation
    @MockK private lateinit var git: Git
    @MockK private lateinit var repo: Repo
    @MockK private lateinit var resources: Resources
    @MockK private lateinit var deleteCommand: DeleteBranchCommand
    @MockK private lateinit var createCommand: CreateBranchCommand
    @MockK private lateinit var pullCommand: PullCommand
    @MockK private lateinit var checkoutCommand: CheckoutCommand
    @MockK private lateinit var manifestAccessor: ManifestAccessor

    val onProgress = mockk<(Float, String?) -> Unit>(relaxed = true)

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        every { context.resources }.returns(resources)

        mockkObject(TargetTranslation)
        mockkConstructor(ManifestAccessor::class)

        every { manifestAccessor.manifest }.returns(mockk())
        every { manifestAccessor.reload() }.just(runs)
        every { manifestAccessor.save() }.just(runs)
        every { manifestAccessor.save(any()) }.just(runs)

        every {
            prefRepository.getDefaultPref(
                IPreferenceRepository.KEY_PREF_GIT_SERVER_PORT,
                any(),
                String::class.java
            )
        }.returns("22")

        every { targetTranslation.manifestAccessor }.returns(manifestAccessor)
        every { targetTranslation.manifest }.returns(mockk())
        every { targetTranslation.commitSync() }.returns(true)

        every { git.branchDelete() }.returns(deleteCommand)
        every { git.branchCreate() }.returns(createCommand)
        every { git.pull() }.returns(pullCommand)
        every { git.checkout() }.returns(checkoutCommand)

        every { deleteCommand.setBranchNames(any<String>()) }.returns(deleteCommand)
        every { deleteCommand.setForce(any()) }.returns(deleteCommand)
        every { deleteCommand.call() }.returns(listOf())

        every { createCommand.setName(any()) }.returns(createCommand)
        every { createCommand.setForce(any()) }.returns(createCommand)
        every { createCommand.call() }.returns(mockk())

        every { pullCommand.setTransportConfigCallback(any()) }.returns(pullCommand)
        every { pullCommand.setRemote(any()) }.returns(pullCommand)
        every { pullCommand.setStrategy(any()) }.returns(pullCommand)
        every { pullCommand.setRemoteBranchName(any()) }.returns(pullCommand)

        every { checkoutCommand.setStage(any()) }.returns(checkoutCommand)
        every { checkoutCommand.addPath(any()) }.returns(checkoutCommand)
        every { checkoutCommand.call() }.returns(mockk())

        every { repo.git }.returns(git)
        every { repo.deleteRemote(any()) }.just(runs)
        every { repo.setRemote(any(), any()) }.just(runs)

        every { resources.getString(R.string.pref_default_git_server_port) }
            .returns("22")
        every { context.getString(R.string.auth_failure_retry) }
            .returns("Auth failure.")

    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test pull target translation repository authorized new repo`() = runTest {
        every { profile.gogsUser }.returns(mockk())

        val repository: Repository = mockk {
            every { sshUrl }.returns("ssh://repo.git")
        }
        coEvery { getRepository.execute(any(), any()) }.returns(repository)

        every { targetTranslation.repo }.returns(repo)
        every { targetTranslation.path }.returns(mockk())

        val pullResult: PullResult = mockk()
        val mergeResult: MergeResult = mockk()
        every { mergeResult.conflicts }.returns(mapOf())
        every { pullResult.mergeResult }.returns(mergeResult)
        every { pullCommand.call() }.returns(pullResult)

        val result = PullTargetTranslation(
            context,
            getRepository,
            profile,
            prefRepository,
            directoryProvider
        ).execute(
            targetTranslation,
            MergeStrategy.RECURSIVE,
            null,
            onProgress
        )

        assertEquals(PullTargetTranslation.Status.UP_TO_DATE, result.status)
        assertEquals("Pulled Successfully!", result.message)

        verify { onProgress(any(), "Downloading updates") }
        verify { profile.gogsUser }
        verify { repository.sshUrl }
        coVerify { getRepository.execute(any(), any()) }
        verify { targetTranslation.repo }
        verify { mergeResult.conflicts }
        verify { pullResult.mergeResult }
        verify { pullCommand.call() }
    }

    @Test
    fun `test pull target translation can't get or create repository`() = runTest {
        every { profile.gogsUser }.returns(mockk())

        coEvery { getRepository.execute(any(), any()) }.returns(null)

        every { targetTranslation.repo }.returns(repo)

        val result = PullTargetTranslation(
            context,
            getRepository,
            profile,
            prefRepository,
            directoryProvider
        ).execute(
            targetTranslation,
            MergeStrategy.RECURSIVE,
            null,
            onProgress
        )

        assertEquals(PullTargetTranslation.Status.UNKNOWN, result.status)
        assertNull(result.message)

        verify { profile.gogsUser }
        coVerify { getRepository.execute(any(), any()) }
        verify { targetTranslation.repo }
        verify(exactly = 0) { targetTranslation.path }
        verify(exactly = 0) { pullCommand.call() }
    }

    @Test
    fun `test pull target translation not authorized`() = runTest {
        every { profile.gogsUser }.returns(null)

        val result = PullTargetTranslation(
            context,
            getRepository,
            profile,
            prefRepository,
            directoryProvider
        ).execute(
            targetTranslation,
            MergeStrategy.RECURSIVE,
            null,
            onProgress
        )

        assertEquals(PullTargetTranslation.Status.AUTH_FAILURE, result.status)
        assertEquals("Auth failure.", result.message)

        verify { profile.gogsUser }
        verify(exactly = 0) {
            onProgress(any(), "Downloading updates")
        }
        coVerify(exactly = 0) { getRepository.execute(any(), any()) }
        verify(exactly = 0) { targetTranslation.repo }
        verify(exactly = 0) { targetTranslation.path }
        verify(exactly = 0) { pullCommand.call() }
    }

    @Test
    fun `test pull target translation repository update origin failed`() = runTest {
        every { profile.gogsUser }.returns(mockk())

        val repository: Repository = mockk {
            every { sshUrl }.returns("ssh://repo.git")
        }
        coEvery { getRepository.execute(any(), any()) }.returns(repository)

        every { targetTranslation.repo }.returns(repo)
        every { targetTranslation.path }.returns(mockk())

        every { repo.deleteRemote(any()) }.throws(IOException("Delete origin failed."))

        val result = PullTargetTranslation(
            context,
            getRepository,
            profile,
            prefRepository,
            directoryProvider
        ).execute(
            targetTranslation,
            MergeStrategy.RECURSIVE,
            null,
            onProgress
        )

        assertEquals(PullTargetTranslation.Status.UNKNOWN, result.status)
        assertEquals("Delete origin failed.", result.message)

        verify { onProgress(any(), "Downloading updates") }
        verify { profile.gogsUser }
        verify { repository.sshUrl }
        coVerify { getRepository.execute(any(), any()) }
        verify { targetTranslation.repo }
        verify { repo.deleteRemote(any()) }
        verify(exactly = 0) { targetTranslation.path }
        verify(exactly = 0) { pullCommand.call() }
    }

    @Test
    fun `test pull target translation repository with merge conflicts`() = runTest {
        every { profile.gogsUser }.returns(mockk())

        val repository: Repository = mockk {
            every { sshUrl }.returns("ssh://repo.git")
        }
        coEvery { getRepository.execute(any(), any()) }.returns(repository)

        every { targetTranslation.repo }.returns(repo)
        every { targetTranslation.path }.returns(mockk())

        val pullResult: PullResult = mockk()
        val mergeResult: MergeResult = mockk()
        val conflicts = mapOf(
            "manifest.json" to arrayOf(intArrayOf()),
            "LICENSE.md" to arrayOf(intArrayOf()),
            "01.txt" to arrayOf(intArrayOf())
        )
        every { mergeResult.conflicts }.returns(conflicts)
        every { pullResult.mergeResult }.returns(mergeResult)
        every { pullCommand.call() }.returns(pullResult)

        val localManifest: Manifest = mockk()
        every { targetTranslation.mergeManifests(any()) }.returns(localManifest)

        val result = PullTargetTranslation(
            context,
            getRepository,
            profile,
            prefRepository,
            directoryProvider
        ).execute(
            targetTranslation,
            MergeStrategy.RECURSIVE,
            null,
            onProgress
        )

        assertEquals(PullTargetTranslation.Status.MERGE_CONFLICTS, result.status)
        assertEquals("Pulled Successfully!", result.message)

        verify { onProgress(any(), "Downloading updates") }
        verify { profile.gogsUser }
        verify { repository.sshUrl }
        coVerify { getRepository.execute(any(), any()) }
        verify { targetTranslation.repo }
        verify { mergeResult.conflicts }
        verify { pullResult.mergeResult }
        verify { pullCommand.call() }
        verify(exactly = 2) { git.checkout() }
        verify { targetTranslation.mergeManifests(any()) }
    }

    @Test
    fun `test pull target translation, auth failed`() = runTest {
        every { profile.gogsUser }.returns(mockk())

        val repository: Repository = mockk {
            every { sshUrl }.returns("ssh://repo.git")
        }
        coEvery { getRepository.execute(any(), any()) }.returns(repository)

        every { targetTranslation.repo }.returns(repo)
        every { targetTranslation.path }.returns(mockk())

        val exception = TransportException(
            "An error occurred.",
            Exception(
                Exception("Auth fail")
            )
        )
        every { pullCommand.call() }.throws(exception)

        val result = PullTargetTranslation(
            context,
            getRepository,
            profile,
            prefRepository,
            directoryProvider
        ).execute(
            targetTranslation,
            MergeStrategy.RECURSIVE,
            null,
            onProgress
        )

        assertEquals(PullTargetTranslation.Status.AUTH_FAILURE, result.status)
        assertNull(result.message)

        verify { onProgress(any(), "Downloading updates") }
        verify { profile.gogsUser }
        verify { repository.sshUrl }
        coVerify { getRepository.execute(any(), any()) }
        verify { targetTranslation.repo }
        verify { pullCommand.call() }
    }

    @Test
    fun `test pull target translation, remote repo not found`() = runTest {
        every { profile.gogsUser }.returns(mockk())

        val repository: Repository = mockk {
            every { sshUrl }.returns("ssh://repo.git")
        }
        coEvery { getRepository.execute(any(), any()) }.returns(repository)

        every { targetTranslation.repo }.returns(repo)
        every { targetTranslation.path }.returns(mockk())

        val exception = TransportException(
            "An error occurred.",
            NoRemoteRepositoryException(URIish(), "Remote repo not found")
        )
        every { pullCommand.call() }.throws(exception)

        val result = PullTargetTranslation(
            context,
            getRepository,
            profile,
            prefRepository,
            directoryProvider
        ).execute(
            targetTranslation,
            MergeStrategy.RECURSIVE,
            null,
            onProgress
        )

        assertEquals(PullTargetTranslation.Status.NO_REMOTE_REPO, result.status)
        assertNull(result.message)

        verify { onProgress(any(), "Downloading updates") }
        verify { profile.gogsUser }
        verify { repository.sshUrl }
        coVerify { getRepository.execute(any(), any()) }
        verify { targetTranslation.repo }
        verify { pullCommand.call() }
    }

    @Test
    fun `test pull target translation, remote repo not found 2`() = runTest {
        every { profile.gogsUser }.returns(mockk())

        val repository: Repository = mockk {
            every { sshUrl }.returns("ssh://repo.git")
        }
        coEvery { getRepository.execute(any(), any()) }.returns(repository)

        every { targetTranslation.repo }.returns(repo)
        every { targetTranslation.path }.returns(mockk())

        val exception = Exception(
            NoRemoteRepositoryException(URIish(), "Remote repo not found")
        )
        every { pullCommand.call() }.throws(exception)

        val result = PullTargetTranslation(
            context,
            getRepository,
            profile,
            prefRepository,
            directoryProvider
        ).execute(
            targetTranslation,
            MergeStrategy.RECURSIVE,
            null,
            onProgress
        )

        assertEquals(PullTargetTranslation.Status.NO_REMOTE_REPO, result.status)
        assertNull(result.message)

        verify { onProgress(any(), "Downloading updates") }
        verify { profile.gogsUser }
        verify { repository.sshUrl }
        coVerify { getRepository.execute(any(), any()) }
        coVerify { targetTranslation.repo }
        verify { pullCommand.call() }
    }

    @Test
    fun `test pull target translation, out of memory error`() = runTest {
        every { profile.gogsUser }.returns(mockk())

        val repository: Repository = mockk {
            every { sshUrl }.returns("ssh://repo.git")
        }
        coEvery { getRepository.execute(any(), any()) }.returns(repository)

        every { targetTranslation.repo }.returns(repo)
        every { targetTranslation.path }.returns(mockk())

        every { pullCommand.call() }.throws(OutOfMemoryError("An error occurred."))

        val result = PullTargetTranslation(
            context,
            getRepository,
            profile,
            prefRepository,
            directoryProvider
        ).execute(
            targetTranslation,
            MergeStrategy.RECURSIVE,
            null,
            onProgress
        )

        assertEquals(PullTargetTranslation.Status.OUT_OF_MEMORY, result.status)
        assertNull(result.message)

        verify { onProgress(any(), "Downloading updates") }
        verify { profile.gogsUser }
        verify { repository.sshUrl }
        coVerify { getRepository.execute(any(), any()) }
        verify { targetTranslation.repo }
        verify { pullCommand.call() }
    }

    @Test
    fun `test pull target translation, generic error`() = runTest {
        every { profile.gogsUser }.returns(mockk())

        val repository: Repository = mockk {
            every { sshUrl }.returns("ssh://repo.git")
        }
        coEvery { getRepository.execute(any(), any()) }.returns(repository)

        every { targetTranslation.repo }.returns(repo)
        every { targetTranslation.path }.returns(mockk())

        every { pullCommand.call() }.throws(Exception("An error occurred."))

        val result = PullTargetTranslation(
            context,
            getRepository,
            profile,
            prefRepository,
            directoryProvider
        ).execute(
            targetTranslation,
            MergeStrategy.RECURSIVE,
            null,
            onProgress
        )

        assertEquals(PullTargetTranslation.Status.UNKNOWN, result.status)
        assertNull(result.message)

        verify { onProgress(any(), "Downloading updates") }
        verify { profile.gogsUser }
        verify { repository.sshUrl }
        coVerify { getRepository.execute(any(), any()) }
        verify { targetTranslation.repo }
        verify { pullCommand.call() }
    }
}