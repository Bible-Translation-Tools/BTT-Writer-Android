package com.door43.usecases

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.door43.OnProgressListener
import com.door43.TestUtils
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.R
import com.door43.translationstudio.core.ArchiveImporter
import com.door43.translationstudio.core.MergeConflictsHandler
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import com.door43.util.FileUtilities
import com.door43.util.Zip
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifySequence
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.resourcecontainer.Language
import org.unfoldingword.resourcecontainer.Project
import org.unfoldingword.resourcecontainer.Resource
import org.unfoldingword.resourcecontainer.ResourceContainer
import java.io.File
import java.io.InputStream

class ImportProjectsTest {

    @MockK private lateinit var context: Context
    @MockK private lateinit var translator: Translator
    @MockK private lateinit var backupRC: BackupRC
    @MockK private lateinit var directoryProvider: IDirectoryProvider
    @MockK private lateinit var archiveImporter: ArchiveImporter
    @MockK private lateinit var library: Door43Client
    @MockK private lateinit var progressListener: OnProgressListener
    @MockK private lateinit var contentResolver: ContentResolver

    @JvmField
    @Rule
    var tempDir: TemporaryFolder = TemporaryFolder()

    private lateinit var tStudioFile: File
    private lateinit var pdfFile: File

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        every { context.contentResolver }.returns(contentResolver)

        tStudioFile = tempDir.newFile("aa_mrk_text_ulb.tstudio")
        pdfFile = tempDir.newFile("aa_mrk_text_ulb.pdf")

        tStudioFile.writeText("tstudio")
        pdfFile.writeText("pdf")

        mockkStatic(Zip::class)
        every { Zip.unzipFromStream(any(), any()) }.answers {
            val content = firstArg<InputStream>().bufferedReader().use { it.readText() }
            if (content.contains("tstudio")) {
                Unit
            } else {
                throw Exception("Invalid file")
            }
        }

        mockkStatic(TargetTranslation::class)
        every { TargetTranslation.updateGenerator(any(), any()) }.just(runs)

        every { directoryProvider.cacheDir }.returns(tempDir.newFolder("cache"))
        every { translator.path }.returns(tempDir.newFolder("translations"))

        mockkStatic(FileUtilities::class)
        every { FileUtilities.deleteQuietly(any()) }.returns(true)

        every { progressListener.onProgress(any(), any(), any()) }.just(runs)
        every { context.getString(R.string.importing_file) }.returns("Importing file")

        mockkStatic(MergeConflictsHandler::class)
        mockkStatic(ResourceContainer::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
        tempDir.delete()
    }

    @Test
    fun `test import new project from file`() {
        val dir = tempDir.newFolder("aa_mrk_text_ulb")
        val targetTranslation: TargetTranslation = mockk {
            every { id }.returns("aa_mrk_text_ulb")
        }

        every { archiveImporter.importArchive(any()) }
            .returns(listOf(dir))

        every { TargetTranslation.open(any(), any()) }.answers {
            val translation = firstArg<File>()
            if (!translation.absolutePath.startsWith(translator.path.absolutePath)) {
                targetTranslation
            } else {
                // local translation doesn't exist
                null
            }
        }

        val result = ImportProjects(
            context,
            translator,
            backupRC,
            directoryProvider,
            archiveImporter,
            library
        ).importProject(tStudioFile, false)

        assertNotNull(result)
        requireNotNull(result)

        assertTrue(result.isSuccess)
        assertEquals(targetTranslation.id, result.importedSlug)
        assertFalse(result.alreadyExists)
        assertFalse(result.mergeConflict)

        verifyImportSuccess(targetTranslation)
    }

    @Test
    fun `test import new project from dir`() {
        val importDir = tempDir.newFolder("aa_mrk_text_ulb_import")
        val dir = tempDir.newFolder("aa_mrk_text_ulb")
        val targetTranslation: TargetTranslation = mockk {
            every { id }.returns("aa_mrk_text_ulb")
        }

        every { archiveImporter.importArchive(any()) }
            .returns(listOf(dir))

        every { TargetTranslation.open(any(), any()) }.answers {
            val translation = firstArg<File>()
            if (!translation.absolutePath.startsWith(translator.path.absolutePath)) {
                targetTranslation
            } else {
                // local translation doesn't exist
                null
            }
        }

        val result = ImportProjects(
            context,
            translator,
            backupRC,
            directoryProvider,
            archiveImporter,
            library
        ).importProject(importDir, false)

        assertNotNull(result)
        requireNotNull(result)

        assertTrue(result.isSuccess)
        assertEquals(targetTranslation.id, result.importedSlug)
        assertFalse(result.alreadyExists)
        assertFalse(result.mergeConflict)

        verify(exactly = 0) { Zip.unzipFromStream(any(), any()) }
        verify(exactly = 0) { directoryProvider.cacheDir }

        verify { TargetTranslation.updateGenerator(any(), any()) }
        verify { translator.path }
        verify { FileUtilities.deleteQuietly(any()) }
        verify { targetTranslation.id }
        verify { archiveImporter.importArchive(any()) }
        verify { TargetTranslation.open(any(), any()) }
        verify { FileUtilities.deleteQuietly(any()) }
    }

