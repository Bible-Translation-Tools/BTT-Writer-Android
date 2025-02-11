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
import org.junit.Before
import org.junit.Test
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.Index
import org.unfoldingword.door43client.models.Translation
import org.unfoldingword.resourcecontainer.Language
import org.unfoldingword.resourcecontainer.Project
import org.unfoldingword.resourcecontainer.Resource

class GetAvailableSourcesTest {

    @MockK private lateinit var library: Door43Client
    @MockK private lateinit var progressListener: OnProgressListener
    @MockK private lateinit var index: Index

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        TestUtils.setPropertyReflection(library, "index", index)

        every { progressListener.onProgress(any(), any(), any()) }.just(runs)
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

        val result = GetAvailableSources(library)
            .execute("test", progressListener)

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
        verify { progressListener.onProgress(any(), any(), any()) }
    }

    private fun mockLanguage(slug: String): Language {
        val language: Language = mockk()
        TestUtils.setPropertyReflection(language, "slug", slug)
        return language
    }

    private fun mockProject(slug: String): Project {
        val project: Project = mockk()
        TestUtils.setPropertyReflection(project, "slug", slug)
        return project
    }

    private fun mockResource(slug: String): Resource {
        val resource: Resource = mockk()
        TestUtils.setPropertyReflection(resource, "slug", slug)
        return resource
    }

    private fun mockTranslation(lang: String, book: String, resType: String): Translation {
        val translation: Translation = mockk()

        val project = if (resType == "dict") "bible" else book
        val resource = if (resType == "dict") "tw" else "ulb"

        TestUtils.setPropertyReflection(translation, "language", mockLanguage(lang))
        TestUtils.setPropertyReflection(translation, "project", mockProject(project))
        TestUtils.setPropertyReflection(translation, "resource", mockResource(resource))

        return translation
    }
}