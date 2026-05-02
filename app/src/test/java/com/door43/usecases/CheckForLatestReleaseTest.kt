package com.door43.usecases

import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.App
import com.door43.translationstudio.AppInfo
import com.door43.translationstudio.Platform
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CheckForLatestReleaseTest {

    @MockK private lateinit var prefRepository: IPreferenceRepository
    @MockK private lateinit var platform: Platform
    @MockK private lateinit var info: AppInfo

    private val server = MockWebServer()

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        mockkObject(App)

        every { platform.info }.returns(info)
        every { prefRepository.getGithubRepoApi() }.returns(server.url("/api").toString())
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test checkForLatestRelease when there is a new release`() = runTest {
        server.enqueue(createReleaseResponse())

        every { info.versionCode }.returns(1)
        every { platform.info }.returns(info)

        val result = CheckForLatestRelease(prefRepository, platform).execute()

        assertNotNull(result.release)

        val release = result.release!!

        assertEquals("release", release.name)
        assertEquals("/download.apk", release.downloadUrl)
        assertEquals(12345, release.downloadSize)
        assertEquals(10, release.build)

        verify { prefRepository.getGithubRepoApi() }
    }

    @Test
    fun `test checkForLatestRelease when there is no new release`() = runTest {
        server.enqueue(createReleaseResponse())

        every { info.versionCode }.returns(10)

        val result = CheckForLatestRelease(prefRepository, platform).execute()

        assertNull(result.release)

        verify { prefRepository.getGithubRepoApi() }
    }

    private fun createReleaseResponse(): MockResponse {
        val body = """
            {
                "tag_name": "tag+10",
                "name": "release",
                "assets": [
                    {
                        "browser_download_url": "/download.apk",
                        "size": 12345
                    }
                ]
            }
        """.trimIndent()

        return MockResponse()
            .addHeader("Content-Type", "application/json")
            .setBody(body)
            .setResponseCode(200)
    }
}