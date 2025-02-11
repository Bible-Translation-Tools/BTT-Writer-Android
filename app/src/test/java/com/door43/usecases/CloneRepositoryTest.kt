package com.door43.usecases

import android.content.Context
import android.content.res.Resources
import com.door43.OnProgressListener
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.R
import com.door43.util.FileUtilities
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.eclipse.jgit.api.CloneCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.errors.NoRemoteRepositoryException
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.transport.URIish
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File

class CloneRepositoryTest {

    @MockK private lateinit var context: Context
    @MockK private lateinit var prefRepository: IPreferenceRepository
    @MockK private lateinit var directoryProvider: IDirectoryProvider
    @MockK private lateinit var progressListener: OnProgressListener
    @MockK private lateinit var resources: Resources
    @MockK private lateinit var cloneCommand: CloneCommand
    @MockK private lateinit var git: Git

    private val repoUrl = "/aa_gen_text_reg"
    private val repoDir: File = mockk()

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        mockkStatic(FileUtilities::class)
        mockkStatic(Git::class)

        every { FileUtilities.deleteQuietly(any()) }.returns(true)
        every { Git.cloneRepository() }.returns(cloneCommand)
        every { cloneCommand.setTransportConfigCallback(any()) }.returns(cloneCommand)
        every { cloneCommand.setURI(any()) }.returns(cloneCommand)
        every { cloneCommand.setDirectory(any()) }.returns(cloneCommand)

        every { context.resources }.returns(resources)
        every {
            prefRepository.getDefaultPref(any(), any(), String::class.java)
        }.returns("22")
        every { progressListener.onProgress(any(), any(), any()) } just runs

