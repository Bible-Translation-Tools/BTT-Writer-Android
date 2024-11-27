package com.door43.translationstudio.ui

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import com.door43.util.RuntimeWrapper
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
@LargeTest
@HiltAndroidTest
class SplashScreenActivityTest {

    @Inject
    @ApplicationContext
    lateinit var appContext: Context

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Before
    fun setUp() {
        hiltRule.inject()
        MockKAnnotations.init(this)

        mockkObject(RuntimeWrapper)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testSlowDeviceShowsDialog() {
        every { RuntimeWrapper.availableProcessors() }
            .returns(App.MINIMUM_NUMBER_OF_PROCESSORS.toInt() - 1)
        every { RuntimeWrapper.maxMemory() }
            .returns(App.MINIMUM_REQUIRED_RAM - 100)

        val scenario = ActivityScenario.launch(SplashScreenActivity::class.java)
        scenario.moveToState(Lifecycle.State.RESUMED)

        UiTestUtils.checkDialogTextState(R.string.slow_device, true)
        onView(withText(R.string.label_continue)).perform(click())

        UiTestUtils.checkDialogTextState(R.string.slow_device, false)
        UiTestUtils.checkTextState(R.string.welcome, true)
        UiTestUtils.checkTextState(R.string.updating_app, true)

        scenario.close()
    }

    @Test
    fun testFastDeviceDoesNotShowDialog() {
        every { RuntimeWrapper.availableProcessors() }
            .returns(App.MINIMUM_NUMBER_OF_PROCESSORS.toInt() + 1)
        every { RuntimeWrapper.maxMemory() }
            .returns(App.MINIMUM_REQUIRED_RAM + 100)

        val scenario = ActivityScenario.launch(SplashScreenActivity::class.java)
        scenario.moveToState(Lifecycle.State.RESUMED)

        UiTestUtils.checkDialogTextState(R.string.slow_device, false)
        UiTestUtils.checkTextState(R.string.welcome, true)
        UiTestUtils.checkTextState(R.string.updating_app, true)

        scenario.close()
    }
}