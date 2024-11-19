package com.door43.usecases

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import com.door43.TestUtils
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.R
import com.door43.translationstudio.core.ChapterTranslation
import com.door43.translationstudio.core.FrameTranslation
import com.door43.translationstudio.core.PdfPrinter
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.core.Translator.Companion.TSTUDIO_EXTENSION
import com.door43.translationstudio.core.Translator.Companion.ZIP_EXTENSION
import com.door43.translationstudio.core.Typography
import com.door43.usecases.ExportProjects.ExportType
import com.door43.util.FileUtilities
import com.door43.util.RepoUtils
import com.door43.util.Zip
import com.itextpdf.text.pdf.BaseFont
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.eclipse.jgit.errors.TransportException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.Index
import org.unfoldingword.resourcecontainer.Project
import org.unfoldingword.resourcecontainer.Resource
import java.io.File
import java.io.OutputStream


class ExportProjectsTest {

    @JvmField
    @Rule
    var tempDir: TemporaryFolder = TemporaryFolder()

    @MockK private lateinit var context: Context
    @MockK private lateinit var directoryProvider: IDirectoryProvider
    @MockK private lateinit var library: Door43Client
    @MockK private lateinit var typography: Typography
    @MockK private lateinit var targetTranslation: TargetTranslation
    @MockK private lateinit var contentResolver: ContentResolver
    @MockK private lateinit var packageManager: PackageManager
    @MockK private lateinit var packageInfo: PackageInfo
    @MockK private lateinit var index: Index

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        mockkStatic(Uri::class)
        mockkStatic(Zip::class)
        mockkObject(RepoUtils)
        mockkStatic(FileUtilities::class)
        mockkObject(ExportProjects.BookData)
        mockkConstructor(PdfPrinter::class)
        mockkStatic(BaseFont::class)

        TestUtils.setPropertyReflection(library, "index", index)
        val project: Project = mockk()
        val resource: Resource = mockk()

        every { index.getProject(any(), any(), any()) }.returns(project)
        every { index.getResources(any(), any()) }.returns(listOf(resource))

        TestUtils.setPropertyReflection(project, "slug", "mrk")
        TestUtils.setPropertyReflection(resource, "slug", "ulb")

        every { context.contentResolver }.returns(contentResolver)
        every { context.packageManager }.returns(packageManager)
        every { context.packageName }.returns("org.example.writer")
        every { packageManager.getPackageInfo(any<String>(), any<Int>()) }.returns(packageInfo)

        TestUtils.setPropertyReflection(packageInfo, "versionCode", 10)

        every { targetTranslation.commitSync(any(), any()) }.returns(true)
        every { targetTranslation.commit() }.just(runs)
        every { targetTranslation.id }.returns("aa_mrk_text_ulb")
        every { targetTranslation.commitHash }.returns("abc123")
        every { targetTranslation.targetLanguageDirection }.returns("ltr")
        every { targetTranslation.targetLanguageName }.returns("aa")
        every { targetTranslation.path }.returns(mockk())
        every { targetTranslation.format }.returns(TranslationFormat.DEFAULT)
        every { targetTranslation.projectId }.returns("mrk")

        every { directoryProvider.writeStringToFile(any(), any()) }.just(runs)

        every { Zip.zipToStream(any(), any()) }.just(runs)
        every { RepoUtils.recover(any()) }.returns(true)
        every { FileUtilities.deleteQuietly(any()) }.returns(true)

        every { typography.getAssetPath(any()) }.returns("/fonts/font.ttf")
        every { typography.getFontSize(any()) }.returns(16f)
        every { context.getString(R.string.pref_default_translation_typeface) }
            .returns("font.ttf")
        
