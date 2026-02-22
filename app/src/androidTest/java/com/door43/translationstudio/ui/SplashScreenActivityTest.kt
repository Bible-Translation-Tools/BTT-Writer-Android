package com.door43.translationstudio.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.translationstudio.App
import com.door43.translationstudio.KoinAndroidTest
import com.door43.translationstudio.R
import com.door43.translationstudio.UITest
import com.door43.translationstudio.ui.UiTestUtils.checkDialogText
import com.door43.util.RuntimeWrapper
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@UITest
class SplashScreenActivityTest : KoinAndroidTest() {

    @Before
    fun setUp() {
        // Koin is already initialized via KoinTestApplication
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
            checkDialogText(R.string.slow_device, false)
        }
    }

    @Test
    fun testMigrateAppShowDialog() {
        every { RuntimeWrapper.availableProcessors() }
            .returns(App.MINIMUM_NUMBER_OF_PROCESSORS.toInt() + 1)
        every { RuntimeWrapper.maxMemory() }
            .returns(App.MINIMUM_REQUIRED_RAM + 100)

        ActivityScenario.launch(SplashScreenActivity::class.java).use {
            checkDialogText(R.string.migrate_from_old_app, true)
            checkDialogText(R.string.migrate_from_old_app_description, true)
        }
    }
}
