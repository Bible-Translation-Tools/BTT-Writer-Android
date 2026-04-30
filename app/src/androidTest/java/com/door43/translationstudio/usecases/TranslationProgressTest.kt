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
import com.door43.usecases.TranslationProgress
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.inject
import org.bibletranslationtools.resourcecatalog.ResourceCatalogClient


@RunWith(AndroidJUnit4::class)
@IntegrationTest
class TranslationProgressTest : KoinAndroidTest() {

    private val appContext: Context by inject()
    private val importProjects: ImportProjects by inject()
    private val directoryProvider: IDirectoryProvider by inject()
    private val assetsProvider: AssetsProvider by inject()
    private val catalogClient: ResourceCatalogClient by inject()
    private val translator: Translator by inject()
    private val profile: Profile by inject()
    private val translationProgress: TranslationProgress by inject()
    private val platform: Platform by inject()

    @Before
    fun setUp() {
    }

    @After
    fun tearDown() {
        directoryProvider.clearCache()
        directoryProvider.deleteTranslations()
    }

    @Test
    fun testTranslationProgress() {
        val targetTranslation = importTargetTranslation("aa")

        assertNotNull("Target translation should not be null", targetTranslation)

        val progress = translationProgress.execute(targetTranslation!!)

        assertEquals("No finished chunks", 0, targetTranslation.numFinished)
        assertEquals(
            "Progress should be 0 because no chunks are marked as done",
            0f,
            progress
        )

        val totalTranslated = targetTranslation.numTranslated
        targetTranslation.finishFrame("01", "01")

        val progress2 = translationProgress.execute(targetTranslation)

        assertEquals("Has one finished chunk", 1, targetTranslation.numFinished)

        val expectedProgress = 1 / totalTranslated.toFloat()

        assertEquals(286, totalTranslated)
        assertEquals(
            "Progress should be 0 because no chunks are marked as done",
            expectedProgress,
            progress2
        )

        targetTranslation.finishChapterTitle("01")
        targetTranslation.finishFrame("02", "03")
        targetTranslation.finishFrame("03", "01")
        targetTranslation.finishFrame("04", "06")

        // Also try to finish non-existent chunk, that should not add to finished chunks number
        targetTranslation.finishFrame("99", "99")

        val progress3 = translationProgress.execute(targetTranslation)

        assertEquals("Has five finished chunks", 5, targetTranslation.numFinished)

        val expectedProgress2 = 5 / totalTranslated.toFloat()

        assertEquals(286, totalTranslated)
        assertEquals(
            "Progress should be not be 0",
            expectedProgress2,
            progress3
        )
    }

    private fun importTargetTranslation(lang: String): TargetTranslation? {
        return TestUtils.importTargetTranslation(
            catalogClient,
            appContext,
            platform,
            directoryProvider,
            profile,
            assetsProvider,
            importProjects,
            translator,
            lang,
            "usfm/mrk.usfm"
        )
    }
}
