package com.door43.translationstudio.ui

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.translationstudio.KoinAndroidTest
import com.door43.translationstudio.R
import com.door43.translationstudio.UITest
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.ui.UiTestUtils.checkText
import com.door43.translationstudio.ui.profile.TermsOfUseActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.inject

@RunWith(AndroidJUnit4::class)
@UITest
class TermsOfUseActivityTest : KoinAndroidTest() {

    private val appContext: Context by inject()
    private val profile: Profile by inject()

    @Before
    fun setUp() {
        // Koin is already initialized via KoinTestApplication
        // This will make sure that profile is logged in
        profile.fullName = "Test"
    }

    @Test
    fun testAgreed() {
        assertEquals(0, profile.termsOfUseLastAccepted)

        ActivityScenario.launch(TermsOfUseActivity::class.java).use {
            verifyMainViewsInPlace(true)
            onView(withText(R.string.license_accept)).tryPerform(click())
            verifyMainViewsInPlace(false)

            val termsVersion = appContext.resources.getInteger(R.integer.terms_of_use_version)
            assertEquals(termsVersion, profile.termsOfUseLastAccepted)
        }
    }

    @Test
    fun testDenied() {
        assertTrue(profile.loggedIn)

        ActivityScenario.launch(TermsOfUseActivity::class.java).use {
            verifyMainViewsInPlace(true)
            onView(withText(R.string.license_deny)).tryPerform(click())

            assertFalse(profile.loggedIn)
        }
    }

    @Test
    fun testProfileLoggedOut() {
        profile.fullName = ""

        val scenario = ActivityScenario.launch(TermsOfUseActivity::class.java)
        Thread.sleep(3000)
        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
    }

    @Test
    fun testAlreadyAccepted() {
        val termsVersion = appContext.resources.getInteger(R.integer.terms_of_use_version)
        profile.termsOfUseLastAccepted = termsVersion

        ActivityScenario.launch(TermsOfUseActivity::class.java).use {
            verifyMainViewsInPlace(false)
        }
    }

    @Test
    fun testShowLicenceDialog() {
        // TODO Bring back when migrated to compose
//        ActivityScenario.launch(TermsOfUseActivity::class.java).use {
//            verifyMainViewsInPlace(true)
//
//            onView(withText(R.string.view_license_agreement)).tryPerform(click())
//
//            checkText(R.string.label_close, true)
//            onView(withId(R.id.license_text)).tryCheck(matches(isDisplayed()))
//        }
    }

    @Test
    fun testShowGuidesDialog() {
        // TODO Bring back when migrated to compose
//        ActivityScenario.launch(TermsOfUseActivity::class.java).use {
//            verifyMainViewsInPlace(true)
//
//            onView(withText(R.string.view_translation_guidelines)).tryPerform(click())
//
//            checkText(R.string.label_close, true)
//            onView(withId(R.id.license_text)).tryCheck(matches(isDisplayed()))
//        }
    }

    @Test
    fun testShowStatementOfFaithDialog() {
        // TODO Bring back when migrated to compose
//        ActivityScenario.launch(TermsOfUseActivity::class.java).use {
//            verifyMainViewsInPlace(true)
//
//            onView(withText(R.string.view_statement_of_faith)).tryPerform(click())
//
//            checkText(R.string.label_close, true)
//            onView(withId(R.id.license_text)).tryCheck(matches(isDisplayed()))
//        }
    }

    private fun verifyMainViewsInPlace(displayed: Boolean) {
        checkText(R.string.terms_title, displayed)
        checkText(R.string.terms, displayed)
        checkText(R.string.view_license_agreement, displayed)
        checkText(R.string.view_translation_guidelines, displayed)
        checkText(R.string.view_statement_of_faith, displayed)
    }
}
