package com.door43.translationstudio.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.translationstudio.R
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class SettingsActivityTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun testSettingsActivityShowContentServerAndScroll() {
        val scenario = ActivityScenario.launch(SettingsActivity::class.java)

        testTopViewsInPlace(true)

        onView(withText(R.string.content_server)).perform(click())

        UiTestUtils.checkText("WACS", true)
        UiTestUtils.checkText("DCS", true)

        onView(withText(R.string.title_cancel)).perform(click())

        onView(withId(R.id.recycler_view)).perform(swipeUp())

        testBottomViewsInPlace(true)

        scenario.close()
    }

    private fun testTopViewsInPlace(displayed: Boolean) {
        UiTestUtils.checkText(R.string.device_name, displayed)
        UiTestUtils.checkText(R.string.pref_title_color_theme, displayed)
        UiTestUtils.checkText(R.string.pref_title_source_typeface, displayed)
        UiTestUtils.checkText(R.string.pref_title_translation_typeface, displayed)
        UiTestUtils.checkText(R.string.content_server, displayed)
    }

    private fun testBottomViewsInPlace(displayed: Boolean) {
        UiTestUtils.checkText(R.string.view_statement_of_faith, displayed)
        UiTestUtils.checkText(R.string.pref_title_check_hardware_requirements, displayed)
    }
}