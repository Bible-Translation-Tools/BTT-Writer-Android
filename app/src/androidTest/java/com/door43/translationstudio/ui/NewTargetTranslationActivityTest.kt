package com.door43.translationstudio.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity.RESULT_CANCELED
import androidx.appcompat.app.AppCompatActivity.RESULT_OK
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions.scrollToPosition
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.App.Companion.deviceLanguageCode
import com.door43.translationstudio.R
import com.door43.translationstudio.TestUtils
import com.door43.translationstudio.UITest
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.UiTestUtils.checkDialogText
import com.door43.translationstudio.ui.UiTestUtils.checkListViewHasItemsCount
import com.door43.translationstudio.ui.UiTestUtils.checkRecyclerViewChild
import com.door43.translationstudio.ui.UiTestUtils.checkText
import com.door43.translationstudio.ui.newtranslation.NewTargetTranslationActivity
import com.door43.translationstudio.ui.newtranslation.NewTargetTranslationActivity.Companion.EXTRA_CHANGE_TARGET_LANGUAGE_ONLY
import com.door43.translationstudio.ui.newtranslation.NewTargetTranslationActivity.Companion.EXTRA_DISABLED_LANGUAGES
import com.door43.translationstudio.ui.newtranslation.NewTargetTranslationActivity.Companion.EXTRA_TARGET_TRANSLATION_ID
import com.door43.translationstudio.ui.newtranslation.NewTargetTranslationActivity.Companion.RESULT_DUPLICATE
import com.door43.usecases.ImportProjects
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.unfoldingword.door43client.Door43Client
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
@UITest
class NewTargetTranslationActivityTest {

    @ApplicationContext @Inject lateinit var context: Context
    @Inject lateinit var library: Door43Client
    @Inject lateinit var importProjects: ImportProjects
    @Inject lateinit var assetsProvider: AssetsProvider
    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var translator: Translator
    @Inject lateinit var profile: Profile

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

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
    fun testCreateNewTargetTranslation() {
        val langCount = library.index.targetLanguages.size

        val scenario = ActivityScenario.launchActivityForResult(NewTargetTranslationActivity::class.java)

        checkText(R.string.title_activity_new_target_translation, true)
        onView(withHint(R.string.choose_target_language)).tryCheck(matches(isDisplayed()))
        onView(withId(R.id.search)).tryCheck(matches(isDisplayed()))
        onView(withId(R.id.languages)).tryCheck(matches(isDisplayed()))

        checkListViewHasItemsCount(withId(R.id.languages), langCount)
        checkRecyclerViewChild(withId(R.id.languages), withId(R.id.languageName), 0, true)

        onView(withText("aab")).tryPerform(click())
        onView(withText("bible-nt")).tryPerform(click())
        onView(withText("Mark")).tryPerform(click())

        val result = scenario.result
        assertEquals(RESULT_OK, result.resultCode)
        assertNotNull(result.resultData.getStringExtra(EXTRA_TARGET_TRANSLATION_ID))

        scenario.close()
    }

    @Test
    fun testChangeTargetTranslationLanguage() {
        val targetTranslation = getTargetTranslation()
        val intent = Intent(context, NewTargetTranslationActivity::class.java)
        intent.putExtra(EXTRA_TARGET_TRANSLATION_ID, targetTranslation.id)
        intent.putExtra(EXTRA_DISABLED_LANGUAGES, arrayOf(targetTranslation.targetLanguage.slug))
        intent.putExtra(EXTRA_CHANGE_TARGET_LANGUAGE_ONLY, true)

        val scenario = ActivityScenario.launchActivityForResult<NewTargetTranslationActivity>(intent)

        checkText(R.string.title_activity_new_target_translation, true)
        onView(withHint(R.string.choose_target_language)).tryCheck(matches(isDisplayed()))
        onView(withId(R.id.search)).tryCheck(matches(isDisplayed()))
        onView(withId(R.id.languages)).tryCheck(matches(isDisplayed()))

        // Clicking original language does nothing
        onView(withText(targetTranslation.targetLanguage.slug)).tryPerform(click())
        assertEquals(Lifecycle.State.RESUMED, scenario.state)

        onView(withText("aab")).tryPerform(click())

        val result = scenario.result
        assertEquals(Activity.RESULT_OK, result.resultCode)

        scenario.close()
    }

