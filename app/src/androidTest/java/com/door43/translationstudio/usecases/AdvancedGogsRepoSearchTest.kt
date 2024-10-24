package com.door43.translationstudio.usecases

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.OnProgressListener
import com.door43.usecases.AdvancedGogsRepoSearch
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.unfoldingword.tools.logger.Logger
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AdvancedGogsRepoSearchTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var advancedGogsRepoSearch: AdvancedGogsRepoSearch

    @Before
    fun setUp() {
        hiltRule.inject()
        Logger.flush()
    }

    @Test
    fun searchReposByUser() {
        val user = "mxaln"

        var progressMessage: String? = null
        val progressListener = object : OnProgressListener {
            override fun onProgress(progress: Int, max: Int, message: String?) {
                progressMessage = message
            }
            override fun onIndeterminate() {
            }
        }
        val repos = advancedGogsRepoSearch.execute(user, "", 5, progressListener)

        assertTrue(repos.isNotEmpty())
        assertFalse(progressMessage.isNullOrEmpty())
    }

    @Test
    fun searchReposByRepoName() {
        val repo = "_gen_"
        val repos = advancedGogsRepoSearch.execute("", repo, 5)
        assertTrue(repos.isNotEmpty())
    }

    @Test
    fun searchReposByUserAndRepoName() {
        val user = "mxaln"
        val repo = "_gen_"
        val repos = advancedGogsRepoSearch.execute(user, repo, 5)
        assertTrue(repos.isNotEmpty())
    }

    @Test
    fun searchNonExistentUser() {
        val user = "non-existent-user"
        val repos = advancedGogsRepoSearch.execute(user, "", 5)
        assertTrue(repos.isEmpty())
    }

    @Test
    fun searchNonExistentRepo() {
        val repo = "non-existent-repo"
        val repos = advancedGogsRepoSearch.execute("", repo, 5)
        assertTrue(repos.isEmpty())
    }

    @Test
    fun searchReposLimitedToOne() {
        val user = "mxaln"
        val repo = "_gen_"
        val repos = advancedGogsRepoSearch.execute(user, repo, 1)
        assertTrue(repos.isNotEmpty())
        assertEquals(repos.size, 1)
    }
}