package com.door43.usecases

import android.content.Context
import android.content.res.Resources
import com.door43.OnProgressListener
import com.door43.TestUtils
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.SettingsActivity
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.Index
import org.unfoldingword.door43client.models.Translation
import org.unfoldingword.resourcecontainer.Language
import org.unfoldingword.resourcecontainer.Project
import org.unfoldingword.resourcecontainer.Resource

class UpdateSourceTest {

    @MockK private lateinit var context: Context
    @MockK private lateinit var library: Door43Client
    @MockK private lateinit var prefRepository: IPreferenceRepository
    @MockK private lateinit var progressListener: OnProgressListener
    @MockK private lateinit var index: Index
    @MockK private lateinit var resources: Resources

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        every { context.resources }.returns(resources)

        TestUtils.setPropertyReflection(library, "index", index)

        every { progressListener.onProgress(any(), any(), any()) }.just(runs)
        every { library.getResourceContainerLastModified(any(), any(), any()) }
            .returns(1234567890)
        every { library.updateSources(any(), any()) }.just(runs)

        every { prefRepository.getDefaultPref(
            SettingsActivity.KEY_PREF_MEDIA_SERVER,
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
    fun `test update source, nothing new`() {
        val message = "Test"

        every { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
            .returns(listOf(getTranslation("en", "mrk", "ulb")))

        val result = UpdateSource(
            context,
            library,
            prefRepository
        ).execute(message, progressListener)

        assertTrue(result.success)
        assertEquals(0, result.updatedCount)
        assertEquals(0, result.addedCount)

        verifyCommonStuff()
    }

    @Test
    fun `test update source, added new translation`() {
        val message = "Test"

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
        ).execute(message, progressListener)

        assertTrue(result.success)
        assertEquals(0, result.updatedCount)
        assertEquals(1, result.addedCount)

        verifyCommonStuff()
    }

    @Test
    fun `test update source, updated old translation`() {
        val message = "Test"

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
        ).execute(message, progressListener)

        assertTrue(result.success)
        assertEquals(1, result.updatedCount)
        assertEquals(0, result.addedCount)

        verifyCommonStuff()
    }

    @Test
    fun `test update source, throws exception`() {
        val message = "Test"

        every { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
            .returns(listOf(getTranslation("en", "mrk", "ulb")))

        every { library.updateSources(any(), any()) }.throws(Exception("An error occurred."))

        val result = UpdateSource(
            context,
            library,
            prefRepository
        ).execute(message, progressListener)

        assertFalse(result.success)
        assertEquals(0, result.updatedCount)
        assertEquals(0, result.addedCount)

        verify { progressListener.onProgress(any(), any(), "Test") }
        verify(exactly = 1) { library.index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 1) { library.getResourceContainerLastModified(any(), any(), any()) }
        verify { prefRepository.getRootCatalogApi() }
        verify { library.updateSources(any(), any()) }
    }

    private fun verifyCommonStuff() {
        verify { progressListener.onProgress(any(), any(), "Test") }
        verify(exactly = 2) { library.index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 2) { library.getResourceContainerLastModified(any(), any(), any()) }
        verify { prefRepository.getRootCatalogApi() }
        verify { library.updateSources(any(), any()) }
    }

    private fun getTranslation(lang: String, book: String, res: String): Translation {
        val translation: Translation = mockk()

        val language: Language = mockk()
        TestUtils.setPropertyReflection(language, "slug", lang)

        val project: Project = mockk()
        TestUtils.setPropertyReflection(project, "slug", book)

        val resource: Resource = mockk()
        TestUtils.setPropertyReflection(resource, "slug", res)

        TestUtils.setPropertyReflection(
            translation,
            "resourceContainerSlug",
            "${lang}_${book}_${res}"
        )
        TestUtils.setPropertyReflection(translation, "language", language)
        TestUtils.setPropertyReflection(translation, "project", project)
        TestUtils.setPropertyReflection(translation, "resource", resource)

        return translation
    }
}