        every { anyConstructed<PdfPrinter>().includeMedia(any()) }.just(runs)
        every { anyConstructed<PdfPrinter>().includeIncomplete(any()) }.just(runs)
        every { BaseFont.createFont(any(), any(), any()) }.returns(mockk())
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test export project from file`() {
        val outputFile: File = mockk()
        val uri: Uri = mockk()
        every { Uri.fromFile(any()) }.returns(uri)
        every { directoryProvider.createTempDir(any()) }.returns(tempDir.root)

        val outputStream: OutputStream = mockk()
        every { contentResolver.openOutputStream(uri) }.returns(outputStream)
        every { outputStream.close() } just runs

        ExportProjects(
            context,
            directoryProvider,
            library,
            typography
        ).exportProject(targetTranslation, outputFile)

        verify { Uri.fromFile(any()) }
        verify { directoryProvider.createTempDir(any()) }
        verify { contentResolver.openOutputStream(uri) }
        verify { outputStream.close() }
        verify { Zip.zipToStream(any(), any()) }
        verify { FileUtilities.deleteQuietly(any()) }
        verify { targetTranslation.commitSync(any(), any()) }
        verify { targetTranslation.commit() }
    }

    @Test
    fun `test export project from uri recovering bad repo`() {
        val uri: Uri = mockk()
        every { directoryProvider.createTempDir(any()) }.returns(tempDir.root)

        val outputStream: OutputStream = mockk()
        every { contentResolver.openOutputStream(uri) }.returns(outputStream)
        every { outputStream.close() } just runs

        var called = false
        every { targetTranslation.commit() }.answers {
            if (!called) {
                called = true
                throw TransportException("An error occurred.")
            } else Unit
        }

        ExportProjects(
            context,
            directoryProvider,
            library,
            typography
        ).exportProject(targetTranslation, uri)

        verify(exactly = 2) { directoryProvider.createTempDir(any()) }
        verify(exactly = 1) { contentResolver.openOutputStream(uri) }
        verify(exactly = 1) { outputStream.close() }
        verify { RepoUtils.recover(any()) }
        verify { FileUtilities.deleteQuietly(any()) }
        verify { targetTranslation.commitSync(any(), any()) }
        verify { targetTranslation.commit() }
    }

    @Test
    fun `test exporting directory to the specified file`() {
        val projectDir = tempDir.newFolder("project")
        val outFile = tempDir.newFile("output.tstudio")

        ExportProjects(
            context,
            directoryProvider,
            library,
            typography
        ).exportProject(projectDir, outFile)

        verify { Zip.zipToStream(any(), any()) }
    }

    @Test
    fun `test exporting directory to invalid file`() {
        val projectDir = tempDir.newFolder("project")
        val outFile = tempDir.newFile("output.pdf")

        assertThrows(
            "Output file must have '$TSTUDIO_EXTENSION' or '$ZIP_EXTENSION' extension",
            Exception::class.java
        ) {
            ExportProjects(
                context,
                directoryProvider,
                library,
                typography
            ).exportProject(projectDir, outFile)
        }

        verify(exactly = 0) { Zip.zipToStream(any(), any()) }
    }

    @Test
    fun `test exporting nonexistent directory fails`() {
        val projectDir = File("project")
        val outFile = tempDir.newFile("output.zip")

        assertThrows(
            "Project directory doesn't exist.",
            Exception::class.java
        ) {
            ExportProjects(
                context,
                directoryProvider,
                library,
                typography
            ).exportProject(projectDir, outFile)
        }

        verify(exactly = 0) { Zip.zipToStream(any(), any()) }
    }

    @Test
    fun `test export project as USFM file`() {
        val uri: Uri = mockk()

        val dir = tempDir.newFolder("project")
        every { directoryProvider.createTempDir() }.returns(dir)
        val file = tempDir.newFile("output.usfm")
        every { directoryProvider.createTempFile(any(), any(), any()) }.returns(file)

        mockTranslationContents()
        val bookData = mockBookData()

        val outputText = StringBuffer()
        val outputStream: OutputStream = mockk {
            every { write(any(), any(), any()) }.answers {
                val arr = firstArg<ByteArray>()
                outputText.append(String(arr))
                Unit
            }
        }
        every { contentResolver.openOutputStream(uri) }.returns(outputStream)
        every { outputStream.close() } just runs

        val result = ExportProjects(
            context,
            directoryProvider,
            library,
            typography
        ).exportUSFM(targetTranslation, uri)

        assertTrue(result.success)
        assertEquals(uri, result.uri)
        assertEquals(ExportType.USFM, result.exportType)

        assertTrue(file.length() > 0)

        val text = outputText.toString()

        assertTrue(text.contains("\\id mrk Gospel of Mark, Mark, aa, Afar"))
        assertTrue(text.contains("\\ide usfm"))
        assertTrue(text.contains("\\h Gospel of Mark"))
        assertTrue(text.contains("\\toc1 Gospel of Mark"))
        assertTrue(text.contains("\\toc2 Mark"))
        assertTrue(text.contains("\\toc3 mrk"))
        assertTrue(text.contains("\\mt Gospel of Mark"))
        assertTrue(text.contains("\\c 01"))
        assertTrue(text.contains("\\cl Chapter 1"))
        assertTrue(text.contains("\\cd Chapter reference"))
        assertTrue(text.contains("This is a test verse contents"))

        verifyUSFMExport(uri, bookData)

        verify { outputStream.write(any(), any(), any()) }
        verify { outputStream.close() }
    }

    @Test
    fun `test export project as USFM file fails with bad uri`() {
        val uri: Uri = mockk()

        val dir = tempDir.newFolder("project")
        every { directoryProvider.createTempDir() }.returns(dir)
        val file = tempDir.newFile("output.usfm")
        every { directoryProvider.createTempFile(any(), any(), any()) }.returns(file)

        mockTranslationContents()
        val bookData = mockBookData()

        val outputText = StringBuffer()
        val outputStream: OutputStream = mockk {
            every { write(any(), any(), any()) }.answers {
                val arr = firstArg<ByteArray>()
                outputText.append(String(arr))
                Unit
            }
        }
        every { contentResolver.openOutputStream(uri) }.throws(Exception("Bad uri"))
        every { outputStream.close() } just runs

        val result = ExportProjects(
            context,
            directoryProvider,
            library,
            typography
        ).exportUSFM(targetTranslation, uri)

        assertFalse(result.success)
        assertEquals(uri, result.uri)
        assertEquals(ExportType.USFM, result.exportType)

        assertTrue(file.length() > 0)

        val text = outputText.toString()
        assertTrue(text.isEmpty())

        verifyUSFMExport(uri, bookData)

        verify(exactly = 0) { outputStream.write(any(), any(), any()) }
        verify(exactly = 0) { outputStream.close() }
    }

    @Test
    fun `test export project as PDF file`() {
        val uri: Uri = mockk()

        val file = tempDir.newFile("test.pdf")
        every { anyConstructed<PdfPrinter>().print() }.returns(file)

        val outputStream: OutputStream = mockk()
        every { contentResolver.openOutputStream(uri) }.returns(outputStream)
        every { outputStream.close() } just runs

        val result = ExportProjects(
            context,
            directoryProvider,
            library,
            typography
        ).exportPDF(
            targetTranslation,
            uri,
            includeImages = true,
            includeIncompleteFrames = true,
            null
        )

        assertTrue(result.success)
        assertEquals(uri, result.uri)
        assertEquals(ExportType.PDF, result.exportType)

        verifyPDFExport(uri)

        verify { outputStream.close() }
        verify { FileUtilities.deleteQuietly(any()) }
    }

    @Test
    fun `test export project as PDF file with bad uri`() {
        val uri: Uri = mockk()

        val file = tempDir.newFile("test.pdf")
        every { anyConstructed<PdfPrinter>().print() }.returns(file)

        val outputStream: OutputStream = mockk()
        every { contentResolver.openOutputStream(uri) }.throws(Exception("Bad uri"))
        every { outputStream.close() } just runs

        val result = ExportProjects(
            context,
            directoryProvider,
            library,
            typography
        ).exportPDF(
            targetTranslation,
            uri,
            includeImages = true,
            includeIncompleteFrames = true,
            null
        )

        assertFalse(result.success)
        assertEquals(uri, result.uri)
        assertEquals(ExportType.PDF, result.exportType)

        verifyPDFExport(uri)

        verify(exactly = 0) { outputStream.close() }
        verify(exactly = 0) { FileUtilities.deleteQuietly(any()) }
    }

    private fun verifyUSFMExport(
        uri: Uri,
        bookData: ExportProjects.BookData
    ) {
        verify { directoryProvider.createTempDir() }
        verify { directoryProvider.createTempFile(any(), any(), any()) }
        verify { targetTranslation.chapterTranslations }
        verify { targetTranslation.getFrameTranslations(any(), any()) }
        verify { bookData.bookCode }
        verify { bookData.bookTitle }
        verify { bookData.bookName }
        verify { bookData.languageId }
        verify { bookData.languageName }

        verify { ExportProjects.BookData.generate(any(), any()) }
        verify { contentResolver.openOutputStream(uri) }
        verify { FileUtilities.deleteQuietly(any()) }
    }

    private fun verifyPDFExport(uri: Uri) {
        verify { anyConstructed<PdfPrinter>().print() }
        verify { contentResolver.openOutputStream(uri) }
        verify { typography.getAssetPath(any()) }
        verify { typography.getFontSize(any()) }
        verify { context.getString(R.string.pref_default_translation_typeface) }
        verify { anyConstructed<PdfPrinter>().includeMedia(any()) }
        verify { anyConstructed<PdfPrinter>().includeIncomplete(any()) }
        verify { BaseFont.createFont(any(), any(), any()) }
    }

    private fun mockTranslationContents() {
        val chapterTranslation: ChapterTranslation = mockk()
        TestUtils.setPropertyReflection(chapterTranslation, "id", "01")
        TestUtils.setPropertyReflection(chapterTranslation, "title", "Chapter 1")
        TestUtils.setPropertyReflection(chapterTranslation, "reference", "Chapter reference")
        every { targetTranslation.chapterTranslations }.returns(arrayOf(chapterTranslation))

        val frameTranslation: FrameTranslation = mockk()
        TestUtils.setPropertyReflection(frameTranslation, "id", "01")
        TestUtils.setPropertyReflection(frameTranslation, "body", "This is a test verse contents")
        every { targetTranslation.getFrameTranslations(any(), any()) }
            .returns(arrayOf(frameTranslation))
    }

    private fun mockBookData(): ExportProjects.BookData {
        val bookData: ExportProjects.BookData = mockk {
            every { bookCode }.returns("mrk")
            every { bookTitle }.returns("Gospel of Mark")
            every { bookName }.returns("Mark")
            every { languageId }.returns("aa")
            every { languageName }.returns("Afar")
        }
        every { ExportProjects.BookData.generate(any(), any()) }
            .returns(bookData)

        return bookData
    }
}