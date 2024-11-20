package com.door43.translationstudio.core

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.Uri
import android.text.TextUtils
import com.door43.OnProgressListener
import com.door43.TestUtils
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import com.door43.translationstudio.R
import com.door43.util.FileUtilities
import io.mockk.runs
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.json.JSONObject
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
    @MockK private lateinit var profile: Profile
    @MockK private lateinit var library: Door43Client
    @MockK private lateinit var assetsProvider: AssetsProvider
    @MockK private lateinit var targetLanguage: TargetLanguage
    @MockK private lateinit var progressListener: OnProgressListener
    @MockK private lateinit var index: Index
    @MockK private lateinit var packageManager: PackageManager
    @MockK private lateinit var packageInfo: PackageInfo
    @MockK private lateinit var contentResolver: ContentResolver

    @MockK private lateinit var mockFile: File
    @MockK private lateinit var mockUri: Uri

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        mockkStatic(FileUtilities::class)
        mockkStatic(TextUtils::class)
        mockkStatic(TargetTranslation::class)

        every { context.resources }.returns(resources)
        every { context.packageManager }.returns(packageManager)
        every { context.packageName }.returns("writer")
        every { context.contentResolver }.returns(contentResolver)

        every { packageManager.getPackageInfo(any(String::class), 0) }.returns(packageInfo)
        every { TargetTranslation.create(any(), any(), any(), any(), any(),
            any(), any(), any(), any()) }.returns(mockk())

        mockStringResources()

        every { directoryProvider.cacheDir } returns File("/cache")
        every { progressListener.onProgress(any(), any(), any()) } just runs

        every { index.getVersifications("en") } returns listOf(
            Versification("en", "English")
        )

        // Use reflection to modify property that is final
        // because mockk can't do that
        TestUtils.setPropertyReflection(library, "index", index)

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

    @Test fun `test Builder creation from JSON string`() {
        val jsonString = getProcessUsfmJsonString()

        val builder = ProcessUSFM.Builder(
            context,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
        builder.fromJsonString(jsonString)
        val processUSFM = builder.build()

        assertNotNull("ProcessUSFM should not be null", processUSFM)
        assertTrue("ProcessUSFM should be successful", processUSFM?.isProcessSuccess ?: false)
        assertEquals("Book missing names should match", 2, processUSFM?.booksMissingNames?.size)
        assertEquals("Import projects should match", 2, processUSFM?.importProjects?.size)
        assertNotNull("Results string should not be null", processUSFM?.resultsString)

        assertTrue(processUSFM?.resultsString?.contains("Found book: Book1") ?: false)
        assertTrue(processUSFM?.resultsString?.contains("Found book: Book2") ?: false)
        assertTrue(processUSFM?.resultsString?.contains("Error1") ?: false)
        assertTrue(processUSFM?.resultsString?.contains("Error2") ?: false)
    }

    @Test
    fun `test Builder creation from JSON object`() {
        val jsonObject = JSONObject(getProcessUsfmJsonString())
        val builder = ProcessUSFM.Builder(
            context,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
        builder.fromJson(jsonObject)
        val processUSFM = builder.build()

        assertNotNull("ProcessUSFM should not be null", processUSFM)
        assertTrue("ProcessUSFM should be successful", processUSFM?.isProcessSuccess ?: false)
        assertEquals("Book missing names should match", 2, processUSFM?.booksMissingNames?.size)
        assertEquals("Import projects should match", 2, processUSFM?.importProjects?.size)
        assertNotNull("Results string should not be null", processUSFM?.resultsString)

        assertTrue(processUSFM?.resultsString?.contains("Found book: Book1") ?: false)
        assertTrue(processUSFM?.resultsString?.contains("Found book: Book2") ?: false)
        assertTrue(processUSFM?.resultsString?.contains("Error1") ?: false)
        assertTrue(processUSFM?.resultsString?.contains("Error2") ?: false)
    }

    @Test fun `test successful file processing`() {
        every { mockFile.name }.returns("mrk.usfm")
        every { FileUtilities.readFileToString(mockFile) }.returns(
            TestUtils.getResource(mockFile.name)?.readText() ?: ""
        )
        mockChunkMarkers()

        TestUtils.setPropertyReflection(targetLanguage, "slug", "aa")

        val processUSFM = ProcessUSFM.Builder(
            context,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromFile(targetLanguage, mockFile, progressListener)
            .build()

        requireNotNull(processUSFM)

        verifyBookResult(processUSFM)

        assertTrue(processUSFM.resultsString.contains("No Errors"))
        verify { mockFile.name }
        verify { FileUtilities.readFileToString(mockFile) }
    }

    @Test fun `test Builder creation from URI`() {
        val inputStream: InputStream = mockk()
        every { FileUtilities.getUriDisplayName(context, mockUri) }.returns("mrk.usfm")
        every { contentResolver.openInputStream(mockUri) }.returns(inputStream)
        every { inputStream.close() } just runs
        every { FileUtilities.readStreamToString(any()) }
            .returns(TestUtils.getResource("mrk.usfm")?.readText() ?: "")
        mockChunkMarkers()

        TestUtils.setPropertyReflection(targetLanguage, "slug", "aa")

        val processUSFM = ProcessUSFM.Builder(
            context,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromUri(targetLanguage, mockUri, progressListener)
            .build()

        requireNotNull(processUSFM)

        verifyBookResult(processUSFM)

        assertTrue(processUSFM.resultsString.contains("No Errors"))
        verify { FileUtilities.getUriDisplayName(context, mockUri) }
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

        TestUtils.setPropertyReflection(targetLanguage, "slug", "aa")
        mockChunkMarkers()

        val processUSFM = ProcessUSFM.Builder(
            context,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromRc(targetLanguage, rcPath, progressListener)
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

        TestUtils.setPropertyReflection(targetLanguage, "slug", "aa")

        val processUSFM = ProcessUSFM.Builder(
            context,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromFile(targetLanguage, mockFile, progressListener)
            .build()

        assertNotNull("ProcessUSFM should not be null", processUSFM)
        requireNotNull(processUSFM)

        verifyBookResult(processUSFM)

        assertFalse(processUSFM.resultsString.contains("No Errors"))
        assertTrue(processUSFM.resultsString.contains("Warning No verses in range 1 to 4 in chapter: 02"))
        assertTrue(processUSFM.resultsString.contains("Warning No verses in range 5 to 8 in chapter: 02"))
        verify { resources.getString(R.string.warning_prefix) }
        verify { resources.getString(R.string.could_not_find_verses_in_chapter) }

        verify { mockFile.name }
        verify { FileUtilities.readFileToString(mockFile) }
    }

    @Test fun `test book with missing verse fails`() {
        every { mockFile.name }.returns("mrk.usfm")
        every { FileUtilities.readFileToString(mockFile) }.returns(
            TestUtils.getResource("mrk-missing-verse.usfm")?.readText() ?: ""
        )
        mockChunkMarkers()

        TestUtils.setPropertyReflection(targetLanguage, "slug", "aa")

        val processUSFM = ProcessUSFM.Builder(
            context,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromFile(targetLanguage, mockFile, progressListener)
            .build()

        assertNotNull("ProcessUSFM should not be null", processUSFM)
        requireNotNull(processUSFM)

        verifyBookResult(processUSFM)

        assertFalse(processUSFM.resultsString.contains("No Errors"))
        assertTrue(processUSFM.resultsString.contains("Warning Missing 1 verse(s) in range 4 to 6 in chapter: 01"))
        verify { resources.getString(R.string.warning_prefix) }
        verify { resources.getString(R.string.missing_verses_in_chapter) }

        verify { mockFile.name }
        verify { FileUtilities.readFileToString(mockFile) }
    }

    @Test fun `test book with missing verse range fails`() {
        every { mockFile.name }.returns("mrk.usfm")
        every { FileUtilities.readFileToString(mockFile) }.returns(
            TestUtils.getResource("mrk-missing-range.usfm")?.readText() ?: ""
        )
        mockChunkMarkers()

        TestUtils.setPropertyReflection(targetLanguage, "slug", "aa")

        val processUSFM = ProcessUSFM.Builder(
            context,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromFile(targetLanguage, mockFile, progressListener)
            .build()

        assertNotNull("ProcessUSFM should not be null", processUSFM)
        requireNotNull(processUSFM)

        verifyBookResult(processUSFM)

        assertFalse(processUSFM.resultsString.contains("No Errors"))
        assertTrue(processUSFM.resultsString.contains("Warning No verses in range 4 to 6 in chapter: 01"))
        verify { resources.getString(R.string.warning_prefix) }
        verify { resources.getString(R.string.could_not_find_verses_in_chapter) }

        verify { mockFile.name }
        verify { FileUtilities.readFileToString(mockFile) }
    }

    @Test fun `test book with extra verse fails`() {
        every { mockFile.name }.returns("mrk.usfm")
        every { FileUtilities.readFileToString(mockFile) }.returns(
            TestUtils.getResource("mrk-extra-verse.usfm")?.readText() ?: ""
        )
        mockChunkMarkers()

        TestUtils.setPropertyReflection(targetLanguage, "slug", "aa")

        val processUSFM = ProcessUSFM.Builder(
            context,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromFile(targetLanguage, mockFile, progressListener)
            .build()

        assertNotNull("ProcessUSFM should not be null", processUSFM)
        requireNotNull(processUSFM)

        verifyBookResult(processUSFM)

        assertFalse(processUSFM.resultsString.contains("No Errors"))
        assertTrue(processUSFM.resultsString.contains("Extra 1 verse(s) in range 5 to 8 in chapter: 02"))
        verify { resources.getString(R.string.warning_prefix) }
        verify { resources.getString(R.string.extra_verses_in_chapter) }

        verify { mockFile.name }
        verify { FileUtilities.readFileToString(mockFile) }
    }

    @Test fun `test processing bad usfm file fails`() {
        every { mockFile.name }.returns("mrk.usfm")
        every { FileUtilities.readFileToString(mockFile) }.returns(
            TestUtils.getResource("mrk-bad-file.usfm")?.readText() ?: ""
        )
        mockChunkMarkers()

        TestUtils.setPropertyReflection(targetLanguage, "slug", "aa")

        val processUSFM = ProcessUSFM.Builder(
            context,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromFile(targetLanguage, mockFile, progressListener)
            .build()

        assertNotNull("ProcessUSFM should not be null", processUSFM)
        requireNotNull(processUSFM)

        assertFalse(processUSFM.resultsString.contains("No Errors"))
        assertTrue(processUSFM.resultsString.contains("Error: Missing book short name"))
        assertEquals(1, processUSFM.booksMissingNames.size)
        assertEquals("mrk.usfm", processUSFM.booksMissingNames.first().description)
        assertEquals("This is not a usfm file", processUSFM.booksMissingNames.first().contents)
        assertTrue(processUSFM.importProjects.isEmpty())
        verify { resources.getString(R.string.error_prefix) }
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

        TestUtils.setPropertyReflection(targetLanguage, "slug", "aa")

        val processUSFM = ProcessUSFM.Builder(
            context,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromFile(targetLanguage, mockFile, progressListener)
            .build()

        assertNotNull("ProcessUSFM should not be null", processUSFM)
        requireNotNull(processUSFM)

        assertFalse(processUSFM.resultsString.contains("No Errors"))
        assertTrue(processUSFM.resultsString.contains("Error: Missing book short name"))
        assertEquals(1, processUSFM.booksMissingNames.size)
        assertEquals("mrk.usfm", processUSFM.booksMissingNames.first().description)
        assertTrue(processUSFM.importProjects.isEmpty())
        verify { resources.getString(R.string.error_prefix) }
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
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromFile(targetLanguage, mockFile, progressListener)
            .build()

        processUSFM?.cleanup()

        assertNotNull(processUSFM)
        verify { FileUtilities.deleteQuietly(any()) }
    }

    @Test fun `test toJson conversion`() {
        val processUSFM = ProcessUSFM.Builder(
            context,
            directoryProvider,
            profile,
            library,
            assetsProvider
        )
            .fromFile(targetLanguage, mockFile, progressListener)
            .build()

        assertNotNull(processUSFM)
        requireNotNull(processUSFM)

        every { targetLanguage.toJSON() }.returns(JSONObject())
        val json = processUSFM.toJson()

        assertNotNull(json)
        requireNotNull(json)

        assertTrue(json.has("TempDir"))
        assertTrue(json.has("SourceFiles"))
        assertTrue(json.has("TempSrc"))
        assertTrue(json.has("ImportProjects"))
        assertTrue(json.has("Success"))
        assertTrue(json.has("Errors"))
        assertTrue(json.has("CurrentBook"))
        assertTrue(json.has("ChapterCount"))
        assertTrue(json.has("FoundBooks"))
        assertTrue(json.has("CurrentChapter"))
        assertTrue(json.has("TempOutput"))
        assertTrue(json.has("TargetLanguage"))
        assertTrue(json.has("MissingNames"))

        assertFalse(json.getBoolean("Success"))
        assertTrue(json.getJSONArray("Errors").getString(0).contains("Error: Error reading File"))

        verify { targetLanguage.toJSON() }
    }

    private fun getProcessUsfmJsonString(): String {
        return """
            {
                "TargetLanguage": {
                    "slug": "aa",
                    "name": "Afar",
                    "anglicized_name": "Afar",
                    "direction": "ltr",
                    "region": "Africa",
                    "is_gateway_language": false
                },
                "TempDir": "/temp/dir",
                "TempOutput": "/temp/output",
                "TempDest": "/temp/dest",
                "TempSrc": "/temp/src",
                "ProjectFolder": "/project/folder",
                "Chapter": "01",
                "SourceFiles": ["/source/file1", "/source/file2"],
                "ImportProjects": ["/import/project1", "/import/project2"],
                "Errors": ["Error1", "Error2"],
                "FoundBooks": ["Book1", "Book2"],
                "CurrentBook": 1,
                "BookName": "Mark",
                "BookShortName": "mrk",
                "Success": true,
                "CurrentChapter": 1,
                "ChapterCount": 10,
                "MissingNames": [
                    {
                        "description": "Description1",
                        "invalidName": "InvalidName1",
                        "contents": "Contents1"
                    },
                    {
                        "description": "Description2",
                        "invalidName": "InvalidName2",
                        "contents": "Contents2"
                    }
                ]
            }
        """.trimIndent()
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
        every {resources.getString(R.string.found_book) } returns "Found book: %s"
        every {resources.getString(R.string.no_error) } returns "No Errors"
        every {resources.getString(R.string.no_verse) } returns "No verse markers found"
        every {resources.getString(R.string.initializing_import) } returns "Initializing Import"
        every {resources.getString(R.string.finished_loading) } returns "Finished Loading"
        every {resources.getString(R.string.file_write_for_verse) } returns "Error writing verse %s"
        every {resources.getString(R.string.error_prefix) } returns "Error: %s"
        every {resources.getString(R.string.warning_prefix) } returns "Warning %s"
        every {resources.getString(R.string.building_manifest) } returns "Building Manifest"
        every {resources.getString(R.string.missing_book_name) } returns "Missing book name"
        every {resources.getString(R.string.missing_book_short_name) } returns "Missing book short name"
        every {resources.getString(R.string.processing_chapter) } returns "Processing chapter: %s"
        every {resources.getString(R.string.could_not_find_chapter) } returns "Could not find chapter: %s"
        every {resources.getString(R.string.file_write_error) } returns "Error writing File"
        every {resources.getString(R.string.file_read_error_detail) } returns "Error reading File '%s'"
        every {resources.getString(R.string.could_not_parse_chapter) } returns "Could not parse chapter: %s"
        every {resources.getString(R.string.could_not_find_verses_in_chapter) } returns "No verses in range %d to %d in chapter: %s"
        every {resources.getString(R.string.missing_verses_in_chapter) } returns "Missing %d verse(s) in range %d to %d in chapter: %s"
        every {resources.getString(R.string.extra_verses_in_chapter) } returns "Extra %d verse(s) in range %d to %d in chapter: %s"
        every {resources.getString(R.string.could_not_parse) } returns "Could not parse '%s'"
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
        verify { packageManager.getPackageInfo(any(String::class), 0) }
        verify { TargetTranslation.create(any(), any(), any(), any(), any(),
            any(), any(), any(), any()) }
        verify { directoryProvider.cacheDir }
        verify { progressListener.onProgress(any(), any(), any()) }
        verify { index.getVersifications("en") }
        verify { FileUtilities.forceMkdir(any()) }
        verify { FileUtilities.writeStringToFile(any(), any()) }
        verify { profile.nativeSpeaker }
    }
}
