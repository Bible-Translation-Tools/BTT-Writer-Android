package com.door43.usecases

import com.door43.TestUtils
import com.door43.translationstudio.core.ContainerCache
import com.door43.translationstudio.ui.translate.ListItem
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.Index
import org.unfoldingword.door43client.models.Translation
import org.unfoldingword.resourcecontainer.Language
import org.unfoldingword.resourcecontainer.Link
import org.unfoldingword.resourcecontainer.Project
import org.unfoldingword.resourcecontainer.ResourceContainer

class RenderHelpsTest {

    @MockK private lateinit var library: Door43Client
    @MockK private lateinit var language: Language
    @MockK private lateinit var project: Project
    @MockK private lateinit var index: Index

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        TestUtils.setPropertyReflection(language, "slug", "en")
        TestUtils.setPropertyReflection(project, "slug", "mrk")

        TestUtils.setPropertyReflection(library, "index", index)

        mockkStatic(ContainerCache::class)
        every { ContainerCache.cache(library, any()) }
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
        val listItem: ListItem = mockk {
            every { chunkConfig }.returns(mockTw())
        }
        TestUtils.setPropertyReflection(listItem, "chapterSlug", "01")
        TestUtils.setPropertyReflection(listItem, "chunkSlug", "01")

        val source: ResourceContainer = mockk {
            every { chunks(any()) }.returns(arrayOf("01", "03", "05"))
        }
        TestUtils.setPropertyReflection(listItem, "source", source)
        TestUtils.setPropertyReflection(source, "language", language)
        TestUtils.setPropertyReflection(source, "project", project)

        val result = RenderHelps(library).execute(listItem)

        assertEquals(3, result.helps.size)
        assertEquals(3, (result.helps["questions"]!! as List<*>).size)
        assertEquals(3, (result.helps["notes"]!! as List<*>).size)
        assertEquals(2, (result.helps["words"]!! as List<*>).size)

