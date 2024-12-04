package com.door43.translationstudio.ui

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.ui.UiTestUtils.checkText
import com.door43.translationstudio.ui.UiTestUtils.onWaitForView
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class TermsOfUseActivityTest {

    @Inject
    @ApplicationContext
    lateinit var appContext: Context

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject lateinit var profile: Profile

    @Before
    fun setUp() {
        hiltRule.inject()

        // This will make sure that profile is logged in
        profile.fullName = "Test"
    }

    @Test
    fun testTermsOfUseActivityAgreed() {
        assertEquals(0, profile.termsOfUseLastAccepted)

        val scenario = ActivityScenario.launch(TermsOfUseActivity::class.java)

        testMainViewsInPlace(true)
        onWaitForView(withText(R.string.license_accept)).perform(click())
        testMainViewsInPlace(false)

        val termsVersion = appContext.resources.getInteger(R.integer.terms_of_use_version)
        assertEquals(termsVersion, profile.termsOfUseLastAccepted)

        scenario.close()
    }

    @Test
    fun testTermsOfUseActivityDenied() {
        assertTrue(profile.loggedIn)

        val scenario = ActivityScenario.launch(TermsOfUseActivity::class.java)

        testMainViewsInPlace(true)
        onWaitForView(withText(R.string.license_deny)).perform(click())
        testMainViewsInPlace(false)

        assertFalse(profile.loggedIn)

        scenario.close()
    }

    @Test
    fun testTermsOfUseActivityProfileLoggedOut() {
        profile.fullName = null

        val scenario = ActivityScenario.launch(TermsOfUseActivity::class.java)

        assertEquals(Lifecycle.State.DESTROYED, scenario.state)

        scenario.close()
    }

    @Test
    fun testTermsOfUseActivityAlreadyAccepted() {
        val termsVersion = appContext.resources.getInteger(R.integer.terms_of_use_version)
        profile.termsOfUseLastAccepted = termsVersion

        val scenario = ActivityScenario.launch(TermsOfUseActivity::class.java)

        testMainViewsInPlace(false)

        scenario.close()
    }

    @Test
    fun testTermsOfUseActivityShowLicenceDialog() {
        val scenario = ActivityScenario.launch(TermsOfUseActivity::class.java)

        testMainViewsInPlace(true)

        onWaitForView(withText(R.string.view_license_agreement)).perform(click())

        checkText(R.string.label_close, true)
        onWaitForView(withId(R.id.license_text)).check(matches(isDisplayed()))

        scenario.close()
    }

    @Test
    fun testTermsOfUseActivityShowGuidesDialog() {
        val scenario = ActivityScenario.launch(TermsOfUseActivity::class.java)

        testMainViewsInPlace(true)

        onWaitForView(withText(R.string.view_translation_guidelines)).perform(click())

        checkText(R.string.label_close, true)
        onWaitForView(withId(R.id.license_text)).check(matches(isDisplayed()))

        scenario.close()
    }

    @Test
    fun testTermsOfUseActivityShowStatementOfFaithDialog() {
        val scenario = ActivityScenario.launch(TermsOfUseActivity::class.java)

        testMainViewsInPlace(true)

        onWaitForView(withText(R.string.view_statement_of_faith)).perform(click())

        checkText(R.string.label_close, true)
        onWaitForView(withId(R.id.license_text)).check(matches(isDisplayed()))

        scenario.close()
    }

    private fun testMainViewsInPlace(displayed: Boolean) {
        checkText(R.string.terms_title, displayed)
        checkText(R.string.terms, displayed)
        checkText(R.string.view_license_agreement, displayed)
        checkText(R.string.view_translation_guidelines, displayed)
        checkText(R.string.view_statement_of_faith, displayed)
    }
}