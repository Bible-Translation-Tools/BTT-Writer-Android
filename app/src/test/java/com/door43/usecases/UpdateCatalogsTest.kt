package com.door43.usecases

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import com.door43.data.IPreferenceRepository
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
import org.bibletranslationtools.resourcecatalog.ResourceCatalogClient
import org.bibletranslationtools.resourcecatalog.library.Index
import org.bibletranslationtools.resourcecatalog.library.models.TargetLanguage

class UpdateCatalogsTest {

    @MockK private lateinit var context: Context
    @MockK private lateinit var prefRepository: IPreferenceRepository
    @MockK private lateinit var catalogClient: ResourceCatalogClient
    @MockK private lateinit var index: Index

    private lateinit var testContext: Context
    private val mockResources = mockk<Resources>()
    private val onProgress = mockk<(Float, String?) -> Unit>(relaxed = true)

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        testContext = object : ContextWrapper(context) {
            override fun getResources(): Resources {
                return mockResources
            }
        }

        every { mockResources.getString(any()) }.returns("test")
        every { prefRepository.getDefaultPref(any(), any(), String::class.java) }
            .returns("/api")
        every { catalogClient.library } returns index
        every { onProgress(any(), any()) }.just(runs)
        coEvery { catalogClient.updateCatalogs(any(), any()) }.just(runs)
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

        val result = UpdateCatalogs(testContext, catalogClient, prefRepository)
            .execute(false, onProgress)

        assertTrue(result.success)
        assertEquals(0, result.addedCount)

        verify(exactly = 2) { index.getTargetLanguages() }
        coVerify { catalogClient.updateCatalogs(emptyList(), any()) }
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

        val result = UpdateCatalogs(testContext, catalogClient, prefRepository)
            .execute(false, onProgress)

        assertTrue(result.success)
        assertEquals(1, result.addedCount)

        verify(exactly = 2) { index.getTargetLanguages() }
        coVerify { catalogClient.updateCatalogs(emptyList(), any()) }
    }

    @Test
    fun `test update catalogs, force update`() = runTest  {
        every { index.getTargetLanguages() }.returns(listOf())

        val result = UpdateCatalogs(testContext, catalogClient, prefRepository)
            .execute(true, onProgress)

        assertTrue(result.success)
        assertEquals(0, result.addedCount)

        verify(exactly = 2) { index.getTargetLanguages() }
        coVerify { catalogClient.updateCatalogs(any(), any()) }
    }

    @Test
    fun `test update catalogs, throws exception`() = runTest  {
        every { index.getTargetLanguages() }.returns(listOf())
        coEvery { catalogClient.updateCatalogs(any(), any()) }.throws(Exception("An error occurred."))

        val result = UpdateCatalogs(testContext, catalogClient, prefRepository)
            .execute(true, onProgress)

        assertFalse(result.success)
        assertEquals(0, result.addedCount)

        verify(exactly = 1) { index.getTargetLanguages() }
        coVerify { catalogClient.updateCatalogs(any(), any()) }
    }
}