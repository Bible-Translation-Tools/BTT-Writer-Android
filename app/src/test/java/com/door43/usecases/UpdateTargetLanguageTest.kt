package com.door43.usecases

import com.door43.TestUtils
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.Index
import org.unfoldingword.door43client.models.TargetLanguage

class UpdateTargetLanguageTest {

    @MockK private lateinit var library: Door43Client
    @MockK private lateinit var index: Index
    @MockK private lateinit var translator: Translator

    private val language = TargetLanguage("aa", "Afar", "Afar", "rtl", "Africa", false)
    private val other = TargetLanguage("bb", "Other", "Other", "ltr", "Asia", false)

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        TestUtils.setPropertyReflection(library, "index", index)
        every { index.addTargetLanguage(any()) }.returns(true)
        every { index.addTempTargetLanguage(any()) }.returns(true)
    }

    private fun project(lang: TargetLanguage): TargetTranslation = mockk {
        every { id }.returns("${lang.slug}_mrk")
        every { targetLanguage }.returns(lang)
        every { changeTargetLanguage(any()) }.returns(true)
    }

    @Test
    fun updatesRegularLanguageAndOnlyMatchingProjects() {
        every { index.isTempTargetLanguage("aa") }.returns(false)
        val matching = project(language)
        val notMatching = project(other)
        every { translator.targetTranslations }.returns(arrayOf(matching, notMatching))

        assertTrue(UpdateTargetLanguage(library, translator).execute(language))

        verify { index.addTargetLanguage(language) }
        verify(exactly = 0) { index.addTempTargetLanguage(any()) }
        verify { matching.changeTargetLanguage(language) }
        verify(exactly = 0) { notMatching.changeTargetLanguage(any()) }
    }

    @Test
    fun updatesTempLanguageInTempTable() {
        every { index.isTempTargetLanguage("aa") }.returns(true)
        every { translator.targetTranslations }.returns(arrayOf())

        assertTrue(UpdateTargetLanguage(library, translator).execute(language))

        verify { index.addTempTargetLanguage(language) }
        verify(exactly = 0) { index.addTargetLanguage(any()) }
    }

    @Test
    fun brokenProjectDoesNotBlockOthersButFailsResult() {
        every { index.isTempTargetLanguage("aa") }.returns(false)
        val broken = project(language)
        every { broken.changeTargetLanguage(any()) }.throws(RuntimeException("disk"))
        val unsaved = project(language)
        every { unsaved.changeTargetLanguage(any()) }.returns(false)
        val healthy = project(language)
        every { translator.targetTranslations }.returns(arrayOf(broken, unsaved, healthy))

        assertFalse(UpdateTargetLanguage(library, translator).execute(language))

        verify { healthy.changeTargetLanguage(language) }
    }

    @Test
    fun failedIndexWriteStopsBeforeProjects() {
        every { index.isTempTargetLanguage("aa") }.returns(false)
        every { index.addTargetLanguage(any()) }.returns(false)

        assertFalse(UpdateTargetLanguage(library, translator).execute(language))

        verify(exactly = 0) { translator.targetTranslations }
    }
}