    @Test
    fun `test import project over old one from file, no overwrite, merge success`() {
        val dir = tempDir.newFolder("aa_mrk_text_ulb")
        val targetTranslation: TargetTranslation = mockk {
            every { id }.returns("aa_mrk_text_ulb")
        }
        val localTranslation: TargetTranslation = mockk {
            every { commitSync() }.returns(true)
            every { merge(any(), any()) }.returns(true)
        }

        every { archiveImporter.importArchive(any()) }
            .returns(listOf(dir))

        every { TargetTranslation.open(any(), any()) }.answers {
            val translation = firstArg<File>()
            if (!translation.absolutePath.startsWith(translator.path.absolutePath)) {
                targetTranslation
            } else {
                // local translation exists
                localTranslation
            }
        }

        val result = ImportProjects(
            context,
            translator,
            backupRC,
            directoryProvider,
            archiveImporter,
            library
        ).importProject(tStudioFile, false)

        assertNotNull(result)
        requireNotNull(result)

        assertTrue(result.isSuccess)
        assertEquals(targetTranslation.id, result.importedSlug)
        assertTrue(result.alreadyExists)
        assertFalse(result.mergeConflict)

        verifyImportSuccess(targetTranslation)

        verify { localTranslation.commitSync() }
        verify { localTranslation.merge(any(), any()) }
    }

    @Test
    fun `test import project over old one from file, no overwrite, merge fails`() {
        val dir = tempDir.newFolder("aa_mrk_text_ulb")
        val targetTranslation: TargetTranslation = mockk {
            every { id }.returns("aa_mrk_text_ulb")
        }
        val localTranslation: TargetTranslation = mockk {
            every { commitSync() }.returns(true)
            every { merge(any(), any()) }.returns(false)
        }

        every { archiveImporter.importArchive(any()) }
            .returns(listOf(dir))

        every { TargetTranslation.open(any(), any()) }.answers {
            val translation = firstArg<File>()
            if (!translation.absolutePath.startsWith(translator.path.absolutePath)) {
                targetTranslation
            } else {
                // local translation exists
                localTranslation
            }
        }

        val result = ImportProjects(
            context,
            translator,
            backupRC,
            directoryProvider,
            archiveImporter,
            library
        ).importProject(tStudioFile, false)

        assertNotNull(result)
        requireNotNull(result)

        assertTrue(result.isSuccess)
        assertEquals(targetTranslation.id, result.importedSlug)
        assertTrue(result.alreadyExists)
        assertTrue(result.mergeConflict)

        verifyImportSuccess(targetTranslation)

        verify { localTranslation.commitSync() }
        verify { localTranslation.merge(any(), any()) }
    }

    @Test
    fun `test import project over old one from file with overwrite`() {
        val dir = tempDir.newFolder("aa_mrk_text_ulb")
        val targetTranslation: TargetTranslation = mockk {
            every { id }.returns("aa_mrk_text_ulb")
        }
        val localTranslation: TargetTranslation = mockk {
            every { commitSync() }.returns(true)
            every { merge(any(), any()) }.returns(false)
        }

        every { archiveImporter.importArchive(any()) }
            .returns(listOf(dir))

        every { TargetTranslation.open(any(), any()) }.answers {
            val translation = firstArg<File>()
            if (!translation.absolutePath.startsWith(translator.path.absolutePath)) {
                targetTranslation
            } else {
                // local translation exists
                localTranslation
            }
        }

        val result = ImportProjects(
            context,
            translator,
            backupRC,
            directoryProvider,
            archiveImporter,
            library
        ).importProject(tStudioFile, true)

        assertNotNull(result)
        requireNotNull(result)

        assertTrue(result.isSuccess)
        assertEquals(targetTranslation.id, result.importedSlug)
        assertTrue(result.alreadyExists)
        assertFalse(result.mergeConflict)

        verifyImportSuccess(targetTranslation)

        // Merge should not happen
        verify(exactly = 0) { localTranslation.commitSync() }
        verify(exactly = 0) { localTranslation.merge(any(), any()) }
    }

