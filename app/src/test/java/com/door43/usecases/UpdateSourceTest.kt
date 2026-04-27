package com.door43.usecases

import android.content.Context
import android.content.res.Resources
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.R
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.bibletranslationtools.resourcecontainer.Language
import org.bibletranslationtools.resourcecontainer.Project
import org.bibletranslationtools.resourcecontainer.Resource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.Index
import org.unfoldingword.door43client.models.Translation

class UpdateSourceTest {

    @MockK private lateinit var context: Context
    @MockK private lateinit var library: Door43Client
    @MockK private lateinit var prefRepository: IPreferenceRepository
    @MockK private lateinit var index: Index
    @MockK private lateinit var resources: Resources

    val onProgress = mockk<(Float, String?) -> Unit>(relaxed = true)

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        every { context.resources }.returns(resources)
        every { library.index } returns index

        every { onProgress(any(), any()) }.just(runs)
        every { library.getResourceContainerLastModified(any(), any(), any()) }
            .returns(1234567890)
        coEvery { library.updateSources(any(), any()) }.just(runs)

        every { prefRepository.getDefaultPref(
            IPreferenceRepository.KEY_PREF_MEDIA_SERVER,
            any<String>()
        ) }.returns("/api")
        every { prefRepository.getRootCatalogApi() }.returns("/catalog")

        every { resources.getString(R.string.pref_default_media_server) }
            .returns("/api")
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test update source, nothing new`() = runTest  {
        every { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
            .returns(listOf(getTranslation("en", "mrk", "ulb")))

        val result = UpdateSource(
            context,
            library,
            prefRepository
        ).execute(onProgress)

        assertTrue(result.success)
        assertEquals(0, result.updatedCount)
        assertEquals(0, result.addedCount)

        verifyCommonStuff()
    }

    @Test
    fun `test update source, added new translation`() = runTest  {
        val translation1 = getTranslation("en", "mrk", "ulb")
        val translation2 = getTranslation("id", "luk", "ayt")

        var called = 0
        every { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }.answers {
            when (++called) {
                1 -> listOf(translation1)
                else -> listOf(translation1, translation2)
            }
        }

        val result = UpdateSource(
            context,
            library,
            prefRepository
        ).execute(onProgress)

        assertTrue(result.success)
        assertEquals(0, result.updatedCount)
        assertEquals(1, result.addedCount)

        verifyCommonStuff()
    }

    @Test
    fun `test update source, updated old translation`() = runTest  {
        val translation = getTranslation("en", "mrk", "ulb")

        var called = 0
        every { library.getResourceContainerLastModified(any(), any(), any()) }.answers {
            when (++called) {
                1 -> 123
                else -> 234
            }
        }

        every { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
            .returns(listOf(translation))

        val result = UpdateSource(
            context,
            library,
            prefRepository
        ).execute(onProgress)

        assertTrue(result.success)
        assertEquals(1, result.updatedCount)
        assertEquals(0, result.addedCount)

        verifyCommonStuff()
    }

    @Test
    fun `test update source, throws exception`() = runTest  {
        every { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
            .returns(listOf(getTranslation("en", "mrk", "ulb")))

        coEvery { library.updateSources(any(), any()) }.throws(Exception("An error occurred."))

        val result = UpdateSource(
            context,
            library,
            prefRepository
        ).execute(onProgress)

        assertFalse(result.success)
        assertEquals(0, result.updatedCount)
        assertEquals(0, result.addedCount)

        verify(exactly = 1) { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 1) { library.getResourceContainerLastModified(any(), any(), any()) }
        verify { prefRepository.getRootCatalogApi() }
        coVerify { library.updateSources(any(), any()) }
    }

    private fun verifyCommonStuff() {
        verify(exactly = 2) { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 2) { library.getResourceContainerLastModified(any(), any(), any()) }
        verify { prefRepository.getRootCatalogApi() }
        coVerify { library.updateSources(any(), any()) }
    }

    private fun getTranslation(lang: String, book: String, res: String): Translation {
        val translation: Translation = mockk()

        val language: Language = mockk {
            every { slug }.returns(lang)
        }
        val project: Project = mockk {
            every { slug }.returns(book)
        }
        val resource: Resource = mockk {
            every { slug }.returns(res)
        }

        every { translation.resourceContainerSlug } returns "${lang}_${book}_${res}"
        every { translation.language } returns language
        every { translation.project } returns project
        every { translation.resource } returns resource

        return translation
    }
}