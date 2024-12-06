package com.door43.translationstudio.ui

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
        } catch (_: Throwable) {
        }
    }
    return this.check(viewAssert)
}

/**
 * Tries the number of [retries] to [ViewInteraction.perform] the action
 * @param viewActions The actions to perform
 * @param retries The number of retries
 */
fun ViewInteraction.tryPerform(
    vararg viewActions: ViewAction,
    retries: Int = 5
): ViewInteraction {
    for (i in 1..retries) {
        try {
            return this.perform(*viewActions)
        } catch (_: Throwable) {}
    }
    return this.perform(*viewActions)
}