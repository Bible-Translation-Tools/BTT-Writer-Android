package com.door43.translationstudio.usecases

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.OnProgressListener
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.IntegrationTest
import com.door43.usecases.DownloadResourceContainers
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
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@IntegrationTest
class DownloadResourceContainersTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var directoryProvider: IDirectoryProvider
    @Inject
    lateinit var downloadResourceContainers: DownloadResourceContainers
    @Inject
    lateinit var library: Door43Client

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        directoryProvider.clearCache()
    }

    @Test
    fun downloadResourceContainerSucceeded() {
        val translation = library.index.getTranslation("en_gen_ulb")
        assertNotNull("Translation should not be null", translation)

        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        val result = downloadResourceContainers.download(translation, progressListener)

        assertNotNull("Download result should not be null", result)
        assertTrue("Download result should be successful", result.success)
        assertTrue(
            "Download result should have at least one container",
            result.containers.isNotEmpty()
        )
        assertNotNull("Progress message should not be null", progressMessage)
    }

    @Test
    fun downloadResourceContainersSucceeded() {
        val translationIds = listOf("en_gen_ulb", "id_gen_ayt")

        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        val result = downloadResourceContainers.download(translationIds, progressListener)

        assertNotNull("Download result should not be null", result)

        assertTrue(
            "id_gen_ayt should be downloaded",
            result.downloadedTranslations.contains("id_gen_ayt")
        )
        assertTrue(
            "id_gen_ayt should be downloaded",
            result.downloadedContainers.map { it.slug }.contains("id_gen_ayt")
        )
        assertTrue(
            "id_gen_tn should be downloaded",
            result.downloadedContainers.map { it.slug }.contains("id_gen_tn")
        )
        assertTrue(
            "id_gen_tq should be downloaded",
            result.downloadedContainers.map { it.slug }.contains("id_gen_tq")
        )
        assertTrue(
            "id_bible_tw should be downloaded",
            result.downloadedContainers.map { it.slug }.contains("id_bible_tw")
        )
        assertFalse(
            "en_gen_ulb should not be in downloaded translations list",
            result.downloadedTranslations.contains("en_gen_ulb")
        )
        assertTrue(
            "en_gen_ulb should be downloaded",
            result.downloadedContainers.map { it.slug }.contains("en_gen_ulb")
        )
        assertTrue(
            "en_gen_tn should be downloaded",
            result.downloadedContainers.map { it.slug }.contains("en_gen_tn")
        )
        assertTrue(
            "en_bible_tw should be downloaded",
            result.downloadedContainers.map { it.slug }.contains("en_bible_tw")
        )
        assertFalse(
            "en_gen_tq should not be downloaded",
            result.downloadedContainers.map { it.slug }.contains("en_gen_tq")
        )
        assertTrue(
            "en_gen_tq should be in failed helps list",
            result.failedHelpsDownloads.contains("en_gen_tq")
        )
        assertTrue(
            "en_gen_ulb should be in failed sources list",
            result.failedSourceDownloads.contains("en_gen_ulb")
        )

        assertNotNull("Progress message should not be null", progressMessage)
    }

    @Test
    fun downloadNoneResourceContainers() {
        val result = downloadResourceContainers.download(listOf())

        assertNotNull("Download result should not be null", result)
        assertEquals(result.downloadedTranslations.size, 0)
        assertEquals(result.downloadedContainers.size, 0)
        assertEquals(result.failedSourceDownloads.size, 0)
        assertEquals(result.failedHelpsDownloads.size, 0)
        assertEquals(result.failureMessages.size, 0)
    }

    @Test
    fun downloadIncorrectResourceContainers() {
        val badTranslationIds = listOf("bad_tr_id1", "bad_tr_id2")
        val result = downloadResourceContainers.download(badTranslationIds)

        assertNotNull("Download result should not be null", result)

        assertEquals(
            "Failed downloads should be 2",
            result.failedSourceDownloads.size, badTranslationIds.size
        )
        assertEquals(result.downloadedTranslations.size, 0)
        assertEquals(result.downloadedContainers.size, 0)
        assertEquals(result.failedHelpsDownloads.size, 0)
        assertEquals(result.failureMessages.size, badTranslationIds.size)

        assertTrue(
            "bad_tr_id1 should be in failure messages",
            result.failureMessages.containsKey("bad_tr_id1")
        )
        assertTrue(
            "bad_tr_id2 should be in failure messages",
            result.failureMessages.containsKey("bad_tr_id2")
        )
        assertTrue(
            "bad_tr_id1 and bad_tr_id2 should have failure messages",
            result.failureMessages.values.filter { it.isNotEmpty() }.size == 2
        )
        assertTrue(
            "bad_tr_id1 should be in failed sources list",
            result.failedSourceDownloads.contains("bad_tr_id1")
        )
        assertTrue(
            "bad_tr_id2 should be in failed sources list",
            result.failedSourceDownloads.contains("bad_tr_id2")
        )
    }
}