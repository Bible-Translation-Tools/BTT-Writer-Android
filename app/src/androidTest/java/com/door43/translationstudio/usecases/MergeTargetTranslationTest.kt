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
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import com.door43.usecases.ImportProjects
import com.door43.usecases.MergeTargetTranslation
import com.door43.usecases.MergeTargetTranslation.Status
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.inject
import org.unfoldingword.door43client.Door43Client
import kotlin.test.DefaultAsserter.assertTrue


@RunWith(AndroidJUnit4::class)
@IntegrationTest
class MergeTargetTranslationTest : KoinAndroidTest() {

    private val appContext: Context by inject()
    private val directoryProvider: IDirectoryProvider by inject()
    private val library: Door43Client by inject()
    private val profile: Profile by inject()
    private val assetsProvider: AssetsProvider by inject()
    private val importProjects: ImportProjects by inject()
    private val translator: Translator by inject()
    private val mergeTargetTranslation: MergeTargetTranslation by inject()
    private val platform: Platform by inject()

    private var sourceTranslation: TargetTranslation? = null
    private var destinationTranslation: TargetTranslation? = null

    @Before
    fun setUp() {
        setupTranslations()
    }

    @After
    fun tearDown() {
        directoryProvider.clearCache()
        directoryProvider.deleteTranslations()
    }

    @Test
    fun testMergeTargetTranslationWithDeletion() {
        val result = mergeTargetTranslation.execute(
            destinationTranslation!!,
            sourceTranslation!!,
            true
        )

        assertTrue("Merge should have succeeded", result.success)
        assertEquals("Status should be MERGE_CONFLICTS", Status.MERGE_CONFLICTS, result.status)
        assertEquals(
            "Source translation should match result",
            sourceTranslation,
            result.sourceTranslation
        )
        assertEquals(
            "Destination translation should match result",
            destinationTranslation,
            result.destinationTranslation
        )

        assertTrue(
            "Destination translation should exist",
            translator.targetTranslations.map { it.id }.contains(destinationTranslation!!.id)
        )

        assertFalse(
            "Source translation should not exist",
            translator.targetTranslations.map { it.id }.contains(sourceTranslation!!.id)
        )
    }

    @Test
    fun testMergeTargetTranslationWithoutDeletion() {
        val result = mergeTargetTranslation.execute(
            destinationTranslation!!,
            sourceTranslation!!,
            false
        )

        assertTrue("Merge should have succeeded", result.success)
        assertEquals("Status should be MERGE_CONFLICTS", Status.MERGE_CONFLICTS, result.status)
        assertEquals(
            "Source translation should match result",
            sourceTranslation,
            result.sourceTranslation
        )
        assertEquals(
            "Destination translation should match result",
            destinationTranslation,
            result.destinationTranslation
        )

        assertTrue(
            "Destination translation should exist",
            translator.targetTranslations.map { it.id }.contains(destinationTranslation!!.id)
        )

        assertTrue(
            "Source translation should exist",
            translator.targetTranslations.map { it.id }.contains(sourceTranslation!!.id)
        )
    }

    private fun setupTranslations() {
        sourceTranslation = TestUtils.importTargetTranslation(
            library,
            appContext,
            platform,
            directoryProvider,
            profile,
            assetsProvider,
            importProjects,
            translator,
            "aa",
            "usfm/mrk.usfm"
        )

        assertNotNull("Source translation is null", sourceTranslation)

        destinationTranslation = TestUtils.importTargetTranslation(
            library,
            appContext,
            platform,
            directoryProvider,
            profile,
            assetsProvider,
            importProjects,
            translator,
            "aae",
            "usfm/mrk.usfm"
        )

        assertNotNull("Destination translation is null", destinationTranslation)
    }
}