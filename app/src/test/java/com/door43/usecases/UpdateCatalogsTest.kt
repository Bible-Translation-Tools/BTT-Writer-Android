package com.door43.usecases

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
    @MockK private lateinit var index: Index

    val onProgress = mockk<(Float, String?) -> Unit>(relaxed = true)

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        every { library.index } returns index
        every { onProgress(any(), any()) }.just(runs)
        coEvery { library.updateCatalogs(any(), any()) }.just(runs)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test update catalogs, nothing new`() = runTest {
        val targetLanguage: TargetLanguage = mockk {
            every { slug }.returns("en")
        }

        every { index.getTargetLanguages() }.returns(listOf(targetLanguage))

        val result = UpdateCatalogs(library)
            .execute(false, onProgress)

        assertTrue(result.success)
        assertEquals(0, result.addedCount)

        verify(exactly = 2) { index.getTargetLanguages() }
        coVerify { library.updateCatalogs(false, any()) }
    }

    @Test
    fun `test update catalogs, new language`() = runTest  {
        val targetLanguage: TargetLanguage = mockk {
            every { slug }.returns("en")
        }
        val newLanguage: TargetLanguage = mockk {
            every { slug }.returns("fr")
        }

        var calls = 0
        every { index.getTargetLanguages() }.answers {
            when (calls) {
                0 -> {
                    calls++
                    listOf(targetLanguage)
                }
                else -> listOf(targetLanguage, newLanguage)
            }
        }

        val result = UpdateCatalogs(library)
            .execute(false, onProgress)

        assertTrue(result.success)
        assertEquals(1, result.addedCount)

        verify(exactly = 2) { index.getTargetLanguages() }
        coVerify { library.updateCatalogs(false, any()) }
    }

    @Test
    fun `test update catalogs, force update`() = runTest  {
        every { index.getTargetLanguages() }.returns(listOf())

        val result = UpdateCatalogs(library)
            .execute(true, onProgress)

        assertTrue(result.success)
        assertEquals(0, result.addedCount)

        verify(exactly = 2) { index.getTargetLanguages() }
        coVerify { library.updateCatalogs(true, any()) }
    }

    @Test
    fun `test update catalogs, throws exception`() = runTest  {
        every { index.getTargetLanguages() }.returns(listOf())
        coEvery { library.updateCatalogs(any(), any()) }.throws(Exception("An error occurred."))

        val result = UpdateCatalogs(library)
            .execute(true, onProgress)

        assertFalse(result.success)
        assertEquals(0, result.addedCount)

        verify(exactly = 1) { index.getTargetLanguages() }
        coVerify { library.updateCatalogs(true, any()) }
    }
}