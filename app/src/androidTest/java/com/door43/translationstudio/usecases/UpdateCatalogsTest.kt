package com.door43.translationstudio.usecases

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.IntegrationTest
import com.door43.translationstudio.R
import com.door43.usecases.UpdateCatalogs
import dagger.hilt.android.qualifiers.ApplicationContext
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
@IntegrationTest
class UpdateCatalogsTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject @ApplicationContext lateinit var context: Context
    @Inject lateinit var updateCatalogs: UpdateCatalogs
    @Inject lateinit var library: Door43Client
    @Inject lateinit var directoryProvider: IDirectoryProvider
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

                return when (request.path) {
                    "/langnames.json" -> successResponse.setBody(createResponse("langnames"))
                    "/questionnaire.json" -> successResponse.setBody(createResponse("questionnaire"))
                    "/temp-langs.json" -> successResponse.setBody(createResponse("temp_langs"))
                    "/approved-langs.json" -> successResponse.setBody(createResponse("approved_temp_langs"))
                    else -> notFoundResponse
                }
            }
        }
        server.dispatcher = dispatcher
    }

    companion object {
        private var _directoryProvider: IDirectoryProvider? = null

        @JvmStatic
        @AfterClass
        fun tearDown() {
            _directoryProvider?.deleteLibrary()
            _directoryProvider = null
        }
    }

    @Test
    fun testUpdateCatalogs() {
        prepareCatalogs()

        val message = context.resources.getString(R.string.updating_languages)
        val result = updateCatalogs.execute(false, message)

        assertTrue("Update catalogs should succeed", result.success)
        assertEquals("Added 2 languages", 2, result.addedCount)

        verifyTargetLanguages()
        verifyQuestionnaire()
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