package com.door43.translationstudio.usecases

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.IntegrationTest
import com.door43.translationstudio.KoinAndroidTest
import com.door43.translationstudio.Platform
import com.door43.translationstudio.TestUtils
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.core.Translator.Companion.TSTUDIO_EXTENSION
import com.door43.translationstudio.core.Translator.Companion.ZIP_EXTENSION
import com.door43.usecases.BackupRC
import com.door43.usecases.ImportProjects
import com.door43.util.FileUtilities
import com.door43.util.Zip
import kotlinx.coroutines.test.runTest
import org.bibletranslationtools.resourcecatalog.ResourceCatalogClient
import org.bibletranslationtools.resourcecatalog.library.models.TargetLanguage
import org.bibletranslationtools.resourcecatalog.library.models.Translation
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.inject
import java.io.File


@RunWith(AndroidJUnit4::class)
@IntegrationTest
class BackupRCTest : KoinAndroidTest() {

    private val appContext: Context by inject()
    private val backupRC: BackupRC by inject()
    private val directoryProvider: IDirectoryProvider by inject()
    private val catalogClient: ResourceCatalogClient by inject()
    private val assetProvider: AssetsProvider by inject()
    private val importProjects: ImportProjects by inject()
    private val profile: Profile by inject()
    private val assetsProvider: AssetsProvider by inject()
    private val translator: Translator by inject()
    private val platform: Platform by inject()

    private var tempDir: File? = null
    private lateinit var targetLanguage: TargetLanguage

    @Before
    fun setUp() {
        targetLanguage = catalogClient.library.getTargetLanguage("aae")!!
    }

    @After
    fun tearDown() {
        FileUtilities.deleteQuietly(tempDir)
        deleteBackups()
        directoryProvider.clearCache()
        directoryProvider.deleteTranslations()
    }

    @Test
    fun testBackupResourceContainer() = runTest {
        val source = "source/fa_jud_nmv.zip"
        val rcTranslation = importSourceTranslation(source)

        assertNotNull(rcTranslation)

        val rcFile = backupRC.backupResourceContainer(rcTranslation!!)

        assertNotNull("RC file should not be null", rcFile)
        assertTrue("RC file should exist", rcFile.exists())

        val id = "fa_jud_nmv"
        val backupFiles = directoryProvider.backupsDir.listFiles()

        assertNotNull("Backups dir should not be null", backupFiles)
        assertTrue("Backups dir should not be empty", backupFiles!!.isNotEmpty())

        val backupFile = backupFiles.firstOrNull {
            it.name.startsWith(id) && it.name.endsWith(".tsrc")
        }
        assertNotNull("Backup with id exists", backupFile)
    }

    @Test
    fun testBackupTargetTranslation() {
        val source = "usfm/mrk.usfm"
        val targetTranslation = TestUtils.importTargetTranslation(
            catalogClient,
            appContext,
            platform,
            directoryProvider,
            profile,
            assetsProvider,
            importProjects,
            translator,
            "aae",
            source
        )

        assertNotNull("Target translation should not be null", targetTranslation)

        val backedUp = backupRC.backupTargetTranslation(targetTranslation, false)
        assertTrue("Backup should succeed", backedUp)

        val id = targetTranslation!!.id
        val backupFiles = directoryProvider.backupsDir.listFiles()

        assertNotNull("Backups dir should not be null", backupFiles)
        assertTrue("Backups dir should not be empty", backupFiles!!.isNotEmpty())

        val backupFile = backupFiles.firstOrNull {
            it.name.startsWith(id) && it.name.endsWith(TSTUDIO_EXTENSION)
        }
        assertNotNull("Backup with id exists", backupFile)
    }

    @Test
    fun testBackupTargetTranslationOrphan() {
        val source = "usfm/mrk.usfm"
        val targetTranslation = TestUtils.importTargetTranslation(
            catalogClient,
            appContext,
            platform,
            directoryProvider,
            profile,
            assetsProvider,
            importProjects,
            translator,
            "aae",
            source
        )

        assertNotNull("Target translation should not be null", targetTranslation)

        val backedUp = backupRC.backupTargetTranslation(targetTranslation, true)
        assertTrue("Backup should succeed", backedUp)

        val id = targetTranslation!!.id
        val backupFiles = directoryProvider.backupsDir.listFiles()

        assertNotNull("Backups dir should not be null", backupFiles)
        assertTrue("Backups dir should not be empty", backupFiles.isNotEmpty())

        val backupFile = backupFiles.firstOrNull {
            it.name.startsWith(id) && it.name.endsWith(ZIP_EXTENSION)
        }
        assertNotNull("Backup with id exists", backupFile)
    }

    @Test
    fun testBackupTargetTranslationDir() {
        val source = "usfm/19-PSA.usfm"
        val targetTranslation = TestUtils.importTargetTranslation(
            catalogClient,
            appContext,
            platform,
            directoryProvider,
            profile,
            assetsProvider,
            importProjects,
            translator,
            "aae",
            source
        )

        assertNotNull("Target translation should not be null", targetTranslation)

        val backedUp = backupRC.backupTargetTranslation(targetTranslation!!.path)

        assertTrue("Backup should succeed", backedUp)
    }

    private suspend fun importSourceTranslation(path: String): Translation? {
        return assetProvider.open(path).use {
            try {
                tempDir = directoryProvider.createTempDir("tempRc")

                assertNotNull("tempDir should not be null", tempDir)
                assertTrue("tempDir should exist", tempDir!!.exists())

                Zip.unzipFromStream(it, tempDir!!)

                assertFalse("tempDir should not be empty", tempDir!!.listFiles().isNullOrEmpty())

                val rc = catalogClient.importResourceContainer(tempDir!!)

                assertNotNull("rc should not be null", rc)

                catalogClient.library.getTranslation(rc.slug)
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun deleteBackups() {
        val backupFiles = directoryProvider.backupsDir.listFiles()
        if (backupFiles != null) {
            for (file in backupFiles) {
                FileUtilities.safeDelete(file)
            }
        }
    }
}
