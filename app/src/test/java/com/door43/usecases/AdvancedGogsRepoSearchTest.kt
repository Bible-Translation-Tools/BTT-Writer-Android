package com.door43.usecases

import com.door43.OnProgressListener
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.bibletranslationtools.gogsclient.Repository
import org.bibletranslationtools.gogsclient.User
import kotlinx.coroutines.test.runTest
import io.mockk.coEvery
import io.mockk.coVerify

class AdvancedGogsRepoSearchTest {

    @MockK private lateinit var searchGogsUsers: SearchGogsUsers
    @MockK private lateinit var searchGogsRepositories: SearchGogsRepositories
    @MockK private lateinit var progressListener: OnProgressListener

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        every { progressListener.onProgress(any(), any()) } just runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test search by user and repo queries`() = runTest {
        val userQuery = "test"
        val repoQuery = "_gen_"
        val limit = 1

        val user: User = mockk()
        every { user.id }.returns(1)

        coEvery { searchGogsUsers.execute(userQuery, limit, progressListener) }
            .returns(listOf(user))

        val repository: Repository = mockk()
        every { repository.name }.returns("aa_gen_text_reg")

        coEvery { searchGogsRepositories.execute(any(), repoQuery, limit, progressListener) }
            .returns(listOf(repository))

        val repositories = AdvancedGogsRepoSearch(
            searchGogsUsers,
            searchGogsRepositories
        ).execute(userQuery, repoQuery, limit, progressListener)

        assertEquals(1, repositories.size)

        verify { user.id }
        coVerify { searchGogsUsers.execute(userQuery, limit, progressListener) }
        coVerify { searchGogsRepositories.execute(any(), repoQuery, limit, progressListener) }
        verify { progressListener.onProgress(any(), "Searching for repositories") }
    }

    @Test
    fun `test search by user query only`() = runTest {
        val userQuery = "test"
        val repoQuery = "_"
        val limit = 1

        val user: User = mockk()
        every { user.id }.returns(1)

        coEvery { searchGogsUsers.execute(userQuery, limit, progressListener) }
            .returns(listOf(user))

        val repository: Repository = mockk()
        every { repository.name }.returns("aa_gen_text_reg")

        val repoQuerySlot = slot<String>()
        coEvery { searchGogsRepositories.execute(any(), capture(repoQuerySlot), limit, progressListener) }
            .returns(listOf(repository))

        val repositories = AdvancedGogsRepoSearch(
            searchGogsUsers,
            searchGogsRepositories
        ).execute(userQuery, repoQuery, limit, progressListener)

        assertEquals(1, repositories.size)
        assertEquals("Empty repo query should be replaced with _", "_", repoQuerySlot.captured)

        verify { user.id }
        coVerify { searchGogsUsers.execute(userQuery, limit, progressListener) }
        coVerify { searchGogsRepositories.execute(any(), repoQuery, limit, progressListener) }
        verify { progressListener.onProgress(any(), "Searching for repositories") }
    }

    @Test
    fun `test search by repo query`() = runTest {
        val repoQuery = "_gen_"
        val limit = 1

        val repository: Repository = mockk()
        every { repository.name }.returns("aa_gen_text_reg")

        coEvery { searchGogsRepositories.execute(0, repoQuery, limit, progressListener) }
            .returns(listOf(repository))

        val repositories = AdvancedGogsRepoSearch(
            searchGogsUsers,
            searchGogsRepositories
        ).execute("", repoQuery, limit, progressListener)

        assertEquals(1, repositories.size)

        coVerify(exactly = 0) { searchGogsUsers.execute(any(), limit, progressListener) }
        coVerify { searchGogsRepositories.execute(any(), repoQuery, limit, progressListener) }
        verify { progressListener.onProgress(any(), "Searching for repositories") }
    }

    @Test
    fun `test search with empty queries`() = runTest {
        val userQuery = ""
        val repoQuery = ""
        val limit = 1

        val repository: Repository = mockk()
        every { repository.name }.returns("aa_gen_text_reg")

        val repoQuerySlot = slot<String>()
        coEvery { searchGogsRepositories.execute(any(), capture(repoQuerySlot), limit, progressListener) }
            .returns(listOf(repository))

        val repositories = AdvancedGogsRepoSearch(
            searchGogsUsers,
            searchGogsRepositories
        ).execute(userQuery, repoQuery, limit, progressListener)

        assertEquals(1, repositories.size)
        assertEquals("Empty repo query should be replaced with _", "_", repoQuerySlot.captured)

        coVerify(exactly = 0) { searchGogsUsers.execute(any(), limit, progressListener) }
        coVerify { searchGogsRepositories.execute(any(), any(), limit, progressListener) }
        verify { progressListener.onProgress(any(), "Searching for repositories") }
    }
}