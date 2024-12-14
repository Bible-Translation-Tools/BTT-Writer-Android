package com.door43.translationstudio.usecases

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.IntegrationTest
import com.door43.translationstudio.TestUtils
import com.door43.translationstudio.core.ProcessUSFM
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.core.Translator.Companion.PDF_EXTENSION
import com.door43.translationstudio.core.Translator.Companion.TSTUDIO_EXTENSION
import com.door43.translationstudio.core.Translator.Companion.USFM_EXTENSION
import com.door43.usecases.ExportProjects
import com.door43.usecases.ExportProjects.ExportType
import com.door43.usecases.ImportProjects
import com.door43.util.Zip
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.TargetLanguage
import org.unfoldingword.tools.logger.Logger
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@IntegrationTest
class ExportProjectsTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject
    @ApplicationContext lateinit var appContext: Context
    @Inject lateinit var exportProjects: ExportProjects
    @Inject lateinit var assetsProvider: AssetsProvider
    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var library: Door43Client
    @Inject lateinit var profile: Profile
    @Inject lateinit var importProjects: ImportProjects
    @Inject lateinit var translator: Translator

    private var targetTranslation: TargetTranslation? = null
    private var targetLanguage: TargetLanguage? = null

    @Before
    fun setUp() {
        hiltRule.inject()

        targetLanguage = library.index.getTargetLanguage("aa")
        targetTranslation = TestUtils.importTargetTranslation(
            library,
            appContext,
            directoryProvider,
            profile,
            assetsProvider,
            importProjects,
            translator,
            "aa",
            "usfm/mrk.usfm"
        )
    }

    @After
    fun tearDown() {
        Logger.flush()
        directoryProvider.clearCache()
        directoryProvider.deleteTranslations()
    }

    @Test
    fun testExportProjectFromTargetTranslationToFile() {
        assertNotNull("Target translation should not be null", targetTranslation)

        val tempFile = directoryProvider.createTempFile("aa_mrk_text_reg", ".tstudio")

        exportProjects.exportProject(targetTranslation!!, tempFile)

        testExportedProjectCorrect(tempFile)
    }

    @Test
    fun testExportProjectFromTargetTranslationWithBadHead() {
        assertNotNull("Target translation should not be null", targetTranslation)

        // Commit to force git initialization
        targetTranslation!!.commitSync(".", false)

        // Make HEAD file bad
        val headFile = targetTranslation!!.path.listFiles()?.singleOrNull {
            it.name == ".git"
        }?.listFiles()?.firstOrNull {
            it.name == "HEAD"
        }
        assertNotNull("Head file should exist", headFile)
        headFile?.writeText("some_bad_data")

        val tempFile = directoryProvider.createTempFile("aa_mrk_text_reg", ".tstudio")

        exportProjects.exportProject(targetTranslation!!, tempFile)

        testExportedProjectCorrect(tempFile)
    }

    @Test
    fun testExportProjectFromTargetTranslationToUri() {
        assertNotNull("Target translation should not be null", targetTranslation)

        val tempFile = directoryProvider.createTempFile("aa_mrk_text_reg", ".tstudio")
        val tempFileUri = Uri.fromFile(tempFile)

        val result = exportProjects.exportProject(targetTranslation!!, tempFileUri)

        assertTrue("Result should be successful", result.success)
        assertEquals("Result URI should match", tempFileUri, result.uri)
        assertEquals("Result export type should be PROJECT", ExportType.PROJECT, result.exportType)

        testExportedProjectCorrect(tempFile)
    }

    @Test
    fun testExportProjectFromDirToFile() {
        assertNotNull("Target translation should not be null", targetTranslation)

        val tempFile = directoryProvider.createTempFile("aa_mrk_text_reg", ".tstudio")

        exportProjects.exportProject(targetTranslation!!.path, tempFile)

        testExportedProjectCorrect(tempFile)
    }

    @Test
    fun testExportProjectToUSFM() {
        assertNotNull("Target translation should not be null", targetTranslation)

        val tempFile = directoryProvider.createTempFile("aa_mrk_text_reg", ".usfm")
        val tempFileUri = Uri.fromFile(tempFile)

        val result = exportProjects.exportUSFM(targetTranslation!!, tempFileUri)

        assertTrue("Result should be successful", result.success)
        assertEquals("Result URI should match", tempFileUri, result.uri)
        assertEquals("Result export type should be PROJECT", ExportType.USFM, result.exportType)

        testExportedUSFMFileCorrect(tempFile)
    }

    @Test
    fun testExportProjectToPDF() {
        assertNotNull("Target translation should not be null", targetTranslation)

        val tempFile = directoryProvider.createTempFile("aa_mrk_text_reg", ".pdf")
        val tempFileUri = Uri.fromFile(tempFile)

        val tempDir = directoryProvider.createTempDir("images")

        val result = exportProjects.exportPDF(
            targetTranslation!!,
            tempFileUri,
            includeImages = true,
            includeIncompleteFrames = true,
            tempDir
        )

        assertTrue("Result should be successful", result.success)
        assertEquals("Result URI should match", tempFileUri, result.uri)
        assertEquals("Result export type should be PROJECT", ExportType.PDF, result.exportType)

        assertTrue(
            "Images dir should be empty for non-obs project",
            tempDir.listFiles().isNullOrEmpty()
        )

        assertEquals("This should be PDF file", tempFile.extension, PDF_EXTENSION)
        assertTrue("Temp file should exist", tempFile.exists())
        assertTrue("Temp file should not be empty", tempFile.length() > 0)
    }

    private fun testExportedProjectCorrect(file: File) {
        assertEquals("This should be project file", file.extension, TSTUDIO_EXTENSION)
        assertTrue("Temp file should exist", file.exists())
        assertTrue("Temp file should not be empty", file.length() > 0)

        val projectDirs = directoryProvider.createTempDir("project")

        FileInputStream(file).use {
            Zip.unzipFromStream(it, projectDirs)
        }

        assertTrue("projectDirs should exist", projectDirs.exists())
        assertFalse("Project dirs should not be empty", projectDirs.listFiles().isNullOrEmpty())

        // Find targetTranslation directory
        val projectDir = projectDirs.listFiles()?.firstOrNull {
            it.name == targetTranslation!!.id
        }

        assertNotNull("Project dir should exist", projectDir)

        val destTranslation = TargetTranslation.open(projectDir, null)

        assertNotNull("Destination target translation should exist", destTranslation)
        assertEquals(
            "Destination target translation should match",
            targetTranslation!!.id,
            destTranslation!!.id
        )
    }

    private fun testExportedUSFMFileCorrect(file: File) {
        assertEquals("This should be usfm file", file.extension, USFM_EXTENSION)
        assertTrue("Temp file should exist", file.exists())
        assertTrue("Temp file should not be empty", file.length() > 0)

        assertNotNull("Target language should not be null", targetLanguage)

        val usfm = ProcessUSFM.Builder(
            appContext,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromFile(targetLanguage!!, file, null)
            .build()

        assertNotNull("USFM should not be null", usfm)
        assertTrue("USFM process should be successful", usfm!!.isProcessSuccess)
        assertEquals("Import projects should be 1", 1, usfm.importProjects.size)

        val projectId = usfm.importProjects.first().name
        val (project, language) = projectId.split("-")

        assertEquals("Project ID should match", targetTranslation!!.projectId, project)
        assertEquals("Language ID should match", targetLanguage!!.slug, language)
    }
}