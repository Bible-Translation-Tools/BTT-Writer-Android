package com.door43.usecases

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.door43.TestUtils
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.AppInfo
import com.door43.translationstudio.Platform
import com.door43.translationstudio.R
import com.door43.translationstudio.core.ArchiveImporter
import com.door43.translationstudio.core.MergeConflictsHandler
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import com.door43.util.FileUtilities
import com.door43.util.Zip
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifySequence
import kotlinx.coroutines.test.runTest
import org.bibletranslationtools.resourcecontainer.Language
import org.bibletranslationtools.resourcecontainer.Project
import org.bibletranslationtools.resourcecontainer.Resource
import org.bibletranslationtools.resourcecontainer.ResourceContainer
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
import java.io.File
import java.io.InputStream

class ImportProjectsTest {

    @MockK private lateinit var context: Context
    @MockK private lateinit var translator: Translator
    @MockK private lateinit var backupRC: BackupRC
    @MockK private lateinit var directoryProvider: IDirectoryProvider
    @MockK private lateinit var archiveImporter: ArchiveImporter
    @MockK private lateinit var library: Door43Client
    @MockK private lateinit var contentResolver: ContentResolver
    @MockK private lateinit var platform: Platform
    @MockK private lateinit var info: AppInfo

    val onProgress = mockk<(Float, String?) -> Unit>(relaxed = true)

    @JvmField
    @Rule
    var tempDir: TemporaryFolder = TemporaryFolder()

    private lateinit var tStudioFile: File
    private lateinit var pdfFile: File

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        every { context.contentResolver }.returns(contentResolver)

        every { info.versionCode }.returns(1)
        every { platform.info }.returns(info)

        tStudioFile = tempDir.newFile("aa_mrk_text_ulb.tstudio")
        pdfFile = tempDir.newFile("aa_mrk_text_ulb.pdf")

        tStudioFile.writeText("tstudio")
        pdfFile.writeText("pdf")

        mockkObject(Zip)
        every { Zip.unzipFromStream(any(), any()) }.answers {
            val content = firstArg<InputStream>().bufferedReader().use { it.readText() }
            if (content.contains("tstudio")) {
                Unit
            } else {
                throw Exception("Invalid file")
            }
        }

        mockkObject(TargetTranslation)

        every { directoryProvider.cacheDir }.returns(tempDir.newFolder("cache"))
        every { translator.path }.returns(tempDir.newFolder("translations"))

        mockkObject(FileUtilities)
        every { FileUtilities.deleteQuietly(any()) }.returns(true)

        every { onProgress(any(), any()) }.just(runs)
        every { context.getString(R.string.importing_file) }.returns("Importing file")

