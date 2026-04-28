package com.door43.translationstudio.core

import android.content.ContentResolver
import android.content.Context
import android.content.res.Resources
import android.net.Uri
import android.text.TextUtils
import com.door43.TestUtils
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.AppInfo
import com.door43.translationstudio.Platform
import com.door43.translationstudio.R
import com.door43.util.FileUtilities
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.Index
import org.unfoldingword.door43client.models.ChunkMarker
import org.unfoldingword.door43client.models.TargetLanguage
import org.unfoldingword.door43client.models.Versification
import java.io.File
import java.io.InputStream


class ProcessUSFMTest {

    @MockK private lateinit var context: Context
    @MockK private lateinit var resources: Resources
    @MockK private lateinit var directoryProvider: IDirectoryProvider
    @MockK private lateinit var platform: Platform
    @MockK private lateinit var profile: Profile
    @MockK private lateinit var library: Door43Client
    @MockK private lateinit var assetsProvider: AssetsProvider
    @MockK private lateinit var targetLanguage: TargetLanguage
    @MockK private lateinit var index: Index
    @MockK private lateinit var contentResolver: ContentResolver
    @MockK private lateinit var appInfo: AppInfo

    @MockK private lateinit var mockFile: File
    @MockK private lateinit var mockUri: Uri

    val onProgress = mockk<(Float, String?) -> Unit>(relaxed = true)

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        mockkObject(FileUtilities)
        mockkStatic(TextUtils::class)
        mockkObject(TargetTranslation)

        every { context.resources }.returns(resources)
        every { context.contentResolver }.returns(contentResolver)

        every { appInfo.versionCode }.returns(10)
        every { platform.info }.returns(appInfo)

        every { TargetTranslation.create(any(), any(), any(), any(), any(),
            any(), any(), any(), any()) }.returns(mockk())

        mockStringResources()

        every { directoryProvider.cacheDir } returns File("/cache")

        every { index.getVersifications("en") } returns listOf(
            Versification("en", "English")
        )

        // Use reflection to modify property that is final
        // because mockk can't do that
        every { library.index } returns index

        val str1 = slot<String>()
        val str2 = slot<String>()
        every { TextUtils.concat(capture(str1), capture(str2)) }
            .answers { "${str1.captured} ${str2.captured}" }

        every { FileUtilities.forceMkdir(any()) } just runs
        every { FileUtilities.writeStringToFile(any(), any()) } just runs
        every { FileUtilities.deleteQuietly(any()) }.returns(true)

