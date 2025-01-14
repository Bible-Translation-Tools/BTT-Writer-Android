package com.door43.usecases

import android.content.Context
import android.net.Uri
import com.door43.OnProgressListener
import com.door43.data.IDirectoryProvider
import com.door43.util.FileUtilities
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MigrateTranslationsTest {

    @MockK private lateinit var context: Context
    @MockK private lateinit var directoryProvider: IDirectoryProvider
    @MockK private lateinit var importProjects: ImportProjects
    @MockK private lateinit var progressListener: OnProgressListener

    @JvmField
    @Rule
    var tempDir: TemporaryFolder = TemporaryFolder()

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        mockkStatic(FileUtilities::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
        tempDir.delete()
    }

    @Test
    fun `test migration is done`() {
        every { directoryProvider.createTempDir(any()) }.returns(tempDir.newFolder())
        every { directoryProvider.translationsDir }.returns(tempDir.newFolder())
        every { directoryProvider.backupsDir }.returns(tempDir.newFolder())

        justRun { FileUtilities.copyDirectory(any(), any(), any(), any()) }
        every { FileUtilities.deleteQuietly(any()) }.returns(true)

        every { importProjects.importProjects(any(), any(), any()) }.returns(mockk())

        val sourceFolder: Uri = mockk()

        MigrateTranslations(context, importProjects, directoryProvider)
            .execute(sourceFolder, progressListener)

        verify(exactly = 2) { directoryProvider.createTempDir(any()) }
        verify(exactly = 2) { FileUtilities.copyDirectory(any(), any(), any(), any()) }
        verify(exactly = 2) { FileUtilities.deleteQuietly(any()) }

        verify { importProjects.importProjects(any(), any(), any()) }
    }
}