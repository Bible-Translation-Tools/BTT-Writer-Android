package com.door43.translationstudio.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.GeneralLocation
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.translationstudio.R
import com.door43.translationstudio.UITest
import com.door43.translationstudio.ui.UiTestUtils.checkText
import com.door43.translationstudio.ui.UiTestUtils.waitFor
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
@UITest
class SettingsActivityTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun testShowContentServerAndScroll() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            verifyTopViewsInPlace(true)

            val onList = onView(withId(R.id.recycler_view))
            onList.tryPerform(slowSwipeUp())

            waitFor(3000)

            onView(withText(R.string.content_server)).tryPerform(click())

            checkText("WACS", true)
            checkText("DCS", true)

            onView(withText(R.string.title_cancel)).tryPerform(click())

            onList.tryPerform(swipeUp())

            verifyBottomViewsInPlace(true)
        }
    }

    private fun verifyTopViewsInPlace(displayed: Boolean) {
        checkText(R.string.device_name, displayed)
        checkText(R.string.pref_title_color_theme, displayed)
        checkText(R.string.pref_title_source_typeface, displayed)
        checkText(R.string.pref_title_translation_typeface, displayed)
        checkText(R.string.content_server, displayed)
    }

    private fun verifyBottomViewsInPlace(displayed: Boolean) {
        checkText(R.string.view_statement_of_faith, displayed)
        checkText(R.string.pref_title_check_hardware_requirements, displayed)
    }

    private fun slowSwipeUp(): ViewAction {
        return ViewActions.actionWithAssertions(
            GeneralSwipeAction(
                Swipe.SLOW,
                GeneralLocation.translate(
                    GeneralLocation.BOTTOM_CENTER,
                    0f,
                    -EDGE_FUZZ_FACTOR
                ),
                GeneralLocation.TOP_CENTER,
                Press.FINGER
            )
        )
    }

    private companion object {
        const val EDGE_FUZZ_FACTOR = 0.083f
    }
}