    @Test
    fun testCreateNewTargetTranslationWithConflict() {
        val existingTranslation = getTargetTranslation("aab")

        val scenario = ActivityScenario.launchActivityForResult(NewTargetTranslationActivity::class.java)

        checkText(R.string.title_activity_new_target_translation, true)
        onView(withHint(R.string.choose_target_language)).tryCheck(matches(isDisplayed()))
        onView(withId(R.id.search)).tryCheck(matches(isDisplayed()))
        onView(withId(R.id.languages)).tryCheck(matches(isDisplayed()))

        onView(withText("aab")).tryPerform(click())
        onView(withText("bible-nt")).tryPerform(click())
        onView(withText("Mark")).tryPerform(click())

        val result = scenario.result
        assertEquals(RESULT_DUPLICATE, result.resultCode)
        assertEquals(
            existingTranslation.id,
            result.resultData.getStringExtra(EXTRA_TARGET_TRANSLATION_ID)
        )

        scenario.close()
    }

    @Test
    fun testChangeTargetTranslationLanguageWithConflictCanceled() {
        val existingTranslation = getTargetTranslation("aab")
        val targetTranslation = getTargetTranslation("aa")

        val intent = Intent(context, NewTargetTranslationActivity::class.java)
        intent.putExtra(EXTRA_TARGET_TRANSLATION_ID, targetTranslation.id)
        intent.putExtra(EXTRA_DISABLED_LANGUAGES, arrayOf(targetTranslation.targetLanguage.slug))
        intent.putExtra(EXTRA_CHANGE_TARGET_LANGUAGE_ONLY, true)

        val scenario = ActivityScenario.launchActivityForResult<NewTargetTranslationActivity>(intent)

        checkText(R.string.title_activity_new_target_translation, true)
        onView(withHint(R.string.choose_target_language)).tryCheck(matches(isDisplayed()))
        onView(withId(R.id.search)).tryCheck(matches(isDisplayed()))
        onView(withId(R.id.languages)).tryCheck(matches(isDisplayed()))

        onView(withText("aab")).tryPerform(click())

        val project = library.index.getProject(deviceLanguageCode, existingTranslation.projectId)
        val dialogMessage = context.getString(
            R.string.warn_existing_target_translation,
            project.name,
            existingTranslation.targetLanguageName
        )
        checkDialogText(dialogMessage, true)
        onView(withText(R.string.no)).inRoot(isDialog()).tryPerform(click())

        assertEquals(RESULT_CANCELED, scenario.result.resultCode)

        scenario.close()
    }

    @Test
    fun testChangeTargetTranslationLanguageConflictMerged() {
        val existingTranslation = getTargetTranslation("aab")
        val targetTranslation = getTargetTranslation("aa")

        val intent = Intent(context, NewTargetTranslationActivity::class.java)
        intent.putExtra(EXTRA_TARGET_TRANSLATION_ID, targetTranslation.id)
        intent.putExtra(EXTRA_DISABLED_LANGUAGES, arrayOf(targetTranslation.targetLanguage.slug))
        intent.putExtra(EXTRA_CHANGE_TARGET_LANGUAGE_ONLY, true)

        val scenario = ActivityScenario.launchActivityForResult<NewTargetTranslationActivity>(intent)

        checkText(R.string.title_activity_new_target_translation, true)
        onView(withHint(R.string.choose_target_language)).tryCheck(matches(isDisplayed()))
        onView(withId(R.id.search)).tryCheck(matches(isDisplayed()))
        onView(withId(R.id.languages)).tryCheck(matches(isDisplayed()))

        onView(withText("aab")).tryPerform(click())

        val project = library.index.getProject(deviceLanguageCode, existingTranslation.projectId)
        val dialogMessage = context.getString(
            R.string.warn_existing_target_translation,
            project.name,
            existingTranslation.targetLanguageName
        )
        checkDialogText(dialogMessage, true)
        onView(withText(R.string.yes)).inRoot(isDialog()).tryPerform(click())

        assertEquals(Activity.RESULT_OK, scenario.result.resultCode)

        scenario.close()
    }

    private fun getTargetTranslation(): TargetTranslation {
        return TestUtils.importTargetTranslation(
            importProjects,
            translator,
            assetsProvider,
            directoryProvider,
            "exports/aa_jud_text_reg.tstudio"
        )!!
    }

    private fun getTargetTranslation(langCode: String): TargetTranslation {
        return TestUtils.importTargetTranslation(
            library,
            context,
            directoryProvider,
            profile,
            assetsProvider,
            importProjects,
            translator,
            langCode,
            "usfm/mrk.usfm"
        )!!
    }
}