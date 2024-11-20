package com.door43.usecases

import android.content.Context
import com.door43.OnProgressListener
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.unfoldingword.resourcecontainer.ResourceContainer

class ImportDraftTest {

    @MockK private lateinit var context: Context
    @MockK private lateinit var translator: Translator
    @MockK private lateinit var profile: Profile
    @MockK private lateinit var progressListener: OnProgressListener
    @MockK private lateinit var draftTranslator: ResourceContainer

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        every { progressListener.onProgress(any(), any(), any()) }.just(runs)
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
            .execute(draftTranslator, progressListener)

        assertNotNull(result.targetTranslation)
        assertEquals(targetTranslation, result.targetTranslation)

        verify { progressListener.onProgress(any(), any(), any()) }
        verify { context.getString(R.string.importing_draft) }
    }
}