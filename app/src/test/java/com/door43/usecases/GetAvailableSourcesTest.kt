package com.door43.usecases

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.bibletranslationtools.resourcecontainer.Language
import org.bibletranslationtools.resourcecontainer.Project
import org.bibletranslationtools.resourcecontainer.Resource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.bibletranslationtools.resourcecatalog.ResourceCatalogClient
import org.bibletranslationtools.resourcecatalog.library.Index
import org.bibletranslationtools.resourcecatalog.library.models.Translation

class GetAvailableSourcesTest {

    @MockK private lateinit var catalogClient: ResourceCatalogClient
    @MockK private lateinit var index: Index

    private val onProgress = mockk<(Float, String?) -> Unit>(relaxed = true)

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        every { catalogClient.library } returns index

        every { onProgress(any(), any()) }.just(runs)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test get available sources`() {
        every { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
            .answers {
                val resType = args[3] as String
                arrayListOf(
                    mockTranslation("en", "mrk", resType),
                    mockTranslation("fr", "gen", resType),
                )
            }

        val result = GetAvailableSources(catalogClient).execute(onProgress)

        assertEquals(4, result.sources.size)
        assertEquals(2, result.byLanguage.size)
        assertEquals(39, result.otBooks.size)
        assertEquals(1, result.otBooks["gen"]!!.size)
        assertEquals(0, result.otBooks.keys.filter { it != "gen" }
            .sumOf { result.otBooks[it]!!.size })
        assertEquals(27, result.ntBooks.size)
        assertEquals(1, result.ntBooks["mrk"]!!.size)
        assertEquals(0, result.ntBooks.keys.filter { it != "mrk" }
            .sumOf { result.ntBooks[it]!!.size })
        assertEquals(1, result.otherBooks.size)
        assertEquals(2, result.otherBooks["bible"]!!.size)

        verify(exactly = 2) { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
        verify { onProgress(any(), any()) }
    }

    private fun mockLanguage(id: String): Language {
        val language: Language = mockk {
            every { slug }.returns(id)
        }
        return language
    }

    private fun mockProject(id: String): Project {
        val project: Project = mockk {
            every { slug }.returns(id)
        }
        return project
    }

    private fun mockResource(id: String): Resource {
        val resource: Resource = mockk {
            every { slug }.returns(id)
        }
        return resource
    }

    private fun mockTranslation(lang: String, book: String, resType: String): Translation {
        val translation: Translation = mockk()

        val project = if (resType == "dict") "bible" else book
        val resource = if (resType == "dict") "tw" else "ulb"

        every { translation.language } returns mockLanguage(lang)
        every { translation.project } returns mockProject(project)
        every { translation.resource } returns mockResource(resource)

        return translation
    }
}