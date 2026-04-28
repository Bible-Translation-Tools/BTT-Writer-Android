package com.door43.translationstudio.usecases

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.IntegrationTest
import com.door43.translationstudio.KoinAndroidTest
import com.door43.usecases.UpdateCatalogs
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.AfterClass
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.inject
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.Catalog
import java.text.SimpleDateFormat
import java.util.Locale


@RunWith(AndroidJUnit4::class)
@IntegrationTest
class UpdateCatalogsTest : KoinAndroidTest() {

    private val updateCatalogs: UpdateCatalogs by inject()
    private val library: Door43Client by inject()
    private val directoryProvider: IDirectoryProvider by inject()
    private val assetsProvider: AssetsProvider by inject()

    private val server = MockWebServer()

    @Before
    fun setUp() {
        _directoryProvider = directoryProvider

        val dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val successResponse = MockResponse().setResponseCode(200)
                val notFoundResponse = MockResponse().setResponseCode(404)

                return when (request.path) {
                    "/langnames.json" -> successResponse.setBody(createResponse("langnames"))
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
    fun testUpdateCatalogs() = runTest {
        prepareCatalogs()

        val result = updateCatalogs.execute(false)

        assertTrue("Update catalogs should succeed", result.success)
        assertEquals("Added 2 languages", 2, result.addedCount)

        verifyTargetLanguages()
    }

    private fun verifyTargetLanguages() {
        val targetLanguages = library.index.getTargetLanguages()
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

    private fun prepareCatalogs() {
        val langCatalogUrl = server.url("/langnames.json").toString()
        val langCatalog = Catalog("langnames", langCatalogUrl, 0)
        library.index.addCatalog(langCatalog)
        createResponse("langnames")

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
