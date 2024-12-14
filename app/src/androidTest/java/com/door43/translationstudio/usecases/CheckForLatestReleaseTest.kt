package com.door43.translationstudio.usecases

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.translationstudio.IntegrationTest
import com.door43.usecases.CheckForLatestRelease
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@IntegrationTest
class CheckForLatestReleaseTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject
    @ApplicationContext
    lateinit var appContext: Context
    @Inject
    lateinit var checkForLatestRelease: CheckForLatestRelease

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun checkForLatestRelease() {
        val result = checkForLatestRelease.execute()

        // Run assertions if there is a new release
        if (result.release != null) {
            val pInfo = appContext.packageManager.getPackageInfo(
                appContext.packageName, 0
            )

            assertFalse(
                "Release name should not be empty",
                result.release!!.name.isEmpty()
            )
            assertTrue(
                "Release file should be .apk",
                result.release!!.downloadUrl.endsWith(".apk")
            )
            assertTrue(
                "Release build should be greater than 0",
                result.release!!.build > 0
            )
            assertTrue(
                "Release build should be greater than current version",
                result.release!!.build > pInfo.versionCode
            )
            assertTrue(
                "Release size should be greater than 0",
                result.release!!.downloadSize > 0
            )
        }

        assertNotNull(result)
    }
}