        verify { listItem.chunkConfig }
        verify { source.chunks(any()) }
        verify { ContainerCache.cacheFromLinks(any(), any(), any()) }
        verify { ContainerCache.cacheClosest(any(), any(), any(), any()) }
    }

    @Test
    fun `test render helps with no tW resource`() {
        val listItem: ListItem = mockk {
            every { chunkConfig }.returns(null)
        }
        TestUtils.setPropertyReflection(listItem, "chapterSlug", "01")
        TestUtils.setPropertyReflection(listItem, "chunkSlug", "01")

        val source: ResourceContainer = mockk {
            every { chunks(any()) }.returns(arrayOf("01", "03", "05"))
        }
        TestUtils.setPropertyReflection(listItem, "source", source)
        TestUtils.setPropertyReflection(source, "language", language)
        TestUtils.setPropertyReflection(source, "project", project)

        val result = RenderHelps(library).execute(listItem)

        assertEquals(3, result.helps.size)
        assertEquals(3, (result.helps["questions"]!! as List<*>).size)
        assertEquals(3, (result.helps["notes"]!! as List<*>).size)
        assertEquals(0, (result.helps["words"]!! as List<*>).size)

        verify { listItem.chunkConfig }
        verify { source.chunks(any()) }
        verify(exactly = 0) { ContainerCache.cacheFromLinks(any(), any(), any()) }
        verify(exactly = 0) { ContainerCache.cacheClosest(any(), any(), any(), any()) }
    }

    @Test
    fun `test render helps with no tQ resource`() {
        val listItem: ListItem = mockk {
            every { chunkConfig }.returns(mockTw())
        }
        TestUtils.setPropertyReflection(listItem, "chapterSlug", "01")
        TestUtils.setPropertyReflection(listItem, "chunkSlug", "01")

        val source: ResourceContainer = mockk {
            every { chunks(any()) }.returns(arrayOf("01", "03", "05"))
        }
        TestUtils.setPropertyReflection(listItem, "source", source)
        TestUtils.setPropertyReflection(source, "language", language)
        TestUtils.setPropertyReflection(source, "project", project)

        every { index.findTranslations(any(), any(), "tq", any(), any(), any(), any()) }
            .returns(listOf())

        val result = RenderHelps(library).execute(listItem)

        assertEquals(3, result.helps.size)
        assertEquals(0, (result.helps["questions"]!! as List<*>).size)
        assertEquals(3, (result.helps["notes"]!! as List<*>).size)
        assertEquals(2, (result.helps["words"]!! as List<*>).size)

        verify { listItem.chunkConfig }
        verify(exactly = 0) { source.chunks(any()) }
        verify { ContainerCache.cacheFromLinks(any(), any(), any()) }
        verify { ContainerCache.cacheClosest(any(), any(), any(), any()) }
    }

    @Test
    fun `test render helps with no tQ resource, no rc`() {
        val listItem: ListItem = mockk {
            every { chunkConfig }.returns(mockTw())
        }
        TestUtils.setPropertyReflection(listItem, "chapterSlug", "01")
        TestUtils.setPropertyReflection(listItem, "chunkSlug", "01")

        val source: ResourceContainer = mockk {
            every { chunks(any()) }.returns(arrayOf("01", "03", "05"))
        }
        TestUtils.setPropertyReflection(listItem, "source", source)
        TestUtils.setPropertyReflection(source, "language", language)
        TestUtils.setPropertyReflection(source, "project", project)

        every { ContainerCache.cache(library, "en_mrk_tq") }
            .returns(null)

        val result = RenderHelps(library).execute(listItem)

        assertEquals(3, result.helps.size)
        assertEquals(0, (result.helps["questions"]!! as List<*>).size)
        assertEquals(3, (result.helps["notes"]!! as List<*>).size)
        assertEquals(2, (result.helps["words"]!! as List<*>).size)

        verify { listItem.chunkConfig }
        verify(exactly = 0) { source.chunks(any()) }
        verify { ContainerCache.cacheFromLinks(any(), any(), any()) }
        verify { ContainerCache.cacheClosest(any(), any(), any(), any()) }
    }

    @Test
    fun `test render helps with no tN resource`() {
        val listItem: ListItem = mockk {
            every { chunkConfig }.returns(mockTw())
        }
        TestUtils.setPropertyReflection(listItem, "chapterSlug", "01")
        TestUtils.setPropertyReflection(listItem, "chunkSlug", "01")

        val source: ResourceContainer = mockk {
            every { chunks(any()) }.returns(arrayOf("01", "03", "05"))
        }
        TestUtils.setPropertyReflection(listItem, "source", source)
        TestUtils.setPropertyReflection(source, "language", language)
        TestUtils.setPropertyReflection(source, "project", project)

        every { index.findTranslations(any(), any(), "tn", any(), any(), any(), any()) }
            .returns(listOf())

        val result = RenderHelps(library).execute(listItem)

        assertEquals(3, result.helps.size)
        assertEquals(3, (result.helps["questions"]!! as List<*>).size)
        assertEquals(0, (result.helps["notes"]!! as List<*>).size)
        assertEquals(2, (result.helps["words"]!! as List<*>).size)

        verify { listItem.chunkConfig }
        verify { source.chunks(any()) }
        verify { ContainerCache.cacheFromLinks(any(), any(), any()) }
        verify { ContainerCache.cacheClosest(any(), any(), any(), any()) }
    }

    @Test
    fun `test render helps with no tN resource, no rc`() {
        val listItem: ListItem = mockk {
            every { chunkConfig }.returns(mockTw())
        }
        TestUtils.setPropertyReflection(listItem, "chapterSlug", "01")
        TestUtils.setPropertyReflection(listItem, "chunkSlug", "01")

        val source: ResourceContainer = mockk {
            every { chunks(any()) }.returns(arrayOf("01", "03", "05"))
        }
        TestUtils.setPropertyReflection(listItem, "source", source)
        TestUtils.setPropertyReflection(source, "language", language)
        TestUtils.setPropertyReflection(source, "project", project)

        every { ContainerCache.cache(library, "en_mrk_tn") }
            .returns(null)

        val result = RenderHelps(library).execute(listItem)

        assertEquals(3, result.helps.size)
        assertEquals(3, (result.helps["questions"]!! as List<*>).size)
        assertEquals(0, (result.helps["notes"]!! as List<*>).size)
        assertEquals(2, (result.helps["words"]!! as List<*>).size)

        verify { listItem.chunkConfig }
        verify { source.chunks(any()) }
        verify { ContainerCache.cacheFromLinks(any(), any(), any()) }
        verify { ContainerCache.cacheClosest(any(), any(), any(), any()) }
    }

    private fun mockHelpsRc(resource: String): ResourceContainer {
        val rc: ResourceContainer = mockk {
            every { chunks(any()) }.returns(arrayOf("01", "03", "05"))
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
        TestUtils.setPropertyReflection(translation, "resourceContainerSlug", slug)
        return translation
    }

    private fun mockTw(): Map<String, List<String>> {
        every { ContainerCache.cacheFromLinks(any(), any(), any()) }.answers {
            val words = secondArg<List<String>>()
            words.map {
                val link: Link = mockk()
                TestUtils.setPropertyReflection(link, "chapter", it)
                TestUtils.setPropertyReflection(link, "resource", "tw")
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