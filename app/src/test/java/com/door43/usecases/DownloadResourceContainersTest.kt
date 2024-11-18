package com.door43.usecases

import com.door43.OnProgressListener
import com.door43.TestUtils
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.Index
import org.unfoldingword.door43client.models.Translation
import org.unfoldingword.resourcecontainer.ContainerTools
import org.unfoldingword.resourcecontainer.Language
import org.unfoldingword.resourcecontainer.Project
import org.unfoldingword.resourcecontainer.Resource
import org.unfoldingword.resourcecontainer.ResourceContainer
import org.unfoldingword.tools.logger.Logger

class DownloadResourceContainersTest {

    @MockK private lateinit var library: Door43Client
    @MockK private lateinit var translation: Translation
    @MockK private lateinit var progressListener: OnProgressListener
    @MockK private lateinit var language: Language
    @MockK private lateinit var project: Project
    @MockK private lateinit var resource: Resource
    @MockK private lateinit var index: Index

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        mockkStatic(Logger::class)

        TestUtils.setPropertyReflection(translation, "language", language)
        TestUtils.setPropertyReflection(translation, "project", project)
        TestUtils.setPropertyReflection(translation, "resource", resource)

        every { progressListener.onProgress(any(), any(), any()) }.just(runs)

        TestUtils.setPropertyReflection(library, "index", index)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test download ulb resource container also downloads helps`() {
        val rcUlb: ResourceContainer = mockk()
        val twUlb: ResourceContainer = mockk()
        val tnUlb: ResourceContainer = mockk()
        val tqUlb: ResourceContainer = mockk()

        TestUtils.setPropertyReflection(language, "slug", "en")
        TestUtils.setPropertyReflection(project, "slug", "mrk")
        TestUtils.setPropertyReflection(resource, "slug", "ulb")

        every { library.download(language.slug, project.slug, resource.slug) }.returns(rcUlb)
        every { library.download(language.slug, "bible", "tw") }.returns(twUlb)
        every { library.download(language.slug, project.slug, "tn") }.returns(tnUlb)
        every { library.download(language.slug, project.slug, "tq") }.returns(tqUlb)

        val result = DownloadResourceContainers(library)
            .download(translation, progressListener)

        assertTrue(result.success)
        assertEquals(4, result.containers.size)

        assertEquals(rcUlb, result.containers[0])
        assertEquals(twUlb, result.containers[1])
        assertEquals(tnUlb, result.containers[2])
        assertEquals(tqUlb, result.containers[3])

        verifySequence {
            progressListener.onProgress(any(), any(), "Downloading resource container")
            progressListener.onProgress(any(), any(), "Downloading translation words")
            progressListener.onProgress(any(), any(), "Downloading translation notes")
            progressListener.onProgress(any(), any(), "Downloading translation questions")
        }

        verify { library.download(language.slug, project.slug, resource.slug) }
        verify { library.download(language.slug, "bible", "tw") }
        verify { library.download(language.slug, project.slug, "tn") }
        verify { library.download(language.slug, project.slug, "tq") }
    }

    @Test
    fun `test download tw resource container`() {
        val resourceSlug = "tw"
        val projectSlug = "bible"
        val twUlb: ResourceContainer = mockk()

        TestUtils.setPropertyReflection(language, "slug", "en")
        TestUtils.setPropertyReflection(project, "slug", projectSlug)
        TestUtils.setPropertyReflection(resource, "slug", resourceSlug)

        every { library.download(language.slug, projectSlug, resourceSlug) }.returns(twUlb)

        val result = DownloadResourceContainers(library)
            .download(translation, progressListener)

        assertTrue(result.success)
        assertEquals(1, result.containers.size)
        assertEquals(twUlb, result.containers[0])

        verify { progressListener.onProgress(any(), any(), "Downloading resource container") }
        verify { library.download(language.slug, projectSlug, resourceSlug) }
    }

    @Test
    fun `test download tn resource container`() {
        val resourceSlug = "tn"
        val tnUlb: ResourceContainer = mockk()

        TestUtils.setPropertyReflection(language, "slug", "en")
        TestUtils.setPropertyReflection(project, "slug", "mrk")
        TestUtils.setPropertyReflection(resource, "slug", resourceSlug)

        every { library.download(language.slug, project.slug, resourceSlug) }.returns(tnUlb)

        val result = DownloadResourceContainers(library)
            .download(translation, progressListener)

        assertTrue(result.success)
        assertEquals(1, result.containers.size)
        assertEquals(tnUlb, result.containers[0])

        verify { progressListener.onProgress(any(), any(), "Downloading resource container") }
        verify { library.download(language.slug, project.slug, resourceSlug) }
    }

    @Test
    fun `test download tq resource container`() {
        val resourceSlug = "tq"
        val tqUlb: ResourceContainer = mockk()

        TestUtils.setPropertyReflection(language, "slug", "en")
        TestUtils.setPropertyReflection(project, "slug", "mrk")
        TestUtils.setPropertyReflection(resource, "slug", resourceSlug)

        every { library.download(language.slug, project.slug, resourceSlug) }.returns(tqUlb)

        val result = DownloadResourceContainers(library)
            .download(translation, progressListener)

        assertTrue(result.success)
        assertEquals(1, result.containers.size)
        assertEquals(tqUlb, result.containers[0])

        verify { progressListener.onProgress(any(), any(), "Downloading resource container") }
        verify { library.download(language.slug, project.slug, resourceSlug) }
    }

    @Test
    fun `test download obs resource container also downloads helps`() {
        val rcUlb: ResourceContainer = mockk()
        val twUlb: ResourceContainer = mockk()
        val tnUlb: ResourceContainer = mockk()
        val tqUlb: ResourceContainer = mockk()

        TestUtils.setPropertyReflection(language, "slug", "en")
        TestUtils.setPropertyReflection(project, "slug", "obs")
        TestUtils.setPropertyReflection(resource, "slug", "ulb")

        every { library.download(language.slug, project.slug, resource.slug) }.returns(rcUlb)
        every { library.download(language.slug, "bible-obs", "tw") }.returns(twUlb)
        every { library.download(language.slug, project.slug, "tn") }.returns(tnUlb)
        every { library.download(language.slug, project.slug, "tq") }.returns(tqUlb)

        val result = DownloadResourceContainers(library)
            .download(translation, progressListener)

        assertTrue(result.success)
        assertEquals(4, result.containers.size)

        assertEquals(rcUlb, result.containers[0])
        assertEquals(twUlb, result.containers[1])
        assertEquals(tnUlb, result.containers[2])
        assertEquals(tqUlb, result.containers[3])

        verifySequence {
            progressListener.onProgress(any(), any(), "Downloading resource container")
            progressListener.onProgress(any(), any(), "Downloading obs translation words")
            progressListener.onProgress(any(), any(), "Downloading translation notes")
            progressListener.onProgress(any(), any(), "Downloading translation questions")
        }

        verify { library.download(language.slug, project.slug, resource.slug) }
        verify { library.download(language.slug, "bible-obs", "tw") }
        verify { library.download(language.slug, project.slug, "tn") }
        verify { library.download(language.slug, project.slug, "tq") }
    }

    @Test
    fun `test ulb resource with exception downloads nothing`() {
        TestUtils.setPropertyReflection(language, "slug", "en")
        TestUtils.setPropertyReflection(project, "slug", "mrk")
        TestUtils.setPropertyReflection(resource, "slug", "ulb")

        every { library.download(language.slug, project.slug, resource.slug) }
            .throws(Exception("An error occurred."))

        val result = DownloadResourceContainers(library)
            .download(translation, progressListener)

        assertFalse(result.success)
        assertEquals(0, result.containers.size)

        verify { progressListener.onProgress(any(), any(), "Downloading resource container") }
        verify { library.download(language.slug, project.slug, resource.slug) }
    }

    @Test
    fun `test download ulb resource with tw exception`() {
        val rcUlb: ResourceContainer = mockk()
        val twUlb: ResourceContainer = mockk()
        val tnUlb: ResourceContainer = mockk()
        val tqUlb: ResourceContainer = mockk()

        TestUtils.setPropertyReflection(language, "slug", "en")
        TestUtils.setPropertyReflection(project, "slug", "mrk")
        TestUtils.setPropertyReflection(resource, "slug", "ulb")

        every { library.download(language.slug, project.slug, resource.slug) }.returns(rcUlb)
        every { library.download(language.slug, "bible", "tw") }
            .throws(Exception("An error occurred."))
        every { library.download(language.slug, project.slug, "tn") }.returns(tnUlb)
        every { library.download(language.slug, project.slug, "tq") }.returns(tqUlb)

        val result = DownloadResourceContainers(library)
            .download(translation, progressListener)

        assertTrue(result.success)
        assertEquals(3, result.containers.size)

        assertEquals(rcUlb, result.containers[0])
        assertEquals(tnUlb, result.containers[1])
        assertEquals(tqUlb, result.containers[2])
        assertFalse(result.containers.contains(twUlb))

        verifySequence {
            progressListener.onProgress(any(), any(), "Downloading resource container")
            progressListener.onProgress(any(), any(), "Downloading translation words")
            progressListener.onProgress(any(), any(), "Downloading translation notes")
            progressListener.onProgress(any(), any(), "Downloading translation questions")
        }

        verify { library.download(language.slug, project.slug, resource.slug) }
        verify { library.download(language.slug, "bible", "tw") }
        verify { library.download(language.slug, project.slug, "tn") }
        verify { library.download(language.slug, project.slug, "tq") }
    }

    @Test
    fun `test download ulb resource with tn exception`() {
        val rcUlb: ResourceContainer = mockk()
        val twUlb: ResourceContainer = mockk()
        val tnUlb: ResourceContainer = mockk()
        val tqUlb: ResourceContainer = mockk()

        TestUtils.setPropertyReflection(language, "slug", "en")
        TestUtils.setPropertyReflection(project, "slug", "mrk")
        TestUtils.setPropertyReflection(resource, "slug", "ulb")

        every { library.download(language.slug, project.slug, resource.slug) }.returns(rcUlb)
        every { library.download(language.slug, "bible", "tw") }.returns(twUlb)
        every { library.download(language.slug, project.slug, "tn") }
            .throws(Exception("An error occurred."))
        every { library.download(language.slug, project.slug, "tq") }.returns(tqUlb)

        val result = DownloadResourceContainers(library)
            .download(translation, progressListener)

        assertTrue(result.success)
        assertEquals(3, result.containers.size)

        assertEquals(rcUlb, result.containers[0])
        assertEquals(twUlb, result.containers[1])
        assertEquals(tqUlb, result.containers[2])
        assertFalse(result.containers.contains(tnUlb))

        verifySequence {
            progressListener.onProgress(any(), any(), "Downloading resource container")
            progressListener.onProgress(any(), any(), "Downloading translation words")
            progressListener.onProgress(any(), any(), "Downloading translation notes")
            progressListener.onProgress(any(), any(), "Downloading translation questions")
        }

        verify { library.download(language.slug, project.slug, resource.slug) }
        verify { library.download(language.slug, "bible", "tw") }
        verify { library.download(language.slug, project.slug, "tn") }
        verify { library.download(language.slug, project.slug, "tq") }
    }

    @Test
    fun `test download ulb resource with tq exception`() {
        val rcUlb: ResourceContainer = mockk()
        val twUlb: ResourceContainer = mockk()
        val tnUlb: ResourceContainer = mockk()
        val tqUlb: ResourceContainer = mockk()

        TestUtils.setPropertyReflection(language, "slug", "en")
        TestUtils.setPropertyReflection(project, "slug", "mrk")
        TestUtils.setPropertyReflection(resource, "slug", "ulb")

        every { library.download(language.slug, project.slug, resource.slug) }.returns(rcUlb)
        every { library.download(language.slug, "bible", "tw") }.returns(twUlb)
        every { library.download(language.slug, project.slug, "tn") }.returns(tnUlb)
        every { library.download(language.slug, project.slug, "tq") }
            .throws(Exception("An error occurred."))

        val result = DownloadResourceContainers(library)
            .download(translation, progressListener)

        assertTrue(result.success)
        assertEquals(3, result.containers.size)

        assertEquals(rcUlb, result.containers[0])
        assertEquals(twUlb, result.containers[1])
        assertEquals(tnUlb, result.containers[2])
        assertFalse(result.containers.contains(tqUlb))

        verifySequence {
            progressListener.onProgress(any(), any(), "Downloading resource container")
            progressListener.onProgress(any(), any(), "Downloading translation words")
            progressListener.onProgress(any(), any(), "Downloading translation notes")
            progressListener.onProgress(any(), any(), "Downloading translation questions")
        }

        verify { library.download(language.slug, project.slug, resource.slug) }
        verify { library.download(language.slug, "bible", "tw") }
        verify { library.download(language.slug, project.slug, "tn") }
        verify { library.download(language.slug, project.slug, "tq") }
    }

    @Test
    fun `test download obs resource with tw exception`() {
        val rcObs: ResourceContainer = mockk()
        val twUlb: ResourceContainer = mockk()
        val tnUlb: ResourceContainer = mockk()
        val tqUlb: ResourceContainer = mockk()

        TestUtils.setPropertyReflection(language, "slug", "en")
        TestUtils.setPropertyReflection(project, "slug", "obs")
        TestUtils.setPropertyReflection(resource, "slug", "ulb")

        every { library.download(language.slug, project.slug, resource.slug) }.returns(rcObs)
        every { library.download(language.slug, "bible-obs", "tw") }
            .throws(Exception("An error occurred."))
        every { library.download(language.slug, project.slug, "tn") }.returns(tnUlb)
        every { library.download(language.slug, project.slug, "tq") }.returns(tqUlb)


        val result = DownloadResourceContainers(library)
            .download(translation, progressListener)

        assertTrue(result.success)
        assertEquals(3, result.containers.size)

        assertEquals(rcObs, result.containers[0])
        assertEquals(tnUlb, result.containers[1])
        assertEquals(tqUlb, result.containers[2])
        assertFalse(result.containers.contains(twUlb))

        verifySequence {
            progressListener.onProgress(any(), any(), "Downloading resource container")
            progressListener.onProgress(any(), any(), "Downloading obs translation words")
            progressListener.onProgress(any(), any(), "Downloading translation notes")
            progressListener.onProgress(any(), any(), "Downloading translation questions")
        }

        verify { library.download(language.slug, project.slug, resource.slug) }
        verify { library.download(language.slug, "bible-obs", "tw") }
        verify { library.download(language.slug, project.slug, "tn") }
        verify { library.download(language.slug, project.slug, "tq") }
    }

    @Test
    fun `test download multiple translations by ids`() {
        val ids = listOf(
            "en_mrk_ulb",
            "id_gen_ayt"
        )

        every { index.getTranslation(any()) }.answers {
            val id = firstArg<String>()
            val parts = id.split("_")
            val language = parts[0]
            val project = parts[1]
            val resource = parts[2]
            mockTranslation(
                mockLanguage(language),
                mockProject(project),
                mockResource(resource)
            )
        }

        every { library.download(any(), any(), any()) }.answers {
            val language = firstArg<String>()
            val project = secondArg<String>()
            val resource = thirdArg<String>()
            mockResourceContainer(language, project, resource)
        }

        every { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }.answers {
            val languageSlug = firstArg<String>()
            val projectSlug = secondArg<String>()
            val resourceSlug = thirdArg<String>()
            mockHelpTranslations(languageSlug, projectSlug, resourceSlug)
        }

        val result = DownloadResourceContainers(library)
            .download(ids, progressListener)

        assertEquals(2, result.downloadedTranslations.size)
        assertTrue(result.downloadedTranslations.contains(ids[0]))
        assertTrue(result.downloadedTranslations.contains(ids[1]))

        assertEquals(0, result.failedSourceDownloads.size)
        assertEquals(0, result.failedHelpsDownloads.size)

        // Check if main resource container was downloaded
        ids.forEach { id ->
            assertNotNull(result.downloadedContainers.singleOrNull {
                it.slug == id
            })
        }

        // Check if help translations were downloaded
        verifyDownloadedContainers(result.downloadedContainers, "en", "bible", "tw")
        verifyDownloadedContainers(result.downloadedContainers, "en", "mrk", "tn")
        verifyDownloadedContainers(result.downloadedContainers, "en", "mrk", "tq")

        verifyDownloadedContainers(result.downloadedContainers, "id", "bible", "tw")
        verifyDownloadedContainers(result.downloadedContainers, "id", "gen", "tn")
        verifyDownloadedContainers(result.downloadedContainers, "id", "gen", "tq")

        verify(exactly = 2) { index.getTranslation(any()) }
        verify(exactly = 8) { library.download(any(), any(), any()) }
        verify(exactly = 6) { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 10) { progressListener.onProgress(any(), any(), any()) }

        verifySequence {
            progressListener.onProgress(-1, 2, "")
            progressListener.onProgress(0, 2, "en_mrk_ulb")
            progressListener.onProgress(0, 2, null)
            progressListener.onProgress(0, 2, null)
            progressListener.onProgress(0, 2, null)
            progressListener.onProgress(1, 2, "id_gen_ayt")
            progressListener.onProgress(1, 2, null)
            progressListener.onProgress(1, 2, null)
            progressListener.onProgress(1, 2, null)
            progressListener.onProgress(2, 2, "")
        }
    }

    @Test
    fun `test download one of the translations not found`() {
        val ids = listOf(
            "en_mrk_ulb",
            "id_gen_ayt"
        )

        every { index.getTranslation(ids[0]) }.answers {
            val id = firstArg<String>()
            val parts = id.split("_")
            val language = parts[0]
            val project = parts[1]
            val resource = parts[2]
            mockTranslation(
                mockLanguage(language),
                mockProject(project),
                mockResource(resource)
            )
        }

        every { index.getTranslation(ids[1]) }.throws(Exception("An error occurred"))

        every { library.download(any(), any(), any()) }.answers {
            val language = firstArg<String>()
            val project = secondArg<String>()
            val resource = thirdArg<String>()
            mockResourceContainer(language, project, resource)
        }

        every { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }.answers {
            val languageSlug = firstArg<String>()
            val projectSlug = secondArg<String>()
            val resourceSlug = thirdArg<String>()
            mockHelpTranslations(languageSlug, projectSlug, resourceSlug)
        }

        val result = DownloadResourceContainers(library)
            .download(ids, progressListener)

        assertEquals(1, result.downloadedTranslations.size)
        assertTrue(result.downloadedTranslations.contains(ids[0]))
        assertFalse(result.downloadedTranslations.contains(ids[1]))

        assertEquals(1, result.failedSourceDownloads.size)
        assertEquals(0, result.failedHelpsDownloads.size)

        // Check if main resource container was downloaded
        assertNotNull(result.downloadedContainers.singleOrNull {
            it.slug == ids[0]
        })
        assertNull(result.downloadedContainers.singleOrNull {
            it.slug == ids[1]
        })

        // Check if help translations were downloaded
        verifyDownloadedContainers(result.downloadedContainers, "en", "bible", "tw")
        verifyDownloadedContainers(result.downloadedContainers, "en", "mrk", "tn")
        verifyDownloadedContainers(result.downloadedContainers, "en", "mrk", "tq")

        verifyNotDownloadedContainers(result.downloadedContainers, "id", "bible", "tw")
        verifyNotDownloadedContainers(result.downloadedContainers, "id", "gen", "tn")
        verifyNotDownloadedContainers(result.downloadedContainers, "id", "gen", "tq")

        verify(exactly = 2) { index.getTranslation(any()) }
        verify(exactly = 4) { library.download(any(), any(), any()) }
        verify(exactly = 3) { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 7) { progressListener.onProgress(any(), any(), any()) }

        verifySequence {
            progressListener.onProgress(-1, 2, "")
            progressListener.onProgress(0, 2, "en_mrk_ulb")
            progressListener.onProgress(0, 2, null)
            progressListener.onProgress(0, 2, null)
            progressListener.onProgress(0, 2, null)
            progressListener.onProgress(1, 2, "id_gen_ayt")
            progressListener.onProgress(2, 2, "")
        }
    }

    @Test
    fun `test download one of the translations failed to download`() {
        val ids = listOf(
            "en_mrk_ulb",
            "id_gen_ayt"
        )

        every { index.getTranslation(any()) }.answers {
            val id = firstArg<String>()
            val parts = id.split("_")
            val language = parts[0]
            val project = parts[1]
            val resource = parts[2]
            mockTranslation(
                mockLanguage(language),
                mockProject(project),
                mockResource(resource)
            )
        }

        every { library.download(any(), any(), any()) }.answers {
            val language = firstArg<String>()
            val project = secondArg<String>()
            val resource = thirdArg<String>()
            mockResourceContainer(language, project, resource)
        }

        every { library.download("id", "gen", "ayt") }
            .throws(Exception("An error occurred."))

        every { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }.answers {
            val languageSlug = firstArg<String>()
            val projectSlug = secondArg<String>()
            val resourceSlug = thirdArg<String>()
            mockHelpTranslations(languageSlug, projectSlug, resourceSlug)
        }

        val result = DownloadResourceContainers(library)
            .download(ids, progressListener)

        assertEquals(1, result.downloadedTranslations.size)
        assertTrue(result.downloadedTranslations.contains(ids[0]))
        assertFalse(result.downloadedTranslations.contains(ids[1]))

        assertEquals(1, result.failedSourceDownloads.size)
        assertEquals(0, result.failedHelpsDownloads.size)

        // Check if main resource container was downloaded
        assertNotNull(result.downloadedContainers.singleOrNull {
            it.slug == ids[0]
        })
        assertNull(result.downloadedContainers.singleOrNull {
            it.slug == ids[1]
        })

        // Check if help translations were downloaded
        verifyDownloadedContainers(result.downloadedContainers, "en", "bible", "tw")
        verifyDownloadedContainers(result.downloadedContainers, "en", "mrk", "tn")
        verifyDownloadedContainers(result.downloadedContainers, "en", "mrk", "tq")

        verifyNotDownloadedContainers(result.downloadedContainers, "id", "bible", "tw")
        verifyNotDownloadedContainers(result.downloadedContainers, "id", "gen", "tn")
        verifyNotDownloadedContainers(result.downloadedContainers, "id", "gen", "tq")

        verify(exactly = 2) { index.getTranslation(any()) }
        verify(exactly = 5) { library.download(any(), any(), any()) }
        verify(exactly = 3) { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 7) { progressListener.onProgress(any(), any(), any()) }

        verifySequence {
            progressListener.onProgress(-1, 2, "")
            progressListener.onProgress(0, 2, "en_mrk_ulb")
            progressListener.onProgress(0, 2, null)
            progressListener.onProgress(0, 2, null)
            progressListener.onProgress(0, 2, null)
            progressListener.onProgress(1, 2, "id_gen_ayt")
            progressListener.onProgress(2, 2, "")
        }
    }

    @Test
    fun `test download one of the help translations failed to download`() {
        val ids = listOf(
            "en_mrk_ulb",
            "id_gen_ayt"
        )

        every { index.getTranslation(any()) }.answers {
            val id = firstArg<String>()
            val parts = id.split("_")
            val language = parts[0]
            val project = parts[1]
            val resource = parts[2]
            mockTranslation(
                mockLanguage(language),
                mockProject(project),
                mockResource(resource)
            )
        }

        every { library.download(any(), any(), any()) }.answers {
            val language = firstArg<String>()
            val project = secondArg<String>()
            val resource = thirdArg<String>()
            mockResourceContainer(language, project, resource)
        }

        every { library.download("id", "gen", "tn") }
            .throws(Exception("An error occurred."))

        every { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }.answers {
            val languageSlug = firstArg<String>()
            val projectSlug = secondArg<String>()
            val resourceSlug = thirdArg<String>()
            mockHelpTranslations(languageSlug, projectSlug, resourceSlug)
        }

        val result = DownloadResourceContainers(library)
            .download(ids, progressListener)

        assertEquals(1, result.downloadedTranslations.size)
        assertTrue(result.downloadedTranslations.contains(ids[0]))
        assertFalse(result.downloadedTranslations.contains(ids[1]))

        assertEquals(1, result.failedSourceDownloads.size)
        assertEquals(1, result.failedHelpsDownloads.size)

        // Check if main resource container was downloaded
        assertNotNull(result.downloadedContainers.singleOrNull {
            it.slug == ids[0]
        })
        assertNotNull(result.downloadedContainers.singleOrNull {
            it.slug == ids[1]
        })

        // Check if help translations were downloaded
        verifyDownloadedContainers(result.downloadedContainers, "en", "bible", "tw")
        verifyDownloadedContainers(result.downloadedContainers, "en", "mrk", "tn")
        verifyDownloadedContainers(result.downloadedContainers, "en", "mrk", "tq")

        verifyDownloadedContainers(result.downloadedContainers, "id", "bible", "tw")
        verifyNotDownloadedContainers(result.downloadedContainers, "id", "gen", "tn")
        verifyDownloadedContainers(result.downloadedContainers, "id", "gen", "tq")

        verify(exactly = 2) { index.getTranslation(any()) }
        verify(exactly = 8) { library.download(any(), any(), any()) }
        verify(exactly = 6) { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 10) { progressListener.onProgress(any(), any(), any()) }

        verifySequence {
            progressListener.onProgress(-1, 2, "")
            progressListener.onProgress(0, 2, "en_mrk_ulb")
            progressListener.onProgress(0, 2, null)
            progressListener.onProgress(0, 2, null)
            progressListener.onProgress(0, 2, null)
            progressListener.onProgress(1, 2, "id_gen_ayt")
            progressListener.onProgress(1, 2, null)
            progressListener.onProgress(1, 2, null)
            progressListener.onProgress(1, 2, null)
            progressListener.onProgress(2, 2, "")
        }
    }

    @Test
    fun `test download multiple translations by ids with one obs resource`() {
        val ids = listOf(
            "en_mrk_ulb",
            "id_obs_ulb"
        )

        every { index.getTranslation(any()) }.answers {
            val id = firstArg<String>()
            val parts = id.split("_")
            val language = parts[0]
            val project = parts[1]
            val resource = parts[2]
            mockTranslation(
                mockLanguage(language),
                mockProject(project),
                mockResource(resource)
            )
        }

        every { library.download(any(), any(), any()) }.answers {
            val language = firstArg<String>()
            val project = secondArg<String>()
            val resource = thirdArg<String>()
            mockResourceContainer(language, project, resource)
        }

        every { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }.answers {
            val languageSlug = firstArg<String>()
            val projectSlug = secondArg<String>()
            val resourceSlug = thirdArg<String>()
            mockHelpTranslations(languageSlug, projectSlug, resourceSlug)
        }

        val result = DownloadResourceContainers(library)
            .download(ids, progressListener)

        assertEquals(2, result.downloadedTranslations.size)
        assertTrue(result.downloadedTranslations.contains(ids[0]))
        assertTrue(result.downloadedTranslations.contains(ids[1]))

        assertEquals(0, result.failedSourceDownloads.size)
        assertEquals(0, result.failedHelpsDownloads.size)

        // Check if main resource container was downloaded
        ids.forEach { id ->
            assertNotNull(result.downloadedContainers.singleOrNull {
                it.slug == id
            })
        }

        // Check if help translations were downloaded
        verifyDownloadedContainers(result.downloadedContainers, "en", "bible", "tw")
        verifyDownloadedContainers(result.downloadedContainers, "en", "mrk", "tn")
        verifyDownloadedContainers(result.downloadedContainers, "en", "mrk", "tq")

        verifyDownloadedContainers(result.downloadedContainers, "id", "bible-obs", "tw")
        verifyDownloadedContainers(result.downloadedContainers, "id", "obs", "tn")
        verifyDownloadedContainers(result.downloadedContainers, "id", "obs", "tq")

        verify(exactly = 2) { index.getTranslation(any()) }
        verify(exactly = 8) { library.download(any(), any(), any()) }
        verify(exactly = 6) { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 10) { progressListener.onProgress(any(), any(), any()) }

        verifySequence {
            progressListener.onProgress(-1, 2, "")
            progressListener.onProgress(0, 2, "en_mrk_ulb")
            progressListener.onProgress(0, 2, null)
            progressListener.onProgress(0, 2, null)
            progressListener.onProgress(0, 2, null)
            progressListener.onProgress(1, 2, "id_obs_ulb")
            progressListener.onProgress(1, 2, null)
            progressListener.onProgress(1, 2, null)
            progressListener.onProgress(1, 2, null)
            progressListener.onProgress(2, 2, "")
        }
    }

    private fun verifyDownloadedContainers(
        containers: List<ResourceContainer>,
        language: String,
        project: String,
        resource: String
    ) {
        assertNotNull(containers.singleOrNull {
            it.language.slug == language && it.project.slug == project && it.resource.slug == resource
        })
    }

    private fun verifyNotDownloadedContainers(
        containers: List<ResourceContainer>,
        language: String,
        project: String,
        resource: String
    ) {
        assertNull(containers.singleOrNull {
            it.language.slug == language && it.project.slug == project && it.resource.slug == resource
        })
    }

    private fun mockTranslation(
        language: Language,
        project: Project,
        resource: Resource
    ): Translation {
        val translation: Translation = mockk()

        TestUtils.setPropertyReflection(translation, "language", language)
        TestUtils.setPropertyReflection(translation, "project", project)
        TestUtils.setPropertyReflection(translation, "resource", resource)

        return translation
    }

    private fun mockLanguage(slug: String): Language {
        val language: Language = mockk()
        TestUtils.setPropertyReflection(language, "slug", slug)
        return language
    }

    private fun mockProject(slug: String): Project {
        val project: Project = mockk()
        TestUtils.setPropertyReflection(project, "slug", slug)
        return project
    }

    private fun mockResource(slug: String): Resource {
        val resource: Resource = mockk()
        TestUtils.setPropertyReflection(resource, "slug", slug)
        return resource
    }

    private fun mockHelpTranslations(lang: String, book: String, res: String): List<Translation> {
        val language = mockLanguage(lang)
        val project = mockProject(book)
        val resource = mockResource(res)

        return listOf(
            mockTranslation(language, project, resource),
        )
    }

    private fun mockResourceContainer(
        language: String,
        project: String,
        resource: String
    ): ResourceContainer {
        return mockk<ResourceContainer> {
            val slug = ContainerTools.makeSlug(language, project, resource)
            TestUtils.setPropertyReflection(this, "language", mockLanguage(language))
            TestUtils.setPropertyReflection(this, "project", mockProject(project))
            TestUtils.setPropertyReflection(this, "resource", mockResource(resource))
            TestUtils.setPropertyReflection(this, "slug", slug)
        }
    }
}