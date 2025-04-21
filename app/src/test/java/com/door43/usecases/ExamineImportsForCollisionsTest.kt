package com.door43.usecases

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.core.ArchiveDetails
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TargetTranslationMigrator
import com.door43.translationstudio.core.Translator
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.unfoldingword.door43client.Door43Client
import java.io.File
import java.io.InputStream

class ExamineImportsForCollisionsTest {

    @MockK private lateinit var context: Context
    @MockK private lateinit var translator: Translator
    @MockK private lateinit var directoryProvider: IDirectoryProvider
    @MockK private lateinit var migrator: TargetTranslationMigrator
    @MockK private lateinit var library: Door43Client
    @MockK private lateinit var contentResolver: ContentResolver
    @MockK private lateinit var archiveDetails: ArchiveDetails

    private var translationFile: File? = null

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        translationFile = File.createTempFile("translation", ".tstudio").also {
            it.deleteOnExit()
        }

        every { context.contentResolver }.returns(contentResolver)
        every { directoryProvider.createTempFile(any(), any()) }.returns(translationFile!!)

        mockkConstructor(ArchiveDetails.Builder::class)
        every { anyConstructed<ArchiveDetails.Builder>().build() }.returns(archiveDetails)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test there is a collision`() {
        val uri: Uri = mockk()
        val inputStream: InputStream = mockk()
        every { contentResolver.openInputStream(uri) }.returns(inputStream)
        every { inputStream.close() } just runs

        var read = false
        every { inputStream.read(any()) }.answers {
            if (!read) {
                read = true
                8
            } else -1
        }

        val targetTranslation: TargetTranslation = mockk()
        every { translator.getTargetTranslation(any()) }.returns(targetTranslation)

        val targetTranslationDetails: ArchiveDetails.TargetTranslationDetails = mockk {
            every { projectName }.returns("mrk")
            every { targetLanguageName }.returns("aa")
            every { targetTranslationSlug }.returns("aa_mrk_text_ulb")
        }
        every { archiveDetails.targetTranslationDetails }.returns(listOf(targetTranslationDetails))

        val result = ExamineImportsForCollisions(
            context,
            translator,
            directoryProvider,
            migrator,
            library
        ).execute(uri)

        assertTrue(result.success)
        assertTrue(result.alreadyPresent)
        assertEquals(uri, result.contentUri)
        assertEquals("mrk - aa", result.projectsFound)

        verify { context.contentResolver }
        verify { directoryProvider.createTempFile(any(), any()) }
        verify { anyConstructed<ArchiveDetails.Builder>().build() }
        verify { translator.getTargetTranslation(any()) }
        verify { targetTranslationDetails.projectName }
        verify { targetTranslationDetails.targetLanguageName }
        verify { targetTranslationDetails.targetTranslationSlug }
    }

    @Test
    fun `test there is no collision`() {
        val uri: Uri = mockk()
        val inputStream: InputStream = mockk()
        every { contentResolver.openInputStream(uri) }.returns(inputStream)
        every { inputStream.close() } just runs

        var read = false
        every { inputStream.read(any()) }.answers {
            if (!read) {
                read = true
                8
            } else -1
        }

        val targetTranslation: TargetTranslation = mockk()
        every { translator.getTargetTranslation(any()) }.returns(targetTranslation)

        every { archiveDetails.targetTranslationDetails }.returns(listOf())

        val result = ExamineImportsForCollisions(
            context,
            translator,
            directoryProvider,
            migrator,
            library
        ).execute(uri)

        assertTrue(result.success)
        assertFalse(result.alreadyPresent)
        assertEquals(uri, result.contentUri)
        assertTrue(result.projectsFound.isNullOrEmpty())

        verify { context.contentResolver }
        verify { directoryProvider.createTempFile(any(), any()) }
        verify { anyConstructed<ArchiveDetails.Builder>().build() }
        verify(exactly = 0) { translator.getTargetTranslation(any()) }
    }
}