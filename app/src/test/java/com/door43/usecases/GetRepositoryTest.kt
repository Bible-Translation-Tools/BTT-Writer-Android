package com.door43.usecases

import com.door43.OnProgressListener
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.unfoldingword.gogsclient.Repository
import org.unfoldingword.gogsclient.User

class GetRepositoryTest {

    @MockK private lateinit var createRepository: CreateRepository
    @MockK private lateinit var searchRepository: SearchGogsRepositories
    @MockK private lateinit var profile: Profile
    @MockK private lateinit var progressListener: OnProgressListener
    @MockK private lateinit var targetTranslation: TargetTranslation

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        every { createRepository.execute(any(), any()) }.returns(true)
        every { progressListener.onProgress(any(), any(), any()) }.just(runs)

        every { targetTranslation.id }.returns("aa_gen_text_reg")
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test get repository successful`() {
        val user: User = mockk()
        every { user.id }.returns(1)
        every { user.username }.returns("test")
        every { profile.gogsUser }.returns(user)

        every { searchRepository.execute(any(), any(), any(), any()) }
            .returns(getRepositories(user, targetTranslation.id))

        val repository = GetRepository(createRepository, searchRepository, profile)
            .execute(targetTranslation, progressListener)

        assertNotNull(repository)
        requireNotNull(repository)

        assertEquals("aa_gen_text_reg", repository.name)
        assertEquals(1, repository.owner.id)
        assertEquals("test", repository.owner.username)
        assertEquals(222, repository.id)

        verify { user.id }
        verify { user.username }
        verify { profile.gogsUser }
        verify { searchRepository.execute(any(), any(), any(), any()) }
        verify { createRepository.execute(any(), any()) }
        verify { progressListener.onProgress(any(), any(), any()) }
        verify { targetTranslation.id }
    }

    @Test
    fun `test get repository not found exact name`() {
        val user: User = mockk()
        every { user.id }.returns(1)
        every { user.username }.returns("test")
        every { profile.gogsUser }.returns(user)

        every { searchRepository.execute(any(), any(), any(), any()) }
            .returns(getRepositories(user, "aa_gen_text_reg_l2"))

        val repository = GetRepository(createRepository, searchRepository, profile)
            .execute(targetTranslation, progressListener)

        assertNull(repository)

        verify { user.id }
        verify { user.username }
        verify { profile.gogsUser }
        verify { searchRepository.execute(any(), any(), any(), any()) }
        verify { createRepository.execute(any(), any()) }
        verify { progressListener.onProgress(any(), any(), any()) }
        verify { targetTranslation.id }
    }

    @Test
    fun `test get repository found no repos`() {
        val user: User = mockk()
        every { user.id }.returns(1)
        every { user.username }.returns("test")
        every { profile.gogsUser }.returns(user)

        every { searchRepository.execute(any(), any(), any(), any()) }
            .returns(listOf())

        val repository = GetRepository(createRepository, searchRepository, profile)
            .execute(targetTranslation, progressListener)

        assertNull(repository)

        verify { profile.gogsUser }
        verify { searchRepository.execute(any(), any(), any(), any()) }
        verify { createRepository.execute(any(), any()) }
        verify { progressListener.onProgress(any(), any(), any()) }
        verify { targetTranslation.id }
    }

    private fun getRepositories(owner: User, repoName: String): List<Repository> {
        val repo1 = """
            {
                "id": 111,
                "name": "aa_gen_text_reg_l3",
                "html_url": "http://example.com/aa_gen_text_reg_l3",
                "clone_url": "http://example.com/aa_gen_text_reg_l3",
                "ssh_url": "ssh://example.com/aa_gen_text_reg_l3.git",
                "isPrivate": false,
                "owner": {
                    "id": ${owner.id},
                    "username": "${owner.username}"
                }
            }
        """.trimIndent()

        val repo2 = """
            {
                "id": 222,
                "name": "$repoName",
                "html_url": "http://example.com/$repoName",
                "clone_url": "http://example.com/$repoName",
                "ssh_url": "ssh://example.com/$repoName.git",
                "isPrivate": false,
                "owner": {
                    "id": ${owner.id},
                    "username": "${owner.username}"
                }
            }
        """.trimIndent()
        return listOf(
            Repository.fromJSON(JSONObject(repo1)),
            Repository.fromJSON(JSONObject(repo2))
        )
    }
}