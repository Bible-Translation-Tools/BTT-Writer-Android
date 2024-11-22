package com.door43.usecases

import android.content.Context
import android.content.res.Resources
import com.door43.OnProgressListener
import com.door43.data.IDirectoryProvider
import com.door43.data.ILanguageRequestRepository
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.R
import com.door43.translationstudio.core.NewLanguageRequest
import com.door43.translationstudio.core.Translator
import com.door43.util.FileUtilities
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifySequence
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class SubmitNewLanguageRequestsTest {

    @MockK private lateinit var context: Context
    @MockK private lateinit var languageRequestRepository: ILanguageRequestRepository
    @MockK private lateinit var prefRepository: IPreferenceRepository
    @MockK private lateinit var directoryProvider: IDirectoryProvider
    @MockK private lateinit var translator: Translator
    @MockK private lateinit var progressListener: OnProgressListener
    @MockK private lateinit var resources: Resources

    private val server = MockWebServer()

    @Before
    fun setup() {
        server.start()
        MockKAnnotations.init(this)

        every { context.resources }.returns(resources)
        every { progressListener.onProgress(any(), any(), any()) }.just(runs)

        every { resources.getString(R.string.submitting_new_language_requests) }
            .returns("Submitting new language requests...")

        every { prefRepository.getQuestionnaireApi() }.returns(server.url("/api").toString())
        every { directoryProvider.externalAppDir }.returns(File("external"))

        mockkStatic(FileUtilities::class)
        every { FileUtilities.writeStringToFile(any(), any()) }.just(runs)

        every { translator.targetTranslations }.returns(arrayOf())
    }

    @After
    fun tearDown() {
        unmockkAll()
        server.shutdown()
    }

    @Test
    fun `test submit new language request successfully`() {
        val response = """
            {
                "status": "success",
                "message": "submitted successfully"
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(response).setResponseCode(200))
        server.enqueue(MockResponse().setBody(response).setResponseCode(200))

        val request: NewLanguageRequest = mockk(relaxed = true) {
            every { toJson() }.returns("{}")
            every { tempLanguageCode }.returns("aa-temp")
        }

        every { languageRequestRepository.getNewLanguageRequests() }
            .returns(listOf(request))

        SubmitNewLanguageRequests(
            context,
            languageRequestRepository,
            prefRepository,
            directoryProvider,
            translator
        ).execute(progressListener)

        verifySequence {
            progressListener.onProgress(any(), any(), "Submitting new language requests...")
            progressListener.onProgress(1, 1, "Submitting new language requests...")
        }

        verify { FileUtilities.writeStringToFile(any(), any()) }
        verify { translator.targetTranslations }
    }

    @Test
    fun `test submit 2 new language requests, one of them already submitted`() {
        val response = """
            {
                "status": "success",
                "message": "submitted successfully"
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(response).setResponseCode(200))
        server.enqueue(MockResponse().setBody(response).setResponseCode(200))

        val request1: NewLanguageRequest = mockk {
            every { toJson() }.returns("{}")
            every { tempLanguageCode }.returns("aa-temp")
            every { submittedAt }.returns(0L)
            every { submittedAt = any() }.just(runs)
        }
        val request2: NewLanguageRequest = mockk {
            every { toJson() }.returns("{}")
            every { tempLanguageCode }.returns("aa-temp")
            every { submittedAt }.returns(1L)
        }

        every { languageRequestRepository.getNewLanguageRequests() }
            .returns(listOf(request1, request2))

        SubmitNewLanguageRequests(
            context,
            languageRequestRepository,
            prefRepository,
            directoryProvider,
            translator
        ).execute(progressListener)

        verifySequence {
            progressListener.onProgress(any(), any(), "Submitting new language requests...")
            progressListener.onProgress(1, 1, "Submitting new language requests...")
        }

        verify(exactly = 1) { FileUtilities.writeStringToFile(any(), any()) }
        verify(exactly = 1) { translator.targetTranslations }
    }

    @Test
    fun `test submit new language requests, duplicate status`() {
        val response = """
            {
                "status": "there is a duplicate",
                "message": "an error occurred"
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(response).setResponseCode(200))

        val request: NewLanguageRequest = mockk {
            every { toJson() }.returns("{}")
            every { tempLanguageCode }.returns("aa-temp")
            every { submittedAt }.returns(0L)
            every { submittedAt = any() }.just(runs)
        }

        every { languageRequestRepository.getNewLanguageRequests() }
            .returns(listOf(request))

        SubmitNewLanguageRequests(
            context,
            languageRequestRepository,
            prefRepository,
            directoryProvider,
            translator
        ).execute(progressListener)

        verifySequence {
            progressListener.onProgress(any(), any(), "Submitting new language requests...")
            progressListener.onProgress(1, 1, "Submitting new language requests...")
        }

        verify { FileUtilities.writeStringToFile(any(), any()) }
        verify { translator.targetTranslations }
    }

    @Test
    fun `test submit new language requests, duplicate message`() {
        val response = """
            {
                "status": "error",
                "message": "there is a duplicate key value in questionnaire"
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(response).setResponseCode(200))

        val request: NewLanguageRequest = mockk {
            every { toJson() }.returns("{}")
            every { tempLanguageCode }.returns("aa-temp")
            every { submittedAt }.returns(0L)
            every { submittedAt = any() }.just(runs)
        }

        every { languageRequestRepository.getNewLanguageRequests() }
            .returns(listOf(request))

        SubmitNewLanguageRequests(
            context,
            languageRequestRepository,
            prefRepository,
            directoryProvider,
            translator
        ).execute(progressListener)

        verifySequence {
            progressListener.onProgress(any(), any(), "Submitting new language requests...")
            progressListener.onProgress(1, 1, "Submitting new language requests...")
        }

        verify { FileUtilities.writeStringToFile(any(), any()) }
        verify { translator.targetTranslations }
    }

    @Test
    fun `test submit new language requests, unknown status`() {
        val response = """
            {
                "status": "unknown",
                "message": "server returned error"
            }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(response).setResponseCode(200))

        val request: NewLanguageRequest = mockk {
            every { toJson() }.returns("{}")
            every { tempLanguageCode }.returns("aa-temp")
            every { submittedAt }.returns(0L)
            every { submittedAt = any() }.just(runs)
        }

        every { languageRequestRepository.getNewLanguageRequests() }
            .returns(listOf(request))

        SubmitNewLanguageRequests(
            context,
            languageRequestRepository,
            prefRepository,
            directoryProvider,
            translator
        ).execute(progressListener)

        verifySequence {
            progressListener.onProgress(any(), any(), "Submitting new language requests...")
            progressListener.onProgress(1, 1, "Submitting new language requests...")
        }

        verify(exactly = 0) { FileUtilities.writeStringToFile(any(), any()) }
        verify(exactly = 0) { translator.targetTranslations }
    }
}