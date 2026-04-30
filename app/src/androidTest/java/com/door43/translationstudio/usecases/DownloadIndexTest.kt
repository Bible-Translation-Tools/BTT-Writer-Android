package com.door43.translationstudio.usecases

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.IntegrationTest
import com.door43.translationstudio.KoinAndroidTest
import com.door43.usecases.ImportIndex
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import org.bibletranslationtools.resourcecatalog.ResourceCatalogClient
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.inject


@RunWith(AndroidJUnit4::class)
@IntegrationTest
class DownloadIndexTest : KoinAndroidTest() {

    private val directoryProvider: IDirectoryProvider by inject()
    private val downloadIndex: ImportIndex by inject()
    private val catalogClient: ResourceCatalogClient by inject()
    private val assetsProvider: AssetsProvider by inject()

    @Before
    fun setUp() {
        directoryProvider.clearCache()
    }

    @Test
    fun downloadIndexSucceeds() {
        var progressMessage: String? = null
        val onProgress: (Float, String?) -> Unit = { _, message ->
            progressMessage = message
        }

        val languagesBefore = catalogClient.library.getTargetLanguages()
        assertTrue("Languages before should not be empty", languagesBefore.isNotEmpty())

        val downloaded = downloadIndex.download(onProgress)

        assertTrue("Download result should be true", downloaded)
        assertNotNull("Progress message should not be null", progressMessage)

        // Create new instance of the library, because after downloading index,
        // library is closed and can't be used anymore
        val newLibrary = ResourceCatalogClient(
            directoryProvider.databaseFile,
            directoryProvider.containersDir
        )
        val languagesAfter = newLibrary.library.getTargetLanguages()

        assertTrue("Languages after should not be empty", languagesAfter.isNotEmpty())
        assertNotEquals(
            "Target languages should have changed",
            languagesBefore.size, languagesAfter.size
        )
    }

    @Test
    fun importIndexSucceeds() {
        val languagesBefore = catalogClient.library.getTargetLanguages()
        assertTrue("Languages before should not be empty", languagesBefore.isNotEmpty())

        val indexFile = directoryProvider.createTempFile("index", ".sqlite")
        assetsProvider.open("index_shrunk.sqlite").use { input ->
            indexFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val imported = downloadIndex.import(Uri.fromFile(indexFile))

        assertTrue("Import result should be true", imported)

        // Create new instance of the library, because after downloading index,
        // library is closed and can't be used anymore
        val newLibrary = ResourceCatalogClient(
            directoryProvider.databaseFile,
            directoryProvider.containersDir
        )
        val languagesAfter = newLibrary.library.getTargetLanguages()

        assertTrue("Languages after should not be empty", languagesAfter.isNotEmpty())
        assertNotEquals(
            "Target languages should have changed",
            languagesBefore.size, languagesAfter.size
        )
    }
}
