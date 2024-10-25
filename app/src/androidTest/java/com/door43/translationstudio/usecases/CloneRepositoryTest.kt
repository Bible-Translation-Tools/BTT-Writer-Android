package com.door43.translationstudio.usecases

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.OnProgressListener
import com.door43.data.IDirectoryProvider
import com.door43.usecases.CloneRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.unfoldingword.tools.logger.Logger
import java.io.File
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CloneRepositoryTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var cloneRepository: CloneRepository
    @Inject
    lateinit var directoryProvider: IDirectoryProvider

    @Before
    fun setUp() {
        hiltRule.inject()
        Logger.flush()
    }

    @After
    fun tearDown() {
        directoryProvider.clearCache()
    }

    @Test
    fun cloneRepositorySuccessfully() {
        val cloneUrl = "https://wacs.bibletranslationtools.org/WycliffeAssociates/en_ulb.git"
        var progressMessage: String? = null
        val progressListener = object : OnProgressListener {
            override fun onProgress(progress: Int, max: Int, message: String?) {
                progressMessage = message
            }
            override fun onIndeterminate() {
            }
        }

        val result = cloneRepository.execute(cloneUrl, progressListener)

        assertNotNull("Clone repository result should not be null", result)
        assertNotNull("Progress message should not be null", progressMessage)
        assertEquals(result.status, CloneRepository.Status.SUCCESS)
        assertEquals(result.cloneUrl, cloneUrl)
        assertTrue(result.cloneDir!!.exists())

        val gitDir: File? = result.cloneDir?.listFiles()?.find { it.name == ".git" }
        assertNotNull("Git directory should not be null", gitDir)

        val manifestFile: File? = result.cloneDir?.listFiles()?.find { it.name == "manifest.yaml" }
        assertNotNull("Manifest file should not be null", manifestFile)
        assertTrue(manifestFile!!.length() > 0)
    }

    @Test
    fun cloneNonExistingRepositoryFailed() {
        val cloneUrl = "https://wacs.bibletranslationtools.org/WycliffeAssociates/non_existing_repo.git"

        var progressMessage: String? = null
        val progressListener = object : OnProgressListener {
            override fun onProgress(progress: Int, max: Int, message: String?) {
                progressMessage = message
            }
            override fun onIndeterminate() {
            }
        }

        val result = cloneRepository.execute(cloneUrl, progressListener)

        assertNotNull("Clone repository result should not be null", result)
        assertNotNull("Progress message should not be null", progressMessage)
        assertEquals(result.status, CloneRepository.Status.NO_REMOTE_REPO)
        assertEquals(result.cloneUrl, cloneUrl)
        assertNull(result.cloneDir)
    }
}