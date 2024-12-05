package com.door43.translationstudio.ui.home

import android.app.Instrumentation
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.ui.SettingsActivity
import com.door43.translationstudio.ui.UiTestUtils.checkContainsText
import com.door43.translationstudio.ui.UiTestUtils.checkDialogText
import com.door43.translationstudio.ui.UiTestUtils.checkText
import com.door43.translationstudio.ui.UiTestUtils.waitFor
import com.door43.translationstudio.ui.tryPerform
import com.door43.translationstudio.ui.publish.PublishActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class HomeActivityTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject lateinit var profile: Profile

    @Before
    fun setUp() {
        hiltRule.inject()
        Intents.init()

        mockkStatic(FileProvider::class)

        //login
        profile.fullName = "TestUser"
    }

    @After
    fun tearDown() {
        Intents.release()
        unmockkAll()
    }

    @Test
    fun testHomeActivityNoTranslations() {
        val scenario = ActivityScenario.launch(HomeActivity::class.java)

        testMainViewsInPlace(true)

        checkText(R.string.translations_welcome, true)

        scenario.close()
    }

    @Test
    fun testHomeActivityCreateTranslation() {
        val scenario = ActivityScenario.launch(HomeActivity::class.java)

        testMainViewsInPlace(true)
        checkText(R.string.translations_welcome, true)

        createTargetTranslation()

        testMainViewsInPlace(true)
        checkText(R.string.translations_welcome, false)
        checkText(R.string.Project, true)
        checkText(R.string.language, true)
        checkText(R.string.progress, true)
        checkText("Mark", true)

        scenario.close()
    }

    @Test
    fun testHomeActivityShowTranslationInfo() {
        val scenario = ActivityScenario.launch(HomeActivity::class.java)

        testMainViewsInPlace(true)
        checkText(R.string.translations_welcome, true)

        createTargetTranslation()

        onView(withId(R.id.infoButton)).tryPerform(click())

        checkDialogText("Mark", true)
        checkDialogText(R.string.Project, true)
        checkDialogText(R.string.target_language, true)
        checkDialogText(R.string.progress, true)
        checkDialogText(R.string.contributors, true)

        scenario.close()
    }

    @Test
    fun testHomeActivityShowBackupDialog() {
        val scenario = ActivityScenario.launch(HomeActivity::class.java)

        testMainViewsInPlace(true)
        checkText(R.string.translations_welcome, true)

        createTargetTranslation()

        onView(withId(R.id.infoButton)).tryPerform(click())

        onView(withId(R.id.backup_button)).tryPerform(click())
        checkDialogText(R.string.title_upload_export, true)
        checkDialogText(R.string.backup_to_door43, true)
        checkDialogText(R.string.export_to_usfm, true)
        checkDialogText(R.string.export_to_pdf, true)
        checkDialogText(R.string.backup_to_sd, true)
        checkDialogText(R.string.backup_to_friend, true)
        checkDialogText(R.string.backup_to_app, true)

        scenario.close()
    }

    @Test
    fun testHomeActivityShowPrintDialog() {
        val scenario = ActivityScenario.launch(HomeActivity::class.java)

        testMainViewsInPlace(true)
        checkText(R.string.translations_welcome, true)

        createTargetTranslation()

        onView(withId(R.id.infoButton)).tryPerform(click())

        onView(withId(R.id.print_button)).tryPerform(click())
        checkDialogText(R.string.include_incomplete_frames, true)
        onView(withId(R.id.print_button)).check(matches(isDisplayed()))

        scenario.close()
    }

    @Test
    fun testHomeActivityShowDeleteDialog() {
        val scenario = ActivityScenario.launch(HomeActivity::class.java)

        testMainViewsInPlace(true)
        checkText(R.string.translations_welcome, true)

        createTargetTranslation()

        onView(withId(R.id.infoButton)).tryPerform(click())

        onView(withId(R.id.delete_button)).tryPerform(click())
        checkDialogText(R.string.confirm_delete_target_translation, true)

        scenario.close()
    }

    @Test
    fun testHomeActivityPublishTranslation() {
        val scenario = ActivityScenario.launch(HomeActivity::class.java)

        intending(hasComponent(PublishActivity::class.java.name)).respondWith(
            Instrumentation.ActivityResult(0, null)
        )

        testMainViewsInPlace(true)
        checkText(R.string.translations_welcome, true)

        createTargetTranslation()

        onView(withId(R.id.infoButton)).tryPerform(click())
        onView(withId(R.id.publish_button)).tryPerform(click())

        intended(hasComponent(PublishActivity::class.java.name))

        scenario.close()
    }

    @Test
    fun testHomeActivityShowContributorsDialog() {
        val scenario = ActivityScenario.launch(HomeActivity::class.java)

        testMainViewsInPlace(true)
        checkText(R.string.translations_welcome, true)

        createTargetTranslation()

        onView(withId(R.id.infoButton)).tryPerform(click())

        onView(withId(R.id.translators)).tryPerform(click())
        checkDialogText(R.string.names_will_be_public, true)
        checkDialogText("TestUser", true)
        checkDialogText(R.string.add_contributor, true)

        onView(withId(R.id.name)).check(matches(isDisplayed()))
        onView(withId(R.id.edit_button)).check(matches(isDisplayed()))

        scenario.close()
    }

    @Test
    fun testHomeActivityUpdateLanguage() {
        val scenario = ActivityScenario.launch(HomeActivity::class.java)

        testMainViewsInPlace(true)
        checkText(R.string.translations_welcome, true)

        createTargetTranslation()

        onView(withId(R.id.infoButton)).tryPerform(click())

        onView(withId(R.id.change_language)).tryPerform(click())
        testMainViewsInPlace(false)

        checkText(R.string.title_activity_new_target_translation, true)

        onView(withText("aab")).tryPerform(click())

        testMainViewsInPlace(true)

        scenario.close()
    }

    @Test
    fun testHomeActivityOptionMenu() {
        val scenario = ActivityScenario.launch(HomeActivity::class.java)

        testMainViewsInPlace(true)
        checkText(R.string.translations_welcome, true)

        onView(withId(R.id.action_more)).tryPerform(click())

        checkText(R.string.menu_update, true)
        checkText(R.string.label_import_options, true)
        checkText(R.string.feedback, true)
        checkText(R.string.share_apk, true)
        checkText(R.string.log_out, true)
        checkText(R.string.action_settings, true)

        scenario.close()
    }

    @Test
    fun testHomeActivityUpdateDialog() {
        val scenario = ActivityScenario.launch(HomeActivity::class.java)

        testMainViewsInPlace(true)
        checkText(R.string.translations_welcome, true)

        onView(withId(R.id.action_more)).tryPerform(click())
        onView(withText(R.string.menu_update)).tryPerform(click())

        checkDialogText(R.string.update_options, true)
        checkDialogText(R.string.update_source, true)
        checkDialogText(R.string.download_index, true)
        checkDialogText(R.string.download_sources, true)
        checkDialogText(R.string.update_languages, true)
        checkDialogText(R.string.check_app_update, true)

        scenario.close()
    }

    @Test
    fun testHomeActivityImportDialog() {
        val scenario = ActivityScenario.launch(HomeActivity::class.java)

        testMainViewsInPlace(true)
        checkText(R.string.translations_welcome, true)

        onView(withId(R.id.action_more)).tryPerform(click())
        onView(withText(R.string.label_import_options)).tryPerform(click())

        checkDialogText(R.string.import_from_door43, true)
        checkDialogText(R.string.import_project_file, true)
        checkDialogText(R.string.import_usfm_file, true)
        checkDialogText(R.string.import_source_text, true)
        checkDialogText(R.string.import_from_device, true)

        onView(withId(R.id.import_from_door43)).tryPerform(click())
        onView(withId(R.id.search_button)).check(matches(isDisplayed()))
        onView(withId(R.id.username)).check(matches(isDisplayed()))
        onView(withId(R.id.translation_id)).check(matches(isDisplayed()))
        onView(withId(R.id.list)).check(matches(isDisplayed()))

        scenario.close()
    }

    @Test
    fun testHomeActivityFeedbackDialog() {
        val scenario = ActivityScenario.launch(HomeActivity::class.java)

        testMainViewsInPlace(true)
        checkText(R.string.translations_welcome, true)

        onView(withId(R.id.action_more)).tryPerform(click())
        onView(withText(R.string.feedback)).tryPerform(click())

        checkDialogText(R.string.requires_internet, true)

        scenario.close()
    }

    @Test
    fun testHomeActivityLogout() {
        val scenario = ActivityScenario.launch(HomeActivity::class.java)

        testMainViewsInPlace(true)
        checkText(R.string.translations_welcome, true)

        assertNotNull(profile.fullName)

        onView(withId(R.id.action_more)).tryPerform(click())
        onView(withText(R.string.log_out)).tryPerform(click())

        assertNull(profile.fullName)

        scenario.close()
    }

    @Test
    fun testHomeActivityOpenSettings() {
        val scenario = ActivityScenario.launch(HomeActivity::class.java)

        intending(hasComponent(SettingsActivity::class.java.name)).respondWith(
            Instrumentation.ActivityResult(0, null)
        )

        testMainViewsInPlace(true)
        checkText(R.string.translations_welcome, true)

        onView(withId(R.id.action_more)).tryPerform(click())
        onView(withText(R.string.action_settings)).tryPerform(click())

        intended(hasComponent(SettingsActivity::class.java.name))

        scenario.close()
    }

    private fun testMainViewsInPlace(displayed: Boolean) {
        checkContainsText("TestUser", displayed)
        checkText(R.string.log_out, displayed)
        checkText(R.string.title_activity_target_translations, displayed)
    }

    private fun createTargetTranslation() {
        onView(withId(R.id.addTargetTranslationButton)).tryPerform(click())
        onView(withText("aaa")).tryPerform(click())
        onView(withText("bible-nt")).tryPerform(click())
        onView(withText("Mark")).tryPerform(click())
        pressBack()
        waitFor(5000)
    }
}