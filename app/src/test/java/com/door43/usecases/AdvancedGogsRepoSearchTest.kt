package com.door43.usecases

import com.door43.OnProgressListener
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.unfoldingword.gogsclient.Repository
import org.unfoldingword.gogsclient.User

class AdvancedGogsRepoSearchTest {

    @MockK private lateinit var submitNewLanguageRequests: SubmitNewLanguageRequests
    @MockK private lateinit var searchGogsUsers: SearchGogsUsers
    @MockK private lateinit var searchGogsRepositories: SearchGogsRepositories
    @MockK private lateinit var progressListener: OnProgressListener

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        every { progressListener.onProgress(any(), any(), any()) } just Runs
        every { submitNewLanguageRequests.execute(progressListener) } just Runs
    }

    @Test
    fun `test search by user and repo queries`() {
        val userQuery = "test"
        val repoQuery = "_gen_"
        val limit = 1

        val user: User = mockk()
        every { user.id }.returns(1)

        every { searchGogsUsers.execute(userQuery, limit, progressListener) }
            .returns(listOf(user))

        val repository: Repository = mockk()
        every { repository.name }.returns("aa_gen_text_reg")

        every { searchGogsRepositories.execute(any(), repoQuery, limit, progressListener) }
            .returns(listOf(repository))

        val repositories = AdvancedGogsRepoSearch(
            submitNewLanguageRequests,
            searchGogsUsers,
            searchGogsRepositories
        ).execute(userQuery, repoQuery, limit, progressListener)

        assertEquals(1, repositories.size)

        verify { user.id }
        verify { submitNewLanguageRequests.execute(progressListener) }
        verify { searchGogsUsers.execute(userQuery, limit, progressListener) }
        verify { searchGogsRepositories.execute(any(), repoQuery, limit, progressListener) }
        verify { progressListener.onProgress(any(), any(), "Searching for repositories") }
    }

    @Test
    fun `test search by user query only`() {
        val userQuery = "test"
        val repoQuery = "_"
        val limit = 1

        val user: User = mockk()
        every { user.id }.returns(1)

        every { searchGogsUsers.execute(userQuery, limit, progressListener) }
            .returns(listOf(user))

        val repository: Repository = mockk()
        every { repository.name }.returns("aa_gen_text_reg")

        val repoQuerySlot = slot<String>()
        every { searchGogsRepositories.execute(any(), capture(repoQuerySlot), limit, progressListener) }
            .returns(listOf(repository))

        val repositories = AdvancedGogsRepoSearch(
            submitNewLanguageRequests,
            searchGogsUsers,
            searchGogsRepositories
        ).execute(userQuery, repoQuery, limit, progressListener)

        assertEquals(1, repositories.size)
        assertEquals("Empty repo query should be replaced with _", "_", repoQuerySlot.captured)

        verify { user.id }
        verify { submitNewLanguageRequests.execute(progressListener) }
        verify { searchGogsUsers.execute(userQuery, limit, progressListener) }
        verify { searchGogsRepositories.execute(any(), repoQuery, limit, progressListener) }
        verify { progressListener.onProgress(any(), any(), "Searching for repositories") }
    }

    @Test
    fun `test search by repo query`() {
        val repoQuery = "_gen_"
        val limit = 1

        val repository: Repository = mockk()
        every { repository.name }.returns("aa_gen_text_reg")

        every { searchGogsRepositories.execute(0, repoQuery, limit, progressListener) }
            .returns(listOf(repository))

        val repositories = AdvancedGogsRepoSearch(
            submitNewLanguageRequests,
            searchGogsUsers,
            searchGogsRepositories
        ).execute("", repoQuery, limit, progressListener)

        assertEquals(1, repositories.size)

        verify { submitNewLanguageRequests.execute(progressListener) }
        verify(exactly = 0) { searchGogsUsers.execute(any(), limit, progressListener) }
        verify { searchGogsRepositories.execute(any(), repoQuery, limit, progressListener) }
        verify { progressListener.onProgress(any(), any(), "Searching for repositories") }
    }

    @Test
    fun `test search with empty queries`() {
        val userQuery = ""
        val repoQuery = ""
        val limit = 1

        val repository: Repository = mockk()
        every { repository.name }.returns("aa_gen_text_reg")

        val repoQuerySlot = slot<String>()
        every { searchGogsRepositories.execute(any(), capture(repoQuerySlot), limit, progressListener) }
            .returns(listOf(repository))

        val repositories = AdvancedGogsRepoSearch(
            submitNewLanguageRequests,
            searchGogsUsers,
            searchGogsRepositories
        ).execute(userQuery, repoQuery, limit, progressListener)

        assertEquals(1, repositories.size)
        assertEquals("Empty repo query should be replaced with _", "_", repoQuerySlot.captured)

        verify { submitNewLanguageRequests.execute(progressListener) }
        verify(exactly = 0) { searchGogsUsers.execute(any(), limit, progressListener) }
        verify { searchGogsRepositories.execute(any(), any(), limit, progressListener) }
        verify { progressListener.onProgress(any(), any(), "Searching for repositories") }
    }
}