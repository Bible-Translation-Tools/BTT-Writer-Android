package com.door43.usecases

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.door43.TestUtils
import com.door43.data.IPreferenceRepository
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.unmockkAll
import io.mockk.verify
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CheckForLatestReleaseTest {

    @MockK private lateinit var context: Context
    @MockK private lateinit var prefRepository: IPreferenceRepository
    @MockK private lateinit var packageManager: PackageManager
    @MockK private lateinit var packageInfo: PackageInfo

    private val server = MockWebServer()

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        every { context.packageName }.returns("org.writer")
        every { context.packageManager }.returns(packageManager)
        every { packageManager.getPackageInfo(any(String::class), 0) }.returns(packageInfo)

        every { prefRepository.getGithubRepoApi() }.returns(server.url("/api").toString())
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test checkForLatestRelease when there is a new release`() {
        server.enqueue(createReleaseResponse())

        TestUtils.setPropertyReflection(packageInfo, "versionCode", 9)

        val result = CheckForLatestRelease(context, prefRepository).execute()

        assertNotNull(result.release)

        val release = result.release!!

        assertEquals("release", release.name)
        assertEquals("/download.apk", release.downloadUrl)
        assertEquals(12345, release.downloadSize)
        assertEquals(10, release.build)

        verify { context.packageName }
        verify { context.packageManager }
        verify { packageManager.getPackageInfo(any(String::class), 0) }
        verify { prefRepository.getGithubRepoApi() }
    }

    @Test
    fun `test checkForLatestRelease when there is no new release`() {
        server.enqueue(createReleaseResponse())

        TestUtils.setPropertyReflection(packageInfo, "versionCode", 10)

        val result = CheckForLatestRelease(context, prefRepository).execute()

        assertNull(result.release)

        verify { context.packageName }
        verify { context.packageManager }
        verify { packageManager.getPackageInfo(any(String::class), 0) }
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

        return MockResponse().setBody(body).setResponseCode(200)
    }
}