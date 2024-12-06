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
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.ui.UiTestUtils.checkDialogText
import com.door43.translationstudio.ui.UiTestUtils.checkText
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
        intending(hasAction(Intent.ACTION_VIEW))
            .respondWith(Instrumentation.ActivityResult(0, null))
    }

    @After
    fun tearDown() {
        unmockkAll()
        Intents.release()
    }

    @Test
    fun testProfileActivityCancel() {
        ActivityScenario.launch(ProfileActivity::class.java).use {
            testMainViewsInPlace(true)
            onView(withText(R.string.title_cancel)).tryPerform(click())
        }
    }

    @Test
    fun testProfileActivityLoginWithServerHasInternet() {
        every { App.isNetworkAvailable }.returns(true)

        ActivityScenario.launch(ProfileActivity::class.java).use {
            testMainViewsInPlace(true)

            onView(withText(R.string.login_doo43)).tryPerform(click())

            testMainViewsInPlace(false)

            checkText(R.string.server_account, true)
            onView(withId(R.id.username)).tryCheck(matches(isDisplayed()))
            onView(withId(R.id.password)).tryCheck(matches(isDisplayed()))

            onView(withId(R.id.ok_button)).tryPerform(click())

            checkDialogText(R.string.double_check_credentials, true)
            onView(withText(R.string.label_ok)).tryPerform(click())
            checkDialogText(R.string.double_check_credentials, false)
        }
    }

    @Test
    fun testProfileActivityLoginWithServerNoInternet() {
        every { App.isNetworkAvailable }.returns(false)

        ActivityScenario.launch(ProfileActivity::class.java).use {
            testMainViewsInPlace(true)

            onView(withText(R.string.login_doo43)).tryPerform(click())

            testMainViewsInPlace(false)

            checkText(R.string.server_account, true)
            onView(withId(R.id.username)).tryCheck(matches(isDisplayed()))
            onView(withId(R.id.password)).tryCheck(matches(isDisplayed()))

            onView(withId(R.id.ok_button)).tryPerform(click())

            checkDialogText(R.string.internet_not_available, true)
            onView(withText(R.string.label_ok)).tryPerform(click())
            checkDialogText(R.string.internet_not_available, false)
        }
    }

    @Test
    fun testProfileActivityCreateLocalAccount() {
        ActivityScenario.launch(ProfileActivity::class.java).use {
            testMainViewsInPlace(true)

            onView(withText(R.string.create_offline_profile)).tryPerform(click())

            testMainViewsInPlace(false)

            checkText(R.string.names_will_be_public, true)
            onView(withId(R.id.full_name)).tryCheck(matches(isDisplayed()))

            onView(withId(R.id.ok_button)).tryPerform(click())

            checkText(R.string.complete_required_fields, true)

            onView(withId(R.id.full_name)).tryPerform(typeText("TestUser"))
            onView(withId(R.id.ok_button)).tryPerform(click())
            checkDialogText(R.string.publishing_privacy_notice, true)
            onView(withText(R.string.label_continue)).tryPerform(click())
            onView(withId(R.id.accept_terms_btn)).tryPerform(click())

            assertEquals("TestUser", profile.fullName)
        }
    }

    @Test
    fun testProfileActivityCreateServerAccount() {
        ActivityScenario.launch(ProfileActivity::class.java).use {
            testMainViewsInPlace(true)
            onView(withText(R.string.register_door43)).tryPerform(click())
            intended(hasAction(Intent.ACTION_VIEW))
        }
    }

    private fun testMainViewsInPlace(displayed: Boolean) {
        checkText(R.string.create_account_title, displayed)
        checkText(R.string.login_doo43, displayed)
        checkText(R.string.register_door43, displayed)
        checkText(R.string.create_offline_profile, displayed)
    }
}