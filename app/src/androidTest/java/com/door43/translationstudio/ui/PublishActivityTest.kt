package com.door43.translationstudio.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.R
import com.door43.translationstudio.TestUtils
import com.door43.translationstudio.core.ChapterTranslation
import com.door43.translationstudio.core.FrameTranslation
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.core.Translator.Companion.EXTRA_TARGET_TRANSLATION_ID
import com.door43.translationstudio.ui.UiTestUtils.checkDialogText
import com.door43.translationstudio.ui.UiTestUtils.checkRecyclerViewChild
import com.door43.translationstudio.ui.UiTestUtils.checkRecyclerViewHasItemsCount
import com.door43.translationstudio.ui.UiTestUtils.checkText
import com.door43.translationstudio.ui.UiTestUtils.clickItemWithId
import com.door43.translationstudio.ui.publish.PublishActivity
import com.door43.translationstudio.ui.publish.PublishActivity.Companion.ACTIVITY_HOME
import com.door43.translationstudio.ui.publish.PublishActivity.Companion.EXTRA_CALLING_ACTIVITY
import com.door43.usecases.ImportProjects
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.unfoldingword.door43client.Door43Client
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class PublishActivityTest {

    @Inject @ApplicationContext lateinit var context: Context
    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var library: Door43Client
    @Inject lateinit var importProjects: ImportProjects
    @Inject lateinit var profile: Profile
    @Inject lateinit var assetsProvider: AssetsProvider
    @Inject lateinit var translator: Translator

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
    fun testWrongTranslationLoaded() {
        val intent = Intent(context, PublishActivity::class.java)
        intent.putExtra(EXTRA_TARGET_TRANSLATION_ID, "1")
        intent.putExtra(EXTRA_CALLING_ACTIVITY, ACTIVITY_HOME)

        ActivityScenario.launch<PublishActivity>(intent).use { scenario ->
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        }
    }

    @Test
    fun testWrongCallingActivity() {
        val targetTranslation = getTargetTranslation()
        val intent = Intent(context, PublishActivity::class.java)
        intent.putExtra(EXTRA_TARGET_TRANSLATION_ID, targetTranslation.id)

        ActivityScenario.launch<PublishActivity>(intent).use { scenario ->
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        }
    }

    @Test
    fun testAllChunksInvalid() {
        val targetTranslation = getTargetTranslation()
        val intent = Intent(context, PublishActivity::class.java)
        intent.putExtra(EXTRA_TARGET_TRANSLATION_ID, targetTranslation.id)
        intent.putExtra(EXTRA_CALLING_ACTIVITY, ACTIVITY_HOME)

        ActivityScenario.launch<PublishActivity>(intent).use {
            checkText(R.string.title_book, true)
            checkText(R.string.translators, true)
            checkText(R.string.menu_upload_export, true)

            // 16th item is the "next" button
            checkRecyclerViewHasItemsCount(withId(R.id.validation_items), 16)
        }
    }

    @Test
    fun testValidateChunkFinishedActivity() {
        val targetTranslation = getTargetTranslation()
        val intent = Intent(context, PublishActivity::class.java)
        intent.putExtra(EXTRA_TARGET_TRANSLATION_ID, targetTranslation.id)
        intent.putExtra(EXTRA_CALLING_ACTIVITY, ACTIVITY_HOME)

        ActivityScenario.launch<PublishActivity>(intent).use { scenario ->
            onView(withId(R.id.validation_items)).tryPerform(
                actionOnItemAtPosition<ViewHolder>(4, clickItemWithId(R.id.review_button))
            )
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        }
    }

    @Test
    fun testBookTitleValid() {
        val targetTranslation = getTargetTranslation()
        val intent = Intent(context, PublishActivity::class.java)
        intent.putExtra(EXTRA_TARGET_TRANSLATION_ID, targetTranslation.id)
        intent.putExtra(EXTRA_CALLING_ACTIVITY, ACTIVITY_HOME)

        makeProjectTitleValid(targetTranslation)

        ActivityScenario.launch<PublishActivity>(intent).use {
            checkText(R.string.title_book, true)
            checkText(R.string.translators, true)
            checkText(R.string.menu_upload_export, true)

            checkRecyclerViewChild(withId(R.id.validation_items), withId(R.id.review_button), 1, false)
            checkRecyclerViewHasItemsCount(withId(R.id.validation_items), 15)

            val titleWarnings = context.getString(R.string.has_warnings, "Jude")
            checkText(titleWarnings, false)
            val chapterWarnings = context.getString(R.string.has_warnings, "Chapter 1")
            checkText(chapterWarnings, true)
        }
    }

    @Test
    fun testChapterTitleValid() {
        val targetTranslation = getTargetTranslation()
        val intent = Intent(context, PublishActivity::class.java)
        intent.putExtra(EXTRA_TARGET_TRANSLATION_ID, targetTranslation.id)
        intent.putExtra(EXTRA_CALLING_ACTIVITY, ACTIVITY_HOME)

        makeChapterTitleValid(targetTranslation)

        ActivityScenario.launch<PublishActivity>(intent).use {
            checkText(R.string.title_book, true)
            checkText(R.string.translators, true)
            checkText(R.string.menu_upload_export, true)

            checkRecyclerViewChild(withId(R.id.validation_items), withId(R.id.review_button), 1, true)
            checkRecyclerViewChild(withId(R.id.validation_items), withId(R.id.review_button), 4, false)
            checkRecyclerViewHasItemsCount(withId(R.id.validation_items), 15)

            val bookTitleWarnings = context.getString(R.string.has_warnings, "Jude")
            checkText(bookTitleWarnings, true)
            val chapterWarnings = context.getString(R.string.has_warnings, "Chapter 1")
            checkText(chapterWarnings, true)
        }
    }

    @Test
    fun testAllChunksValid() {
        val targetTranslation = getTargetTranslation()
        val intent = Intent(context, PublishActivity::class.java)
        intent.putExtra(EXTRA_TARGET_TRANSLATION_ID, targetTranslation.id)
        intent.putExtra(EXTRA_CALLING_ACTIVITY, ACTIVITY_HOME)

        val sourceTranslationId = targetTranslation.sourceTranslations.first()
        val sourceTranslation = library.open(sourceTranslationId)

        makeProjectTitleValid(targetTranslation)
        makeChapterTitleValid(targetTranslation)
        sourceTranslation.chunks("01").forEach {
            makeFrameValid(targetTranslation, it)
        }

        ActivityScenario.launch<PublishActivity>(intent).use {
            checkText(R.string.title_book, true)
            checkText(R.string.translators, true)
            checkText(R.string.menu_upload_export, true)

            checkRecyclerViewChild(withId(R.id.validation_items), withText(containsString("Jude")), 0, true)
            checkRecyclerViewHasItemsCount(withId(R.id.validation_items), 2)

            val bookWarnings = context.getString(R.string.has_warnings, "Jude")
            checkText(bookWarnings, false)
            val chapterWarnings = context.getString(R.string.has_warnings, "Chapter 1")
            checkText(chapterWarnings, false)
        }
    }

    @Test
    fun testShowContributorsEditor() {
        val targetTranslation = getTargetTranslation()
        val intent = Intent(context, PublishActivity::class.java)
        intent.putExtra(EXTRA_TARGET_TRANSLATION_ID, targetTranslation.id)
        intent.putExtra(EXTRA_CALLING_ACTIVITY, ACTIVITY_HOME)

        profile.fullName = "Test User"

        ActivityScenario.launch<PublishActivity>(intent).use {
            checkText(R.string.title_book, true)
            checkText(R.string.translators, true)
            checkText(R.string.menu_upload_export, true)

            onView(withText(R.string.translators)).tryPerform(click())

            checkText(R.string.names_will_be_public, true)
            checkText("Test User", true)
            checkText(R.string.add_contributor, true)

            onView(withText(R.string.add_contributor)).tryPerform(click())

            checkDialogText(R.string.view_license_agreement, true)
            checkDialogText(R.string.view_statement_of_faith, true)
            checkDialogText(R.string.view_translation_guidelines, true)
        }
    }

    @Test
    fun testShowUploadDialog() {
        val targetTranslation = getTargetTranslation()
        val intent = Intent(context, PublishActivity::class.java)
        intent.putExtra(EXTRA_TARGET_TRANSLATION_ID, targetTranslation.id)
        intent.putExtra(EXTRA_CALLING_ACTIVITY, ACTIVITY_HOME)

        profile.fullName = "Test User"

        ActivityScenario.launch<PublishActivity>(intent).use {
            checkText(R.string.title_book, true)
            checkText(R.string.translators, true)
            checkText(R.string.menu_upload_export, true)

            onView(withText(R.string.menu_upload_export)).tryPerform(click())

            checkDialogText(R.string.title_upload_export, true)
            checkDialogText(R.string.backup_to_door43, true)
            checkDialogText(R.string.export_to_usfm, true)
            checkDialogText(R.string.export_to_pdf, true)
            checkDialogText(R.string.backup_to_sd, true)
            checkDialogText(R.string.backup_to_friend, true)
            checkDialogText(R.string.backup_to_app, true)
        }
    }

    @Test
    fun testFirstChunkValid() {
        val targetTranslation = getTargetTranslation()
        val intent = Intent(context, PublishActivity::class.java)
        intent.putExtra(EXTRA_TARGET_TRANSLATION_ID, targetTranslation.id)
        intent.putExtra(EXTRA_CALLING_ACTIVITY, ACTIVITY_HOME)

        makeFrameValid(targetTranslation, "01")

        ActivityScenario.launch<PublishActivity>(intent).use {
            checkText(R.string.title_book, true)
            checkText(R.string.translators, true)
            checkText(R.string.menu_upload_export, true)

            checkRecyclerViewChild(withId(R.id.validation_items), withId(R.id.review_button), 4, false)
            checkRecyclerViewHasItemsCount(withId(R.id.validation_items), 16)
        }
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

    private fun makeProjectTitleValid(targetTranslation: TargetTranslation) {
        targetTranslation.applyProjectTitleTranslation("Test test test")
        targetTranslation.finishFrame("front", "title")
    }

    private fun makeChapterTitleValid(targetTranslation: TargetTranslation) {
        targetTranslation.applyChapterTitleTranslation(
            ChapterTranslation(
                "Chapter 1",
                "Ref",
                "01",
                true,
                true,
                TranslationFormat.USFM
            ),
            "Chapter 1"
        )
        targetTranslation.finishChapterTitle("01")
    }

    private fun makeFrameValid(targetTranslation: TargetTranslation, frameId: String) {
        targetTranslation.applyFrameTranslation(
            FrameTranslation(frameId, "01", "Test", TranslationFormat.USFM, true),
            "Test test test"
        )
        targetTranslation.finishFrame("01", frameId)
    }
}