package com.door43.translationstudio.usecases

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.setDefaultPref
import com.door43.translationstudio.IntegrationTest
import com.door43.translationstudio.KoinAndroidTest
import com.door43.usecases.UpdateSource
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.bibletranslationtools.resourcecatalog.ResourceCatalogClient
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.inject
import java.text.SimpleDateFormat
import java.util.Locale


@RunWith(AndroidJUnit4::class)
@IntegrationTest
class UpdateSourceTest : KoinAndroidTest() {

    private val catalogClient: ResourceCatalogClient by inject()
    private val directoryProvider: IDirectoryProvider by inject()
    private val updateSource: UpdateSource by inject()
    private val prefRepository: IPreferenceRepository by inject()
    private val assetsProvider: AssetsProvider by inject()

    private val server = MockWebServer()

    @Before
    fun setUp() {
        _directoryProvider = directoryProvider

        val dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val successResponse = MockResponse().setResponseCode(200)
                val notFoundResponse = MockResponse().setResponseCode(404)
                val isCatalog = request.path?.endsWith("/catalog.json") ?: false

                return when {
                    request.path == "/mat" -> successResponse.addHeader("Content-Type", "application/json").setBody(createResponse("mat"))
                    request.path == "/mat_es" -> successResponse.addHeader("Content-Type", "application/json").setBody(createResponse("mat_es"))
                    request.path == "/mat_tpi" -> successResponse.addHeader("Content-Type", "application/json").setBody(createResponse("mat_tpi"))
                    request.path == "/mat_test" -> successResponse.addHeader("Content-Type", "application/json").setBody(createResponse("mat_test"))
                    request.path == "/luk" -> successResponse.addHeader("Content-Type", "application/json").setBody(createResponse("luk"))
                    request.path == "/luk_es" -> successResponse.addHeader("Content-Type", "application/json").setBody(createResponse("luk_es"))
                    request.path == "/luk_tpi" -> successResponse.addHeader("Content-Type", "application/json").setBody(createResponse("luk_tpi"))
                    isCatalog -> successResponse.addHeader("Content-Type", "application/json").setBody(createResponse("catalog"))
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
    fun testUpdateSource() = runTest {
        val url = server.url("/test")
        prefRepository.setDefaultPref(IPreferenceRepository.KEY_PREF_MEDIA_SERVER, url.toString())

        val result = updateSource.execute()

        assertTrue("Update source succeeded", result.success)
        assertEquals("Added 1 source", 1, result.addedCount)
        assertEquals("Updated 6 sources", 6, result.updatedCount)

        val sourceLanguages = catalogClient.library.getSourceLanguages()

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
        val projects = catalogClient.library.getProjects("test", false)
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

        val resources = catalogClient.library.getResources("test", "mat")
        assertTrue("Resources should not be empty", resources.isNotEmpty())

        val tstResource = resources.singleOrNull { it.slug == "tst" }
        assertNotNull("TST resource should not be null", tstResource)
        assertEquals(
            "TST resource name should match",
            "Test Unlocked Literal Bible",
            tstResource?.name
        )
        assertEquals("TST resource type should match", "book", tstResource?.type)
        assertEquals("TST resource version should match", "12.2", tstResource?.status?.version)
        assertTrue(
            "TST resource url should match",
            tstResource?.formats?.first()?.url?.endsWith("/mat/test/source.json") ?: false
        )

        val tnResource = resources.singleOrNull { it.slug == "tn" }
        assertNotNull("TN resource should not be null", tnResource)
        assertEquals("TN resource name should match", "translationNotes", tnResource?.name)
        assertEquals("TN resource type should match", "help", tnResource?.type)
        assertEquals("TN resource version should match", "12.2", tnResource?.status?.version)
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
        assertEquals("TQ resource version should match", "12.2", tqResource?.status?.version)
        assertTrue("" +
                "TQ resource url should match",
            tqResource?.formats?.first()?.url?.endsWith("/mat/test/questions.json") ?: false
        )

        val twResource = catalogClient.library.getResource("test", "bible", "tw")
        assertNotNull("TW resource should not be null", twResource)
        assertEquals("TW resource name should match", "translationWords", twResource?.name)
        assertEquals("TW resource type should match", "dict", twResource?.type)
        assertEquals("TW resource version should match", "12.2", twResource?.status?.version)
        assertTrue(
            "TW resource url should be match",
            twResource?.formats?.first()?.url?.endsWith("/mat/test/words.json") ?: false
        )
    }

    private fun verifyLukProject() {
        val projects = catalogClient.library.getProjects("es-419", false)
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
        val datetimeFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSXXX", Locale.US)
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
