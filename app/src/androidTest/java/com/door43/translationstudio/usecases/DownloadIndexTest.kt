package com.door43.translationstudio.usecases

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.OnProgressListener
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.IntegrationTest
import com.door43.usecases.DownloadIndex
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.unfoldingword.door43client.Door43Client
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@IntegrationTest
class DownloadIndexTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject @ApplicationContext lateinit var appContext: Context
    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var downloadIndex: DownloadIndex
    @Inject lateinit var library: Door43Client
    @Inject lateinit var assetsProvider: AssetsProvider

    @Before
    fun setUp() {
        hiltRule.inject()

        directoryProvider.clearCache()
    }

    @Test
    fun downloadIndexSucceeds() {
        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        val languagesBefore = library.index.targetLanguages
        assertTrue("Languages before should not be empty", languagesBefore.size > 0)

        val downloaded = downloadIndex.download(progressListener)

        assertTrue("Download result should be true", downloaded)
        assertNotNull("Progress message should not be null", progressMessage)

        // Create new instance of the library, because after downloading index,
        // library is closed and can't be used anymore
        val newLibrary = Door43Client(appContext, directoryProvider)
        val languagesAfter = newLibrary.index.targetLanguages

        assertTrue("Languages after should not be empty", languagesAfter.size > 0)
        assertNotEquals(
            "Target languages should have changed",
            languagesBefore.size, languagesAfter.size
        )
    }

    @Test
    fun importIndexSucceeds() {
        val languagesBefore = library.index.targetLanguages
        assertTrue("Languages before should not be empty", languagesBefore.size > 0)

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
        val newLibrary = Door43Client(appContext, directoryProvider)
        val languagesAfter = newLibrary.index.targetLanguages

        assertTrue("Languages after should not be empty", languagesAfter.size > 0)
        assertNotEquals(
            "Target languages should have changed",
            languagesBefore.size, languagesAfter.size
        )
    }
}