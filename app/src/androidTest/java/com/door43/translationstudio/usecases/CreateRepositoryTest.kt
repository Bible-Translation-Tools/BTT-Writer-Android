package com.door43.translationstudio.usecases

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.OnProgressListener
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.TestUtils.importTargetTranslation
import com.door43.translationstudio.TestUtils.loginGogsUser
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.Translator
import com.door43.usecases.CreateRepository
import com.door43.usecases.ImportProjects
import com.door43.usecases.SearchGogsUsers
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
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.tools.logger.Logger
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CreateRepositoryTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject
    @ApplicationContext lateinit var appContext: Context
    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var library: Door43Client
    @Inject lateinit var profile: Profile
    @Inject lateinit var assetsProvider: AssetsProvider
    @Inject lateinit var createRepository: CreateRepository
    @Inject lateinit var searchGogsUsers: SearchGogsUsers
    @Inject lateinit var importProjects: ImportProjects
    @Inject lateinit var translator: Translator

    @Before
    fun setUp() {
        hiltRule.inject()
        Logger.flush()

        directoryProvider.clearCache()
    }

    @Test
    fun createRepositoryWithAuthenticationSucceeds() {
        val source = "usfm/mrk.usfm"
        val targetTranslation = importTargetTranslation(
            library,
            appContext,
            directoryProvider,
            profile,
            assetsProvider,
            importProjects,
            translator,
            "aae",
            source
        )

        assertNotNull("Target translation should not be null", targetTranslation)

        loginGogsUser(profile, searchGogsUsers)

        val created = createRepository.execute(targetTranslation!!)

        assertTrue("Repository should be created when authenticated", created)
    }

    @Test
    fun createRepositoryWithoutAuthenticationFails() {
        val source = "usfm/mrk.usfm"
        val targetTranslation = importTargetTranslation(
            library,
            appContext,
            directoryProvider,
            profile,
            assetsProvider,
            importProjects,
            translator,
            "aae",
            source
        )

        assertNotNull("Target translation should not be null", targetTranslation)

        var progressMessage: String? = null
        val progressListener = object : OnProgressListener {
            override fun onProgress(progress: Int, max: Int, message: String?) {
                progressMessage = message
            }
            override fun onIndeterminate() {
            }
        }
        val created = createRepository.execute(targetTranslation!!, progressListener)

        assertFalse("Repository should not be created when not authenticated", created)
        assertNotNull("Progress message should not be null", progressMessage)
    }
}