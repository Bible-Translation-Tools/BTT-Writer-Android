package com.door43.translationstudio.usecases

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.TestUtils
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.Translator
import com.door43.usecases.ExamineImportsForCollisions
import com.door43.usecases.ImportProjects
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.unfoldingword.door43client.Door43Client
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ExamineImportsForCollisionsTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject
    @ApplicationContext lateinit var appContext: Context
    @Inject lateinit var examineImportsForCollisions: ExamineImportsForCollisions
    @Inject lateinit var assetsProvider: AssetsProvider
    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var library: Door43Client
    @Inject lateinit var profile: Profile
    @Inject lateinit var importProjects: ImportProjects
    @Inject lateinit var translator: Translator

    private var tempFile: File? = null

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        directoryProvider.clearCache()
        directoryProvider.deleteTranslations()
    }

    @Test
    fun testNoCollisionOnNewProject() {
        val source = "exports/aa_mrk_text_reg.tstudio"

        val targetLanguage = library.index.getTargetLanguage("aa")
        val project = library.index.getProject("aa", "mrk", true)
        val projectName = "${project.name} - ${targetLanguage.name}"

        tempFile = directoryProvider.createTempFile("aa_mrk_text_reg", ".tstudio").also { file ->
            assetsProvider.open(source).use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
        }

        assertEquals("temp asset file should exist", true, tempFile?.exists())

        val uri = Uri.fromFile(tempFile)

        val result = examineImportsForCollisions.execute(uri)

        assertNotNull("Result should not be null", result)
        assertTrue("Success should be true", result.success)
        assertEquals("Content URI should match", uri, result.contentUri)
        assertFalse("alreadyPresent should be false", result.alreadyPresent)
        assertEquals(
            "projectsFound should be equal to project name",
            projectName,
            result.projectsFound
        )
        assertEquals(
            "Source file and project file should be equal",
            tempFile?.length(),
            result.projectFile?.length()
        )
    }

    @Test
    fun testCollisionOnExistentProject() {
        val importSrc = "usfm/mrk.usfm"
        TestUtils.importTargetTranslation(
            library,
            appContext,
            directoryProvider,
            profile,
            assetsProvider,
            importProjects,
            translator,
            "aa",
            importSrc
        )

        val source = "exports/aa_mrk_text_reg.tstudio"

        val targetLanguage = library.index.getTargetLanguage("aa")
        val project = library.index.getProject("aa", "mrk", true)
        val projectName = "${project.name} - ${targetLanguage.name}"

        tempFile = directoryProvider.createTempFile("aa_mrk_text_reg", ".tstudio").also { file ->
            assetsProvider.open(source).use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
        }

        assertEquals("temp asset file should exist", true, tempFile?.exists())

        val uri = Uri.fromFile(tempFile)

        val result = examineImportsForCollisions.execute(uri)

        assertNotNull("Result should not be null", result)
        assertTrue("Success should be true", result.success)
        assertEquals("Content URI should match", uri, result.contentUri)
        assertTrue("alreadyPresent should be true", result.alreadyPresent)
        assertEquals(
            "projectsFound should be equal to project name",
            projectName,
            result.projectsFound
        )
        assertEquals(
            "Source file and project file should be equal",
            tempFile?.length(),
            result.projectFile?.length()
        )
    }
}