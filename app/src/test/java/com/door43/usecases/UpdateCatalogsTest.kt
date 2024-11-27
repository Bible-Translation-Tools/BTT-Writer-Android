package com.door43.usecases

import com.door43.OnProgressListener
import com.door43.TestUtils
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
import org.unfoldingword.door43client.models.TargetLanguage

class UpdateCatalogsTest {

    @MockK private lateinit var library: Door43Client
    @MockK private lateinit var progressListener: OnProgressListener
    @MockK private lateinit var index: Index

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        TestUtils.setPropertyReflection(library, "index", index)

        every { progressListener.onProgress(any(), any(), any()) }.just(runs)
        every { library.updateCatalogs(any(), any()) }.just(runs)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test update catalogs, nothing new`() {
        val targetLanguage: TargetLanguage = mockk()
        TestUtils.setPropertyReflection(targetLanguage, "slug", "en")

        every { index.targetLanguages }.returns(listOf(targetLanguage))

        val message = "Test"
        val result = UpdateCatalogs(library)
            .execute(false, message, progressListener)

        assertTrue(result.success)
        assertEquals(0, result.addedCount)

        verify { progressListener.onProgress(any(), any(), message) }
        verify(exactly = 2) { index.targetLanguages }
        verify { library.updateCatalogs(false, any()) }
    }

    @Test
    fun `test update catalogs, new language`() {
        val targetLanguage: TargetLanguage = mockk()
        TestUtils.setPropertyReflection(targetLanguage, "slug", "en")
        val newLanguage: TargetLanguage = mockk()
        TestUtils.setPropertyReflection(targetLanguage, "slug", "fr")

        var calls = 0
        every { index.targetLanguages }.answers {
            when (calls) {
                0 -> {
                    calls++
                    listOf(targetLanguage)
                }
                else -> listOf(targetLanguage, newLanguage)
            }
        }

        val message = "Test"
        val result = UpdateCatalogs(library)
            .execute(false, message, progressListener)

        assertTrue(result.success)
        assertEquals(1, result.addedCount)

        verify { progressListener.onProgress(any(), any(), message) }
        verify(exactly = 2) { index.targetLanguages }
        verify { library.updateCatalogs(false, any()) }
    }

    @Test
    fun `test update catalogs, force update`() {
        every { index.targetLanguages }.returns(listOf())

        val message = "Test"
        val result = UpdateCatalogs(library)
            .execute(true, message, progressListener)

        assertTrue(result.success)
        assertEquals(0, result.addedCount)

        verify { progressListener.onProgress(any(), any(), message) }
        verify(exactly = 2) { index.targetLanguages }
        verify { library.updateCatalogs(true, any()) }
    }

    @Test
    fun `test update catalogs, throws exception`() {
        every { index.targetLanguages }.returns(listOf())
        every { library.updateCatalogs(any(), any()) }.throws(Exception("An error occurred."))

        val message = "Test"
        val result = UpdateCatalogs(library)
            .execute(true, message, progressListener)

        assertFalse(result.success)
        assertEquals(0, result.addedCount)

        verify { progressListener.onProgress(any(), any(), message) }
        verify(exactly = 1) { index.targetLanguages }
        verify { library.updateCatalogs(true, any()) }
    }
}