    @Test
    fun `test import project from file, throws exception`() {
        every { archiveImporter.importArchive(any()) }.throws(Exception("An error occurred!"))

        val result = ImportProjects(
            context,
            translator,
            backupRC,
            directoryProvider,
            archiveImporter,
            library
        ).importProject(tStudioFile, false)

        assertNull(result)

        verifyImportFail()

        verify { FileUtilities.deleteQuietly(any()) }
        verify { archiveImporter.importArchive(any()) }
    }

    @Test
    fun `test import project invalid file`() {
        val result = ImportProjects(
            context,
            translator,
            backupRC,
            directoryProvider,
            archiveImporter,
            library
        ).importProject(pdfFile, true)

        assertNull(result)

        verifyImportFail()

        verify(exactly = 0) { FileUtilities.deleteQuietly(any()) }
        verify(exactly = 0) { archiveImporter.importArchive(any()) }
    }

    @Test
    fun `test import projects from files`() {
        val project1 = tempDir.newFile("ru_mrk_text_ulb.tstudio")
        val project2 = tempDir.newFile("fr_gen_text_ulb.tstudio")
        val translation1: TargetTranslation = mockk {
            every { id }.returns("aa_mrk_text_ulb")
            every { commitSync() }.returns(true)
        }
        val translation2: TargetTranslation = mockk {
            every { id }.returns("fr_gen_text_ulb")
            every { commitSync() }.returns(false)
        }
        val localTranslation: TargetTranslation = mockk {
            every { commitSync() }.returns(true)
            every { merge(any(), any()) }.returns(true)
        }
        every { translator.getConflictingTargetTranslation(any()) }.answers {
            when (firstArg<File>()) {
                project2 -> localTranslation
                else -> null
            }
        }

        every { TargetTranslation.open(any(), any()) }.answers {
            when (firstArg<File>()) {
                project1 -> translation1
                project2 -> translation2
                else -> null
            }
        }

        val result = ImportProjects(
            context,
            translator,
            backupRC,
            directoryProvider,
            archiveImporter,
            library
        ).importProjects(
            listOf(project1, project2),
            false,
            progressListener
        )

        assertTrue(result.success)
        assertEquals(localTranslation, result.conflictingTargetTranslation)

        verifySequence {
            progressListener.onProgress(any(), any(), "Importing file")
            progressListener.onProgress(0, any(), project1.name)
            progressListener.onProgress(12, any(), project1.name)
            progressListener.onProgress(50, any(), project2.name)
            progressListener.onProgress(62, any(), project2.name)
            progressListener.onProgress(75, any(), project2.name)
            progressListener.onProgress(100, any(), "Completed!")
        }

        verify { translator.getConflictingTargetTranslation(any()) }
    }

    @Test
    fun `test import projects from files with merge exception`() {
        val project = tempDir.newFile("id_mrk_text_ulb.tstudio")
        val translation: TargetTranslation = mockk {
            every { id }.returns("id_mrk_text_ulb")
            every { commitSync() }.returns(true)
        }
        val localTranslation: TargetTranslation = mockk {
            every { commitSync() }.returns(true)
            every { merge(any(), any()) }.throws(Exception("merge error"))
        }
        every { translator.getConflictingTargetTranslation(any()) }.returns(localTranslation)

        every { TargetTranslation.open(any(), any()) }.returns(translation)

        val result = ImportProjects(
            context,
            translator,
            backupRC,
            directoryProvider,
            archiveImporter,
            library
        ).importProjects(
            listOf(project),
            false,
            progressListener
        )

        assertFalse(result.success)
        assertEquals(localTranslation, result.conflictingTargetTranslation)

        verifySequence {
            progressListener.onProgress(any(), any(), "Importing file")
            progressListener.onProgress(0, any(), project.name)
            progressListener.onProgress(25, any(), project.name)
            progressListener.onProgress(50, any(), project.name)
            progressListener.onProgress(100, any(), "Completed!")
        }

        verify { translator.getConflictingTargetTranslation(any()) }
    }

