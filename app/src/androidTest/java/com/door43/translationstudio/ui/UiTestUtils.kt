package com.door43.translationstudio.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
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
import org.hamcrest.Matchers.not


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
            // Check if the view is still part of hierarchy and not visible
            try {
                interaction.check(matches(not(isDisplayed())))
            } catch (_: NoMatchingViewException) {
                // Check if it's not part of hierarchy
                interaction.check(doesNotExist())
            }
        }
    }

    fun clickItemWithId(id: Int): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View>? {
                return null
            }
            override fun getDescription(): String {
                return "Click on a child view with specified id."
            }
            override fun perform(uiController: UiController, view: View) {
                val v: View = view.findViewById(id)
                if (!v.performClick()) performTouchEvent(v)
            }
        }
    }

    /**
     * Simulate a touch event at the center of a view.
     */
    private fun performTouchEvent(view: View) {
        // Create an ACTION_DOWN MotionEvent
        val downTime = SystemClock.uptimeMillis()
        val eventTime = SystemClock.uptimeMillis()
        val x = view.width / 2f // X coordinate - center of the view
        val y = view.height / 2f // Y coordinate - center of the view
        val metaState = 0

        val motionEventDown = MotionEvent.obtain(
            downTime,
            eventTime,
            MotionEvent.ACTION_DOWN,
            x,
            y,
            metaState
        )

        val motionEventUp = MotionEvent.obtain(
            downTime,
            eventTime + 100,  // Simulate a slight delay for an up event
            MotionEvent.ACTION_UP,
            x,
            y,
            metaState
        )

        // Dispatch the events to the view
        view.dispatchTouchEvent(motionEventDown)
        view.dispatchTouchEvent(motionEventUp)

        // Recycle the MotionEvent objects
        motionEventDown.recycle()
        motionEventUp.recycle()
    }
}