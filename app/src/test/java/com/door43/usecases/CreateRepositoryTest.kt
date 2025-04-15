package com.door43.usecases

import android.content.Context
import android.content.res.Resources
import com.door43.OnProgressListener
import com.door43.TestUtils
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.R
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
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.unfoldingword.gogsclient.Token
import org.unfoldingword.gogsclient.User

class CreateRepositoryTest {

    @MockK private lateinit var context: Context
    @MockK private lateinit var prefRepository: IPreferenceRepository
    @MockK private lateinit var profile: Profile
    @MockK private lateinit var targetTranslation: TargetTranslation
    @MockK private lateinit var progressListener: OnProgressListener
    @MockK private lateinit var resources: Resources

    private val server = MockWebServer()
    private val apiUrl = server.url("/api").toString()

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        every { context.resources }.returns(resources)

        every { progressListener.onProgress(any(), any(), any()) }.just(runs)
        every { prefRepository.getDefaultPref(any(), any(), String::class.java) }.returns(apiUrl)

        every { targetTranslation.id }.returns("aa_gen_text_reg")

        every { resources.getString(R.string.pref_default_gogs_api) }.returns(apiUrl)
        every { context.getString(R.string.gogs_user_agent) }.returns("btt-writer")
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test create repository successful`() {
        val user: User = mockk()
        TestUtils.setPropertyReflection(user, "token", Token("token", "abcd"))
        every { profile.gogsUser }.returns(user)

        server.enqueue(createRepositoryResponse())

        val success = CreateRepository(context, prefRepository, profile)
            .execute(targetTranslation, progressListener)

        assertTrue(success)

        val request = JSONObject(server.takeRequest().body.readString(Charsets.UTF_8))
        assertEquals("aa_gen_text_reg", request.getString("name"))
        assertTrue(request.has("description"))
        assertTrue(request.has("private"))

        verify { progressListener.onProgress(any(), any(), any()) }
        verify { prefRepository.getDefaultPref(any(), any(), String::class.java) }
        verify { targetTranslation.id }
        verify { resources.getString(R.string.pref_default_gogs_api) }
        verify { profile.gogsUser }
    }

    @Test
    fun `test create repository fails because remote exists`() {
        val user: User = mockk()
        TestUtils.setPropertyReflection(user, "token", Token("token", "abcd"))
        every { profile.gogsUser }.returns(user)

        server.enqueue(createRepositoryExistsResponse())

        val success = CreateRepository(context, prefRepository, profile)
            .execute(targetTranslation, progressListener)

        assertTrue(success)

        val request = JSONObject(server.takeRequest().body.readString(Charsets.UTF_8))
        assertEquals("aa_gen_text_reg", request.getString("name"))
        assertTrue(request.has("description"))
        assertTrue(request.has("private"))

        verify { progressListener.onProgress(any(), any(), any()) }
        verify { prefRepository.getDefaultPref(any(), any(), String::class.java) }
        verify { targetTranslation.id }
        verify { resources.getString(R.string.pref_default_gogs_api) }
        verify { profile.gogsUser }
    }

    @Test
    fun `test create repository fails because no user`() {
        every { profile.gogsUser }.returns(null)

        val success = CreateRepository(context, prefRepository, profile)
            .execute(targetTranslation, progressListener)

        assertFalse(success)

        verify { progressListener.onProgress(any(), any(), any()) }
        verify { prefRepository.getDefaultPref(any(), any(), String::class.java) }
        verify(exactly = 0) { targetTranslation.id }
        verify { resources.getString(R.string.pref_default_gogs_api) }
        verify { profile.gogsUser }
    }

    private fun createRepositoryResponse(): MockResponse {
        val body = """
            {
                "id": 222,
                "name": "aa_gen_text_reg",
                "html_url": "http://example.com/aa_gen_text_reg",
                "clone_url": "http://example.com/aa_gen_text_reg.git",
                "ssh_url": "ssh://example.com/aa_gen_text_reg.git",
                "isPrivate": false
            }
        """.trimIndent()

        return MockResponse().setBody(body).setResponseCode(201)
    }

    private fun createRepositoryExistsResponse(): MockResponse {
        return MockResponse().setResponseCode(409)
    }
}