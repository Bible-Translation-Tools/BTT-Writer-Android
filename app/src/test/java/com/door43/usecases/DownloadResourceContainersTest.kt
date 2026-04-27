package com.door43.usecases

import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifySequence
import kotlinx.coroutines.test.runTest
import org.bibletranslationtools.logger.Logger
import org.bibletranslationtools.resourcecontainer.ContainerTools
import org.bibletranslationtools.resourcecontainer.Language
import org.bibletranslationtools.resourcecontainer.Project
import org.bibletranslationtools.resourcecontainer.Resource
import org.bibletranslationtools.resourcecontainer.ResourceContainer
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

class DownloadResourceContainersTest {

    @MockK private lateinit var library: Door43Client
    @MockK private lateinit var translation: Translation
    @MockK private lateinit var language: Language
    @MockK private lateinit var project: Project
    @MockK private lateinit var resource: Resource
    @MockK private lateinit var index: Index

    val onProgress = mockk<(Float, String?) -> Unit>(relaxed = true)

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        mockkStatic(Logger::class)

        every { translation.language } returns language
        every { translation.project } returns project
        every { translation.resource } returns resource
        every { translation.resourceContainerSlug } returns "en_mat_ulb"

        every { onProgress(any(), any()) }.just(runs)
        every { library.index } returns index
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test download ulb resource container also downloads helps`() = runTest {
        val rcUlb: ResourceContainer = mockk()
        val twUlb: ResourceContainer = mockk()
        val tnUlb: ResourceContainer = mockk()
        val tqUlb: ResourceContainer = mockk()

        every { language.slug } returns("en")
        every { project.slug } returns("mrk")
        every { resource.slug } returns("ulb")

        coEvery { library.download("en", "mrk", "ulb") }.returns(rcUlb)
        coEvery { library.download("en", "bible", "tw") }.returns(twUlb)
        coEvery { library.download("en", "mrk", "tn") }.returns(tnUlb)
        coEvery { library.download("en", "mrk", "tq") }.returns(tqUlb)

        val result = DownloadResourceContainers(library).download(translation, onProgress)

        assertTrue(result.success)
        assertEquals(4, result.containers.size)

        assertEquals(rcUlb, result.containers[0])
        assertEquals(twUlb, result.containers[1])
        assertEquals(tnUlb, result.containers[2])
        assertEquals(tqUlb, result.containers[3])

        verifySequence {
            onProgress(any(), "Downloading resource container")
            onProgress(any(), "Downloading translation words")
            onProgress(any(), "Downloading translation notes")
            onProgress(any(), "Downloading translation questions")
        }

        coVerify { library.download("en", "mrk", "ulb") }
        coVerify { library.download("en", "bible", "tw") }
        coVerify { library.download("en", "mrk", "tn") }
        coVerify { library.download("en", "mrk", "tq") }
    }

    @Test
    fun `test download tw resource container`() = runTest {
        val resourceSlug = "tw"
        val projectSlug = "bible"
        val twUlb: ResourceContainer = mockk()

        every { language.slug } returns("en")
        every { project.slug } returns(projectSlug)
        every { resource.slug } returns(resourceSlug)

        coEvery { library.download("en", projectSlug, resourceSlug) }.returns(twUlb)

        val result = DownloadResourceContainers(library)
            .download(translation, onProgress)

        assertTrue(result.success)
        assertEquals(1, result.containers.size)
        assertEquals(twUlb, result.containers[0])

        verify { onProgress(any(), "Downloading resource container") }
        coVerify { library.download("en", projectSlug, resourceSlug) }
    }

    @Test
    fun `test download tn resource container`() = runTest {
        val resourceSlug = "tn"
        val tnUlb: ResourceContainer = mockk()

        every { language.slug } returns("en")
        every { project.slug } returns("mrk")
        every { resource.slug } returns(resourceSlug)

        coEvery { library.download("en", "mrk", resourceSlug) }.returns(tnUlb)

        val result = DownloadResourceContainers(library)
            .download(translation, onProgress)

        assertTrue(result.success)
        assertEquals(1, result.containers.size)
        assertEquals(tnUlb, result.containers[0])

        verify { onProgress(any(), "Downloading resource container") }
        coVerify { library.download("en", "mrk", resourceSlug) }
    }

    @Test
    fun `test download tq resource container`() = runTest {
        val resourceSlug = "tq"
        val tqUlb: ResourceContainer = mockk()

        every { language.slug } returns("en")
        every { project.slug } returns("mrk")
        every { resource.slug } returns(resourceSlug)

        coEvery { library.download("en", "mrk", resourceSlug) }.returns(tqUlb)

        val result = DownloadResourceContainers(library)
            .download(translation, onProgress)

        assertTrue(result.success)
        assertEquals(1, result.containers.size)
        assertEquals(tqUlb, result.containers[0])

        verify { onProgress(any(), "Downloading resource container") }
        coVerify { library.download("en", "mrk", resourceSlug) }
    }

    @Test
    fun `test download obs resource container also downloads helps`() = runTest {
        val rcUlb: ResourceContainer = mockk()
        val twUlb: ResourceContainer = mockk()
        val tnUlb: ResourceContainer = mockk()
        val tqUlb: ResourceContainer = mockk()

        every { language.slug } returns("en")
        every { project.slug } returns("obs")
        every { resource.slug } returns("ulb")

        coEvery { library.download("en", "obs", "ulb") }.returns(rcUlb)
        coEvery { library.download("en", "bible-obs", "tw") }.returns(twUlb)
        coEvery { library.download("en", "obs", "tn") }.returns(tnUlb)
        coEvery { library.download("en", "obs", "tq") }.returns(tqUlb)

        val result = DownloadResourceContainers(library)
            .download(translation, onProgress)

        assertTrue(result.success)
        assertEquals(4, result.containers.size)

        assertEquals(rcUlb, result.containers[0])
        assertEquals(twUlb, result.containers[1])
        assertEquals(tnUlb, result.containers[2])
        assertEquals(tqUlb, result.containers[3])

        verifySequence {
            onProgress(any(), "Downloading resource container")
            onProgress(any(), "Downloading obs translation words")
            onProgress(any(), "Downloading translation notes")
            onProgress(any(), "Downloading translation questions")
        }

        coVerify { library.download("en", "obs", "ulb") }
        coVerify { library.download("en", "bible-obs", "tw") }
        coVerify { library.download("en", "obs", "tn") }
        coVerify { library.download("en", "obs", "tq") }
    }

    @Test
    fun `test ulb resource with exception downloads nothing`() = runTest {
        every { language.slug } returns("en")
        every { project.slug } returns("mrk")
        every { resource.slug } returns("ulb")

        coEvery { library.download("en", "mrk", "ulb") }
            .throws(Exception("An error occurred."))

        val result = DownloadResourceContainers(library)
            .download(translation, onProgress)

        assertFalse(result.success)
        assertEquals(0, result.containers.size)

        verify { onProgress(any(), "Downloading resource container") }
        coVerify { library.download("en", "mrk", "ulb") }
    }

    @Test
    fun `test download ulb resource with tw exception`() = runTest {
        val rcUlb: ResourceContainer = mockk()
        val twUlb: ResourceContainer = mockk()
        val tnUlb: ResourceContainer = mockk()
        val tqUlb: ResourceContainer = mockk()

        every { language.slug } returns("en")
        every { project.slug } returns("mrk")
        every { resource.slug } returns("ulb")

        coEvery { library.download("en", "mrk", "ulb") }.returns(rcUlb)
        coEvery { library.download("en", "bible", "tw") }
            .throws(Exception("An error occurred."))
        coEvery { library.download("en", "mrk", "tn") }.returns(tnUlb)
        coEvery { library.download("en", "mrk", "tq") }.returns(tqUlb)

        val result = DownloadResourceContainers(library)
            .download(translation, onProgress)

        assertTrue(result.success)
        assertEquals(3, result.containers.size)

        assertEquals(rcUlb, result.containers[0])
        assertEquals(tnUlb, result.containers[1])
        assertEquals(tqUlb, result.containers[2])
        assertFalse(result.containers.contains(twUlb))

        verifySequence {
            onProgress(any(), "Downloading resource container")
            onProgress(any(), "Downloading translation words")
            onProgress(any(), "Downloading translation notes")
            onProgress(any(), "Downloading translation questions")
        }

        coVerify { library.download("en", "mrk", "ulb") }
        coVerify { library.download("en", "bible", "tw") }
        coVerify { library.download("en", "mrk", "tn") }
        coVerify { library.download("en", "mrk", "tq") }
    }

    @Test
    fun `test download ulb resource with tn exception`() = runTest {
        val rcUlb: ResourceContainer = mockk()
        val twUlb: ResourceContainer = mockk()
        val tnUlb: ResourceContainer = mockk()
        val tqUlb: ResourceContainer = mockk()

        every { language.slug } returns("en")
        every { project.slug } returns("mrk")
        every { resource.slug } returns("ulb")

        coEvery { library.download("en", "mrk", "ulb") }.returns(rcUlb)
        coEvery { library.download("en", "bible", "tw") }.returns(twUlb)
        coEvery { library.download("en", "mrk", "tn") }
            .throws(Exception("An error occurred."))
        coEvery { library.download("en", "mrk", "tq") }.returns(tqUlb)

        val result = DownloadResourceContainers(library)
            .download(translation, onProgress)

        assertTrue(result.success)
        assertEquals(3, result.containers.size)

        assertEquals(rcUlb, result.containers[0])
        assertEquals(twUlb, result.containers[1])
        assertEquals(tqUlb, result.containers[2])
        assertFalse(result.containers.contains(tnUlb))

        verifySequence {
            onProgress(any(), "Downloading resource container")
            onProgress(any(), "Downloading translation words")
            onProgress(any(), "Downloading translation notes")
            onProgress(any(), "Downloading translation questions")
        }

        coVerify { library.download("en", "mrk", "ulb") }
        coVerify { library.download("en", "bible", "tw") }
        coVerify { library.download("en", "mrk", "tn") }
        coVerify { library.download("en", "mrk", "tq") }
    }

    @Test
    fun `test download ulb resource with tq exception`() = runTest {
        val rcUlb: ResourceContainer = mockk()
        val twUlb: ResourceContainer = mockk()
        val tnUlb: ResourceContainer = mockk()
        val tqUlb: ResourceContainer = mockk()

        every { language.slug } returns("en")
        every { project.slug } returns("mrk")
        every { resource.slug } returns("ulb")

        coEvery { library.download("en", "mrk", "ulb") }.returns(rcUlb)
        coEvery { library.download("en", "bible", "tw") }.returns(twUlb)
        coEvery { library.download("en", "mrk", "tn") }.returns(tnUlb)
        coEvery { library.download("en", "mrk", "tq") }
            .throws(Exception("An error occurred."))

        val result = DownloadResourceContainers(library)
            .download(translation, onProgress)

        assertTrue(result.success)
        assertEquals(3, result.containers.size)

        assertEquals(rcUlb, result.containers[0])
        assertEquals(twUlb, result.containers[1])
        assertEquals(tnUlb, result.containers[2])
        assertFalse(result.containers.contains(tqUlb))

        verifySequence {
            onProgress(any(), "Downloading resource container")
            onProgress(any(), "Downloading translation words")
            onProgress(any(), "Downloading translation notes")
            onProgress(any(), "Downloading translation questions")
        }

        coVerify { library.download("en", "mrk", "ulb") }
        coVerify { library.download("en", "bible", "tw") }
        coVerify { library.download("en", "mrk", "tn") }
        coVerify { library.download("en", "mrk", "tq") }
    }

    @Test
    fun `test download obs resource with tw exception`() = runTest {
        val rcObs: ResourceContainer = mockk()
        val twUlb: ResourceContainer = mockk()
        val tnUlb: ResourceContainer = mockk()
        val tqUlb: ResourceContainer = mockk()

        every { language.slug } returns("en")
        every { project.slug } returns("obs")
        every { resource.slug } returns("ulb")

        coEvery { library.download("en", "obs", "ulb") }.returns(rcObs)
        coEvery { library.download("en", "bible-obs", "tw") }
            .throws(Exception("An error occurred."))
        coEvery { library.download("en", "obs", "tn") }.returns(tnUlb)
        coEvery { library.download("en", "obs", "tq") }.returns(tqUlb)


        val result = DownloadResourceContainers(library)
            .download(translation, onProgress)

        assertTrue(result.success)
        assertEquals(3, result.containers.size)

        assertEquals(rcObs, result.containers[0])
        assertEquals(tnUlb, result.containers[1])
        assertEquals(tqUlb, result.containers[2])
        assertFalse(result.containers.contains(twUlb))

        verifySequence {
            onProgress(any(), "Downloading resource container")
            onProgress(any(), "Downloading obs translation words")
            onProgress(any(), "Downloading translation notes")
            onProgress(any(), "Downloading translation questions")
        }

        coVerify { library.download("en", "obs", "ulb") }
        coVerify { library.download("en", "bible-obs", "tw") }
        coVerify { library.download("en", "obs", "tn") }
        coVerify { library.download("en", "obs", "tq") }
    }

    @Test
    fun `test download multiple translations by ids`() = runTest {
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

        coEvery { library.download(any(), any(), any()) }.answers {
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
            .download(ids, onProgress)

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
        coVerify(exactly = 8) { library.download(any(), any(), any()) }
        verify(exactly = 6) { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 10) { onProgress(any(), any()) }

        verifySequence {
            onProgress(-1f, "")
            onProgress(0f, "en_mrk_ulb")
            onProgress(0f, "en_bible_tw")
            onProgress(0f, "en_mrk_tn")
            onProgress(0f, "en_mrk_tq")
            onProgress(0.5f, "id_gen_ayt")
            onProgress(0.5f, "id_bible_tw")
            onProgress(0.5f, "id_gen_tn")
            onProgress(0.5f, "id_gen_tq")
            onProgress(1f, "")
        }
    }

    @Test
    fun `test download one of the translations not found`() = runTest {
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

        coEvery { library.download(any(), any(), any()) }.answers {
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
            .download(ids, onProgress)

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
        coVerify(exactly = 4) { library.download(any(), any(), any()) }
        verify(exactly = 3) { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 7) { onProgress(any(), any()) }

        verifySequence {
            onProgress(-1f, "")
            onProgress(0f, "en_mrk_ulb")
            onProgress(0f, "en_bible_tw")
            onProgress(0f, "en_mrk_tn")
            onProgress(0f, "en_mrk_tq")
            onProgress(0.5f, "id_gen_ayt")
            onProgress(1f, "")
        }
    }

    @Test
    fun `test download one of the translations failed to download`() = runTest {
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

        coEvery { library.download(any(), any(), any()) }.answers {
            val language = firstArg<String>()
            val project = secondArg<String>()
            val resource = thirdArg<String>()
            mockResourceContainer(language, project, resource)
        }

        coEvery { library.download("id", "gen", "ayt") }
            .throws(Exception("An error occurred."))

        every { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }.answers {
            val languageSlug = firstArg<String>()
            val projectSlug = secondArg<String>()
            val resourceSlug = thirdArg<String>()
            mockHelpTranslations(languageSlug, projectSlug, resourceSlug)
        }

        val result = DownloadResourceContainers(library)
            .download(ids, onProgress)

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
        coVerify(exactly = 5) { library.download(any(), any(), any()) }
        verify(exactly = 3) { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 7) { onProgress(any(), any()) }

        verifySequence {
            onProgress(-1f, "")
            onProgress(0f, "en_mrk_ulb")
            onProgress(0f, "en_bible_tw")
            onProgress(0f, "en_mrk_tn")
            onProgress(0f, "en_mrk_tq")
            onProgress(0.5f, "id_gen_ayt")
            onProgress(1f, "")
        }
    }

    @Test
    fun `test download one of the help translations failed to download`() = runTest {
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

        coEvery { library.download(any(), any(), any()) }.answers {
            val language = firstArg<String>()
            val project = secondArg<String>()
            val resource = thirdArg<String>()
            mockResourceContainer(language, project, resource)
        }

        coEvery { library.download("id", "gen", "tn") }
            .throws(Exception("An error occurred."))

        every { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }.answers {
            val languageSlug = firstArg<String>()
            val projectSlug = secondArg<String>()
            val resourceSlug = thirdArg<String>()
            mockHelpTranslations(languageSlug, projectSlug, resourceSlug)
        }

        val result = DownloadResourceContainers(library)
            .download(ids, onProgress)

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
        coVerify(exactly = 8) { library.download(any(), any(), any()) }
        verify(exactly = 6) { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 10) { onProgress(any(), any()) }

        verifySequence {
            onProgress(-1f, "")
            onProgress(0f, "en_mrk_ulb")
            onProgress(0f, "en_bible_tw")
            onProgress(0f, "en_mrk_tn")
            onProgress(0f, "en_mrk_tq")
            onProgress(0.5f, "id_gen_ayt")
            onProgress(0.5f, "id_bible_tw")
            onProgress(0.5f, "id_gen_tn")
            onProgress(0.5f, "id_gen_tq")
            onProgress(1f, "")
        }
    }

    @Test
    fun `test download multiple translations by ids with one obs resource`() = runTest {
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

        coEvery { library.download(any(), any(), any()) }.answers {
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
            .download(ids, onProgress)

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
        coVerify(exactly = 8) { library.download(any(), any(), any()) }
        verify(exactly = 6) { index.findTranslations(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 10) { onProgress(any(), any()) }

        verifySequence {
            onProgress(-1f, "")
            onProgress(0f, "en_mrk_ulb")
            onProgress(0f, "en_bible_tw")
            onProgress(0f, "en_mrk_tn")
            onProgress(0f, "en_mrk_tq")
            onProgress(0.5f, "id_obs_ulb")
            onProgress(0.5f, "id_bible-obs_tw")
            onProgress(0.5f, "id_obs_tn")
            onProgress(0.5f, "id_obs_tq")
            onProgress(1f, "")
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

        every { translation.language } returns language
        every { translation.project } returns project
        every { translation.resource } returns resource
        every { translation.resourceContainerSlug } returns "${language.slug}_${project.slug}_${resource.slug}"

        return translation
    }

    private fun mockLanguage(id: String): Language {
        val language: Language = mockk {
            every { slug } returns(id)
        }
        return language
    }

    private fun mockProject(id: String): Project {
        val project: Project = mockk {
            every { slug } returns(id)
        }
        return project
    }

    private fun mockResource(id: String): Resource {
        val resource: Resource = mockk {
            every { slug } returns(id)
        }
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
        languageSlug: String,
        projectSlug: String,
        resourceSlug: String
    ): ResourceContainer {
        return mockk<ResourceContainer> {
            val rcSlug = ContainerTools.makeSlug(languageSlug, projectSlug, resourceSlug)
            every { language }.returns(mockLanguage(languageSlug))
            every { project }.returns(mockProject(projectSlug))
            every { resource }.returns(mockResource(resourceSlug))
            every { slug }.returns(rcSlug)
        }
    }
}