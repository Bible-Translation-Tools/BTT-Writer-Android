package com.door43.translationstudio.usecases

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.setDefaultPref
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.SettingsActivity
import com.door43.usecases.UpdateSource
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.unfoldingword.door43client.Door43Client
import java.text.SimpleDateFormat
import javax.inject.Inject


@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class UpdateSourceTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject @ApplicationContext lateinit var context: Context
    @Inject lateinit var library: Door43Client
    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var updateSource: UpdateSource
    @Inject lateinit var prefRepository: IPreferenceRepository
    @Inject lateinit var assetsProvider: AssetsProvider

    private val server = MockWebServer()

    @Before
    fun setUp() {
        hiltRule.inject()

        _directoryProvider = directoryProvider

        val dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val successResponse = MockResponse().setResponseCode(200)
                val notFoundResponse = MockResponse().setResponseCode(404)
                val isCatalog = request.path?.endsWith("/catalog.json") ?: false

                return when {
                    request.path == "/mat" -> successResponse.setBody(createResponse("mat"))
                    request.path == "/mat_es" -> successResponse.setBody(createResponse("mat_es"))
                    request.path == "/mat_tpi" -> successResponse.setBody(createResponse("mat_tpi"))
                    request.path == "/mat_test" -> successResponse.setBody(createResponse("mat_test"))
                    request.path == "/luk" -> successResponse.setBody(createResponse("luk"))
                    request.path == "/luk_es" -> successResponse.setBody(createResponse("luk_es"))
                    request.path == "/luk_tpi" -> successResponse.setBody(createResponse("luk_tpi"))
                    isCatalog -> successResponse.setBody(createResponse("catalog"))
                    else -> notFoundResponse
                }
            }
        }
        server.dispatcher = dispatcher
    }

    @After
    fun tearDown() {
        server.shutdown()
        directoryProvider.clearCache()
    }

    companion object {
        private var _directoryProvider: IDirectoryProvider? = null

        @JvmStatic
        @AfterClass
        fun cleanUp() {
            _directoryProvider?.deleteLibrary()
            _directoryProvider = null
        }
    }

    @Test
    fun testUpdateSource() {
        val url = server.url("/test")
        prefRepository.setDefaultPref(SettingsActivity.KEY_PREF_MEDIA_SERVER, url.toString())

        val message = context.resources.getString(R.string.updating_sources)
        val result = updateSource.execute(message)

        assertTrue("Update source succeeded", result.success)
        assertEquals("Added 1 source", 1, result.addedCount)
        assertEquals("Updated 6 sources", 6, result.updatedCount)

        val sourceLanguages = library.index.getSourceLanguages()

        assertNotNull(
            "Test Source language should be added",
            sourceLanguages.singleOrNull { it.slug == "test" && it.name == "Test Language" }
        )
        assertNotNull(
            "Spanish source language should exist",
            sourceLanguages.singleOrNull { it.slug == "es-419" && it.name == "Espa\u00f1ol (Latin American Spanish)" }
        )
        assertNotNull(
            "Tok Pisin source language should exist",
            sourceLanguages.singleOrNull { it.slug == "tpi" && it.name == "Tok Pisin" }
        )

        verifyTestProject()
        verifyLukProject()
    }

    private fun verifyTestProject() {
        val projects = library.index.getProjects("test")
        val project = projects.singleOrNull { it.slug == "mat" }

        assertEquals("There should be 2 test project", 2, projects.size)
        assertNotNull("Project should not be null", project)
        assertEquals("Project slug should match", "mat", project?.slug)
        assertEquals("Project name should match", "Matthew New", project?.name)
        assertEquals(
            "Project description should match",
            "Mateo Test",
            project?.description
        )
        assertTrue(
            "Project chunksUrl should match",
            project?.chunksUrl?.endsWith("mat/test/chunks.json") ?: false
        )
        assertEquals(
            "Project languageSlug should match",
            "test",
            project?.languageSlug
        )

        val twProject = projects.singleOrNull { it.slug == "bible" }
        assertNotNull("TW project should not be null", twProject)
        assertEquals("TW project slug should be bible", "bible", twProject?.slug)
        assertEquals("TW project name should match", "translationWords", twProject?.name)
        assertEquals("TW project languageSlug should match", "test", twProject?.languageSlug)

        val resources = library.index.getResources("test", "mat")
        assertTrue("Resources should not be empty", resources.isNotEmpty())

        val tstResource = resources.singleOrNull { it.slug == "tst" }
        assertNotNull("TST resource should not be null", tstResource)
        assertEquals(
            "TST resource name should match",
            "Test Unlocked Literal Bible",
            tstResource?.name
        )
        assertEquals("TST resource type should match", "book", tstResource?.type)
        assertEquals("TST resource version should match", "12.2", tstResource?.version)
        assertTrue(
            "TST resource url should match",
            tstResource?.formats?.first()?.url?.endsWith("/mat/test/source.json") ?: false
        )

        val tnResource = resources.singleOrNull { it.slug == "tn" }
        assertNotNull("TN resource should not be null", tnResource)
        assertEquals("TN resource name should match", "translationNotes", tnResource?.name)
        assertEquals("TN resource type should match", "help", tnResource?.type)
        assertEquals("TN resource version should match", "12.2", tnResource?.version)
        assertTrue(
            "TN resource url should match",
            tnResource?.formats?.first()?.url?.endsWith("/mat/test/notes.json") ?: false
        )

        val tqResource = resources.singleOrNull { it.slug == "tq" }
        assertNotNull("TQ resource should not be null", tqResource)
        assertEquals(
            "TQ resource name should match",
            "translationQuestions",
            tqResource?.name
        )
        assertEquals("TQ resource type should match", "help", tqResource?.type)
        assertEquals("TQ resource version should match", "12.2", tqResource?.version)
        assertTrue("" +
                "TQ resource url should match",
            tqResource?.formats?.first()?.url?.endsWith("/mat/test/questions.json") ?: false
        )

        val twResource = library.index.getResource("test", "bible", "tw")
        assertNotNull("TW resource should not be null", twResource)
        assertEquals("TW resource name should match", "translationWords", twResource?.name)
        assertEquals("TW resource type should match", "dict", twResource?.type)
        assertEquals("TW resource version should match", "12.2", twResource?.version)
        assertTrue(
            "TW resource url should be match",
            twResource?.formats?.first()?.url?.endsWith("/mat/test/words.json") ?: false
        )
    }

    private fun verifyLukProject() {
        val projects = library.index.getProjects("es-419")
        val project = projects.singleOrNull { it.slug == "luk" }

        assertEquals("There should be 67 test project", 67, projects.size)
        assertNotNull("Project should not be null", project)
        assertEquals("Project slug should match", "luk", project?.slug)
        assertEquals("Project name should match", "Lucas", project?.name)
        assertEquals(
            "Project description should match",
            "Lucas Spanish",
            project?.description
        )
        assertTrue(
            "Project chunksUrl should match",
            project?.chunksUrl?.endsWith("luk/es/chunks.json") ?: false
        )
        assertEquals(
            "Project languageSlug should match",
            "es-419",
            project?.languageSlug
        )
    }

    private fun createResponse(id: String): String {
        val baseUrl = server.url("/").toString()
        val datetimeFormat = SimpleDateFormat("yyyyMMdd")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSXXX")
        val project = directoryProvider.createTempFile(id, ".json")
        assetsProvider.open("catalog/$id.json").use { input ->
            project.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        val datetime = datetimeFormat.format(System.currentTimeMillis())
        val date = dateFormat.format(System.currentTimeMillis())
        val text = project.readText()
            .replace("{server}/", baseUrl)
            .replace("{datetime}", datetime)
            .replace("{date}", date)

        return text
    }
}