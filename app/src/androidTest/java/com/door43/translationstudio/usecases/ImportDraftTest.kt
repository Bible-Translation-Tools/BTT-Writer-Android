package com.door43.translationstudio.usecases

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.core.Translator
import com.door43.usecases.ImportDraft
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.unfoldingword.door43client.Door43Client
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ImportDraftTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject lateinit var importDraft: ImportDraft
    @Inject lateinit var library: Door43Client
    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var translator: Translator

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        directoryProvider.clearCache()
        directoryProvider.deleteTranslations()
    }

    @Test
    fun testImportDraftTranslation() {
        val draftId = "en_gen_ulb"
        val draftTranslation = try {
            library.open(draftId)
        } catch (e: Exception) {
            null
        }

        assertNotNull("Draft translation should not be null", draftTranslation)
        assertEquals("Draft translation should be correct", draftId, draftTranslation!!.slug)
        assertEquals(
            "Draft language should be correct",
            "en",
            draftTranslation.language.slug
        )
        assertEquals(
            "Draft project should be correct",
            "gen",
            draftTranslation.project.slug
        )
        assertEquals(
            "Draft resource should be correct",
            "ulb",
            draftTranslation.resource.slug
        )

        val result = importDraft.execute(draftTranslation)

        assertNotNull("Target translation should not be null", result.targetTranslation)
        assertEquals(
            "Languages should be correct",
            draftTranslation.project.languageSlug,
            result.targetTranslation!!.targetLanguageId
        )
        assertEquals(
            "Projects should be correct",
            draftTranslation.project.slug,
            result.targetTranslation!!.projectId
        )
        assertEquals(
            "Target resource should be correct",
            "reg",
            result.targetTranslation!!.resourceSlug
        )
        assertEquals(
            "Target translation id should be correct",
            "en_gen_text_reg",
            result.targetTranslation!!.id
        )

        val targetTranslation = translator.getTargetTranslation(result.targetTranslation!!.id)

        assertEquals(
            "Target translation should be correctly opened",
            targetTranslation!!.id,
            result.targetTranslation!!.id
        )
    }
}