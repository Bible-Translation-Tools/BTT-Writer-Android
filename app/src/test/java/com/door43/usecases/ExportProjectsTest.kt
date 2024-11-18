package com.door43.usecases

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import com.door43.TestUtils
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Typography
import com.door43.util.FileUtilities
import com.door43.util.RepoUtils
import com.door43.util.Zip
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.eclipse.jgit.errors.TransportException
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.unfoldingword.door43client.Door43Client
import java.io.File
import java.io.InputStream
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

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        mockkStatic(Uri::class)
        mockkStatic(Zip::class)
        mockkObject(RepoUtils)

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

        every { directoryProvider.writeStringToFile(any(), any()) }.just(runs)

        every { Zip.zipToStream(any(), any()) }.just(runs)
        every { RepoUtils.recover(any()) }.returns(true)
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
    }
}