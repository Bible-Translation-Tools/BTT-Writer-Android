package com.door43.translationstudio.ui

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.pressKey
import androidx.test.espresso.action.ViewActions.swipeLeft
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.contrib.RecyclerViewActions.scrollTo
import androidx.test.espresso.contrib.RecyclerViewActions.scrollToPosition
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.R
import com.door43.translationstudio.TestUtils
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.core.Translator.Companion.EXTRA_START_WITH_MERGE_FILTER
import com.door43.translationstudio.core.Translator.Companion.EXTRA_TARGET_TRANSLATION_ID
import com.door43.translationstudio.core.Translator.Companion.EXTRA_VIEW_MODE
import com.door43.translationstudio.ui.UiTestUtils.checkContainsText
import com.door43.translationstudio.ui.UiTestUtils.checkDialogContainsText
import com.door43.translationstudio.ui.UiTestUtils.checkText
import com.door43.translationstudio.ui.UiTestUtils.clickItemWithId
import com.door43.translationstudio.ui.translate.TargetTranslationActivity
import com.door43.translationstudio.ui.translate.review.ReviewHolder
import com.door43.usecases.ImportProjects
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.MockKAnnotations
import io.mockk.unmockkAll
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.unfoldingword.door43client.Door43Client
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class TargetTranslationActivityTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject @ApplicationContext lateinit var context: Context
    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var library: Door43Client
    @Inject lateinit var importProjects: ImportProjects
    @Inject lateinit var profile: Profile
    @Inject lateinit var assetsProvider: AssetsProvider
    @Inject lateinit var translator: Translator

    @Before
    fun setUp() {
        hiltRule.inject()
        MockKAnnotations.init(this)
    }

    @After
    fun tearDown() {
        directoryProvider.clearCache()
        directoryProvider.deleteTranslations()
        unmockkAll()
    }

    @Test
    fun testWrongTranslationLoaded() {
        val intent = Intent(context, TargetTranslationActivity::class.java)
        intent.putExtra(EXTRA_TARGET_TRANSLATION_ID, "1")

        ActivityScenario.launch<TargetTranslationActivity>(intent).use { scenario ->
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        }
    }

    @Test
    fun testNoSource() {
        val targetTranslation = getTargetTranslationWithoutSource()

        val intent = Intent(context, TargetTranslationActivity::class.java)
        intent.putExtra(EXTRA_TARGET_TRANSLATION_ID, targetTranslation.id)

        ActivityScenario.launch<TargetTranslationActivity>(intent).use {
            checkText(R.string.choose_first_source_translation, true)
        }
    }

    @Test
    fun testSelectSource() {
        val targetTranslation = getTargetTranslationWithoutSource()

        val intent = Intent(context, TargetTranslationActivity::class.java)
        intent.putExtra(EXTRA_TARGET_TRANSLATION_ID, targetTranslation.id)

        ActivityScenario.launch<TargetTranslationActivity>(intent).use {
            checkText(R.string.choose_first_source_translation, true)

            // Don't select any source translation
            onView(withId(R.id.secondaryNewTabButton)).tryPerform(click())
            onView(withHint(R.string.choose_source_translations))
                .inRoot(isDialog())
                .tryCheck(matches(isDisplayed()))
            onView(withId(R.id.cancelButton))
                .inRoot(isDialog())
                .tryPerform(click())
            checkText(R.string.choose_first_source_translation, true)

            // Now try to select a source
            addSourceTranslation()
            checkText("Jude", true)
            checkText("Chapter 1", true)

            //waitFor(100000)
        }
    }

    @Test
    fun testReadMode() {
        val targetTranslation = getTargetTranslationWithSource()

        val intent = Intent(context, TargetTranslationActivity::class.java)
        intent.putExtra(EXTRA_TARGET_TRANSLATION_ID, targetTranslation.id)

        ActivityScenario.launch<TargetTranslationActivity>(intent).use {
            checkText("Jude", true)
            checkText("Chapter 1", true)

            onView(withText("Jude")).tryPerform(swipeLeft())
            checkContainsText("Jude - Af", true)
            val withButton = matches(hasDescendant(withId(R.id.begin_translating_button)))
            onView(withId(R.id.recycler_view)).tryCheck(withButton)
        }
    }

    @Test
    fun testChunkMode() {
        val targetTranslation = getTargetTranslationWithSource()

        val intent = Intent(context, TargetTranslationActivity::class.java)
        intent.putExtra(EXTRA_TARGET_TRANSLATION_ID, targetTranslation.id)

        ActivityScenario.launch<TargetTranslationActivity>(intent).use {
            checkText("Jude", true)
            checkText("Chapter 1", true)

            onView(withId(R.id.action_chunk)).tryPerform(click())

            val onList = onView(withId(R.id.recycler_view))

            onList.tryPerform(scrollToPosition<ViewHolder>(2))
            onList.tryPerform(actionOnItemAtPosition<ViewHolder>(2, swipeLeft()))
            onList.tryPerform(
                actionOnItemAtPosition<ReviewHolder>(
                    2,
                    typeText("Test test test")
                )
            )

            onList.tryPerform(scrollToPosition<ViewHolder>(12))
            onList.tryPerform(actionOnItemAtPosition<ViewHolder>(12, swipeLeft()))
            onList.tryPerform(scrollToPosition<ViewHolder>(2))
            checkContainsText("Test test test", true)
        }
    }

    @Test
    fun testReviewMode() {
        val targetTranslation = getTargetTranslationWithSource()

        val intent = Intent(context, TargetTranslationActivity::class.java)
        intent.putExtra(EXTRA_TARGET_TRANSLATION_ID, targetTranslation.id)

        ActivityScenario.launch<TargetTranslationActivity>(intent).use {
            checkText("Jude", true)
            checkText("Chapter 1", true)

            onView(withId(R.id.action_review)).tryPerform(click())

            val onList = onView(withId(R.id.recycler_view))

            onList.tryPerform(scrollToPosition<ViewHolder>(2))
            onList.tryCheck(matches(hasDescendant(allOf(
                withId(R.id.target_translation_editable_body),
                not(isDisplayed())
            ))))
            onList.tryPerform(
                actionOnItemAtPosition<ReviewHolder>(
                    2,
                    clickItemWithId(R.id.edit_translation_button)
                )
            )
            onList.tryCheck(matches(hasDescendant(allOf(
                withId(R.id.target_translation_editable_body),
                isDisplayed()
            ))))
            onList.perform(pressKey(KeyEvent.KEYCODE_MOVE_END))
            onList.tryPerform(
                actionOnItemAtPosition<ReviewHolder>(
                    2,
                    typeText("Test test test")
                )
            )
            onList.tryPerform(
                actionOnItemAtPosition<ReviewHolder>(
                    2,
                    clickItemWithId(R.id.edit_translation_button)
                )
            )
            onList.tryCheck(matches(hasDescendant(allOf(
                withId(R.id.target_translation_editable_body),
                not(isDisplayed())
            ))))
            onList.tryCheck(matches(hasDescendant(
                withText(containsString("Test test test"))
            )))
            onList.tryPerform(scrollToPosition<ViewHolder>(12))
            onList.tryPerform(scrollTo<ViewHolder>(hasDescendant(withText(containsString("Test test test")))))
        }
    }

    @Test
    fun testReviewModeMarkDoneCancel() {
        val targetTranslation = getTargetTranslationWithSource()

        val intent = Intent(context, TargetTranslationActivity::class.java)
        intent.putExtra(EXTRA_TARGET_TRANSLATION_ID, targetTranslation.id)
        intent.putExtra(EXTRA_VIEW_MODE, TranslationViewMode.REVIEW.ordinal)

        ActivityScenario.launch<TargetTranslationActivity>(intent).use { scenario ->
            checkText("Jude", true)
            checkText("Chapter 1", true)

            val onList = onView(withId(R.id.recycler_view))

            onList.tryPerform(scrollToPosition<ViewHolder>(2))
            onList.tryPerform(
                actionOnItemAtPosition<ReviewHolder>(
                    2,
                    clickItemWithId(R.id.done_button)
                )
            )
            val text = "Are you sure you are done with this chunk?"
            checkDialogContainsText(text, true)
            onView(withText(R.string.title_cancel)).inRoot(isDialog())
                .tryPerform(click())
            scenario.onActivity {
                val btn = it.findViewById<SwitchCompat>(R.id.done_button)
                assertFalse(btn.isChecked)
            }
        }
    }

    @Test
    fun testReviewModeMarkDoneConfirmValid() {
        val targetTranslation = getTargetTranslationWithSource()

        val intent = Intent(context, TargetTranslationActivity::class.java)
        intent.putExtra(EXTRA_TARGET_TRANSLATION_ID, targetTranslation.id)
        intent.putExtra(EXTRA_VIEW_MODE, TranslationViewMode.REVIEW.ordinal)

        ActivityScenario.launch<TargetTranslationActivity>(intent).use { scenario ->
            checkText("Jude", true)
            checkText("Chapter 1", true)

            val onList = onView(withId(R.id.recycler_view))

            onList.tryPerform(scrollToPosition<ViewHolder>(2))
            onList.tryPerform(
                actionOnItemAtPosition<ReviewHolder>(
                    2,
                    clickItemWithId(R.id.edit_translation_button)
                )
            )
            onList.tryPerform(
                actionOnItemAtPosition<ReviewHolder>(
                    2,
                    typeText("\\v 1Test \\v 2Test")
                )
            )
            onList.tryPerform(
                actionOnItemAtPosition<ReviewHolder>(
                    2,
                    clickItemWithId(R.id.edit_translation_button)
                )
            )
            onList.tryPerform(
                actionOnItemAtPosition<ReviewHolder>(
                    2,
                    clickItemWithId(R.id.done_button)
                )
            )
            val text = "Are you sure you are done with this chunk?"
            checkDialogContainsText(text, true)

            onView(withText(R.string.confirm)).inRoot(isDialog()).tryPerform(click())

            scenario.onActivity {
                val btn = it.findViewById<SwitchCompat>(R.id.done_button)
                assertTrue(btn.isChecked)
            }
        }
    }

    @Test
    fun testConflictMode() {
        val targetTranslation = getTargetTranslationWithSource()

        val intent = Intent(context, TargetTranslationActivity::class.java)
        intent.putExtra(EXTRA_TARGET_TRANSLATION_ID, targetTranslation.id)
        intent.putExtra(EXTRA_VIEW_MODE, TranslationViewMode.REVIEW.ordinal)
        intent.putExtra(EXTRA_START_WITH_MERGE_FILTER, true)

        ActivityScenario.launch<TargetTranslationActivity>(intent).use {
            checkText("Jude", true)
            checkText("Chapter 1", true)

            onView(withId(R.id.warn_merge_conflict)).tryCheck(matches(isDisplayed()))
        }
    }

    private fun getTargetTranslationWithSource(): TargetTranslation {
        return TestUtils.importTargetTranslation(
            importProjects,
            translator,
            assetsProvider,
            directoryProvider,
            "exports/aa_jud_text_reg.tstudio"
        )!!
    }

    private fun getTargetTranslationWithoutSource(): TargetTranslation {
        return TestUtils.importTargetTranslation(
            library,
            context,
            directoryProvider,
            profile,
            assetsProvider,
            importProjects,
            translator,
            "aa",
            "usfm/66-JUD.usfm"
        )!!
    }

    private fun addSourceTranslation() {
        onView(withId(R.id.secondaryNewTabButton)).tryPerform(click())
        onView(withText("English (en) - Unlocked Literal Bible"))
            .inRoot(isDialog())
            .tryPerform(click())
        onView(withId(R.id.confirmButton)).tryPerform(click())
    }
}