        every { resources.getString(R.string.downloading) }.returns("Downloading...")
        every { resources.getString(R.string.pref_default_git_server_port) }.returns("22")
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test clone repository successfully`() {
        every { repoDir.name }.returns("temp_dir")
        every { repoDir.path }.returns("/temp_dir")
        every { directoryProvider.createTempDir(any()) }.returns(repoDir)

        val repository: Repository = mockk()

        every { cloneCommand.call() }.returns(git)
        every { git.repository }.returns(repository)
        every { repository.close() } just runs

        val result = CloneRepository(context, prefRepository, directoryProvider)
            .execute(repoUrl, progressListener)

        verifyCommonResult(result)

        assertEquals(CloneRepository.Status.SUCCESS, result.status)
        assertEquals("/temp_dir", result.cloneDir?.path)
        assertEquals("temp_dir", result.cloneDir?.name)

        verify { git.repository }
        verify { repository.close() }
        verify(exactly = 0) { FileUtilities.deleteQuietly(any()) }
    }

    @Test
    fun `test clone repository with Auth failure exception`() {
        every { repoDir.name }.returns("temp_dir")
        every { repoDir.path }.returns("/temp_dir")
        every { directoryProvider.createTempDir(any()) }.returns(repoDir)

        val exception = TransportException(
            "An error occurred.",
            Throwable("Sub exception", Throwable("Auth fail"))
        )

        every { cloneCommand.call() }.throws(exception)

        val result = CloneRepository(context, prefRepository, directoryProvider)
            .execute(repoUrl, progressListener)

        verifyCommonResult(result)

        assertEquals(CloneRepository.Status.AUTH_FAILURE, result.status)
        assertNull(result.cloneDir)

        verify(exactly = 0) { git.repository }
        verify { FileUtilities.deleteQuietly(any()) }
    }

    @Test
    fun `test clone private repository throws exception`() {
        every { repoDir.name }.returns("temp_dir")
        every { repoDir.path }.returns("/temp_dir")
        every { directoryProvider.createTempDir(any()) }.returns(repoDir)

        val exception = TransportException(
            "An error occurred.",
            Throwable("Cloning private repository is not permitted")
        )

        every { cloneCommand.call() }.throws(exception)

        val result = CloneRepository(context, prefRepository, directoryProvider)
            .execute(repoUrl, progressListener)

        verifyCommonResult(result)

        assertEquals(CloneRepository.Status.AUTH_FAILURE, result.status)
        assertNull(result.cloneDir)

        verify(exactly = 0) { git.repository }
        verify { FileUtilities.deleteQuietly(any()) }
    }

    @Test
    fun `test no remote repository throws exception`() {
        every { repoDir.name }.returns("temp_dir")
        every { repoDir.path }.returns("/temp_dir")
        every { directoryProvider.createTempDir(any()) }.returns(repoDir)

        val exception = TransportException(
            "An error occurred.",
            NoRemoteRepositoryException(URIish(), "Remote repository not found")
        )

        every { cloneCommand.call() }.throws(exception)

        val result = CloneRepository(context, prefRepository, directoryProvider)
            .execute(repoUrl, progressListener)

        verifyCommonResult(result)

        assertEquals(CloneRepository.Status.NO_REMOTE_REPO, result.status)
        assertNull(result.cloneDir)

        verify(exactly = 0) { git.repository }
        verify { FileUtilities.deleteQuietly(any()) }
    }

    @Test
    fun `test out of memory throws exception`() {
        every { repoDir.name }.returns("temp_dir")
        every { repoDir.path }.returns("/temp_dir")
        every { directoryProvider.createTempDir(any()) }.returns(repoDir)

        val exception = OutOfMemoryError("Out of memory!")

        every { cloneCommand.call() }.throws(exception)

        val result = CloneRepository(context, prefRepository, directoryProvider)
            .execute(repoUrl, progressListener)

        verifyCommonResult(result)

        assertEquals(CloneRepository.Status.OUT_OF_MEMORY, result.status)
        assertNull(result.cloneDir)

        verify(exactly = 0) { git.repository }
        verify { FileUtilities.deleteQuietly(any()) }
    }

    @Test
    fun `test generic Throwable exception`() {
        every { repoDir.name }.returns("temp_dir")
        every { repoDir.path }.returns("/temp_dir")
        every { directoryProvider.createTempDir(any()) }.returns(repoDir)

        val exception = Throwable("An error occurred.")

        every { cloneCommand.call() }.throws(exception)

        val result = CloneRepository(context, prefRepository, directoryProvider)
            .execute(repoUrl, progressListener)

        verifyCommonResult(result)

        assertEquals(CloneRepository.Status.UNKNOWN, result.status)
        assertNull(result.cloneDir)

        verify(exactly = 0) { git.repository }
        verify { FileUtilities.deleteQuietly(any()) }
    }

    @Test
    fun `test generic Exception exception`() {
        every { repoDir.name }.returns("temp_dir")
        every { repoDir.path }.returns("/temp_dir")
        every { directoryProvider.createTempDir(any()) }.returns(repoDir)

        val exception = Exception("An error occurred.")

        every { cloneCommand.call() }.throws(exception)

        val result = CloneRepository(context, prefRepository, directoryProvider)
            .execute(repoUrl, progressListener)

        verifyCommonResult(result)

        assertEquals(CloneRepository.Status.UNKNOWN, result.status)
        assertNull(result.cloneDir)

        verify(exactly = 0) { git.repository }
        verify { FileUtilities.deleteQuietly(any()) }
    }

    private fun verifyCommonResult(result: CloneRepository.Result) {
        assertEquals(repoUrl, result.cloneUrl)

        verify { Git.cloneRepository() }
        verify { cloneCommand.setTransportConfigCallback(any()) }
        verify { cloneCommand.setURI(any()) }
        verify { cloneCommand.setDirectory(any()) }

        verify { context.resources }
        verify { prefRepository.getDefaultPref(any(), any(), String::class.java) }
        verify { progressListener.onProgress(any(), any(), any()) }

        verify { resources.getString(R.string.downloading) }
        verify { resources.getString(R.string.pref_default_git_server_port) }

        verify { directoryProvider.createTempDir(any()) }
        verify { cloneCommand.call() }
    }
}