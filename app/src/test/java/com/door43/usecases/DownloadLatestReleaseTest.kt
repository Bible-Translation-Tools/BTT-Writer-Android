package com.door43.usecases

import android.content.ActivityNotFoundException
import android.content.Context
import com.door43.translationstudio.App
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class DownloadLatestReleaseTest {

    @MockK private lateinit var context: Context
    @MockK private lateinit var release: CheckForLatestRelease.Release

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        mockkObject(App)

        every { context.packageName }.returns("org.example.writer")
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test download latest release store version`() {
        every { App.isStoreVersion }.returns(true)
        every { context.startActivity(any()) }.just(runs)

        DownloadLatestRelease(context).execute(release)

        verify { context.packageName }
        verify { App.isStoreVersion }
        verify { context.startActivity(any()) }
    }

    @Test
    fun `test download latest release store version throws exception`() {
        every { App.isStoreVersion }.returns(true)

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

        DownloadLatestRelease(context).execute(release)

        verify { context.packageName }
        verify { App.isStoreVersion }
        verify(exactly = attempt) { context.startActivity(any()) }
    }

    @Test
    fun `test download latest release non-store version`() {
        every { App.isStoreVersion }.returns(false)
        every { context.startActivity(any()) }.just(runs)
        every { release.downloadUrl }.returns("http://download")

        DownloadLatestRelease(context).execute(release)

        verify(exactly = 0) { context.packageName }
        verify { App.isStoreVersion }
        verify { context.startActivity(any()) }
        verify { release.downloadUrl }
    }
}