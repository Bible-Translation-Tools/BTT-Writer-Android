package com.door43.usecases

import android.content.Context
import android.content.res.Resources
import androidx.preference.PreferenceManager
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.data.getPrivatePref
import com.door43.data.setDefaultPref
import com.door43.data.setPrivatePref
import com.door43.translationstudio.App
import com.door43.translationstudio.AppInfo
import com.door43.translationstudio.Platform
import com.door43.translationstudio.R
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TargetTranslationMigrator
import com.door43.translationstudio.core.Translator
import com.door43.util.FileUtilities
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.bibletranslationtools.resourcecontainer.ResourceContainer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.Index
import org.unfoldingword.door43client.models.Translation
import java.io.File

class UpdateAppTest {

    @MockK private lateinit var context: Context
    @MockK private lateinit var prefRepository: IPreferenceRepository
    @MockK private lateinit var directoryProvider: IDirectoryProvider
    @MockK private lateinit var library: Door43Client
    @MockK private lateinit var backupRC: BackupRC
    @MockK private lateinit var translator: Translator
    @MockK private lateinit var migrator: TargetTranslationMigrator
    @MockK private lateinit var index: Index
    @MockK private lateinit var resources: Resources
    @MockK private lateinit var info: AppInfo
    @MockK private lateinit var platform: Platform

    val onProgress = mockk<(Float, String?) -> Unit>(relaxed = true)

    @JvmField
    @Rule
    var tempDir: TemporaryFolder = TemporaryFolder()

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        every { context.resources }.returns(resources)
        every { context.packageName }.returns("org.example.writer")
        every { context.externalCacheDir }.returns(tempDir.newFolder("old_cache"))
        every { context.deleteDatabase(any()) }.returns(true)

        every { info.versionCode }.returns(10)
        every { platform.info }.returns(info)

        every { library.index } returns index
        every { prefRepository.getPrivatePref("last_version_code", any<Int>()) }
            .returns(1)
        every { prefRepository.setPrivatePref(any(), any<Int>()) }.just(runs)
        every { prefRepository.setDefaultPref(any(), any<String>()) }.just(runs)
        every { prefRepository.getDefaultPref(
            IPreferenceRepository.KEY_PREF_LANGUAGES_URL,
            any<String>()
        ) }.returns("/lang_names.jsom")
        every { prefRepository.getDefaultPref(
            IPreferenceRepository.KEY_PREF_TRANSLATION_TYPEFACE,
            any<String>()
        ) }.returns("font.ttf")
        every { prefRepository.getDefaultPref(
            IPreferenceRepository.KEY_PREF_SOURCE_TYPEFACE,
            any<String>()
        ) }.returns("font.ttf")

        every { index.getImportedTranslations() }.returns(listOf())
        every { library.tearDown() }.just(runs)
        every { directoryProvider.deleteLibrary() }.just(runs)
        every { directoryProvider.deployDefaultLibrary() }.just(runs)
        every { directoryProvider.internalAppDir }
            .returns(tempDir.newFolder("internal"))
        every { directoryProvider.cacheDir }.returns(tempDir.newFolder("cache"))

        mockkObject(ResourceContainer)
        coEvery { library.importResourceContainer(any()) }.returns(mockk())
        every { library.updateLanguageUrl(any()) }.just(runs)

        mockkObject(FileUtilities)
        every { FileUtilities.deleteQuietly(any()) }.returns(true)
        every { FileUtilities.moveOrCopyQuietly(any(), any()) }.returns(true)
        every { FileUtilities.copyDirectory(any<File>(), any(), any()) }.just(runs)
        every { FileUtilities.copyFile(any(), any()) }.just(runs)

        every { resources.getString(R.string.pref_default_language_url) }
            .returns("/lang_names.json")
        every { context.getString(R.string.pref_default_translation_typeface) }
            .returns("font.ttf")
        every { resources.getString(R.string.pref_default_translation_typeface) }
            .returns("font.ttf")

        every { translator.path }.returns(tempDir.newFolder("translations"))

        mockkObject(TargetTranslation)
        every { translator.targetTranslations }.returns(arrayOf())

        every { onProgress(any(), any()) }.just(runs)

        mockkStatic(PreferenceManager::class)
        every { PreferenceManager.setDefaultValues(any(), any(), any()) }.just(runs)

