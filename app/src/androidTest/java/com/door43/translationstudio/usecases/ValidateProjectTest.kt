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
import com.door43.usecases.ValidateProject
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
class ValidateProjectTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject @ApplicationContext lateinit var appContext: Context
    @Inject lateinit var importProjects: ImportProjects
    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var assetsProvider: AssetsProvider
    @Inject lateinit var library: Door43Client
    @Inject lateinit var translator: Translator
    @Inject lateinit var profile: Profile
    @Inject lateinit var validateProject: ValidateProject

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
    fun testValidateProject() {
        val sourceTranslationId = "en_mrk_ulb"
        val targetTranslation = importTargetTranslation("aa")

        assertNotNull("Target translation should not be null", targetTranslation)

        val validate = validateProject.execute(targetTranslation!!.id, sourceTranslationId)
        val invalidItems = validate.filter { !it.isValid }

        assertEquals("All items should be invalid", invalidItems.size, validate.size)

        // Mark some chunks as done
        targetTranslation.finishFrame("01", "01")
        targetTranslation.finishFrame("02", "01")
        targetTranslation.finishFrame("03", "01")

        val validate2 = validateProject.execute(targetTranslation.id, sourceTranslationId)
        val invalidItems2 = validate2.filter { !it.isValid }
        val validItems = validate2.filter { it.isValid }

        assertEquals(
            "There should be ${invalidItems.size} errors",
            invalidItems.size - 3,
            invalidItems2.size
        )
        assertEquals(
            "There should be ${validItems.size} valid items",
            3,
            validItems.size
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