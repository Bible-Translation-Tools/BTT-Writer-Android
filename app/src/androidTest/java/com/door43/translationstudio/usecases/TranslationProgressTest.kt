package com.door43.translationstudio.usecases

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.TestUtils
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import com.door43.usecases.ImportProjects
import com.door43.usecases.TranslationProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.unfoldingword.door43client.Door43Client
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TranslationProgressTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject
    @ApplicationContext lateinit var appContext: Context
    @Inject lateinit var importProjects: ImportProjects
    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var assetsProvider: AssetsProvider
    @Inject lateinit var library: Door43Client
    @Inject lateinit var translator: Translator
    @Inject lateinit var profile: Profile
    @Inject lateinit var translationProgress: TranslationProgress

    @Before
    fun setUp() {
        hiltRule.inject()
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

        assertEquals("No finished chunks", 0, targetTranslation.numFinished())
        assertEquals(
            "Progress should be 0 because no chunks are marked as done",
            0.0,
            progress
        )

        val totalTranslated = targetTranslation.numTranslated()
        targetTranslation.finishFrame("01", "01")

        val progress2 = translationProgress.execute(targetTranslation)

        assertEquals("Has one finished chunk", 1, targetTranslation.numFinished())

        val expectedProgress = 1 / totalTranslated.toDouble()

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

        assertEquals("Has five finished chunks", 5, targetTranslation.numFinished())

        val expectedProgress2 = 5 / totalTranslated.toDouble()

        assertEquals(286, totalTranslated)
        assertEquals(
            "Progress should be not be 0",
            expectedProgress2,
            progress3
        )
    }

    private fun importTargetTranslation(lang: String): TargetTranslation? {
        return TestUtils.importTargetTranslation(
            library,
            appContext,
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