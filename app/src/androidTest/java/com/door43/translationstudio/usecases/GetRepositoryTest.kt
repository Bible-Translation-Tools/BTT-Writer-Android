package com.door43.translationstudio.usecases

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.TestUtils
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.Translator
import com.door43.usecases.GetRepository
import com.door43.usecases.ImportProjects
import com.door43.usecases.SearchGogsUsers
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.tools.logger.Logger
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class GetRepositoryTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject
    @ApplicationContext lateinit var appContext: Context
    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var library: Door43Client
    @Inject lateinit var profile: Profile
    @Inject lateinit var assetsProvider: AssetsProvider
    @Inject lateinit var getRepository: GetRepository
    @Inject lateinit var searchGogsUsers: SearchGogsUsers
    @Inject lateinit var importProjects: ImportProjects
    @Inject lateinit var translator: Translator

    @Before
    fun setUp() {
        hiltRule.inject()
        Logger.flush()
    }

    @After
    fun tearDown() {
        directoryProvider.clearCache()
        directoryProvider.deleteTranslations()
    }

    @Test
    fun getRepositorySucceeds() {
        val source = "usfm/mrk.usfm"
        val targetTranslation = TestUtils.importTargetTranslation(
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

        TestUtils.loginGogsUser(profile, searchGogsUsers)

        val repo = getRepository.execute(targetTranslation!!)

        assertNotNull("Repository should not be null", repo)

        assertEquals(targetTranslation.id, repo!!.name)
    }
}