    @Test
    fun `test import project from uri`() {
        val uri: Uri = mockk()

        every { FileUtilities.getUriDisplayName(any(), any()) }
            .returns("aa_mrk_text_ulb.tstudio")

        every { contentResolver.openInputStream(any()) }
            .returns(tStudioFile.inputStream())

        val dir = tempDir.newFolder("aa_mrk_text_ulb")
        val targetTranslation: TargetTranslation = mockk {
            every { id }.returns("aa_mrk_text_ulb")
        }

        every { TargetTranslation.open(any(), any()) }.answers {
            when (firstArg<File>()) {
                dir -> targetTranslation
                else -> null
            }
        }

        every { archiveImporter.importArchive(any()) }
            .returns(listOf(dir))

        val result = ImportProjects(
            context,
            translator,
            backupRC,
            directoryProvider,
            archiveImporter,
            library
        ).importProject(uri, false, progressListener)

        assertTrue(result.success)
        assertEquals(uri, result.filePath)
        assertEquals("aa_mrk_text_ulb.tstudio", result.readablePath)
        assertEquals("aa_mrk_text_ulb", result.importedSlug)
        assertFalse(result.alreadyExists)
        assertFalse(result.mergeConflict)
        assertFalse(result.invalidFileName)

        verifyUriImport(targetTranslation)
    }

    @Test
    fun `test import project from uri with merge conflict`() {
        val uri: Uri = mockk()

        every { FileUtilities.getUriDisplayName(any(), any()) }
            .returns("aa_mrk_text_ulb.tstudio")

        every { contentResolver.openInputStream(any()) }
            .returns(tStudioFile.inputStream())

        val dir = tempDir.newFolder("aa_mrk_text_ulb")
        val targetTranslation: TargetTranslation = mockk {
            every { id }.returns("aa_mrk_text_ulb")
        }
        val localTranslation: TargetTranslation = mockk {
            every { commitSync() }.returns(true)
            every { merge(any(), any()) }.returns(false)
        }

        every { TargetTranslation.open(any(), any()) }.answers {
            when (firstArg<File>()) {
                dir -> targetTranslation
                else -> null
            }
        }

        every { TargetTranslation.open(any(), any()) }.answers {
            val file = firstArg<File>()
            if (!file.absolutePath.startsWith(translator.path.absolutePath)) {
                targetTranslation
            } else {
                // local translation exists
                localTranslation
            }
        }

        every { archiveImporter.importArchive(any()) }
            .returns(listOf(dir))

        every { MergeConflictsHandler.isTranslationMergeConflicted(any(), any()) }
            .returns(true)

        val result = ImportProjects(
            context,
            translator,
            backupRC,
            directoryProvider,
            archiveImporter,
            library
        ).importProject(uri, false, progressListener)

        assertTrue(result.success)
        assertEquals(uri, result.filePath)
        assertEquals("aa_mrk_text_ulb.tstudio", result.readablePath)
        assertEquals("aa_mrk_text_ulb", result.importedSlug)
        assertTrue(result.alreadyExists)
        assertTrue(result.mergeConflict)
        assertFalse(result.invalidFileName)

        verifyUriImport(targetTranslation)
    }

    @Test
    fun `test import project from uri with merge conflict overwrite`() {
        val uri: Uri = mockk()

        every { FileUtilities.getUriDisplayName(any(), any()) }
            .returns("aa_mrk_text_ulb.tstudio")

        every { contentResolver.openInputStream(any()) }
            .returns(tStudioFile.inputStream())

        val dir = tempDir.newFolder("aa_mrk_text_ulb")
        val targetTranslation: TargetTranslation = mockk {
            every { id }.returns("aa_mrk_text_ulb")
        }
        val localTranslation: TargetTranslation = mockk {
            every { commitSync() }.returns(true)
            every { merge(any(), any()) }.returns(false)
        }

        every { TargetTranslation.open(any(), any()) }.answers {
            when (firstArg<File>()) {
                dir -> targetTranslation
                else -> null
            }
        }

        every { TargetTranslation.open(any(), any()) }.answers {
            val file = firstArg<File>()
            if (!file.absolutePath.startsWith(translator.path.absolutePath)) {
                targetTranslation
            } else {
                // local translation exists
                localTranslation
            }
        }

        every { archiveImporter.importArchive(any()) }
            .returns(listOf(dir))

        every { MergeConflictsHandler.isTranslationMergeConflicted(any(), any()) }
            .returns(true)

        val result = ImportProjects(
            context,
            translator,
            backupRC,
            directoryProvider,
            archiveImporter,
            library
        ).importProject(uri, true, progressListener)

        assertTrue(result.success)
        assertEquals(uri, result.filePath)
        assertEquals("aa_mrk_text_ulb.tstudio", result.readablePath)
        assertEquals("aa_mrk_text_ulb", result.importedSlug)
        assertTrue(result.alreadyExists)
        assertFalse(result.mergeConflict)
        assertFalse(result.invalidFileName)

        verifyUriImport(targetTranslation)
    }