        mockkObject(MergeConflictsHandler)
        mockkObject(ResourceContainer)
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
            library,
            platform
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
            every { updateGenerator(any()) }.just(runs)
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
            library,
            platform
        ).importProject(importDir, false)

        assertNotNull(result)
        requireNotNull(result)

        assertTrue(result.isSuccess)
        assertEquals(targetTranslation.id, result.importedSlug)
        assertFalse(result.alreadyExists)
        assertFalse(result.mergeConflict)

        verify(exactly = 0) { Zip.unzipFromStream(any(), any()) }
        verify(exactly = 0) { directoryProvider.cacheDir }

        verify(exactly = 0) { targetTranslation.updateGenerator(any()) }
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
            every { updateGenerator(any()) }.just(runs)
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
            library,
            platform
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
            every { updateGenerator(any()) }.just(runs)
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
            library,
            platform
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
            every { updateGenerator(any()) }.just(runs)
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
            library,
            platform
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
            library,
            platform
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
            library,
            platform
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
            library,
            platform
        ).importProjects(
            listOf(project1, project2),
            false,
            onProgress
        )

        assertTrue(result.success)
        assertEquals(localTranslation, result.conflictingTargetTranslations.first())

        verifySequence {
            onProgress(any(), "Importing file")
            onProgress(0f, project1.name)
            onProgress(0.125f, project1.name)
            onProgress(0.5f, project2.name)
            onProgress(0.625f, project2.name)
            onProgress(0.75f, project2.name)
            onProgress(1f,  "Completed!")
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
            library,
            platform
        ).importProjects(
            listOf(project),
            false,
            onProgress
        )

        assertFalse(result.success)
        assertEquals(true, result.conflictingTargetTranslations.isEmpty())

        verifySequence {
            onProgress(any(), "Importing file")
            onProgress(0f, project.name)
            onProgress(0.25f, project.name)
            onProgress(0.5f, project.name)
            onProgress(1f, "Completed!")
        }

        verify { translator.getConflictingTargetTranslation(any()) }
    }

    @Test
    fun `test import project from uri`() {
        val uri: Uri = mockk()

        every { FileUtilities.getFileName(any(), any()) }
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
            library,
            platform
        ).importProject(uri, false, onProgress)

        assertTrue(result.success)
        assertEquals(uri, result.filePath)
        assertEquals("aa_mrk_text_ulb.tstudio", result.readablePath)
        assertEquals("aa_mrk_text_ulb", result.importedSlug)
        assertFalse(result.alreadyExists)
        assertFalse(result.hasMergeConflict)
        assertFalse(result.invalidFileName)

        verifyUriImport(targetTranslation)
    }

    @Test
    fun `test import project from uri with merge conflict`() {
        val uri: Uri = mockk()

        every { FileUtilities.getFileName(any(), any()) }
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
            every { updateGenerator(any()) }.just(runs)
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
            library,
            platform
        ).importProject(uri, false, onProgress)

        assertTrue(result.success)
        assertEquals(uri, result.filePath)
        assertEquals("aa_mrk_text_ulb.tstudio", result.readablePath)
        assertEquals("aa_mrk_text_ulb", result.importedSlug)
        assertTrue(result.alreadyExists)
        assertTrue(result.hasMergeConflict)
        assertFalse(result.invalidFileName)

        verifyUriImport(targetTranslation)
    }

    @Test
    fun `test import project from uri with merge conflict overwrite`() {
        val uri: Uri = mockk()

        every { FileUtilities.getFileName(any(), any()) }
            .returns("aa_mrk_text_ulb.tstudio")

        every { contentResolver.openInputStream(any()) }
            .returns(tStudioFile.inputStream())

        val dir = tempDir.newFolder("aa_mrk_text_ulb")
        val targetTranslation: TargetTranslation = mockk {
            every { id }.returns("aa_mrk_text_ulb")
            every { updateGenerator(any()) }.just(runs)
        }
        val localTranslation: TargetTranslation = mockk {
            every { commitSync() }.returns(true)
            every { merge(any(), any()) }.returns(false)
            every { updateGenerator(any()) }.just(runs)
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
            library,
            platform
        ).importProject(uri, true, onProgress)

        assertTrue(result.success)
        assertEquals(uri, result.filePath)
        assertEquals("aa_mrk_text_ulb.tstudio", result.readablePath)
        assertEquals("aa_mrk_text_ulb", result.importedSlug)
        assertTrue(result.alreadyExists)
        assertFalse(result.hasMergeConflict)
        assertFalse(result.invalidFileName)

        verifyUriImport(targetTranslation)
    }

    @Test
    fun `test import project from invalid file uri`() {
        val uri: Uri = mockk()

        every { FileUtilities.getFileName(any(), any()) }
            .returns("aa_mrk_text_ulb.pdf")

        val result = ImportProjects(
            context,
            translator,
            backupRC,
            directoryProvider,
            archiveImporter,
            library,
            platform
        ).importProject(uri, true, onProgress)

        assertFalse(result.success)
        assertEquals(uri, result.filePath)
        assertEquals("aa_mrk_text_ulb.pdf", result.readablePath)
        assertNull(result.importedSlug)
        assertFalse(result.alreadyExists)
        assertFalse(result.hasMergeConflict)
        assertTrue(result.invalidFileName)

        verify { FileUtilities.getFileName(any(), any()) }
        verify(exactly = 0) { contentResolver.openInputStream(any()) }
        verify(exactly = 0) { TargetTranslation.open(any(), any()) }
        verify(exactly = 0) { archiveImporter.importArchive(any()) }
    }

    @Test
    fun `test import new source text from uri`() = runTest {
        val uri: Uri = mockk()

        val srcDir = tempDir.newFolder("fa_mrk_nmv")
        every { directoryProvider.createTempDir(any()) }.returns(srcDir)
        every { FileUtilities.copyDirectory(any(), any<Uri>(), any()) }
            .just(runs)

        every { library.open(any()) }.throws(Exception("local rc not found."))
        coEvery { library.importResourceContainer(srcDir) }.returns(mockk())

        val tempRc: ResourceContainer = mockk {
            every { slug }.returns("en")
        }
        every { ResourceContainer.load(srcDir) }.returns(tempRc)

        val result = ImportProjects(
            context,
            translator,
            backupRC,
            directoryProvider,
            archiveImporter,
            library,
            platform
        ).importSource(uri, false)

        assertTrue(result.success)
        assertFalse(result.hasConflict)
        assertNull(result.error)
        assertNull(result.uri)

        verify { directoryProvider.createTempDir(any()) }
        verify { FileUtilities.copyDirectory(any(), any<Uri>(), any()) }
        verify { library.open(any()) }
        coVerify { library.importResourceContainer(srcDir) }
        verify { ResourceContainer.load(srcDir) }
        verify { FileUtilities.deleteQuietly(any()) }
    }

    @Test
    fun `test import existing source text from uri fails`() = runTest {
        val uri: Uri = mockk()

        val srcDir = tempDir.newFolder("fa_mrk_nmv")
        every { directoryProvider.createTempDir(any()) }.returns(srcDir)
        every { FileUtilities.copyDirectory(any(), any<Uri>(), any()) }
            .just(runs)

        every { library.open(any()) }.returns(mockk())
        coEvery { library.importResourceContainer(srcDir) }.returns(mockk())

        val tempRc = mockResourceContainer()
        every { ResourceContainer.load(srcDir) }.returns(tempRc)

        every { context.getString(R.string.overwrite_content, any()) } answers {
            "Overwrite %s?".format((args[1] as Array<*>)[0])
        }

        val expectedErrorMessage = "Overwrite Farsi - Mark - New Millennium Version?"

        val result = ImportProjects(
            context,
            translator,
            backupRC,
            directoryProvider,
            archiveImporter,
            library,
            platform
        ).importSource(uri, false)

        assertFalse(result.success)
        assertTrue(result.hasConflict)
        assertEquals(expectedErrorMessage, result.error)
        assertEquals(uri, result.uri)

        verify { directoryProvider.createTempDir(any()) }
        verify { FileUtilities.copyDirectory(any(), any<Uri>(), any()) }
        verify { library.open(any()) }
        coVerify(exactly = 0) { library.importResourceContainer(srcDir) }
        verify { ResourceContainer.load(srcDir) }
        verify { FileUtilities.deleteQuietly(any()) }
    }

    @Test
    fun `test import existing source text from uri overwrite`() = runTest {
        val uri: Uri = mockk()

        val srcDir = tempDir.newFolder("fa_mrk_nmv")
        every { directoryProvider.createTempDir(any()) }.returns(srcDir)
        every { FileUtilities.copyDirectory(any(), any<Uri>(), any()) }
            .just(runs)

        every { library.open(any()) }.returns(mockk())
        coEvery { library.importResourceContainer(srcDir) }.returns(mockk())

        val tempRc = mockResourceContainer()
        TestUtils.setPropertyReflection(tempRc, "slug", "en")
        every { ResourceContainer.load(srcDir) }.returns(tempRc)

        val result = ImportProjects(
            context,
            translator,
            backupRC,
            directoryProvider,
            archiveImporter,
            library,
            platform
        ).importSource(uri, true)

        assertTrue(result.success)
        assertFalse(result.hasConflict)
        assertNull(result.error)
        assertNull(result.uri)

        verify { directoryProvider.createTempDir(any()) }
        verify { FileUtilities.copyDirectory(any(), any<Uri>(), any()) }
        verify { library.open(any()) }
        coVerify { library.importResourceContainer(srcDir) }
        verify { ResourceContainer.load(srcDir) }
        verify { FileUtilities.deleteQuietly(any()) }
    }

    @Test
    fun `test import invalid source text from uri`() = runTest {
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
            library,
            platform
        ).importSource(uri, false)

        assertFalse(result.success)
        assertFalse(result.hasConflict)
        assertEquals(expectedErrorMessage, result.error)
        assertNull(result.uri)

        verify { directoryProvider.createTempDir(any()) }
        verify { FileUtilities.copyDirectory(any(), any<Uri>(), any()) }
        verify(exactly = 0) { library.open(any()) }
        coVerify(exactly = 0) { library.importResourceContainer(srcDir) }
        verify { ResourceContainer.load(srcDir) }
        verify(exactly = 0) { FileUtilities.deleteQuietly(any()) }
    }

    @Test
    fun `test import source text from uri failed`() = runTest {
        val uri: Uri = mockk()

        val srcDir = tempDir.newFolder("fa_mrk_nmv")
        every { directoryProvider.createTempDir(any()) }.returns(srcDir)
        every { FileUtilities.copyDirectory(any(), any<Uri>(), any()) }
            .just(runs)

        every { library.open(any()) }.throws(Exception("local rc not found."))
        coEvery { library.importResourceContainer(srcDir) }.throws(Exception("Failed to import rc."))

        val tempRc: ResourceContainer = mockk {
            every { slug }.returns("slug")
        }
        TestUtils.setPropertyReflection(tempRc, "slug", "en")
        every { ResourceContainer.load(srcDir) }.returns(tempRc)

        val expectedErrorMessage = "Failed to import rc."

        val result = ImportProjects(
            context,
            translator,
            backupRC,
            directoryProvider,
            archiveImporter,
            library,
            platform
        ).importSource(uri, false)

        assertFalse(result.success)
        assertFalse(result.hasConflict)
        assertEquals(expectedErrorMessage, result.error)
        assertNull(result.uri)

        verify { directoryProvider.createTempDir(any()) }
        verify { FileUtilities.copyDirectory(any(), any<Uri>(), any()) }
        verify { library.open(any()) }
        coVerify { library.importResourceContainer(srcDir) }
        verify { ResourceContainer.load(srcDir) }
        verify { FileUtilities.deleteQuietly(any()) }
    }

    private fun verifyImportSuccess(targetTranslation: TargetTranslation) {
        verify { Zip.unzipFromStream(any(), any()) }
        // verify { TargetTranslation.updateGenerator(any(), any()) }
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
        verify(exactly = 0) { translator.path }
        verify(exactly = 0) { TargetTranslation.open(any(), any()) }
    }

    private fun verifyUriImport(targetTranslation: TargetTranslation) {
        verify { FileUtilities.getFileName(any(), any()) }
        verify { contentResolver.openInputStream(any()) }
        verify { targetTranslation.id }
        verify { TargetTranslation.open(any(), any()) }
        verify { archiveImporter.importArchive(any()) }
    }

    private fun mockResourceContainer(): ResourceContainer {
        val mockLanguage: Language = mockk {
            every { name }.returns("Farsi")
        }
        val mockProject: Project = mockk {
            every { name }.returns("Mark")
        }
        val mockResource: Resource = mockk {
            every { name }.returns("New Millennium Version")
        }

        val rc: ResourceContainer = mockk {
            every { language }.returns(mockLanguage)
            every { project }.returns(mockProject)
            every { resource }.returns(mockResource)
            every { slug }.returns("fa_mrk_nmv")
        }

        return rc
    }
}