package com.door43.translationstudio.ui

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.UiTestUtils.checkDialogText
import com.door43.translationstudio.ui.UiTestUtils.checkText
import com.door43.translationstudio.ui.devtools.DeveloperToolsActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class DeveloperToolsActivityTest {

    @ApplicationContext @Inject lateinit var context: Context

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun testAllOptionShown() {
        ActivityScenario.launch(DeveloperToolsActivity::class.java).use {
            onView(withId(R.id.appVersionText)).tryCheck(matches(isDisplayed()))
            onView(withId(R.id.appBuildNumberText)).tryCheck(matches(isDisplayed()))
            onView(withId(R.id.deviceUDIDText)).tryCheck(matches(isDisplayed()))

            checkText(R.string.regenerate_ssh_keys, true)
            checkText(R.string.regenerate_ssh_keys_hint, true)
            checkText(R.string.read_debug_log, true)
            checkText(R.string.read_debug_log_hint, true)
            checkText(R.string.simulate_crash, true)
            checkText(R.string.check_system_resources, true)
            checkText(R.string.check_system_resources_hint, true)
            checkText(R.string.delete_library, true)
            checkText(R.string.delete_library_hint, true)
        }
    }

    @Test
    fun testRegenerateShhKeys() {
        ActivityScenario.launch(DeveloperToolsActivity::class.java).use {
            onView(withText(R.string.regenerate_ssh_keys)).tryPerform(click())
        }
    }

    @Test
    fun testReadDebugLog() {
        ActivityScenario.launch(DeveloperToolsActivity::class.java).use {
            onView(withText(R.string.read_debug_log)).tryPerform(click())
        }
    }

    @Test
    fun testCheckSystemResources() {
        ActivityScenario.launch(DeveloperToolsActivity::class.java).use {
            onView(withText(R.string.check_system_resources)).tryPerform(click())
            checkDialogText(R.string.system_resources_check, true)
        }
    }

    @Test
    fun testDeleteLibrary() {
        ActivityScenario.launch(DeveloperToolsActivity::class.java).use {
            onView(withText(R.string.delete_library)).tryPerform(click())
            checkText(R.string.library_was_deleted, true)
        }
    }

    @Test
    fun testSimulateCrash() {
        val scenario = ActivityScenario.launch(DeveloperToolsActivity::class.java)

        val message = context.getString(R.string.simulating_crash)
        // Using broad exception here because espresso intercepts all exceptions
        // and re-throws its own exceptions
        assertThrows(message, Exception::class.java) {
            onView(withText(R.string.simulate_crash)).tryPerform(click())
        }
        scenario.close()
    }
}