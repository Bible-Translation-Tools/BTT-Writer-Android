package com.door43.translationstudio.usecases

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.core.ImportUSFM
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator.Companion.TSTUDIO_EXTENSION
import com.door43.translationstudio.core.Translator.Companion.ZIP_EXTENSION
import com.door43.usecases.BackupRC
import com.door43.usecases.ImportProjects
import com.door43.util.FileUtilities
import com.door43.util.Zip
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.TargetLanguage
import org.unfoldingword.door43client.models.Translation
import org.unfoldingword.tools.logger.Logger
import java.io.File
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BackupRCTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject
    @ApplicationContext lateinit var appContext: Context
    @Inject lateinit var backupRC: BackupRC
    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var library: Door43Client
    @Inject lateinit var assetProvider: AssetsProvider
    @Inject lateinit var importProjects: ImportProjects
    @Inject lateinit var profile: Profile
    @Inject lateinit var assetsProvider: AssetsProvider

    private var tempDir: File? = null
    private lateinit var targetLanguage: TargetLanguage
    private var targetTranslation: TargetTranslation? = null
    private var targetTranslationDir: File? = null

    @Before
    fun setUp() {
        hiltRule.inject()
        Logger.flush()

        targetLanguage = library.index.getTargetLanguage("aae")
    }

    @After
    fun tearDown() {
        FileUtilities.deleteQuietly(tempDir)
        deleteBackups()
    }

    @Test
    fun backupResourceContainerSucceeds() {
        val source = "source/fa_jud_nmv.zip"
        val rcTranslation = importSourceTranslation(source)

        assertNotNull(rcTranslation)

        val rcFile = backupRC.backupResourceContainer(rcTranslation!!)

        assertNotNull("RC file should not be null", rcFile)
        assertTrue("RC file should exist", rcFile.exists())

        val id = "fa_jud_nmv"
        val backupFiles = directoryProvider.backupsDir.listFiles()

        assertNotNull("Backups dir should not be null", backupFiles)
        assertTrue("Backups dir should not be empty", backupFiles.isNotEmpty())

        val backupFile = backupFiles.firstOrNull {
            it.name.startsWith(id) && it.name.endsWith(".tsrc")
        }
        assertNotNull("Backup with id exists", backupFile)
    }

    @Test
    fun backupTargetTranslationSucceeds() {
        val source = "usfm/mrk.usfm"
        importTranslation(source)

        assertNotNull("Target translation should not be null", targetTranslation)

        val backedUp = backupRC.backupTargetTranslation(targetTranslation, false)
        assertTrue("Backup should succeed", backedUp)

        val id = generateTranslationId()
        val backupFiles = directoryProvider.backupsDir.listFiles()

        assertNotNull("Backups dir should not be null", backupFiles)
        assertTrue("Backups dir should not be empty", backupFiles.isNotEmpty())

        val backupFile = backupFiles.firstOrNull {
            it.name.startsWith(id) && it.name.endsWith(TSTUDIO_EXTENSION)
        }
        assertNotNull("Backup with id exists", backupFile)
    }

    @Test
    fun backupTargetTranslationOrphanSucceeds() {
        val source = "usfm/mrk.usfm"
        importTranslation(source)

        assertNotNull("Target translation should not be null", targetTranslation)

        val backedUp = backupRC.backupTargetTranslation(targetTranslation, true)
        assertTrue("Backup should succeed", backedUp)

        val id = generateTranslationId()
        val backupFiles = directoryProvider.backupsDir.listFiles()

        assertNotNull("Backups dir should not be null", backupFiles)
        assertTrue("Backups dir should not be empty", backupFiles.isNotEmpty())

        val backupFile = backupFiles.firstOrNull {
            it.name.startsWith(id) && it.name.endsWith(ZIP_EXTENSION)
        }
        assertNotNull("Backup with id exists", backupFile)
    }

    @Test
    fun backupTargetTranslationDirSucceeds() {
        val source = "usfm/19-PSA.usfm"
        importTranslation(source)

        assertNotNull("Target translation should not be null", targetTranslationDir)

        val backedUp = backupRC.backupTargetTranslation(targetTranslationDir!!)

        assertTrue("Backup should succeed", backedUp)
    }

    private fun importSourceTranslation(path: String): Translation? {
        return assetProvider.open(path).use {
            try {
                tempDir = directoryProvider.createTempDir("tempRc")

                assertNotNull("tempDir should not be null", tempDir)
                assertTrue("tempDir should exist", tempDir!!.exists())

                Zip.unzipFromStream(it, tempDir!!)

                assertFalse("tempDir should not be empty", tempDir!!.listFiles().isNullOrEmpty())

                val rc = library.importResourceContainer(tempDir)

                assertNotNull("rc should not be null", rc)

                library.index.getTranslation(rc.slug)
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun importTranslation(path: String) {
        //import USFM file to be used for testing
        val usfm = ImportUSFM.Builder(
            appContext,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromRc(targetLanguage, path, null)
            .build()

        assertNotNull("usfm should not be null", usfm)
        assertNotNull("target language should not be null", targetLanguage)
        assertTrue("import usfm test file should succeed", usfm!!.isProcessSuccess)
        val imports = usfm.importProjects
        Assert.assertEquals("import usfm test file should succeed", 1, imports.size.toLong())

        //open import as targetTranslation
        val projectFolder = imports[0]

        targetTranslation = TargetTranslation.open(projectFolder, null)
        targetTranslationDir = projectFolder
    }

    private fun deleteBackups() {
        val backupFiles = directoryProvider.backupsDir.listFiles()
        if (backupFiles != null) {
            for (file in backupFiles) {
                FileUtilities.safeDelete(file)
            }
        }
    }

    private fun generateTranslationId(): String {
        val lang = targetTranslation?.targetLanguage?.slug ?: "_"
        val book = targetTranslation?.projectId ?: "_"
        val type = targetTranslation?.translationType?.id ?: "_"
        val resource = targetTranslation?.resourceSlug ?: "_"
        return "${lang}_${book}_${type}_${resource}"
    }
}