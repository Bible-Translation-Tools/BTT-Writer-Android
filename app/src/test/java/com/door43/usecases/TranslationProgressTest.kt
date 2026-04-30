package com.door43.usecases

import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.bibletranslationtools.resourcecatalog.ResourceCatalogClient
import org.bibletranslationtools.resourcecatalog.library.Index
import org.bibletranslationtools.resourcecatalog.library.models.Translation
import org.bibletranslationtools.resourcecontainer.Language
import org.bibletranslationtools.resourcecontainer.Resource
import org.bibletranslationtools.resourcecontainer.ResourceContainer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TranslationProgressTest {

    @MockK private lateinit var catalogClient: ResourceCatalogClient
    @MockK private lateinit var translator: Translator
    @MockK private lateinit var targetTranslation: TargetTranslation
    @MockK private lateinit var index: Index

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        every { catalogClient.library } returns index
        every { targetTranslation.projectId }.returns("mrk")
        every { targetTranslation.id }.returns("aa_mrk_text_ulb")

        mockSourceTranslations()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test complete progress with selected source`() {
        val rc: ResourceContainer = mockk {
            every { chapters() }.returns(listOf("01", "02", "03"))
            every { chunks(any()) }.answers {
                val chapter = firstArg<String>()
                when (chapter) {
                    "01" -> listOf("01", "02", "03")
                    "02" -> listOf("01", "03", "05")
                    "03" -> listOf("01", "05")
                    else -> listOf()
                }
            }
        }
        every { catalogClient.openResourceContainer(any()) }.returns(rc)
        every { translator.getSelectedSourceTranslationId(any()) }
            .returns("id_mrk_ayt")
        every { targetTranslation.numFinished }.returns(8)

        val progress = TranslationProgress(catalogClient, translator).execute(targetTranslation)

        assertEquals(1f, progress, 0f)

        verify { rc.chapters() }
        verify { rc.chunks(any()) }
        verify { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
        verify { targetTranslation.numFinished }
        verify { catalogClient.openResourceContainer(any()) }
        verify { translator.getSelectedSourceTranslationId(any()) }
    }

    @Test
    fun `test complete progress with no selected source, with English as default`() {
        val rc: ResourceContainer = mockk {
            every { chapters() }.returns(listOf("01", "02", "03"))
            every { chunks(any()) }.answers {
                val chapter = firstArg<String>()
                when (chapter) {
                    "01" -> listOf("01", "02", "03")
                    "02" -> listOf("01", "03", "05")
                    "03" -> listOf("01", "05")
                    else -> listOf()
                }
            }
        }
        every { catalogClient.openResourceContainer(any()) }.returns(rc)
        every { translator.getSelectedSourceTranslationId(any()) }
            .returns(null)
        every { targetTranslation.numFinished }.returns(8)

        val progress = TranslationProgress(catalogClient, translator).execute(targetTranslation)

        assertEquals(1f, progress, 0f)

        verify { rc.chapters() }
        verify { rc.chunks(any()) }
        verify { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
        verify { targetTranslation.numFinished }
        verify { catalogClient.openResourceContainer(any()) }
        verify { translator.getSelectedSourceTranslationId(any()) }
    }

    @Test
    fun `test zero progress when source translation not found`() {
        every { translator.getSelectedSourceTranslationId(any()) }
            .returns("wrong_rc_id")
        every { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
            .returns(listOf())

        val progress = TranslationProgress(catalogClient, translator).execute(targetTranslation)

        assertEquals(0f, progress, 0f)

        verify { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
        verify { translator.getSelectedSourceTranslationId(any()) }
        verify(exactly = 0) { targetTranslation.numFinished }
        verify(exactly = 0) { catalogClient.openResourceContainer(any()) }
    }

    @Test
    fun `test zero progress when source rc is not downloaded`() {
        every { catalogClient.openResourceContainer(any()) }.throws(Exception("Rc is not downloaded."))
        every { translator.getSelectedSourceTranslationId(any()) }
            .returns("id_mrk_ayt")
        every { targetTranslation.numFinished }.returns(8)

        val progress = TranslationProgress(catalogClient, translator).execute(targetTranslation)

        assertEquals(0f, progress, 0f)

        verify { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
        verify { catalogClient.openResourceContainer(any()) }
        verify { translator.getSelectedSourceTranslationId(any()) }
        verify(exactly = 0) { targetTranslation.numFinished }
    }

    @Test
    fun `test half progress with the selected source`() {
        val rc: ResourceContainer = mockk {
            every { chapters() }.returns(listOf("01", "02", "03"))
            every { chunks(any()) }.answers {
                val chapter = firstArg<String>()
                when (chapter) {
                    "01" -> listOf("01", "02", "03")
                    "02" -> listOf("01", "03", "05")
                    "03" -> listOf("01", "05")
                    else -> listOf()
                }
            }
        }
        every { catalogClient.openResourceContainer(any()) }.returns(rc)
        every { translator.getSelectedSourceTranslationId(any()) }
            .returns("id_mrk_ayt")
        every { targetTranslation.numFinished }.returns(4)

        val progress = TranslationProgress(catalogClient, translator).execute(targetTranslation)

        assertEquals(0.5f, progress, 0f)

        verify { rc.chapters() }
        verify { rc.chunks(any()) }
        verify { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
        verify { targetTranslation.numFinished }
        verify { catalogClient.openResourceContainer(any()) }
        verify { translator.getSelectedSourceTranslationId(any()) }
    }

    @Test
    fun `test target chunks more than source chunks still returns 100`() {
        val rc: ResourceContainer = mockk {
            every { chapters() }.returns(listOf("01", "02", "03"))
            every { chunks(any()) }.answers {
                val chapter = firstArg<String>()
                when (chapter) {
                    "01" -> listOf("01", "02", "03")
                    "02" -> listOf("01", "03", "05")
                    "03" -> listOf("01", "05")
                    else -> listOf()
                }
            }
        }
        every { catalogClient.openResourceContainer(any()) }.returns(rc)
        every { translator.getSelectedSourceTranslationId(any()) }
            .returns("id_mrk_ayt")
        every { targetTranslation.numFinished }.returns(10)

        val progress = TranslationProgress(catalogClient, translator).execute(targetTranslation)

        assertEquals(1f, progress, 0f)

        verify { rc.chapters() }
        verify { rc.chunks(any()) }
        verify { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
        verify { targetTranslation.numFinished }
        verify { catalogClient.openResourceContainer(any()) }
        verify { translator.getSelectedSourceTranslationId(any()) }
    }

    @Test
    fun `test when source chunks empty, progress is 0`() {
        val rc: ResourceContainer = mockk {
            every { chapters() }.returns(listOf())
            every { chunks(any()) }.returns(listOf())
        }
        every { catalogClient.openResourceContainer(any()) }.returns(rc)
        every { translator.getSelectedSourceTranslationId(any()) }
            .returns("id_mrk_ayt")
        every { targetTranslation.numFinished }.returns(8)

        val progress = TranslationProgress(catalogClient, translator).execute(targetTranslation)

        assertEquals(0f, progress, 0f)

        verify { rc.chapters() }
        verify { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
        verify { targetTranslation.numFinished }
        verify { catalogClient.openResourceContainer(any()) }
        verify { translator.getSelectedSourceTranslationId(any()) }
        verify(exactly = 0) { rc.chunks(any()) }
    }

    private fun mockSourceTranslations() {
        val english: Language = mockk {
            every { slug }.returns("en")
        }
        val ulb: Resource = mockk {
            every { slug }.returns("ulb")
        }
        val enTranslation: Translation = mockk()

        every { enTranslation.language } returns english
        every { enTranslation.resource } returns ulb
        every { enTranslation.resourceContainerSlug } returns "en_mrk_ulb"

        val indonesian: Language = mockk {
            every { slug }.returns("id")
        }
        val ayt: Resource = mockk {
            every { slug }.returns("ayt")
        }
        val idTranslation: Translation = mockk()
        every { idTranslation.language } returns indonesian
        every { idTranslation.resource } returns ayt
        every { idTranslation.resourceContainerSlug } returns "id_mrk_ayt"

        every { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
            .returns(listOf(enTranslation, idTranslation))
    }
}