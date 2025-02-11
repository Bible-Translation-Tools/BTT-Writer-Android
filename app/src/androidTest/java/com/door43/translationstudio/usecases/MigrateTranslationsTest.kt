package com.door43.translationstudio.usecases

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.OnProgressListener
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.IntegrationTest
import com.door43.translationstudio.R
import com.door43.usecases.ImportProjects
import com.door43.usecases.MigrateTranslations
import com.door43.util.FileUtilities
import com.door43.util.Zip
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@IntegrationTest
class MigrateTranslationsTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject @ApplicationContext lateinit var appContext: Context
    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var importProjects: ImportProjects
    @Inject lateinit var assetsProvider: AssetsProvider

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        directoryProvider.clearCache()
    }

    @Test
    fun migrateOldAppDataEmpty() {
        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        val sourceDir = Uri.fromFile(directoryProvider.createTempDir("BTTWriter"))

        MigrateTranslations(appContext, importProjects, directoryProvider)
            .execute(sourceDir, progressListener)

        assertEquals("Completed!", progressMessage)
        assertEquals(0, directoryProvider.translationsDir.listFiles()?.size ?: 0)
    }

    @Test
    fun migrateOldAppDataWithTranslations() {
        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        val bttWriterDir = directoryProvider.createTempDir("BTTWriter")
        val sourceDir = Uri.fromFile(bttWriterDir)

        assetsProvider.open("exports/aa_jud_text_reg.tstudio").use { stream ->
            val tempDir = directoryProvider.createTempDir("temp")
            Zip.unzipFromStream(stream, tempDir)

            val translationDir = tempDir.listFiles()!!.first { it.isDirectory }
            FileUtilities.copyDirectory(translationDir, File(bttWriterDir, "translations/aa_jud_text_reg"), null)
        }

        MigrateTranslations(appContext, importProjects, directoryProvider)
            .execute(sourceDir, progressListener)

        assertEquals("Completed!", progressMessage)
        assertEquals(1, directoryProvider.translationsDir.listFiles()?.size ?: 0)
    }

    @Test
    fun migrateOldAppDataWithBackups() {
        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        val bttWriterDir = directoryProvider.createTempDir("BTTWriter")
        val sourceDir = Uri.fromFile(bttWriterDir)

        assetsProvider.open("exports/aa_jud_text_reg.tstudio").use { stream ->
            val tempFile = File(bttWriterDir, "backups/aa_jud_text_reg.tstudio")
            FileUtilities.copyInputStreamToFile(stream, tempFile)
        }

        MigrateTranslations(appContext, importProjects, directoryProvider)
            .execute(sourceDir, progressListener)

        val expectedMessage = appContext.getString(R.string.copying_file, "aa_jud_text_reg.tstudio")

        assertEquals(expectedMessage, progressMessage)
        assertEquals(1, directoryProvider.backupsDir.listFiles()?.size ?: 0)
    }
}