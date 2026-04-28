package com.door43.usecases

import android.content.ActivityNotFoundException
import android.content.Context
import com.door43.translationstudio.Platform
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class DownloadLatestReleaseTest {

    @MockK private lateinit var context: Context
    @MockK private lateinit var release: CheckForLatestRelease.Release
    @MockK private lateinit var platform: Platform

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        every { context.packageName }.returns("org.example.writer")
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test download latest release store version`() {
        every { platform.isStoreVersion }.returns(true)
        every { context.startActivity(any()) }.just(runs)

        DownloadLatestRelease(context, platform).execute(release)

        verify { context.packageName }
        verify { platform.isStoreVersion }
        verify { context.startActivity(any()) }
    }

    @Test
    fun `test download latest release store version throws exception`() {
        every { platform.isStoreVersion }.returns(true)

        var attempt = 1
        every { context.startActivity(any()) }.answers {
            when (attempt) {
                1 -> {
                    attempt++
                    throw ActivityNotFoundException("No activity found")
                }
                else -> Unit
            }
        }

        DownloadLatestRelease(context, platform).execute(release)

        verify { context.packageName }
        verify { platform.isStoreVersion }
        verify(exactly = attempt) { context.startActivity(any()) }
    }

    @Test
    fun `test download latest release non-store version`() {
        every { platform.isStoreVersion }.returns(false)
        every { context.startActivity(any()) }.just(runs)
        every { release.downloadUrl }.returns("http://download")

        DownloadLatestRelease(context, platform).execute(release)

        verify(exactly = 0) { context.packageName }
        verify { platform.isStoreVersion }
        verify { context.startActivity(any()) }
        verify { release.downloadUrl }
    }
}