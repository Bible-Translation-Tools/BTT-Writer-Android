package com.door43.usecases

import com.door43.TestUtils
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
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
import org.unfoldingword.resourcecontainer.Resource
import org.unfoldingword.resourcecontainer.ResourceContainer

class TranslationProgressTest {

    @MockK private lateinit var library: Door43Client
    @MockK private lateinit var translator: Translator
    @MockK private lateinit var targetTranslation: TargetTranslation
    @MockK private lateinit var index: Index

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        TestUtils.setPropertyReflection(library, "index", index)

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
            every { chapters() }.returns(arrayOf("01", "02", "03"))
            every { chunks(any()) }.answers {
                val chapter = firstArg<String>()
                when (chapter) {
                    "01" -> arrayOf("01", "02", "03")
                    "02" -> arrayOf("01", "03", "05")
                    "03" -> arrayOf("01", "05")
                    else -> arrayOf()
                }
            }
        }
        every { library.open(any()) }.returns(rc)
        every { translator.getSelectedSourceTranslationId(any()) }
            .returns("id_mrk_ayt")
        every { targetTranslation.numFinished() }.returns(8)

        val progress = TranslationProgress(library, translator).execute(targetTranslation)

        assertEquals(1.0, progress, 0.0)

        verify { rc.chapters() }
        verify { rc.chunks(any()) }
        verify { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
        verify { targetTranslation.numFinished() }
        verify { library.open(any()) }
        verify { translator.getSelectedSourceTranslationId(any()) }
    }

    @Test
    fun `test complete progress with no selected source, with English as default`() {
        val rc: ResourceContainer = mockk {
            every { chapters() }.returns(arrayOf("01", "02", "03"))
            every { chunks(any()) }.answers {
                val chapter = firstArg<String>()
                when (chapter) {
                    "01" -> arrayOf("01", "02", "03")
                    "02" -> arrayOf("01", "03", "05")
                    "03" -> arrayOf("01", "05")
                    else -> arrayOf()
                }
            }
        }
        every { library.open(any()) }.returns(rc)
        every { translator.getSelectedSourceTranslationId(any()) }
            .returns(null)
        every { targetTranslation.numFinished() }.returns(8)

        val progress = TranslationProgress(library, translator).execute(targetTranslation)

        assertEquals(1.0, progress, 0.0)

        verify { rc.chapters() }
        verify { rc.chunks(any()) }
        verify { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
        verify { targetTranslation.numFinished() }
        verify { library.open(any()) }
        verify { translator.getSelectedSourceTranslationId(any()) }
    }

    @Test
    fun `test zero progress when source translation not found`() {
        every { translator.getSelectedSourceTranslationId(any()) }
            .returns("wrong_rc_id")
        every { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
            .returns(listOf())

        val progress = TranslationProgress(library, translator).execute(targetTranslation)

        assertEquals(0.0, progress, 0.0)

        verify { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
        verify { translator.getSelectedSourceTranslationId(any()) }
        verify(exactly = 0) { targetTranslation.numFinished() }
        verify(exactly = 0) { library.open(any()) }
    }

    @Test
    fun `test zero progress when source rc is not downloaded`() {
        every { library.open(any()) }.throws(Exception("Rc is not downloaded."))
        every { translator.getSelectedSourceTranslationId(any()) }
            .returns("id_mrk_ayt")
        every { targetTranslation.numFinished() }.returns(8)

        val progress = TranslationProgress(library, translator).execute(targetTranslation)

        assertEquals(0.0, progress, 0.0)

        verify { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
        verify { library.open(any()) }
        verify { translator.getSelectedSourceTranslationId(any()) }
        verify(exactly = 0) { targetTranslation.numFinished() }
    }

    @Test
    fun `test half progress with the selected source`() {
        val rc: ResourceContainer = mockk {
            every { chapters() }.returns(arrayOf("01", "02", "03"))
            every { chunks(any()) }.answers {
                val chapter = firstArg<String>()
                when (chapter) {
                    "01" -> arrayOf("01", "02", "03")
                    "02" -> arrayOf("01", "03", "05")
                    "03" -> arrayOf("01", "05")
                    else -> arrayOf()
                }
            }
        }
        every { library.open(any()) }.returns(rc)
        every { translator.getSelectedSourceTranslationId(any()) }
            .returns("id_mrk_ayt")
        every { targetTranslation.numFinished() }.returns(4)

        val progress = TranslationProgress(library, translator).execute(targetTranslation)

        assertEquals(0.5, progress, 0.0)

        verify { rc.chapters() }
        verify { rc.chunks(any()) }
        verify { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
        verify { targetTranslation.numFinished() }
        verify { library.open(any()) }
        verify { translator.getSelectedSourceTranslationId(any()) }
    }

    @Test
    fun `test target chunks more than source chunks still returns 100`() {
        val rc: ResourceContainer = mockk {
            every { chapters() }.returns(arrayOf("01", "02", "03"))
            every { chunks(any()) }.answers {
                val chapter = firstArg<String>()
                when (chapter) {
                    "01" -> arrayOf("01", "02", "03")
                    "02" -> arrayOf("01", "03", "05")
                    "03" -> arrayOf("01", "05")
                    else -> arrayOf()
                }
            }
        }
        every { library.open(any()) }.returns(rc)
        every { translator.getSelectedSourceTranslationId(any()) }
            .returns("id_mrk_ayt")
        every { targetTranslation.numFinished() }.returns(10)

        val progress = TranslationProgress(library, translator).execute(targetTranslation)

        assertEquals(1.0, progress, 0.0)

        verify { rc.chapters() }
        verify { rc.chunks(any()) }
        verify { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
        verify { targetTranslation.numFinished() }
        verify { library.open(any()) }
        verify { translator.getSelectedSourceTranslationId(any()) }
    }

    @Test
    fun `test when source chunks empty, progress is 0`() {
        val rc: ResourceContainer = mockk {
            every { chapters() }.returns(arrayOf())
            every { chunks(any()) }.returns(arrayOf())
        }
        every { library.open(any()) }.returns(rc)
        every { translator.getSelectedSourceTranslationId(any()) }
            .returns("id_mrk_ayt")
        every { targetTranslation.numFinished() }.returns(8)

        val progress = TranslationProgress(library, translator).execute(targetTranslation)

        assertEquals(0.0, progress, 0.0)

        verify { rc.chapters() }
        verify { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
        verify { targetTranslation.numFinished() }
        verify { library.open(any()) }
        verify { translator.getSelectedSourceTranslationId(any()) }
        verify(exactly = 0) { rc.chunks(any()) }
    }

    private fun mockSourceTranslations() {
        val english: Language = mockk()
        val ulb: Resource = mockk()
        val enTranslation: Translation = mockk()
        TestUtils.setPropertyReflection(english, "slug", "en")
        TestUtils.setPropertyReflection(ulb, "slug", "ulb")
        TestUtils.setPropertyReflection(enTranslation, "language", english)
        TestUtils.setPropertyReflection(enTranslation, "resource", ulb)
        TestUtils.setPropertyReflection(enTranslation, "resourceContainerSlug", "en_mrk_ulb")

        val indonesian: Language = mockk()
        val ayt: Resource = mockk()
        val idTranslation: Translation = mockk()
        TestUtils.setPropertyReflection(indonesian, "slug", "id")
        TestUtils.setPropertyReflection(ayt, "slug", "ayt")
        TestUtils.setPropertyReflection(idTranslation, "language", indonesian)
        TestUtils.setPropertyReflection(idTranslation, "resource", ayt)
        TestUtils.setPropertyReflection(idTranslation, "resourceContainerSlug", "id_mrk_ayt")

        every { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
            .returns(listOf(enTranslation, idTranslation))
    }
}