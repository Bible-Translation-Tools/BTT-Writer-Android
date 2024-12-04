package com.door43.translationstudio.ui

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject


@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class ProfileActivityTest {

    @Inject
    @ApplicationContext
    lateinit var appContext: Context

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var profile: Profile

    @Before
    fun setUp() {
        hiltRule.inject()
        Intents.init()
        mockkObject(App)


        // Stub all browser intents to prevent actual launching
        Intents.intending(IntentMatchers.hasAction(Intent.ACTION_VIEW))
            .respondWith(Instrumentation.ActivityResult(0, null))
    }

    @After
    fun tearDown() {
        unmockkAll()
        Intents.release()
    }

    @Test
    fun testProfileActivityCancel() {
        val scenario = ActivityScenario.launch(ProfileActivity::class.java)

        testMainViewsInPlace(true)

        onView(withText(R.string.title_cancel)).perform(click())

        scenario.close()
    }

    @Test
    fun testProfileActivityLoginWithServerHasInternet() {
        every { App.isNetworkAvailable }.returns(true)

        val scenario = ActivityScenario.launch(ProfileActivity::class.java)

        testMainViewsInPlace(true)

        onView(withText(R.string.login_doo43)).perform(click())

        testMainViewsInPlace(false)

        UiTestUtils.checkText(R.string.server_account, true)
        onView(withId(R.id.username)).check(matches(isDisplayed()))
        onView(withId(R.id.password)).check(matches(isDisplayed()))

        onView(withId(R.id.ok_button)).perform(click())

        //UiTestUtils.checkDialogTextState(R.string.logging_in, true)

        // Implicitly set a delay to wait for progress dialog to close
        onView(isRoot()).perform(UiTestUtils.waitFor(1000))

        UiTestUtils.checkDialogText(R.string.double_check_credentials, true)
        onView(withText(R.string.label_ok)).perform(click())
        UiTestUtils.checkDialogText(R.string.double_check_credentials, false)

        scenario.close()
    }

    @Test
    fun testProfileActivityLoginWithServerNoInternet() {
        every { App.isNetworkAvailable }.returns(false)

        val scenario = ActivityScenario.launch(ProfileActivity::class.java)

        testMainViewsInPlace(true)

        onView(withText(R.string.login_doo43)).perform(click())

        testMainViewsInPlace(false)

        UiTestUtils.checkText(R.string.server_account, true)
        onView(withId(R.id.username)).check(matches(isDisplayed()))
        onView(withId(R.id.password)).check(matches(isDisplayed()))

        onView(withId(R.id.ok_button)).perform(click())

        //UiTestUtils.checkDialogTextState(R.string.logging_in, true)

        // Implicitly set a delay to wait for progress dialog to close
        onView(isRoot()).perform(UiTestUtils.waitFor(1000))

        UiTestUtils.checkDialogText(R.string.internet_not_available, true)
        onView(withText(R.string.label_ok)).perform(click())
        UiTestUtils.checkDialogText(R.string.internet_not_available, false)

        scenario.close()
    }

    @Test
    fun testProfileActivityCreateLocalAccount() {
        val scenario = ActivityScenario.launch(ProfileActivity::class.java)

        testMainViewsInPlace(true)

        onView(withText(R.string.create_offline_profile)).perform(click())

        testMainViewsInPlace(false)

        UiTestUtils.checkText(R.string.names_will_be_public, true)
        onView(withId(R.id.full_name)).check(matches(isDisplayed()))

        onView(withId(R.id.ok_button)).perform(click())

        UiTestUtils.checkText(R.string.complete_required_fields, true)

        onView(withId(R.id.full_name)).perform(typeText("TestUser"))
        onView(withId(R.id.ok_button)).perform(click())
        UiTestUtils.checkDialogText(R.string.publishing_privacy_notice, true)
        onView(withText(R.string.label_continue)).perform(click())
        onView(withId(R.id.accept_terms_btn)).perform(click())

        assertEquals("TestUser", profile.fullName)

        scenario.close()
    }

    @Test
    fun testProfileActivityCreateServerAccount() {
        val scenario = ActivityScenario.launch(ProfileActivity::class.java)

        testMainViewsInPlace(true)

        onView(withText(R.string.register_door43)).perform(click())

        Intents.intended(IntentMatchers.hasAction(Intent.ACTION_VIEW))

        scenario.close()
    }

    private fun testMainViewsInPlace(displayed: Boolean) {
        UiTestUtils.checkText(R.string.create_account_title, displayed)
        UiTestUtils.checkText(R.string.login_doo43, displayed)
        UiTestUtils.checkText(R.string.register_door43, displayed)
        UiTestUtils.checkText(R.string.create_offline_profile, displayed)
    }
}