        every { profile.nativeSpeaker }.returns(NativeSpeaker("tester"))
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test fun `test successful file processing`() {
        every { mockFile.name }.returns("mrk.usfm")
        every { FileUtilities.readFileToString(mockFile) }.returns(
            TestUtils.getResource(mockFile.name)?.readText() ?: ""
        )
        mockChunkMarkers()

        every { targetLanguage.slug }.returns("aa")

        val processUSFM = ProcessUSFM.Builder(
            context,
            platform,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromFile(targetLanguage, mockFile, onProgress)
            .build()

        requireNotNull(processUSFM)

        verifyBookResult(processUSFM)

        assertTrue(processUSFM.resultsString.contains("No Errors"))
        verify { mockFile.name }
        verify { FileUtilities.readFileToString(mockFile) }
    }

    @Test fun `test Builder creation from URI`() {
        val inputStream: InputStream = mockk()
        every { FileUtilities.getFileName(context, mockUri) }.returns("mrk.usfm")
        every { contentResolver.openInputStream(mockUri) }.returns(inputStream)
        every { inputStream.close() } just runs
        every { FileUtilities.readStreamToString(any()) }
            .returns(TestUtils.getResource("mrk.usfm")?.readText() ?: "")
        mockChunkMarkers()

        every { targetLanguage.slug }.returns("aa")

        val processUSFM = ProcessUSFM.Builder(
            context,
            platform,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromUri(targetLanguage, mockUri, onProgress)
            .build()

        requireNotNull(processUSFM)

        verifyBookResult(processUSFM)

        assertTrue(processUSFM.resultsString.contains("No Errors"))
        verify { FileUtilities.getFileName(context, mockUri) }
        verify { contentResolver.openInputStream(mockUri) }
        verify { inputStream.close() }
        verify { FileUtilities.readStreamToString(any()) }
    }

    @Test fun `test Builder creation from RC path`() {
        val rcPath = "/rc/mrk.usfm"
        val inputStream: InputStream = mockk()
        every { assetsProvider.open(rcPath) }.returns(inputStream)
        every { inputStream.close() } just runs
        every { FileUtilities.readStreamToString(any()) }
            .returns(TestUtils.getResource("mrk.usfm")?.readText() ?: "")
        every { FileUtilities.getFilename(rcPath) }.returns("mrk.usfm")

        every { targetLanguage.slug }.returns("aa")
        mockChunkMarkers()

        val processUSFM = ProcessUSFM.Builder(
            context,
            platform,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromRc(targetLanguage, rcPath, onProgress)
            .build()

        requireNotNull(processUSFM)

        verifyBookResult(processUSFM)

        verify { assetsProvider.open(rcPath) }
        verify { inputStream.close() }
        verify { FileUtilities.readStreamToString(any()) }
        verify { FileUtilities.getFilename(rcPath) }
    }

    @Test fun `test book with single chapter fails`() {
        every { mockFile.name }.returns("mrk.usfm")
        every { FileUtilities.readFileToString(mockFile) }.returns(
            TestUtils.getResource("mrk-single-chapter.usfm")?.readText() ?: ""
        )
        mockChunkMarkers()

        every { targetLanguage.slug }.returns("aa")

        val processUSFM = ProcessUSFM.Builder(
            context,
            platform,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromFile(targetLanguage, mockFile, onProgress)
            .build()

        assertNotNull("ProcessUSFM should not be null", processUSFM)
        requireNotNull(processUSFM)

        verifyBookResult(processUSFM)

        assertFalse(processUSFM.resultsString.contains("No Errors"))
        assertTrue(processUSFM.resultsString.contains("Warning No verses in range 1 to 4 in chapter: 02"))
        assertTrue(processUSFM.resultsString.contains("Warning No verses in range 5 to 8 in chapter: 02"))
        verify { resources.getString(R.string.warning_prefix, any()) }
        verify { resources.getString(R.string.could_not_find_verses_in_chapter, any(), any(), any()) }

        verify { mockFile.name }
        verify { FileUtilities.readFileToString(mockFile) }
    }

    @Test fun `test book with missing verse fails`() {
        every { mockFile.name }.returns("mrk.usfm")
        every { FileUtilities.readFileToString(mockFile) }.returns(
            TestUtils.getResource("mrk-missing-verse.usfm")?.readText() ?: ""
        )
        mockChunkMarkers()

        every { targetLanguage.slug }.returns("aa")

        val processUSFM = ProcessUSFM.Builder(
            context,
            platform,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromFile(targetLanguage, mockFile, onProgress)
            .build()

        assertNotNull("ProcessUSFM should not be null", processUSFM)
        requireNotNull(processUSFM)

        verifyBookResult(processUSFM)

        assertFalse(processUSFM.resultsString.contains("No Errors"))
        assertTrue(processUSFM.resultsString.contains("Warning Missing 1 verse(s) in range 4 to 6 in chapter: 01"))
        verify { resources.getString(R.string.warning_prefix, any()) }
        verify { resources.getString(R.string.missing_verses_in_chapter, any(), any(), any(), any()) }

        verify { mockFile.name }
        verify { FileUtilities.readFileToString(mockFile) }
    }

    @Test fun `test book with missing verse range fails`() {
        every { mockFile.name }.returns("mrk.usfm")
        every { FileUtilities.readFileToString(mockFile) }.returns(
            TestUtils.getResource("mrk-missing-range.usfm")?.readText() ?: ""
        )
        mockChunkMarkers()

        every { targetLanguage.slug }.returns("aa")

        val processUSFM = ProcessUSFM.Builder(
            context,
            platform,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromFile(targetLanguage, mockFile, onProgress)
            .build()

        assertNotNull("ProcessUSFM should not be null", processUSFM)
        requireNotNull(processUSFM)

        verifyBookResult(processUSFM)

        assertFalse(processUSFM.resultsString.contains("No Errors"))
        assertTrue(processUSFM.resultsString.contains("Warning No verses in range 4 to 6 in chapter: 01"))
        verify { resources.getString(R.string.warning_prefix, any()) }
        verify { resources.getString(R.string.could_not_find_verses_in_chapter, any(), any(), any()) }

        verify { mockFile.name }
        verify { FileUtilities.readFileToString(mockFile) }
    }

    @Test fun `test book with extra verse fails`() {
        every { mockFile.name }.returns("mrk.usfm")
        every { FileUtilities.readFileToString(mockFile) }.returns(
            TestUtils.getResource("mrk-extra-verse.usfm")?.readText() ?: ""
        )
        mockChunkMarkers()

        every { targetLanguage.slug }.returns("aa")

        val processUSFM = ProcessUSFM.Builder(
            context,
            platform,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromFile(targetLanguage, mockFile, onProgress)
            .build()

        assertNotNull("ProcessUSFM should not be null", processUSFM)
        requireNotNull(processUSFM)

        verifyBookResult(processUSFM)

        assertFalse(processUSFM.resultsString.contains("No Errors"))
        assertTrue(processUSFM.resultsString.contains("Extra 1 verse(s) in range 5 to 8 in chapter: 02"))
        verify { resources.getString(R.string.warning_prefix, any()) }
        verify { resources.getString(R.string.extra_verses_in_chapter, any(), any(), any(), any()) }

        verify { mockFile.name }
        verify { FileUtilities.readFileToString(mockFile) }
    }

    @Test fun `test processing bad usfm file fails`() {
        every { mockFile.name }.returns("mrk.usfm")
        every { FileUtilities.readFileToString(mockFile) }.returns(
            TestUtils.getResource("mrk-bad-file.usfm")?.readText() ?: ""
        )
        mockChunkMarkers()

        every { targetLanguage.slug }.returns("aa")

        val processUSFM = ProcessUSFM.Builder(
            context,
            platform,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromFile(targetLanguage, mockFile, onProgress)
            .build()

        assertNotNull("ProcessUSFM should not be null", processUSFM)
        requireNotNull(processUSFM)

        assertFalse(processUSFM.resultsString.contains("No Errors"))
        assertTrue(processUSFM.resultsString.contains("Error: Missing book short name"))
        assertEquals(1, processUSFM.booksMissingNames.size)
        assertEquals("mrk.usfm", processUSFM.booksMissingNames.first().description)
        assertEquals("This is not a usfm file", processUSFM.booksMissingNames.first().contents)
        assertTrue(processUSFM.importProjects.isEmpty())
        verify { resources.getString(R.string.error_prefix, any()) }
        verify { resources.getString(R.string.missing_book_short_name) }

        verify { mockFile.name }
        verify { FileUtilities.readFileToString(mockFile) }

        val missingItem = processUSFM.booksMissingNames.first()
        processUSFM.processText(
            missingItem.contents!!,
            missingItem.description!!,
            false,
            "mrk"
        )

        assertFalse(processUSFM.resultsString.contains("No Errors"))
        assertTrue(processUSFM.resultsString.contains("Error: No verse markers found"))
        assertTrue(processUSFM.importProjects.isEmpty())
        verify {resources.getString(R.string.no_verse) }
    }

    @Test fun `test processing usfm file without header`() {
        every { mockFile.name }.returns("mrk.usfm")
        every { FileUtilities.readFileToString(mockFile) }.returns(
            TestUtils.getResource("mrk-no-header.usfm")?.readText() ?: ""
        )
        mockChunkMarkers()

        every { targetLanguage.slug }.returns("aa")

        val processUSFM = ProcessUSFM.Builder(
            context,
            platform,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromFile(targetLanguage, mockFile, onProgress)
            .build()

        assertNotNull("ProcessUSFM should not be null", processUSFM)
        requireNotNull(processUSFM)

        assertFalse(processUSFM.resultsString.contains("No Errors"))
        assertTrue(processUSFM.resultsString.contains("Error: Missing book short name"))
        assertEquals(1, processUSFM.booksMissingNames.size)
        assertEquals("mrk.usfm", processUSFM.booksMissingNames.first().description)
        assertTrue(processUSFM.importProjects.isEmpty())
        verify { resources.getString(R.string.error_prefix, any()) }
        verify { resources.getString(R.string.missing_book_short_name) }

        verify { mockFile.name }
        verify { FileUtilities.readFileToString(mockFile) }

        val missingItem = processUSFM.booksMissingNames.first()
        processUSFM.processText(
            missingItem.contents!!,
            missingItem.description!!,
            false,
            "mrk"
        )

        verifyBookResult(processUSFM)

        assertTrue(processUSFM.resultsString.contains("Error: Missing book name"))
        assertTrue(processUSFM.importProjects.isNotEmpty())
        verify {resources.getString(R.string.missing_book_name) }
    }

    @Test fun `test cleanup temp directory`() {
        val processUSFM = ProcessUSFM.Builder(
            context,
            platform,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromFile(targetLanguage, mockFile, onProgress)
            .build()

        processUSFM?.cleanup()

        assertNotNull(processUSFM)
        verify { FileUtilities.deleteQuietly(any()) }
    }

    private fun mockChunkMarkers() {
        every { index.getChunkMarkers("mrk", "en") } returns listOf(
            ChunkMarker("1", "1"),
            ChunkMarker("1", "4"),
            ChunkMarker("1", "7"),
            ChunkMarker("1", "16"),
            ChunkMarker("1", "24"),
            ChunkMarker("1", "28"),
            ChunkMarker("2", "1"),
            ChunkMarker("2", "5"),
            ChunkMarker("2", "9"),
        )
    }

    private fun mockStringResources() {
        every {resources.getString(R.string.found_book, any()) } answers {
            "Found book: %s".format((args[1] as Array<*>)[0])
        }
        every {resources.getString(R.string.no_error) } returns "No Errors"
        every {resources.getString(R.string.no_verse) } returns "No verse markers found"
        every {resources.getString(R.string.initializing_import) } returns "Initializing Import"
        every {resources.getString(R.string.finished_loading) } returns "Finished Loading"
        every {resources.getString(R.string.file_write_for_verse, any()) } answers {
            "Error writing verse %s".format((args[1] as Array<*>)[0])
        }
        every {resources.getString(R.string.error_prefix, any()) } answers {
            "Error: %s".format((args[1] as Array<*>)[0])
        }
        every {resources.getString(R.string.warning_prefix, any()) } answers {
            "Warning %s".format((args[1] as Array<*>)[0])
        }
        every {resources.getString(R.string.building_manifest) } returns "Building Manifest"
        every {resources.getString(R.string.missing_book_name) } returns "Missing book name"
        every {resources.getString(R.string.missing_book_short_name) } returns "Missing book short name"
        every {resources.getString(R.string.processing_chapter, any()) } answers {
            "Processing chapter: %s".format((args[1] as Array<*>)[0])
        }
        every {resources.getString(R.string.could_not_find_chapter, any()) } answers {
            "Could not find chapter: %s".format((args[1] as Array<*>)[0])
        }
        every {resources.getString(R.string.file_write_error) } returns "Error writing File"
        every {resources.getString(R.string.file_read_error_detail, any()) } answers {
            "Error reading File '%s'".format((args[1] as Array<*>)[0])
        }
        every {resources.getString(R.string.could_not_parse_chapter, any()) } answers {
            "Could not parse chapter: %s".format((args[1] as Array<*>)[0])
        }
        every {resources.getString(R.string.could_not_find_verses_in_chapter, any(), any(), any()) } answers {
            val varargs = (args[1] as Array<*>)
            "No verses in range %d to %d in chapter: %s".format(varargs[0], varargs[1], varargs[2])
        }
        every {resources.getString(R.string.missing_verses_in_chapter, any(), any(), any(), any()) } answers {
            val varargs = (args[1] as Array<*>)
            "Missing %d verse(s) in range %d to %d in chapter: %s".format(varargs[0], varargs[1], varargs[2], varargs[3])
        }
        every {resources.getString(R.string.extra_verses_in_chapter, any(), any(), any(), any()) } answers {
            val varargs = (args[1] as Array<*>)
            "Extra %d verse(s) in range %d to %d in chapter: %s".format(varargs[0], varargs[1], varargs[2], varargs[3])
        }
        every {resources.getString(R.string.could_not_parse, any()) } answers {
            "Could not parse '%s'".format((args[1] as Array<*>)[0])
        }
        every {resources.getString(R.string.error_reading_file, any())} answers {
            "Error reading file: %s".format((args[1] as Array<*>)[0])
        }
        every { resources.getString(R.string.no_chunk_list, any()) } answers {
            "No chunk list found for '%s'".format((args[1] as Array<*>)[0])
        }
    }

    private fun verifyBookResult(result: ProcessUSFM?) {
        assertNotNull("ProcessUSFM should not be null", result)
        requireNotNull(result)

        assertTrue("ProcessUSFM should be successful", result.isProcessSuccess)
        assertTrue(result.resultsString.contains("Found book: mrk = mrk.usfm"))
        assertEquals(1, result.importProjects.size)
        assertTrue(result.importProjects.first().name.endsWith("mrk-aa"))
        assertTrue(result.booksMissingNames.isEmpty())

        verify { index.getChunkMarkers("mrk", "en") }
        verify { TargetTranslation.create(any(), any(), any(), any(), any(),
            any(), any(), any(), any()) }
        verify { directoryProvider.cacheDir }
        verify { onProgress(any(), any()) }
        verify { index.getVersifications("en") }
        verify { FileUtilities.forceMkdir(any()) }
        verify { FileUtilities.writeStringToFile(any(), any()) }
        verify { profile.nativeSpeaker }
    }
}
