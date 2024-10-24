package com.door43.translationstudio.usecases

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.usecases.BackupRC
import com.door43.usecases.ImportProjects
import com.door43.util.FileUtilities
import com.door43.util.Zip
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.Translation
import org.unfoldingword.tools.logger.Logger
import java.io.File
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BackupRCTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject lateinit var backupRC: BackupRC
    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var library: Door43Client
    @Inject lateinit var assetProvider: AssetsProvider
    @Inject lateinit var importProjects: ImportProjects

    private var tempDir: File? = null

    @Before
    fun setUp() {
        hiltRule.inject()
        Logger.flush()
    }

    @After
    fun tearDown() {
        FileUtilities.deleteQuietly(tempDir)
    }

    @Test
    fun backupResourceContainerSucceeds() {
        val source = "source/fa_jud_nmv.zip"
        val rcTranslation = importSourceTranslation(source)

        assertNotNull(rcTranslation)

        val rcFile = backupRC.backupResourceContainer(rcTranslation!!)

        assertNotNull(rcFile)
        assertTrue(rcFile.exists())
    }

    private fun importSourceTranslation(path: String): Translation? {
        return assetProvider.open(path).use {
            try {
                tempDir = directoryProvider.createTempDir("tempRc")
                Zip.unzipFromStream(it, tempDir!!)

                val rc = library.importResourceContainer(tempDir)
                library.index.getTranslation(rc.slug)
            } catch (e: Exception) {
                null
            }
        }
    }
}