package com.door43.translationstudio.ui

import android.app.Activity
import android.content.pm.ActivityInfo
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
import androidx.test.espresso.util.TreeIterables
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
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
        val interaction = if (displayed) {
            waitForView(withText(resource))
        } else {
            onView(withText(resource))
        }
        checkState(interaction, displayed)
    }

    /**
     * make sure text is displayed or not displayed in view
     * @param text
     * @param displayed
     */
    fun checkText(text: String, displayed: Boolean) {
        val interaction = if (displayed) {
            waitForView(withText(text))
        } else {
            onView(withText(text))
        }
        checkState(interaction, displayed)
    }

    fun checkContainsText(text: String, displayed: Boolean) {
        val interaction = if (displayed) {
            waitForView(withText(containsStringIgnoringCase(text)))
        } else {
            onView(withText(containsStringIgnoringCase(text)))
        }
        checkState(interaction, displayed)
    }

    fun checkDialogText(resource: Int, displayed: Boolean) {
        val interaction = if (displayed) {
            waitForView(withText(resource))
        } else {
            onView(withText(resource))
        }
        if (displayed) {
            interaction.inRoot(isDialog())
        }
        checkState(interaction, displayed)
    }

    fun checkDialogText(text: String, displayed: Boolean) {
        val interaction = if (displayed) {
            waitForView(withText(text))
        } else {
            onView(withText(text))
        }
        interaction.inRoot(isDialog())
        checkState(interaction, displayed)
    }

    fun checkDialogContainsText(text: String, displayed: Boolean) {
        val interaction = if (displayed) {
            waitForView(withText(containsStringIgnoringCase(text)))
        } else {
            onView(withText(containsStringIgnoringCase(text)))
        }
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
        onView(isRoot()).perform(action)
    }

    fun onWaitForView(matcher: Matcher<View>): ViewInteraction {
        return waitForView(matcher)
    }

    private fun checkState(interaction: ViewInteraction, displayed: Boolean) {
        val displayState = if (displayed) {
            matches(isDisplayed())
        } else {
            doesNotExist()
        }
        interaction.check(displayState)
    }

    /**
     * Tries to find a view with given [viewMatchers]. If found, it returns the
     * [ViewInteraction] for given [viewMatchers]. If not found, it waits for given [wait]
     * before attempting to find the view again. It retries for given number of [retries].
     *
     */
    private fun waitForView(
        vararg viewMatchers: Matcher<View>,
        retries: Int = 5,
        wait: Long = 1000L,
    ): ViewInteraction {
        require(retries > 0 && wait > 0)
        val viewMatcher = allOf(*viewMatchers)
        for (i in 0 until retries) {
            try {
                onView(isRoot()).perform(searchForView(viewMatcher))
                break
            } catch (e: NoMatchingViewException) {
                Thread.sleep(wait)
            }
        }
        return onView(viewMatcher)
    }

    private fun searchForView(viewMatcher: Matcher<View>): ViewAction {
        return object : ViewAction {
            override fun getConstraints() = isRoot()
            override fun getDescription() = "search for view with $viewMatcher in the root view"
            override fun perform(uiController: UiController, view: View) {
                TreeIterables.breadthFirstViewTraversal(view).forEach {
                    if (viewMatcher.matches(it)) {
                        return
                    }
                }
                throw NoMatchingViewException.Builder()
                    .withRootView(view)
                    .withViewMatcher(viewMatcher)
                    .build()
            }
        }
    }
}