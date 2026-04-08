package com.door43.translationstudio.usecases

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.OnProgressListener
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.IntegrationTest
import com.door43.translationstudio.KoinAndroidTest
import com.door43.translationstudio.core.Translator.Companion.TSTUDIO_EXTENSION
import com.door43.translationstudio.core.Translator.Companion.USFM_EXTENSION
import com.door43.usecases.ImportProjects
import com.door43.util.Zip
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.inject
import org.unfoldingword.tools.logger.Logger
import java.io.File


@RunWith(AndroidJUnit4::class)
@IntegrationTest
class ImportProjectsTest : KoinAndroidTest() {

    private val importProjects: ImportProjects by inject()
    private val directoryProvider: IDirectoryProvider by inject()
    private val assetsProvider: AssetsProvider by inject()

    @Before
    fun setUp() {
    }

    @After
    fun tearDown() {
        Logger.flush()
        directoryProvider.clearCache()
        directoryProvider.deleteTranslations()
    }

    @Test
    fun testImportProjectFile() {
        val projectFile = getProjectFile()

        val result = importProjects.importProject(projectFile, false)

        assertNotNull("Result should not be null", result)
        assertTrue("Import should be successful", result!!.isSuccess)
        assertEquals("Imported slug should match", "aa_mrk_text_reg", result.importedSlug)
        assertFalse("There should be no merge conflict", result.mergeConflict)
        assertFalse("Project should not already exist", result.alreadyExists)


        // Import project again
        val result2 = importProjects.importProject(projectFile, false)

        assertNotNull("Result should not be null", result2)
        assertTrue("Import should be successful", result2!!.isSuccess)
        assertEquals("Imported slug should match", "aa_mrk_text_reg", result2.importedSlug)
        assertTrue("There should be merge conflict", result2.mergeConflict)
        assertTrue("Project should already exist", result2.alreadyExists)

        // Import project again with overwrite flag
        val result3 = importProjects.importProject(projectFile, true)

        assertNotNull("Result should not be null", result3)
        assertTrue("Import should be successful", result3!!.isSuccess)
        assertEquals("Imported slug should match", "aa_mrk_text_reg", result3.importedSlug)
        assertFalse("There should be no merge conflict", result3.mergeConflict)
        assertTrue("Project should already exist", result3.alreadyExists)
    }

    @Test
    fun testImportProjectDirectory() {
        val projectDirectory = assetsProvider.open("exports/aa_mrk_text_reg.tstudio").use {
            val tempDir = directoryProvider.createTempDir("test")
            Zip.unzipFromStream(it, tempDir)
            tempDir
        }

        val result = importProjects.importProject(projectDirectory, false)

        assertNotNull("Result should not be null", result)
        assertTrue("Import should be successful", result!!.isSuccess)
        assertEquals("Imported slug should match", "aa_mrk_text_reg", result.importedSlug)
        assertFalse("There should be no merge conflict", result.mergeConflict)
        assertFalse("Project should not already exist", result.alreadyExists)
    }

    @Test
    fun testImportProjectUsfmAsFileFails() {
        val projectFile = getUSFMFile()

        val result = importProjects.importProject(projectFile, false)

        assertNotNull("Result should not be null", result)
        assertFalse("Import should not be successful", result!!.isSuccess)
        assertNull("Imported slug should be null", result.importedSlug)
        assertFalse("There should be no merge conflict", result.mergeConflict)
    }

    @Test
    fun testImportIncorrectDirectoryFails() {
        val wrongProjectDirectory = assetsProvider.open("source/fa_jud_nmv.zip").use {
            val tempDir = directoryProvider.createTempDir("test")
            Zip.unzipFromStream(it, tempDir)
            tempDir
        }

        val result = importProjects.importProject(wrongProjectDirectory, false)

        assertNotNull("Result should not be null", result)
        assertFalse("Import should not be successful", result!!.isSuccess)
        assertNull("Imported slug should be null", result.importedSlug)
        assertFalse("There should be no merge conflict", result.mergeConflict)
        assertFalse("Project should not already exist", result.alreadyExists)
    }

    @Test
    fun testImportProjectUri() {
        val projectFile = getProjectFile()
        val projectUri = Uri.fromFile(projectFile)

        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, message ->
            progressMessage = message
        }

        val result = importProjects.importProject(projectUri, false, progressListener)

