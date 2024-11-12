package com.door43.translationstudio.usecases

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.setDefaultPref
import com.door43.translationstudio.ui.SettingsActivity
import com.door43.usecases.UpdateAll
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
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
import org.unfoldingword.door43client.models.Catalog
import java.text.SimpleDateFormat
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class UpdateAllTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject lateinit var updateAll: UpdateAll
    @Inject lateinit var library: Door43Client
    @Inject lateinit var directoryProvider: IDirectoryProvider
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
                    request.path == "/langnames.json" -> successResponse.setBody(createResponse("langnames"))
                    request.path == "/questionnaire.json" -> successResponse.setBody(createResponse("questionnaire"))
                    request.path == "/temp-langs.json" -> successResponse.setBody(createResponse("temp_langs"))
                    request.path == "/approved-langs.json" -> successResponse.setBody(createResponse("approved_temp_langs"))
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
    fun testUpdateAll() {
        val url = server.url("/test")
        prefRepository.setDefaultPref(SettingsActivity.KEY_PREF_MEDIA_SERVER, url.toString())

        prepareCatalogs()

        val result = updateAll.execute(false)

        assertTrue("UpdateAll should succeed", result.success)

        verifyTestProject()
        verifyLukProject()
        verifyTargetLanguages()
        verifyQuestionnaire()
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

    private fun verifyTargetLanguages() {
        val targetLanguages = library.index.targetLanguages
        assertEquals("There should be 4 target languages", 4, targetLanguages.size)

        val aaLang = targetLanguages.singleOrNull { it.slug == "aa" }
        assertNotNull("Afar language should not be null", aaLang)
        assertEquals("Afar language slug should match", "aa", aaLang?.slug)
        assertEquals("Afar language name should match", "Qafar af New", aaLang?.name)
        assertEquals("Afar language anglicized name should match", "Afar New", aaLang?.anglicizedName)

        val test2Lang = targetLanguages.singleOrNull { it.slug == "test2" }
        assertNotNull("Test 2 language should not be null", test2Lang)
        assertEquals("Test 2 language slug should match", "test2", test2Lang?.slug)
        assertEquals("Test 2 language name should match", "Test 2", test2Lang?.name)
        assertEquals("Test 2 language anglicized name should match", "Test 2 Ang", test2Lang?.anglicizedName)

        val temp1Language = targetLanguages.singleOrNull { it.slug == "qaa-x-111111" }
        assertNotNull("Temp language 1 should not be null", temp1Language)
        assertEquals("Temp language slug should match", "qaa-x-111111", temp1Language?.slug)
        assertEquals("Temp language name should match", "Test Temp 1", temp1Language?.name)

        val temp2Language = targetLanguages.singleOrNull { it.slug == "qaa-x-222222" }
        assertNull("Temp language 2 should be null", temp2Language)

        val temp2LanguageApproved = library.index.getApprovedTargetLanguage("qaa-x-222222")
        assertEquals("Temp language slug should match", "ifk-x-yattuca", temp2LanguageApproved?.slug)
        assertEquals("Temp language name should match", "Yattuca", temp2LanguageApproved?.name)
    }

    private fun verifyQuestionnaire() {
        val questionnaires = library.index.questionnaires
        assertTrue("Questionnaires should not be empty", questionnaires.isNotEmpty())

        val questionnaire = questionnaires.singleOrNull { it.tdId == 2L }
        assertNotNull("Questionnaire should not be null", questionnaire)
        assertEquals("es", questionnaire?.languageSlug)
        assertEquals("Spanish", questionnaire?.languageName)

        val questions = library.index.getQuestions(2L)
        assertEquals("Question count should match", 19, questions.size)
        assertEquals("Test questionnaire question", questions.singleOrNull { it.sort == 19 }?.text)
    }

    private fun prepareCatalogs() {
        val langCatalogUrl = server.url("/langnames.json").toString()
        val langCatalog = Catalog("langnames", langCatalogUrl, 0)
        library.index.addCatalog(langCatalog)
        createResponse("langnames")

        val questionnaireCatalogUrl = server.url("/questionnaire.json").toString()
        val questionnaireCatalog = Catalog("new-language-questions", questionnaireCatalogUrl, 0)
        library.index.addCatalog(questionnaireCatalog)
        createResponse("questionnaire")

        val tempLangsCatalogUrl = server.url("/temp-langs.json").toString()
        val tempLangsCatalog = Catalog("temp-langnames", tempLangsCatalogUrl, 0)
        library.index.addCatalog(tempLangsCatalog)
        createResponse("temp_langs")

        val approvedLangsCatalogUrl = server.url("/approved-langs.json").toString()
        val approvedLangsCatalog = Catalog("approved-temp-langnames", approvedLangsCatalogUrl, 0)
        library.index.addCatalog(approvedLangsCatalog)
        createResponse("approved_temp_langs")
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