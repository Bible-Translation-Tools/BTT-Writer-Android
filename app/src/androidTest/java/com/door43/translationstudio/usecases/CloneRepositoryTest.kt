package com.door43.translationstudio.usecases

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.OnProgressListener
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.IntegrationTest
import com.door43.translationstudio.KoinAndroidTest
import com.door43.usecases.CloneRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.inject
import java.io.File

@RunWith(AndroidJUnit4::class)
@IntegrationTest
class CloneRepositoryTest : KoinAndroidTest() {

    private val cloneRepository: CloneRepository by inject()
    private val directoryProvider: IDirectoryProvider by inject()

    @Before
    fun setUp() {
        // Koin is already initialized via KoinTestApplication
    }

    @After
    fun tearDown() {
        directoryProvider.clearCache()
    }

    @Test
    fun cloneRepositorySuccessfully() {
        val cloneUrl = "https://wacs.bibletranslationtools.org/WycliffeAssociates/en_ulb.git"
        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        val result = cloneRepository.execute(cloneUrl, progressListener)

        assertNotNull("Clone repository result should not be null", result)
        assertNotNull("Progress message should not be null", progressMessage)
        assertEquals(CloneRepository.Status.SUCCESS, result.status)
        assertEquals(cloneUrl, result.cloneUrl)
        assertTrue(result.cloneDir!!.exists())

        val gitDir: File? = result.cloneDir.listFiles()?.find { it.name == ".git" }
        assertNotNull("Git directory should not be null", gitDir)

        val manifestFile: File? = result.cloneDir.listFiles()?.find { it.name == "manifest.yaml" }
        assertNotNull("Manifest file should not be null", manifestFile)
        assertTrue(manifestFile!!.length() > 0)
    }

    @Test
    fun cloneNonExistingRepositoryFailed() {
        val cloneUrl = "https://wacs.bibletranslationtools.org/WycliffeAssociates/non_existing_repo.git"

        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        val result = cloneRepository.execute(cloneUrl, progressListener)

        assertNotNull("Clone repository result should not be null", result)
        assertNotNull("Progress message should not be null", progressMessage)
        assertEquals(CloneRepository.Status.NO_REMOTE_REPO, result.status)
        assertEquals(cloneUrl, result.cloneUrl)
        assertNull(result.cloneDir)
    }
}