    @Test
    fun `test import project from invalid file uri`() {
        val uri: Uri = mockk()

        every { FileUtilities.getUriDisplayName(any(), any()) }
            .returns("aa_mrk_text_ulb.pdf")

        val result = ImportProjects(
            context,
            translator,
            backupRC,
            directoryProvider,
            archiveImporter,
            library
        ).importProject(uri, true, progressListener)

        assertFalse(result.success)
        assertEquals(uri, result.filePath)
        assertEquals("aa_mrk_text_ulb.pdf", result.readablePath)
        assertNull(result.importedSlug)
        assertFalse(result.alreadyExists)
        assertFalse(result.mergeConflict)
        assertTrue(result.invalidFileName)

        verify { FileUtilities.getUriDisplayName(any(), any()) }
        verify(exactly = 0) { contentResolver.openInputStream(any()) }
        verify(exactly = 0) { TargetTranslation.open(any(), any()) }
        verify(exactly = 0) { archiveImporter.importArchive(any()) }
    }

    @Test
    fun `test import new source text from uri`() {
        val uri: Uri = mockk()

        val srcDir = tempDir.newFolder("fa_mrk_nmv")
        every { directoryProvider.createTempDir(any()) }.returns(srcDir)
        every { FileUtilities.copyDirectory(any(), any<Uri>(), any()) }
            .just(runs)

        every { library.open(any()) }.throws(Exception("local rc not found."))
        every { library.importResourceContainer(srcDir) }.returns(mockk())

        val tempRc: ResourceContainer = mockk()
        every { ResourceContainer.load(srcDir) }.returns(tempRc)

        val result = ImportProjects(
            context,
            translator,
            backupRC,
            directoryProvider,
            archiveImporter,
            library
        ).importSource(uri)

        assertTrue(result.success)
        assertFalse(result.hasConflict)
        assertNull(result.error)
        assertNull(result.targetDir)

        verify { directoryProvider.createTempDir(any()) }
        verify { FileUtilities.copyDirectory(any(), any<Uri>(), any()) }
        verify { library.open(any()) }
        verify { library.importResourceContainer(srcDir) }
        verify { ResourceContainer.load(srcDir) }
        verify { FileUtilities.deleteQuietly(any()) }
    }

    @Test
    fun `test import existing source text from uri`() {
        val uri: Uri = mockk()

        val srcDir = tempDir.newFolder("fa_mrk_nmv")
        every { directoryProvider.createTempDir(any()) }.returns(srcDir)
        every { FileUtilities.copyDirectory(any(), any<Uri>(), any()) }
            .just(runs)

        every { library.open(any()) }.returns(mockk())
        every { library.importResourceContainer(srcDir) }.returns(mockk())

        val tempRc = mockResourceContainer()
        every { ResourceContainer.load(srcDir) }.returns(tempRc)

        every { context.getString(R.string.overwrite_content) }.returns("Overwrite %s?")

        val expectedErrorMessage = "Overwrite Farsi - Mark - New Millennium Version?"

        val result = ImportProjects(
            context,
            translator,
            backupRC,
            directoryProvider,
            archiveImporter,
            library
        ).importSource(uri)

        assertFalse(result.success)
        assertTrue(result.hasConflict)
        assertEquals(expectedErrorMessage, result.error)
        assertEquals(srcDir, result.targetDir)

        verify { directoryProvider.createTempDir(any()) }
        verify { FileUtilities.copyDirectory(any(), any<Uri>(), any()) }
        verify { library.open(any()) }
        verify(exactly = 0) { library.importResourceContainer(srcDir) }
        verify { ResourceContainer.load(srcDir) }
        verify(exactly = 0) { FileUtilities.deleteQuietly(any()) }
    }

