package com.door43.translationstudio.usecases

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.OnProgressListener
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.setPrivatePref
import com.door43.translationstudio.App
import com.door43.translationstudio.IntegrationTest
import com.door43.translationstudio.KoinAndroidTest
import com.door43.usecases.UpdateApp
import io.mockk.justRun
import io.mockk.mockkObject
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.inject


@RunWith(AndroidJUnit4::class)
@IntegrationTest
class UpdateAppTest : KoinAndroidTest() {

    private val appContext: Context by inject()
    private val updateApp: UpdateApp by inject()
    private val directoryProvider: IDirectoryProvider by inject()
    private val prefRepository: IPreferenceRepository by inject()

    @Before
    fun setUp() {
        directoryProvider.deleteLibrary()
        prefRepository.setPrivatePref("last_version_code", 0)

        mockkObject(App)
        justRun { App.restart() }
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
