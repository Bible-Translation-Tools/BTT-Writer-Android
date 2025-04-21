package com.door43.usecases

import android.content.Context
import android.content.res.Resources
import com.door43.OnProgressListener
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.SettingsActivity
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
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
        every { progressListener.onProgress(any(), any(), any()) }.just(runs)

        every { prefRepository.getRootCatalogApi() }.returns("/api")
        every { prefRepository.getDefaultPref(
            SettingsActivity.KEY_PREF_MEDIA_SERVER,
            any<String>()
        ) }.returns("/api")

        every { resources.getString(R.string.pref_default_media_server) }
            .returns("/api")

        every { library.updateSources(any(), any()) }.just(runs)
        every { library.updateCatalogs(any(), any()) }.just(runs)
        every { library.updateChunks(any()) }.just(runs)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test update all, force catalogs`() {
        val result = UpdateAll(context, prefRepository, library)
            .execute(true, progressListener)

        assertTrue(result.success)

        verify { prefRepository.getRootCatalogApi() }
        verify { library.updateSources(any(), any()) }
        verify { library.updateCatalogs(true, any()) }
        verify { library.updateChunks(any()) }
        verify(exactly = 3) { progressListener.onProgress(any(), any(), any()) }
    }

    @Test
    fun `test update all, don't force catalogs`() {
        val result = UpdateAll(context, prefRepository, library)
            .execute(false, progressListener)

        assertTrue(result.success)

        verify { prefRepository.getRootCatalogApi() }
        verify { library.updateSources(any(), any()) }
        verify { library.updateChunks(any()) }
        verify { library.updateCatalogs(false, any()) }
        verify(exactly = 3) { progressListener.onProgress(any(), any(), any()) }
    }

    @Test
    fun `test update all failed if one of the updated fails`() {
        every { library.updateChunks(any()) }.throws(Exception("chunks updated failed"))

        val result = UpdateAll(context, prefRepository, library)
            .execute(false, progressListener)

        assertFalse(result.success)

        verify { prefRepository.getRootCatalogApi() }
        verify { library.updateSources(any(), any()) }
        verify { library.updateChunks(any()) }
        verify { library.updateCatalogs(false, any()) }
        verify(exactly = 3) { progressListener.onProgress(any(), any(), any()) }
    }
}