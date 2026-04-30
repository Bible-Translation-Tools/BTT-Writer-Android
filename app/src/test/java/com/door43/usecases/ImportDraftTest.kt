package com.door43.usecases

import android.content.Context
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.bibletranslationtools.resourcecontainer.ResourceContainer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class ImportDraftTest {

    @MockK private lateinit var context: Context
    @MockK private lateinit var translator: Translator
    @MockK private lateinit var profile: Profile
    @MockK private lateinit var draftTranslator: ResourceContainer

    private val onProgress = mockk<(Float, String?) -> Unit>(relaxed = true)

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        every { onProgress(any(), any()) }.just(runs)
        every { context.getString(R.string.importing_draft) }
            .returns("Importing draft...")
        every { profile.nativeSpeaker }.returns(mockk())
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test import draft`() {
        val targetTranslation: TargetTranslation = mockk()
        every { translator.importDraftTranslation(any(), any()) }
            .returns(targetTranslation)

        val result = ImportDraft(context, translator, profile)
            .execute(draftTranslator, onProgress)

        assertNotNull(result.targetTranslation)
        assertEquals(targetTranslation, result.targetTranslation)

        verify { onProgress(any(), any()) }
        verify { context.getString(R.string.importing_draft) }
    }
}