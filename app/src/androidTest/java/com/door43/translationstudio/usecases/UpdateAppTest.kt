package com.door43.translationstudio.usecases

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.setPrivatePref
import com.door43.di.appModule
import com.door43.translationstudio.AppInfo
import com.door43.translationstudio.IntegrationTest
import com.door43.translationstudio.KoinAndroidTest
import com.door43.translationstudio.Platform
import com.door43.translationstudio.di.testDataModule
import com.door43.usecases.UpdateApp
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.component.inject
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.context.GlobalContext.stopKoin
import org.koin.dsl.module


@RunWith(AndroidJUnit4::class)
@IntegrationTest
class UpdateAppTest : KoinAndroidTest() {

    private val updateApp: UpdateApp by inject()
    private val directoryProvider: IDirectoryProvider by inject()
    private val prefRepository: IPreferenceRepository by inject()
    private val platform: Platform = mockk()
    private val appInfo: AppInfo = mockk()

    @Before
    fun setUp() {
        stopKoin()
        startKoin {
            androidContext(ApplicationProvider.getApplicationContext())
            allowOverride(true)
            modules(appModule, testDataModule)
            modules(
                module {
                    single<Platform> { platform }
                }
            )
        }

        directoryProvider.deleteLibrary()
        prefRepository.setPrivatePref("last_version_code", 0)

        every { appInfo.versionCode }.returns(0)
        every { platform.info }.returns(appInfo)
        every { platform.restart() }.just(runs)
    }

    @Test
    fun testUpdateAppNewInstall() = runTest {
        var progressMessage: String? = null
        val onProgress: (Float, String?) -> Unit = { _, message ->
            progressMessage = message
        }

        updateApp.execute(onProgress)

        assertNull("Progress message should be null", progressMessage)
    }

    @Test
    fun testUpdateAppCurrentVersion() = runTest {
        val currentVersion = platform.info.versionCode

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
