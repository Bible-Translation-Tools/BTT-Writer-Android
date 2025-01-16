package com.door43.usecases

import android.content.ContentResolver
import android.content.Context
import android.content.res.Resources
import android.net.Uri
import com.door43.OnProgressListener
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.R
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
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.unfoldingword.door43client.Door43Client
import java.io.File

class DownloadIndexTest {

    @MockK private lateinit var context: Context
    @MockK private lateinit var prefRepository: IPreferenceRepository
    @MockK private lateinit var directoryProvider: IDirectoryProvider
    @MockK private lateinit var library: Door43Client
    @MockK private lateinit var progressListener: OnProgressListener
    @MockK private lateinit var resources: Resources
    @MockK private lateinit var contentResolver: ContentResolver

    @get:Rule var tempFolder = TemporaryFolder()

    private val server = MockWebServer()
    private val indexUrl = server.url("/index.sqlite").toString()

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        every { context.resources }.returns(resources)
        every { progressListener.onProgress(any(), any(), any()) }.just(runs)

        every { resources.getString(R.string.downloading_index) }
            .returns("Downloading index")
        every { resources.getString(R.string.pref_default_index_sqlite_url) }
            .returns(indexUrl)

        every { prefRepository.getDefaultPref(any(), any(), String::class.java) }
            .returns(indexUrl)

        val dbFile = File.createTempFile("database", ".sqlite").also {
            it.deleteOnExit()
        }

        every { library.tearDown() }.just(runs)
        every { directoryProvider.databaseFile }.returns(dbFile)
    }

    @After
    fun tearDown() {
        unmockkAll()
        tempFolder.delete()
    }

    @Test
    fun `test download index successful`() {
        server.enqueue(createDownloadResponse())

        val success = DownloadIndex(context, directoryProvider, prefRepository, library)
            .download(progressListener)

        assertTrue(success)
        assertEquals("1234567890", directoryProvider.databaseFile.readText())

        verify { progressListener.onProgress(any(), any(), "Downloading index") }
        verify { resources.getString(R.string.downloading_index) }
        verify { resources.getString(R.string.pref_default_index_sqlite_url) }
        verify { prefRepository.getDefaultPref(any(), any(), String::class.java) }
        verify { library.tearDown() }
        verify { directoryProvider.databaseFile }
    }

    @Test
    fun `test an exception is thrown during download`() {
        every { library.tearDown() }.throws(Exception("An error occurred"))

        val success = DownloadIndex(context, directoryProvider, prefRepository, library)
            .download(progressListener)

        assertFalse(success)

        verify { progressListener.onProgress(any(), any(), "Downloading index") }
        verify { resources.getString(R.string.downloading_index) }
        verify { library.tearDown() }
    }

    @Test
    fun `test server returned error code`() {
        server.enqueue(MockResponse().setResponseCode(500))

        val success = DownloadIndex(context, directoryProvider, prefRepository, library)
            .download(progressListener)

        assertFalse(success)

        verify { progressListener.onProgress(any(), any(), "Downloading index") }
        verify { resources.getString(R.string.downloading_index) }
        verify { library.tearDown() }
    }

    @Test
    fun `test import index successful`() {
        val uri: Uri = mockk()
        val indexFile = tempFolder.newFile().apply {
            writeText("1234567890")
        }

        every { context.contentResolver }.returns(contentResolver)
        every { contentResolver.openInputStream(any()) }
            .returns(indexFile.inputStream())

        val success = DownloadIndex(context, directoryProvider, prefRepository, library)
            .import(uri)

        assertTrue(success)
        assertEquals("1234567890", directoryProvider.databaseFile.readText())

        verify { library.tearDown() }
        verify { directoryProvider.databaseFile }
    }

    private fun createDownloadResponse(): MockResponse {
        val buffer = Buffer().apply {
            write("1234567890".toByteArray())
        }
        return MockResponse().setBody(buffer).setResponseCode(200)
    }
}