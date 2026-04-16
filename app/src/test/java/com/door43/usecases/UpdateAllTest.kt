package com.door43.usecases

import android.content.Context
import android.content.res.Resources
import com.door43.OnProgressListener
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.R
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.unfoldingword.door43client.Door43Client

class UpdateAllTest {

    @MockK private lateinit var context: Context
    @MockK private lateinit var prefRepository: IPreferenceRepository
    @MockK private lateinit var library: Door43Client
    @MockK private lateinit var progressListener: OnProgressListener
    @MockK private lateinit var resources: Resources

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        every { context.resources }.returns(resources)
        every { progressListener.onProgress(any(), any()) }.just(runs)

        every { prefRepository.getRootCatalogApi() }.returns("/api")
        every { prefRepository.getDefaultPref(
            IPreferenceRepository.KEY_PREF_MEDIA_SERVER,
            any<String>()
        ) }.returns("/api")

        every { resources.getString(R.string.pref_default_media_server) }
            .returns("/api")

        coEvery { library.updateSources(any(), any()) }.just(runs)
        coEvery { library.updateCatalogs(any(), any()) }.just(runs)
        coEvery { library.updateChunks(any()) }.just(runs)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test update all, force catalogs`() {
        val result = runBlocking {
            UpdateAll(context, prefRepository, library)
                .execute(true, progressListener)
        }

        assertTrue(result.success)

        verify { prefRepository.getRootCatalogApi() }
        coVerify { library.updateSources(any(), any()) }
        coVerify { library.updateCatalogs(true, any()) }
        coVerify { library.updateChunks(any()) }
        verify(exactly = 3) { progressListener.onProgress(any(), any()) }
    }

    @Test
    fun `test update all, don't force catalogs`() {
        val result = runBlocking {
            UpdateAll(context, prefRepository, library)
                .execute(false, progressListener)
        }

        assertTrue(result.success)

        verify { prefRepository.getRootCatalogApi() }
        coVerify { library.updateSources(any(), any()) }
        coVerify { library.updateChunks(any()) }
        coVerify { library.updateCatalogs(false, any()) }
        verify(exactly = 3) { progressListener.onProgress(any(), any()) }
    }

    @Test
    fun `test update all failed if one of the updated fails`() {
        coEvery { library.updateChunks(any()) }.throws(Exception("chunks updated failed"))

        val result = runBlocking {
            UpdateAll(context, prefRepository, library)
                .execute(false, progressListener)
        }

        assertFalse(result.success)

        verify { prefRepository.getRootCatalogApi() }
        coVerify { library.updateSources(any(), any()) }
        coVerify { library.updateChunks(any()) }
        coVerify { library.updateCatalogs(false, any()) }
        verify(exactly = 3) { progressListener.onProgress(any(), any()) }
    }
}