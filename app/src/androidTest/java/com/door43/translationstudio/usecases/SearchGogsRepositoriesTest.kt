package com.door43.translationstudio.usecases

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.OnProgressListener
import com.door43.usecases.SearchGogsRepositories
import com.door43.usecases.SearchGogsUsers
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.unfoldingword.tools.logger.Logger
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SearchGogsRepositoriesTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var searchGogsRepositories: SearchGogsRepositories
    @Inject
    lateinit var searchGogsUsers: SearchGogsUsers

    @Before
    fun setUp() {
        hiltRule.inject()
        Logger.flush()
    }

    @Test
    fun searchReposByUser() {
        val user = "mXaln"
        val gogsUser = searchGogsUsers.execute(user, 1).singleOrNull()

        assertNotNull(gogsUser)
        assertEquals(gogsUser?.username, user)

        var progressMessage: String? = null
        val progressListener = object : OnProgressListener {
            override fun onProgress(progress: Int, max: Int, message: String?) {
                progressMessage = message
            }
            override fun onIndeterminate() {
            }
        }
        val repos = searchGogsRepositories.execute(gogsUser!!.id, "", 3, progressListener)

        assertTrue(repos.isNotEmpty())
        assertFalse(progressMessage.isNullOrEmpty())
    }

    @Test
    fun searchReposByRepoName() {
        val repo = "_gen_"
        val repos = searchGogsRepositories.execute(0, repo, 3)
        assertTrue(repos.isNotEmpty())
    }

    @Test
    fun searchReposByUserAndRepoName() {
        val user = 0
        val repo = "_gen_"
        val repos = searchGogsRepositories.execute(user, repo, 3)
        assertTrue(repos.isNotEmpty())
    }

    @Test
    fun searchNonExistentRepo() {
        val repo = "non-existent-repo"
        val repos = searchGogsRepositories.execute(0, repo, 3)
        assertTrue(repos.isEmpty())
    }

    @Test
    fun searchReposLimitedToOne() {
        val user = 0
        val repo = "_gen_"
        val repos = searchGogsRepositories.execute(user, repo, 1)
        assertTrue(repos.isNotEmpty())
        assertEquals(repos.size, 1)
    }
}