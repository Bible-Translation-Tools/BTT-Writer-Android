package com.door43.translationstudio.ui

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.UiTestUtils.checkDialogText
import com.door43.translationstudio.ui.UiTestUtils.checkText
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

        ActivityScenario.launch(SplashScreenActivity::class.java).use {
            checkDialogText(R.string.slow_device, true)
            onView(withText(R.string.label_continue)).tryPerform(click())

            //checkText(R.string.welcome, true)
            checkDialogText(R.string.slow_device, false)
        }
    }

    @Test
    fun testFastDeviceDoesNotShowDialog() {
        every { RuntimeWrapper.availableProcessors() }
            .returns(App.MINIMUM_NUMBER_OF_PROCESSORS.toInt() + 1)
        every { RuntimeWrapper.maxMemory() }
            .returns(App.MINIMUM_REQUIRED_RAM + 100)

        ActivityScenario.launch(SplashScreenActivity::class.java).use {
            //checkText(R.string.welcome, true)
            checkText(R.string.updating_app, true)
            checkDialogText(R.string.slow_device, false)
        }
    }
}