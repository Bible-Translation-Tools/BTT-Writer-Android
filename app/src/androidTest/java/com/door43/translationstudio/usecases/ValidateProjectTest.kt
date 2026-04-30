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
import com.door43.translationstudio.core.Validation
import com.door43.usecases.ImportProjects
import com.door43.usecases.ValidateProject
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
class ValidateProjectTest : KoinAndroidTest() {

    private val appContext: Context by inject()
    private val importProjects: ImportProjects by inject()
    private val directoryProvider: IDirectoryProvider by inject()
    private val assetsProvider: AssetsProvider by inject()
    private val catalogClient: ResourceCatalogClient by inject()
    private val translator: Translator by inject()
    private val profile: Profile by inject()
    private val validateProject: ValidateProject by inject()
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
    fun testValidateProject() {
        val sourceTranslationId = "en_mrk_ulb"
        val targetTranslation = importTargetTranslation("aa")

        assertNotNull("Target translation should not be null", targetTranslation)

        val validate = validateProject.execute(targetTranslation!!.id, sourceTranslationId)
        val invalidItems = validate.filter {
            it is Validation.InvalidFrame || it is Validation.InvalidGroup
        }

        assertEquals("All items should be invalid", invalidItems.size, validate.size)

        // Mark some chunks as done
        targetTranslation.finishFrame("01", "01")
        targetTranslation.finishFrame("02", "01")
        targetTranslation.finishFrame("03", "01")

        val validate2 = validateProject.execute(targetTranslation.id, sourceTranslationId)
        val invalidItems2 = validate2.filter {
            it is Validation.InvalidFrame || it is Validation.InvalidGroup
        }
        val validItems = validate2.filter {
            it is Validation.ValidFrame || it is Validation.ValidGroup
        }

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
