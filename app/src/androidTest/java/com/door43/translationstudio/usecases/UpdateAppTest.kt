package com.door43.translationstudio.usecases

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.OnProgressListener
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.setPrivatePref
import com.door43.usecases.UpdateApp
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.unfoldingword.tools.logger.Logger
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class UpdateAppTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject @ApplicationContext lateinit var appContext: Context
    @Inject lateinit var updateApp: UpdateApp
    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var prefRepository: IPreferenceRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        Logger.flush()

        directoryProvider.deleteLibrary()
        prefRepository.setPrivatePref("last_version_code", 0)
    }

    @Test
    fun testUpdateAppNewInstall() {
        var progressMessage: String? = null
        val progressListener = OnProgressListener { _, _, message ->
            progressMessage = message
        }

        updateApp.execute(progressListener)

        assertNull("Progress message should be null", progressMessage)
    }

    @Test
    fun testUpdateAppCurrentVersion() {
        val pInfo = try {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            null
        }

        assertNotNull("Package info should not be null", pInfo)
        val currentVersion = pInfo!!.versionCode

        prefRepository.setPrivatePref("last_version_code", currentVersion)

        updateApp.execute()
    }

//    @Test
//    fun testUpdateAppBeforeVersion87() {
//        prefRepository.setPrivatePref("last_version_code", 86)
//
//        var progressMessage: String? = null
//        val progressListener = OnProgressListener { _, _, message ->
//            progressMessage = message
//        }
//
//        updateApp.execute(progressListener)
//
//        assertNotNull("Progress message should not be null", progressMessage)
//    }
//
//    @Test
//    fun testUpdateAppBeforeVersion103() {
//        prefRepository.setPrivatePref("last_version_code", 102)
//
//        var progressMessage: String? = null
//        val progressListener = OnProgressListener { _, _, message ->
//            progressMessage = message
//        }
//
//        updateApp.execute(progressListener)
//
//        assertNotNull("Progress message should not be null", progressMessage)
//    }
//
//    @Test
//    fun testUpdateAppBeforeVersion111() {
//        prefRepository.setPrivatePref("last_version_code", 110)
//
//        var progressMessage: String? = null
//        val progressListener = OnProgressListener { _, _, message ->
//            progressMessage = message
//        }
//
//        updateApp.execute(progressListener)
//
//        assertNull("Progress message should be null", progressMessage)
//    }
//
//    @Test
//    fun testUpdateAppBeforeVersion122() {
//        prefRepository.setPrivatePref("last_version_code", 121)
//
//        var progressMessage: String? = null
//        val progressListener = OnProgressListener { _, _, message ->
//            progressMessage = message
//        }
//
//        updateApp.execute(progressListener)
//
//        assertNull("Progress message should be null", progressMessage)
//    }
//
//    @Test
//    fun testUpdateAppBeforeVersion139() {
//        prefRepository.setPrivatePref("last_version_code", 138)
//
//        var progressMessage: String? = null
//        val progressListener = OnProgressListener { _, _, message ->
//            progressMessage = message
//        }
//
//        updateApp.execute(progressListener)
//
//        assertNull("Progress message should be null", progressMessage)
//    }
//
//    @Test
//    fun testUpdateAppBeforeVersion142() {
//        prefRepository.setPrivatePref("last_version_code", 141)
//
//        var progressMessage: String? = null
//        val progressListener = OnProgressListener { _, _, message ->
//            progressMessage = message
//        }
//
//        updateApp.execute(progressListener)
//
//        assertNull("Progress message should be null", progressMessage)
//    }
//
//    @Test
//    fun testUpdateAppBeforeVersion175() {
//        prefRepository.setPrivatePref("last_version_code", 174)
//
//        var progressMessage: String? = null
//        val progressListener = OnProgressListener { _, _, message ->
//            progressMessage = message
//        }
//
//        updateApp.execute(progressListener)
//
//        assertNotNull("Progress message should not be null", progressMessage)
//    }
}