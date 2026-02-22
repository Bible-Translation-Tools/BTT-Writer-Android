package com.door43.translationstudio.usecases

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.translationstudio.IntegrationTest
import com.door43.translationstudio.KoinAndroidTest
import com.door43.usecases.CheckForLatestRelease
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.inject


@RunWith(AndroidJUnit4::class)
@IntegrationTest
class CheckForLatestReleaseTest : KoinAndroidTest() {

    private val appContext: Context by inject()
    private val checkForLatestRelease: CheckForLatestRelease by inject()

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
                result.release.name.isEmpty()
            )
            assertTrue(
                "Release file should be .apk",
                result.release.downloadUrl.endsWith(".apk")
            )
            assertTrue(
                "Release build should be greater than 0",
                result.release.build > 0
            )
            assertTrue(
                "Release build should be greater than current version",
                result.release.build > pInfo.versionCode
            )
            assertTrue(
                "Release size should be greater than 0",
                result.release.downloadSize > 0
            )
        }

        assertNotNull(result)
    }
}
