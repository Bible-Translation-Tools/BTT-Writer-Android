package com.door43.translationstudio.usecases

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.OnProgressListener
import com.door43.data.IDirectoryProvider
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
import org.unfoldingword.tools.logger.Logger
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DownloadIndexTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject
    @ApplicationContext lateinit var appContext: Context
    @Inject
    lateinit var directoryProvider: IDirectoryProvider
    @Inject
    lateinit var downloadIndex: DownloadIndex
    @Inject
    lateinit var library: Door43Client

    @Before
    fun setUp() {
        hiltRule.inject()
        Logger.flush()

        directoryProvider.clearCache()
    }

    @Test
    fun downloadIndexSucceeds() {
        var progressMessage: String? = null
        val progressListener = object : OnProgressListener {
            override fun onProgress(progress: Int, max: Int, message: String?) {
                progressMessage = message
            }
            override fun onIndeterminate() {
            }
        }

        val languagesBefore = library.index.targetLanguages
        assertTrue("Languages before should not be empty", languagesBefore.size > 0)

        val downloaded = downloadIndex.execute(progressListener)

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
}