    @Test
    fun `test import invalid source text from uri`() {
        val uri: Uri = mockk()

        val srcDir = tempDir.newFolder("fa_mrk_nmv")
        every { directoryProvider.createTempDir(any()) }.returns(srcDir)
        every { FileUtilities.copyDirectory(any(), any<Uri>(), any()) }
            .just(runs)

        every { ResourceContainer.load(srcDir) }.throws(Exception("Invalid rc."))

        val expectedErrorMessage = "Invalid rc."

        val result = ImportProjects(
            context,
            translator,
            backupRC,
            directoryProvider,
            archiveImporter,
            library
        ).importSource(uri)

        assertFalse(result.success)
        assertFalse(result.hasConflict)
        assertEquals(expectedErrorMessage, result.error)
        assertNull(result.targetDir)

        verify { directoryProvider.createTempDir(any()) }
        verify { FileUtilities.copyDirectory(any(), any<Uri>(), any()) }
        verify(exactly = 0) { library.open(any()) }
        verify(exactly = 0) { library.importResourceContainer(srcDir) }
        verify { ResourceContainer.load(srcDir) }
        verify(exactly = 0) { FileUtilities.deleteQuietly(any()) }
    }

    @Test
    fun `test import source text from uri failed`() {
        val uri: Uri = mockk()

        val srcDir = tempDir.newFolder("fa_mrk_nmv")
        every { directoryProvider.createTempDir(any()) }.returns(srcDir)
        every { FileUtilities.copyDirectory(any(), any<Uri>(), any()) }
            .just(runs)

        every { library.open(any()) }.throws(Exception("local rc not found."))
        every { library.importResourceContainer(srcDir) }.throws(Exception("Failed to import rc."))

        val tempRc: ResourceContainer = mockk()
        every { ResourceContainer.load(srcDir) }.returns(tempRc)

        val expectedErrorMessage = "Failed to import rc."

        val result = ImportProjects(
            context,
            translator,
            backupRC,
            directoryProvider,
            archiveImporter,
            library
        ).importSource(uri)

        assertFalse(result.success)
        assertFalse(result.hasConflict)
        assertEquals(expectedErrorMessage, result.error)
        assertNull(result.targetDir)

        verify { directoryProvider.createTempDir(any()) }
        verify { FileUtilities.copyDirectory(any(), any<Uri>(), any()) }
        verify { library.open(any()) }
        verify { library.importResourceContainer(srcDir) }
        verify { ResourceContainer.load(srcDir) }
        verify { FileUtilities.deleteQuietly(any()) }
    }

    @Test
    fun `test import source from directory`() {
        val dir = tempDir.newFolder("fa_mrk_nmv")

        every { library.importResourceContainer(dir) }.returns(mockk())

        val result = ImportProjects(
            context,
            translator,
            backupRC,
            directoryProvider,
            archiveImporter,
            library
        ).importSource(dir)

        assertTrue(result.success)
        assertFalse(result.hasConflict)
        assertNull(result.error)
        assertNull(result.targetDir)

        verify { library.importResourceContainer(dir) }
        verify { FileUtilities.deleteQuietly(any()) }
    }

    private fun verifyImportSuccess(targetTranslation: TargetTranslation) {
        verify { Zip.unzipFromStream(any(), any()) }
        verify { TargetTranslation.updateGenerator(any(), any()) }
        verify { directoryProvider.cacheDir }
        verify { translator.path }
        verify { FileUtilities.deleteQuietly(any()) }
        verify { targetTranslation.id }
        verify { archiveImporter.importArchive(any()) }
        verify { TargetTranslation.open(any(), any()) }
        verify { FileUtilities.deleteQuietly(any()) }
    }

    private fun verifyImportFail() {
        verify { Zip.unzipFromStream(any(), any()) }
        verify { directoryProvider.cacheDir }
        verify(exactly = 0) { TargetTranslation.updateGenerator(any(), any()) }
        verify(exactly = 0) { translator.path }
        verify(exactly = 0) { TargetTranslation.open(any(), any()) }
    }

    private fun verifyUriImport(targetTranslation: TargetTranslation) {
        verify { FileUtilities.getUriDisplayName(any(), any()) }
        verify { contentResolver.openInputStream(any()) }
        verify { targetTranslation.id }
        verify { TargetTranslation.open(any(), any()) }
        verify { archiveImporter.importArchive(any()) }
    }

    private fun mockResourceContainer(): ResourceContainer {
        val language: Language = mockk()
        val project: Project = mockk()
        val resource: Resource = mockk()

        TestUtils.setPropertyReflection(language, "name", "Farsi")
        TestUtils.setPropertyReflection(project, "name", "Mark")
        TestUtils.setPropertyReflection(resource, "name", "New Millennium Version")

        val rc: ResourceContainer = mockk()

        TestUtils.setPropertyReflection(rc, "language", language)
        TestUtils.setPropertyReflection(rc, "project", project)
        TestUtils.setPropertyReflection(rc, "resource", resource)

        return rc
    }
}