package com.door43.usecases

import com.door43.translationstudio.core.Chunk
import com.door43.translationstudio.core.ContainerCache
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.bibletranslationtools.resourcecatalog.ResourceCatalogClient
import org.bibletranslationtools.resourcecatalog.library.Index
import org.bibletranslationtools.resourcecatalog.library.models.Translation
import org.bibletranslationtools.resourcecontainer.Language
import org.bibletranslationtools.resourcecontainer.Link
import org.bibletranslationtools.resourcecontainer.Project
import org.bibletranslationtools.resourcecontainer.ResourceContainer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RenderHelpsTest {

    @MockK private lateinit var catalogClient: ResourceCatalogClient
    @MockK private lateinit var language: Language
    @MockK private lateinit var project: Project
    @MockK private lateinit var index: Index

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        every { language.slug }.returns("en")
        every { project.slug }.returns("mrk")

        every { catalogClient.library } returns index

        mockkObject(ContainerCache)
        every { ContainerCache.cache(catalogClient, any()) }
            .answers {
                val slug = secondArg<String>()
                when (slug) {
                    "en_mrk_tq" -> mockHelpsRc("tq")
                    "en_mrk_tn" -> mockHelpsRc("tn")
                    else -> null
                }
            }
        every { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }.answers {
            val resource = thirdArg<String>()
            when (resource) {
                "tq" -> listOf(mockHelpsTranslation("en_mrk_tq"))
                "tn" -> listOf(mockHelpsTranslation("en_mrk_tn"))
                else -> emptyList()
            }
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test render helps has all resources`() {
        val listItem: Chunk = mockk {
            every { config }.returns(mockTw())
        }

        every { listItem.chapterSlug } returns "01"
        every { listItem.chunkSlug } returns "01"

        val source: ResourceContainer = mockk {
            every { chunks(any()) }.returns(listOf("01", "03", "05"))
            every { language }.returns(this@RenderHelpsTest.language)
            every { project }.returns(this@RenderHelpsTest.project)
        }
        every { listItem.source } returns source

        val result = RenderHelps(catalogClient).execute(listItem)

        assertEquals(3, result.size)
        assertEquals(3, (result["questions"]!! as List<*>).size)
        assertEquals(3, (result["notes"]!! as List<*>).size)
        assertEquals(2, (result["words"]!! as List<*>).size)

        verify { source.chunks(any()) }
        verify { ContainerCache.cacheFromLinks(any(), any(), any()) }
        verify { ContainerCache.cacheClosest(any(), any(), any(), any()) }
    }

    @Test
    fun `test render helps with no tW resource`() {
        val listItem: Chunk = mockk {
            every { config }.returns(mapOf())
        }
        every { listItem.chapterSlug } returns "01"
        every { listItem.chunkSlug } returns "01"

        val source: ResourceContainer = mockk {
            every { chunks(any()) }.returns(listOf("01", "03", "05"))
            every { language }.returns(this@RenderHelpsTest.language)
            every { project }.returns(this@RenderHelpsTest.project)
        }
        every { listItem.source } returns source

        val result = RenderHelps(catalogClient).execute(listItem)

        assertEquals(3, result.size)
        assertEquals(3, (result["questions"]!! as List<*>).size)
        assertEquals(3, (result["notes"]!! as List<*>).size)
        assertEquals(0, (result["words"]!! as List<*>).size)

        verify { source.chunks(any()) }
        verify(exactly = 0) { ContainerCache.cacheFromLinks(any(), any(), any()) }
        verify(exactly = 0) { ContainerCache.cacheClosest(any(), any(), any(), any()) }
    }

    @Test
    fun `test render helps with no tQ resource`() {
        val listItem: Chunk = mockk {
            every { config }.returns(mockTw())
        }
        every { listItem.chapterSlug } returns "01"
        every { listItem.chunkSlug } returns "01"

        val source: ResourceContainer = mockk {
            every { chunks(any()) }.returns(listOf("01", "03", "05"))
            every { language }.returns(this@RenderHelpsTest.language)
            every { project }.returns(this@RenderHelpsTest.project)
        }
        every { listItem.source } returns source

        every { index.findTranslations(any(), any(), "tq", any(), any(), any(), any()) }
            .returns(listOf())

        val result = RenderHelps(catalogClient).execute(listItem)

        assertEquals(3, result.size)
        assertEquals(0, (result["questions"]!! as List<*>).size)
        assertEquals(3, (result["notes"]!! as List<*>).size)
        assertEquals(2, (result["words"]!! as List<*>).size)

        verify(exactly = 0) { source.chunks(any()) }
        verify { ContainerCache.cacheFromLinks(any(), any(), any()) }
        verify { ContainerCache.cacheClosest(any(), any(), any(), any()) }
    }

    @Test
    fun `test render helps with no tQ resource, no rc`() {
        val listItem: Chunk = mockk {
            every { config }.returns(mockTw())
        }
        every { listItem.chapterSlug } returns "01"
        every { listItem.chunkSlug } returns "01"

        val source: ResourceContainer = mockk {
            every { chunks(any()) }.returns(listOf("01", "03", "05"))
            every { language }.returns(this@RenderHelpsTest.language)
            every { project }.returns(this@RenderHelpsTest.project)
        }
        every { listItem.source } returns source

        every { ContainerCache.cache(catalogClient, "en_mrk_tq") }
            .returns(null)

        val result = RenderHelps(catalogClient).execute(listItem)

        assertEquals(3, result.size)
        assertEquals(0, (result["questions"]!! as List<*>).size)
        assertEquals(3, (result["notes"]!! as List<*>).size)
        assertEquals(2, (result["words"]!! as List<*>).size)

        verify(exactly = 0) { source.chunks(any()) }
        verify { ContainerCache.cacheFromLinks(any(), any(), any()) }
        verify { ContainerCache.cacheClosest(any(), any(), any(), any()) }
    }

    @Test
    fun `test render helps with no tN resource`() {
        val listItem: Chunk = mockk {
            every { config }.returns(mockTw())
        }
        every { listItem.chapterSlug } returns "01"
        every { listItem.chunkSlug } returns "01"

        val source: ResourceContainer = mockk {
            every { chunks(any()) }.returns(listOf("01", "03", "05"))
            every { language }.returns(this@RenderHelpsTest.language)
            every { project }.returns(this@RenderHelpsTest.project)
        }
        every { listItem.source } returns source
        every { index.findTranslations(any(), any(), "tn", any(), any(), any(), any()) }
            .returns(listOf())

        val result = RenderHelps(catalogClient).execute(listItem)

        assertEquals(3, result.size)
        assertEquals(3, (result["questions"]!! as List<*>).size)
        assertEquals(0, (result["notes"]!! as List<*>).size)
        assertEquals(2, (result["words"]!! as List<*>).size)

        verify { source.chunks(any()) }
        verify { ContainerCache.cacheFromLinks(any(), any(), any()) }
        verify { ContainerCache.cacheClosest(any(), any(), any(), any()) }
    }

    @Test
    fun `test render helps with no tN resource, no rc`() {
        val listItem: Chunk = mockk {
            every { config }.returns(mockTw())
        }
        every { listItem.chapterSlug } returns "01"
        every { listItem.chunkSlug } returns "01"

        val source: ResourceContainer = mockk {
            every { chunks(any()) }.returns(listOf("01", "03", "05"))
            every { language }.returns(this@RenderHelpsTest.language)
            every { project }.returns(this@RenderHelpsTest.project)
        }
        every { listItem.source } returns source

        every { ContainerCache.cache(catalogClient, "en_mrk_tn") }
            .returns(null)

        val result = RenderHelps(catalogClient).execute(listItem)

        assertEquals(3, result.size)
        assertEquals(3, (result["questions"]!! as List<*>).size)
        assertEquals(0, (result["notes"]!! as List<*>).size)
        assertEquals(2, (result["words"]!! as List<*>).size)

        verify { source.chunks(any()) }
        verify { ContainerCache.cacheFromLinks(any(), any(), any()) }
        verify { ContainerCache.cacheClosest(any(), any(), any(), any()) }
    }

    private fun mockHelpsRc(resource: String): ResourceContainer {
        val rc: ResourceContainer = mockk {
            every { chunks(any()) }.returns(listOf("01", "03", "05"))
            every { readChunk(any(), any()) }.answers {
                val content = """
                   # {id} $resource title text
                   {id} $resource body text
                """.trimIndent()
                if (resource == "tq") {
                    // Because tq is mapped by verse not by chunk we return
                    // single question per chunk
                    content.replace("{id}", secondArg<String>())
                } else {
                    """
                        ${content.replace("{id}", "01")}
                        ${content.replace("{id}", "03")}
                        ${content.replace("{id}", "05")}
                    """.trimIndent()
                }
            }
        }

        return rc
    }

    private fun mockHelpsTranslation(slug: String): Translation {
        val translation: Translation = mockk()
        every { translation.resourceContainerSlug } returns slug
        return translation
    }

    private fun mockTw(): Map<String, List<String>> {
        every { ContainerCache.cacheFromLinks(any(), any(), any()) }.answers {
            val words = secondArg<List<String>>()
            words.map {
                val link: Link = mockk {
                    every { chapter }.returns(it)
                    every { resource }.returns("tw")
                    every { project }.returns("tw")
                    every { copy(
                        title = any(),
                        url = any(),
                        resource = any(),
                        project = any(),
                        language = any(),
                        arguments = any(),
                        protocol = any(),
                        chapter = any(),
                        chunk = any(),
                        lastChunk = any()
                    ) }.returns(this)
                }
                link
            }
        }

        every { ContainerCache.cacheClosest(any(), any(), any(), any()) }
            .returns(mockHelpsRc("tw"))

        val config: MutableMap<String, List<String>> = HashMap()
        config["words"] = listOf("word1", "word2")

        return config
    }
}