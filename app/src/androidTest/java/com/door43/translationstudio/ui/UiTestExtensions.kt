package com.door43.translationstudio.ui

import android.util.Log
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.ViewInteraction

/**
 * Tries the number of [retries] to [ViewInteraction.check] assertion
 * @param viewAssert The assertion to check
 * @param retries The number of retries
 */
fun ViewInteraction.tryCheck(
    viewAssert: ViewAssertion,
    retries: Int = 5
): ViewInteraction {
    for (i in 1..retries) {
        try {
            return this.check(viewAssert)
        } catch (e: NoMatchingViewException) {
            Log.e("tryCheck", "Error checking assertion: $e")
        }
    }
    return this.check(viewAssert)
}

/**
 * Tries the number of [retries] to [ViewInteraction.perform] the action
 * @param viewAction The action to perform
 * @param retries The number of retries
 */
fun ViewInteraction.tryPerform(
    viewAction: ViewAction,
    retries: Int = 5
): ViewInteraction {
    for (i in 1..retries) {
        try {
            return this.perform(viewAction)
        } catch (e: NoMatchingViewException) {
            Log.e("tryPerform", "Error performing action: $e")
        }
    }
    return this.perform(viewAction)
}