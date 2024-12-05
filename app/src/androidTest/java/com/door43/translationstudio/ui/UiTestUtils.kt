package com.door43.translationstudio.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import org.hamcrest.Matcher
import org.hamcrest.Matchers.containsStringIgnoringCase


object UiTestUtils {

    /**
     * Force page orientation change
     */
    fun <T: Activity> rotateScreen(scenario: ActivityScenario<T>) {
        scenario.onActivity {
            if (it.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        }
    }

    /**
     * Dismiss system dialog that shows busyness of an app
     */
    fun dismissANRSystemDialog() {
        val device = UiDevice.getInstance(getInstrumentation())
        // If running the device in English Locale
        val waitButton = device.findObject(UiSelector().textContains("wait"))
        if (waitButton.exists()) {
            waitButton.click()
        }
    }

    /**
     * make sure text is displayed or not displayed in view
     * @param resource
     * @param displayed
     */
    fun checkText(resource: Int, displayed: Boolean) {
        val interaction = onView(withText(resource))
        checkState(interaction, displayed)
    }

    /**
     * make sure text is displayed or not displayed in view
     * @param text
     * @param displayed
     */
    fun checkText(text: String, displayed: Boolean) {
        val interaction = onView(withText(text))
        checkState(interaction, displayed)
    }

    fun checkContainsText(text: String, displayed: Boolean) {
        val interaction = onView(withText(containsStringIgnoringCase(text)))
        checkState(interaction, displayed)
    }

    fun checkDialogText(resource: Int, displayed: Boolean) {
        val interaction = onView(withText(resource))
        if (displayed) {
            interaction.inRoot(isDialog())
        }
        checkState(interaction, displayed)
    }

    fun checkDialogText(text: String, displayed: Boolean) {
        val interaction = onView(withText(text))
        interaction.inRoot(isDialog())
        checkState(interaction, displayed)
    }

    fun checkDialogContainsText(text: String, displayed: Boolean) {
        val interaction = onView(withText(containsStringIgnoringCase(text)))
        interaction.inRoot(isDialog())
        checkState(interaction, displayed)
    }

    fun waitFor(delay: Long) {
        val action = object : ViewAction {
            override fun getConstraints(): Matcher<View> {
                return isRoot()
            }
            override fun getDescription(): String {
                return "wait for " + delay + "milliseconds"
            }
            override fun perform(uiController: UiController, view: View?) {
                uiController.loopMainThreadForAtLeast(delay)
            }
        }
        onView(isRoot()).tryPerform(action)
    }

    private fun checkState(interaction: ViewInteraction, displayed: Boolean) {
        if (displayed) {
            interaction.tryCheck(matches(isDisplayed()))
        } else {
            interaction.check(doesNotExist())
        }
    }
}