        assertNotNull("Result should not be null", result)
        assertTrue("Import should be successful", result.success)
        assertNotNull("Progress message should not be null", progressMessage)
        assertEquals("Imported slug should match", "aa_mrk_text_reg", result.importedSlug)
        assertFalse("There should be no merge conflict", result.hasMergeConflict)
        assertFalse("Project should not already exist", result.alreadyExists)
        assertTrue(
            "Path should have with tstudio extension",
            result.readablePath.endsWith(TSTUDIO_EXTENSION)
        )
        assertEquals("Uri should match", projectUri, result.filePath)
        assertFalse("File name should not be invalid", result.invalidFileName)
    }

    @Test
    fun testImportUSFMUriShouldFail() {
        val projectFile = getUSFMFile()
        val projectUri = Uri.fromFile(projectFile)

        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, message ->
            progressMessage = message
        }

        val result = importProjects.importProject(projectUri, false, progressListener)

        assertNotNull("Result should not be null", result)
        assertFalse("Import should not be successful", result.success)
        assertNotNull("Progress message should not be null", progressMessage)
        assertNull("Imported slug should be null", result.importedSlug)
        assertFalse("There should be no merge conflict", result.hasMergeConflict)
        assertFalse("Project should not already exist", result.alreadyExists)
        assertTrue(
            "Path should have with usfm extension",
            result.readablePath.endsWith(USFM_EXTENSION)
        )
        assertEquals("Uri should match", projectUri, result.filePath)
        assertTrue("File name should be invalid", result.invalidFileName)
    }

    @Test
    fun testImportSourceTextFromUriDir() {
        val sourceDir = getSourceDir()
        val sourceDirUri = Uri.fromFile(sourceDir)

        assertTrue("Source dir should exist", sourceDir.exists())
        assertTrue("Source dir should be a directory", sourceDir.isDirectory)
        assertTrue(
            "Source dir should have files",
            sourceDir.listFiles()?.isNotEmpty() ?: false
        )

        val result = runBlocking {
            importProjects.importSource(sourceDirUri, false)
        }

        assertTrue("Import should be successful", result.success)
        assertFalse("There should be no merge conflict", result.hasConflict)
        assertNull("Message should be null", result.error)
        assertNull("Uri should be null", result.uri)

        // Import again
        val sourceDir2 = getSourceDir()
        val sourceDir2Uri = Uri.fromFile(sourceDir2)
        val result2 = runBlocking {
            importProjects.importSource(sourceDir2Uri, false)
        }

        assertFalse("Import should not be successful", result2.success)
        assertTrue("There should be merge conflict", result2.hasConflict)
        assertNotNull("Error should not be null", result2.error)
        assertNotNull("Uri should not be null", result2.uri)

        val sourceFiles = sourceDir2.listFiles()?.map { it.name }?.sorted() ?: emptyList()
        assertTrue("Source files should exist", sourceFiles.isNotEmpty())

        // Overwrite source from result target dir
        val result3 = runBlocking {
            importProjects.importSource(result2.uri!!, true)
        }
        assertTrue("Import should be successful", result3.success)
        assertFalse("There should be no merge conflict", result3.hasConflict)
        assertNull("Message should be null", result3.error)
        assertNull("Uri should be null", result3.uri)
    }

    @Test
    fun testImportSourceTextFromUriFileShouldFail() {
        val sourceFile = getSourceFile()
        val sourceFileUri = Uri.fromFile(sourceFile)

        val result = runBlocking {
            importProjects.importSource(sourceFileUri, false)
        }

        assertFalse("Import should not be successful", result.success)
        assertFalse("There should be no merge conflict", result.hasConflict)
        assertNotNull("Message should not be null", result.error)
        assertNull("Uri should be null", result.uri)
    }

    private fun getProjectFile(): File {
        return assetsProvider.open("exports/aa_mrk_text_reg.tstudio").use {
            val tempFile = directoryProvider.createTempFile("test", ".tstudio")
            tempFile.outputStream().use { output ->
                it.copyTo(output)
            }
            tempFile
        }
    }

    private fun getUSFMFile(): File {
        return assetsProvider.open("usfm/18-JOB.usfm").use {
            val tempFile = directoryProvider.createTempFile("test", ".usfm")
            tempFile.outputStream().use { output ->
                it.copyTo(output)
            }
            tempFile
        }
    }

    private fun getSourceFile(): File {
        return assetsProvider.open("source/fa_jud_nmv.zip").use {
            val tempFile = directoryProvider.createTempFile("test", ".zip")
            tempFile.outputStream().use { output ->
                it.copyTo(output)
            }
            tempFile
        }
    }

    private fun getSourceDir(): File {
        return getSourceFile().inputStream().use {
            val sourceDir = directoryProvider.createTempDir("test")
            Zip.unzipFromStream(it, sourceDir)
            sourceDir
        }
    }
}
