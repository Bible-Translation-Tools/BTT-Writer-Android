package com.door43.translationstudio.ui

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.KoinAndroidTest
import com.door43.translationstudio.R
import com.door43.translationstudio.TestUtils
import com.door43.translationstudio.UITest
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.UiTestUtils.checkDialogText
import com.door43.translationstudio.ui.draft.DraftActivity
import com.door43.translationstudio.ui.draft.DraftActivity.Companion.EXTRA_TARGET_TRANSLATION_ID
import com.door43.usecases.ImportProjects
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.inject
import org.unfoldingword.door43client.Door43Client

@RunWith(AndroidJUnit4::class)
@UITest
class DraftActivityTest : KoinAndroidTest() {

    private val context: Context by inject()
    private val directoryProvider: IDirectoryProvider by inject()
    private val library: Door43Client by inject()
    private val importProjects: ImportProjects by inject()
    private val profile: Profile by inject()
    private val assetsProvider: AssetsProvider by inject()
    private val translator: Translator by inject()

    @Before
    fun setUp() {
        // Koin is already initialized via KoinTestApplication
    }

    @After
    fun tearDown() {
        directoryProvider.clearCache()
        directoryProvider.deleteTranslations()
    }

    @Test
    fun testInvalidTranslation() {
        val intent = Intent(context, DraftActivity::class.java)
        intent.putExtra(EXTRA_TARGET_TRANSLATION_ID, "1")

        ActivityScenario.launch<DraftActivity>(intent).use {
//            onView(withId(R.id.drafts)).tryCheck(matches(isDisplayed()))
//            onView(withId(R.id.fab)).tryCheck(matches(isDisplayed()))
        }
    }

    @Test
    fun testValidTranslation() {
        val targetTranslation = getTargetTranslation()

        val intent = Intent(context, DraftActivity::class.java)
        intent.putExtra(EXTRA_TARGET_TRANSLATION_ID, targetTranslation.id)

        ActivityScenario.launch<DraftActivity>(intent).use {
//            onView(withId(R.id.drafts)).tryCheck(matches(isDisplayed()))
//            onView(withId(R.id.fab)).tryCheck(matches(isDisplayed()))
//            onView(allOf(
//                withText(containsString("Chapter 1")),
//                withId(R.id.source_translation_title),
//                hasSibling(withId(R.id.source_translation_body)
//                ))).tryCheck(matches(isDisplayed()))

            // Cancel import draft dialog
//            onView(withId(R.id.fab)).tryPerform(click())
            checkDialogText(R.string.import_draft, true)
            checkDialogText(R.string.import_draft_confirmation, true)
            onView(withText(R.string.menu_cancel)).tryPerform(click())
            checkDialogText(R.string.import_draft, false)

//            onView(withId(R.id.fab)).tryPerform(click())
            checkDialogText(R.string.import_draft, true)
            checkDialogText(R.string.import_draft_confirmation, true)
            onView(withText(R.string.label_import)).tryPerform(click())
            checkDialogText(R.string.importing_draft, true)
        }
    }

    private fun getTargetTranslation(): TargetTranslation {
        return TestUtils.importTargetTranslation(
            library,
            context,
            directoryProvider,
            profile,
            assetsProvider,
            importProjects,
            translator,
            "en",
            "usfm/19-PSA.usfm"
        )!!
    }
}
