package com.door43.usecases

import android.content.Context
import com.door43.TestUtils
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TargetTranslationMigrator
import com.door43.util.FileUtilities
import io.mockk.MockKAnnotations
import io.mockk.runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.Translation
import org.unfoldingword.resourcecontainer.Language
import org.unfoldingword.resourcecontainer.Project
import org.unfoldingword.resourcecontainer.Resource
import java.io.File

class BackupRCTest {

    @MockK private lateinit var context: Context
    @MockK private lateinit var directoryProvider: IDirectoryProvider
    @MockK private lateinit var migrator: TargetTranslationMigrator
    @MockK private lateinit var exportProjects: ExportProjects
    @MockK private lateinit var profile: Profile
    @MockK private lateinit var library: Door43Client
    @MockK private lateinit var translation: Translation
    @MockK private lateinit var language: Language
    @MockK private lateinit var project: Project
    @MockK private lateinit var resource: Resource
    @MockK private lateinit var targetTranslation: TargetTranslation

    private lateinit var backupRC: BackupRC

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        backupRC = BackupRC(
            context,
            directoryProvider,
            migrator,
            exportProjects,
            profile,
            library
        )

        mockkStatic(FileUtilities::class)

        every { FileUtilities.deleteQuietly(any()) }.returns(true)
        every { FileUtilities.copyFile(any(), any()) } just runs

        every { directoryProvider.backupsDir }.returns(File("/backups"))
        every { profile.nativeSpeaker }.returns(mockk())

        TestUtils.setPropertyReflection(translation, "language", language)
        TestUtils.setPropertyReflection(translation, "project", project)
        TestUtils.setPropertyReflection(translation, "resource", resource)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test backupResourceContainer with valid translation`() {
        TestUtils.setPropertyReflection(language, "slug", "fa")
        TestUtils.setPropertyReflection(project, "slug", "mrk")
        TestUtils.setPropertyReflection(resource, "slug", "nmv")
        TestUtils.setPropertyReflection(translation, "resourceContainerSlug", "fa_mrk_nmv")

        every {
            library.exportResourceContainer(
                any(),
                "fa",
                "mrk",
                "nmv"
            )
        } just runs

        val backupFile = backupRC.backupResourceContainer(translation)

        assertEquals("/backups/fa_mrk_nmv.tsrc", backupFile.path)

        verify {
            library.exportResourceContainer(
                any(),
                "fa",
                "mrk",
                "nmv"
            )
        }
    }

    @Test
    fun `test backupResourceContainer throws exception`() {
        TestUtils.setPropertyReflection(language, "slug", "fa")
        TestUtils.setPropertyReflection(project, "slug", "mrk")
        TestUtils.setPropertyReflection(resource, "slug", "nmv")
        TestUtils.setPropertyReflection(translation, "resourceContainerSlug", "fa_mrk_nmv")

        every {
            library.exportResourceContainer(
                any(),
                "fa",
                "mrk",
                "nmv"
            )
        }.throws(Exception("backup failed"))

        assertThrows("backup failed", Exception::class.java) {
            backupRC.backupResourceContainer(translation)
        }

        verify {
            library.exportResourceContainer(
                any(),
                "fa",
                "mrk",
                "nmv"
            )
        }
    }

    @Test
    fun `test backupTargetTranslation with valid targetTranslation`() {
        val tempFile: File = mockk()
        every { tempFile.exists() }.returns(true)
        every { tempFile.isFile }.returns(true)

        every { targetTranslation.id }.returns("aa_mrk_text_reg")
        every { targetTranslation.commitHash }.returns("abcdefghijklmnopqrstuvwxyz")
        every {
            directoryProvider.createTempFile(
                "aa_mrk_text_reg",
                ".tstudio",
                null
            )
        }.returns(tempFile)
        every { targetTranslation.setDefaultContributor(any()) } just runs
        every { exportProjects.exportProject(targetTranslation, tempFile) }.returns(mockk())

        val success = backupRC.backupTargetTranslation(targetTranslation, false)

        assertTrue(success)

        verify { tempFile.exists() }
        verify { tempFile.isFile }
        verify { targetTranslation.id }
        verify { targetTranslation.commitHash }
        verify {
            directoryProvider.createTempFile(
                "aa_mrk_text_reg",
                ".tstudio",
                null
            )
        }
        verify { targetTranslation.setDefaultContributor(any()) }
        verify { exportProjects.exportProject(targetTranslation, tempFile) }
    }

    @Test
    fun `test backupTargetTranslation orphaned`() {
        val tempFile: File = mockk()
        every { tempFile.exists() }.returns(true)
        every { tempFile.isFile }.returns(true)

        every { targetTranslation.id }.returns("aa_mrk_text_reg")
        every { targetTranslation.commitHash }.returns("abcdefghijklmnopqrstuvwxyz")
        every { directoryProvider.createTempFile(any(), any(), null) }.returns(tempFile)
        every { targetTranslation.setDefaultContributor(any()) } just runs
        every { exportProjects.exportProject(targetTranslation, tempFile) }.returns(mockk())

        val success = backupRC.backupTargetTranslation(targetTranslation, true)

        assertTrue(success)

        verify { tempFile.exists() }
        verify { tempFile.isFile }
        verify { targetTranslation.id }
        verify(exactly = 0) { targetTranslation.commitHash }
        verify { directoryProvider.createTempFile(any(), any(), null) }
        verify { targetTranslation.setDefaultContributor(any()) }
        verify { exportProjects.exportProject(targetTranslation, tempFile) }
    }

    @Test
    fun `test backupTargetTranslation from a directory`() {
        val projectDir: File = mockk()
        every { projectDir.name }.returns("aa_mrk_text_reg")
        val tempFile: File = mockk()
        every { tempFile.exists() }.returns(true)
        every { tempFile.isFile }.returns(true)

        every { directoryProvider.createTempFile(any(), any(), null) }.returns(tempFile)
        every { exportProjects.exportProject(projectDir, tempFile) } just runs

        val success = backupRC.backupTargetTranslation(projectDir)

        assertTrue(success)

        verify { projectDir.name }
        verify { tempFile.exists() }
        verify { tempFile.isFile }
        verify { directoryProvider.createTempFile(any(), any(), null) }
        verify { exportProjects.exportProject(projectDir, tempFile) }
        verify { FileUtilities.deleteQuietly(any()) }
    }

    @Test
    fun `test backupTargetTranslation with no targetTranslation fails`() {
        val success = backupRC.backupTargetTranslation(null, false)

        assertFalse(success)
    }
}