        mockkObject(App)
        justRun { App.restart() }
    }

    @After
    fun tearDown() {
        unmockkAll()
        tempDir.delete()
    }

    @Test
    fun `test update app, fresh install`() = runTest {
        every { prefRepository.getPrivatePref("last_version_code", any<Int>()) }
            .returns(0)
        every { library.isLibraryDeployed }.returns(true)

        UpdateApp(
            context,
            prefRepository,
            directoryProvider,
            library,
            backupRC,
            translator,
            migrator,
            platform
        ).execute(onProgress)

        verify { library.isLibraryDeployed }

        verifyCommonStuff()
        verifyUpdateLibrary()
        verifyNoSourceTranslations()
    }

    @Test
    fun `test update app, install update`() = runTest {
        every { prefRepository.getPrivatePref("last_version_code", any<Int>()) }
            .returns(9)
        every { library.isLibraryDeployed }.returns(true)

        UpdateApp(
            context,
            prefRepository,
            directoryProvider,
            library,
            backupRC,
            translator,
            migrator,
            platform
        ).execute(onProgress)

        verify(exactly = 0) { library.isLibraryDeployed }

        verifyCommonStuff()
        verifyUpdateLibrary()
        verifyNoSourceTranslations()

        verifyUpgradePre87()
        verifyUpgradePre103()
        verifyUpgradePre111()
        verifyUpgradePre122()
        verifyUpgradePre139()
        verifyUpgradePre142()
        verifyUpgradePre175()
    }

    @Test
    fun `test update app, backup imported sources`() = runTest {
        every { prefRepository.getPrivatePref("last_version_code", any<Int>()) }
            .returns(10)
        every { library.isLibraryDeployed }.returns(false)

        val translation: Translation = mockk()
        every { index.getImportedTranslations() }.returns(listOf(translation))

        val file = tempDir.newFile("backup.zip")
        every { backupRC.backupResourceContainer(translation) }.returns(file)

        every { ResourceContainer.open(file, any()) }.returns(mockk())

        UpdateApp(
            context,
            prefRepository,
            directoryProvider,
            library,
            backupRC,
            translator,
            migrator,
            platform
        ).execute(onProgress)

        verify { library.isLibraryDeployed }
        verifyCommonStuff()
        verifyUpdateLibrary()

        verify { backupRC.backupResourceContainer(translation) }
        verify { ResourceContainer.open(file, any()) }
        coVerify { library.importResourceContainer(any()) }
        verify { FileUtilities.deleteQuietly(any()) }
    }

    @Test
    fun `test update app, update target translations`() = runTest {
        every { prefRepository.getPrivatePref("last_version_code", any<Int>()) }
            .returns(10)
        every { library.isLibraryDeployed }.returns(true)

        val targetTranslationDir = File(translator.path, "aa_mrk_text_ulb")
        targetTranslationDir.mkdirs()

        val targetTranslation: TargetTranslation = mockk {
            every { unlockRepo() }.returns(true)
            every { commitSync() }.returns(true)
            every { id }.returns("aa_mrk_text_ulb")
        }
        every { translator.targetTranslations }.returns(arrayOf(targetTranslation))

        every { migrator.migrate(targetTranslationDir) }.returns(targetTranslationDir)

        UpdateApp(
            context,
            prefRepository,
            directoryProvider,
            library,
            backupRC,
            translator,
            migrator,
            platform
        ).execute(onProgress)

        verify { library.isLibraryDeployed }
        verifyCommonStuff()
        verifyNoSourceTranslations()

        verify { prefRepository.getDefaultPref(
            IPreferenceRepository.KEY_PREF_LANGUAGES_URL,
            any<String>()
        ) }
        verify { library.updateLanguageUrl(any()) }

        verify { translator.path }
        verify { migrator.migrate(targetTranslationDir) }
        verify { targetTranslation.unlockRepo() }
        verify { targetTranslation.commitSync() }
        verify { targetTranslation.id }
    }

    @Test
    fun `test update app, upgrade build numbers`() = runTest {
        every { prefRepository.getPrivatePref("last_version_code", any<Int>()) }
            .returns(10)
        every { library.isLibraryDeployed }.returns(true)

        val targetTranslation: TargetTranslation = mockk {
            every { id }.returns("aa_mrk_text_ulb")
        }
        every { translator.targetTranslations }.returns(arrayOf(targetTranslation))

        UpdateApp(
            context,
            prefRepository,
            directoryProvider,
            library,
            backupRC,
            translator,
            migrator,
            platform
        ).execute(onProgress)

        verify { library.isLibraryDeployed }
        verifyCommonStuff()
        verifyNoSourceTranslations()

        verify { targetTranslation.updateGenerator(any()) }
        verify { targetTranslation.id }
        verify { translator.path }
    }

    @Test
    fun `test update app, upgrade post 87`() = runTest {
        every { info.versionCode }.returns(89)
        every { prefRepository.getPrivatePref("last_version_code", any<Int>()) }
            .returns(88)
        every { library.isLibraryDeployed }.returns(false)

        UpdateApp(
            context,
            prefRepository,
            directoryProvider,
            library,
            backupRC,
            translator,
            migrator,
            platform
        ).execute(onProgress)

        verify(exactly = 0) { library.isLibraryDeployed }
        verifyCommonStuff()
        verifyUpdateLibrary()
        verifyNoSourceTranslations()

        verifyUpgradePre87(false)
        verifyUpgradePre103()
        verifyUpgradePre111()
        verifyUpgradePre122()
        verifyUpgradePre139()
        verifyUpgradePre142()
        verifyUpgradePre175()
    }

    @Test
    fun `test update app, upgrade post 103`() = runTest {
        every { info.versionCode }.returns(105)
        every { prefRepository.getPrivatePref("last_version_code", any<Int>()) }
            .returns(104)
        every { library.isLibraryDeployed }.returns(false)

        UpdateApp(
            context,
            prefRepository,
            directoryProvider,
            library,
            backupRC,
            translator,
            migrator,
            platform
        ).execute(onProgress)

        verify(exactly = 0) { library.isLibraryDeployed }
        verifyCommonStuff()
        verifyUpdateLibrary()
        verifyNoSourceTranslations()

        verifyUpgradePre87(false)
        verifyUpgradePre103(false)
        verifyUpgradePre111()
        verifyUpgradePre122()
        verifyUpgradePre139()
        verifyUpgradePre142()
        verifyUpgradePre175()
    }

    @Test
    fun `test update app, upgrade post 111`() = runTest {
        every { info.versionCode }.returns(113)
        every { prefRepository.getPrivatePref("last_version_code", any<Int>()) }
            .returns(112)
        every { library.isLibraryDeployed }.returns(false)

        UpdateApp(
            context,
            prefRepository,
            directoryProvider,
            library,
            backupRC,
            translator,
            migrator,
            platform
        ).execute(onProgress)

        verify(exactly = 0) { library.isLibraryDeployed }
        verifyCommonStuff()
        verifyUpdateLibrary()
        verifyNoSourceTranslations()

        verifyUpgradePre87(false)
        verifyUpgradePre103(false)
        verifyUpgradePre111(false)
        verifyUpgradePre122()
        verifyUpgradePre139()
        verifyUpgradePre142()
        verifyUpgradePre175()
    }

    @Test
    fun `test update app, upgrade post 122`() = runTest {
        every { info.versionCode }.returns(124)
        every { prefRepository.getPrivatePref("last_version_code", any<Int>()) }
            .returns(123)
        every { library.isLibraryDeployed }.returns(false)

        UpdateApp(
            context,
            prefRepository,
            directoryProvider,
            library,
            backupRC,
            translator,
            migrator,
            platform
        ).execute(onProgress)

        verify(exactly = 0) { library.isLibraryDeployed }
        verifyCommonStuff()
        verifyUpdateLibrary()
        verifyNoSourceTranslations()

        verifyUpgradePre87(false)
        verifyUpgradePre103(false)
        verifyUpgradePre111(false)
        verifyUpgradePre122(false)
        verifyUpgradePre139()
        verifyUpgradePre142()
        verifyUpgradePre175()
    }

    @Test
    fun `test update app, upgrade post 139`() = runTest {
        every { info.versionCode }.returns(141)
        every { prefRepository.getPrivatePref("last_version_code", any<Int>()) }
            .returns(140)
        every { library.isLibraryDeployed }.returns(false)

        UpdateApp(
            context,
            prefRepository,
            directoryProvider,
            library,
            backupRC,
            translator,
            migrator,
            platform
        ).execute(onProgress)

        verify(exactly = 0) { library.isLibraryDeployed }
        verifyCommonStuff()
        verifyUpdateLibrary()
        verifyNoSourceTranslations()

        verifyUpgradePre87(false)
        verifyUpgradePre103(false)
        verifyUpgradePre111(false)
        verifyUpgradePre122(false)
        verifyUpgradePre139(false)
        verifyUpgradePre142()
        verifyUpgradePre175()
    }

    @Test
    fun `test update app, upgrade post 142`() = runTest {
        every { info.versionCode }.returns(144)
        every { prefRepository.getPrivatePref("last_version_code", any<Int>()) }
            .returns(143)
        every { library.isLibraryDeployed }.returns(false)

        UpdateApp(
            context,
            prefRepository,
            directoryProvider,
            library,
            backupRC,
            translator,
            migrator,
            platform
        ).execute(onProgress)

        verify(exactly = 0) { library.isLibraryDeployed }
        verifyCommonStuff()
        verifyUpdateLibrary()
        verifyNoSourceTranslations()

        verifyUpgradePre87(false)
        verifyUpgradePre103(false)
        verifyUpgradePre111(false)
        verifyUpgradePre122(false)
        verifyUpgradePre139(false)
        verifyUpgradePre142(false)
        verifyUpgradePre175()
    }

    @Test
    fun `test update app, upgrade post 175`() = runTest {
        every { info.versionCode }.returns(177)
        every { prefRepository.getPrivatePref("last_version_code", any<Int>()) }
            .returns(176)
        every { library.isLibraryDeployed }.returns(false)

        UpdateApp(
            context,
            prefRepository,
            directoryProvider,
            library,
            backupRC,
            translator,
            migrator,
            platform
        ).execute(onProgress)

        verify(exactly = 0) { library.isLibraryDeployed }
        verifyCommonStuff()
        verifyNoSourceTranslations()

        verifyUpgradePre87(false)
        verifyUpgradePre103(false)
        verifyUpgradePre111(false)
        verifyUpgradePre122(false)
        verifyUpgradePre139(false)
        verifyUpgradePre142(false)
        verifyUpgradePre175(false)

        verifyUpdateLibrary(false)
    }

    private fun verifyCommonStuff() {
        verify { prefRepository.getPrivatePref("last_version_code", any<Int>()) }
        verify { info.versionCode }
    }

    private fun verifyUpdateLibrary(called: Boolean = true) {
        verify(inverse = !called) { index.getImportedTranslations() }
        verify(inverse = !called) { library.tearDown() }
        verify(inverse = !called) { directoryProvider.deleteLibrary() }
        verify(inverse = !called) { directoryProvider.deployDefaultLibrary() }
    }

    private fun verifyNoSourceTranslations() {
        verify(exactly = 0) { backupRC.backupResourceContainer(any()) }
        coVerify(exactly = 0) { library.importResourceContainer(any()) }
    }

    private fun verifyUpgradePre87(called: Boolean = true) {
        verify(inverse = !called) { prefRepository.setDefaultPref(
            IPreferenceRepository.KEY_PREF_TRANSLATION_TYPEFACE,
            any<String>()
        ) }
    }

    private fun verifyUpgradePre103(called: Boolean = true) {
        verify(inverse = !called) { onProgress(any(), "Updating translations") }
        verify(inverse = !called) { directoryProvider.cacheDir }
        verify(inverse = !called) { context.externalCacheDir }
    }

    private fun verifyUpgradePre111(called: Boolean = true) {
    }

    private fun verifyUpgradePre122(called: Boolean = true) {
        verify(inverse = !called) { PreferenceManager.setDefaultValues(any(), any(), any()) }
    }

    private fun verifyUpgradePre139(called: Boolean = true) {
        verify(inverse = !called) { context.deleteDatabase("app") }
    }

    private fun verifyUpgradePre142(called: Boolean = true) {
        verify(inverse = !called) { context.deleteDatabase("library") }
    }

    private fun verifyUpgradePre175(called: Boolean = true) {
        verify(inverse = !called) { onProgress(any(), "Updating fonts") }
        verify(inverse = !called) { prefRepository.getDefaultPref(
            IPreferenceRepository.KEY_PREF_TRANSLATION_TYPEFACE,
            any<String>()
        ) }
        verify(inverse = !called) { prefRepository.getDefaultPref(
            IPreferenceRepository.KEY_PREF_SOURCE_TYPEFACE,
            any<String>()
        ) }
    }
}