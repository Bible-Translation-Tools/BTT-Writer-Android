package com.door43.translationstudio.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.UiTestUtils.checkDialogContainsText
import com.door43.translationstudio.ui.UiTestUtils.checkDialogText
import com.door43.translationstudio.ui.UiTestUtils.checkText
import com.door43.translationstudio.ui.UiTestUtils.rotateScreen
import com.door43.util.FileUtilities.copyInputStreamToFile
import com.door43.util.FileUtilities.deleteQuietly
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.unfoldingword.door43client.Door43Client
import java.io.File
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
@LargeTest
@HiltAndroidTest
class ImportUsfmActivityTest {
    @Inject
    @ApplicationContext
    lateinit var appContext: Context

    @Inject
    lateinit var directoryProvider: IDirectoryProvider

    @Inject
    lateinit var library: Door43Client

    @Inject
    lateinit var translator: Translator

    @Inject
    lateinit var assetsProvider: AssetsProvider

    private var testFile: File? = null
    private var tempDir: File? =  null
    private var targetTranslationID: String? = null

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        removeTranslation()
        if (tempDir != null) {
            deleteQuietly(tempDir)
            tempDir = null
            testFile = null
        }
    }

    @Test
    @Throws(Exception::class)
    fun markNoChapter() {
        //given
        val testFile = "usfm/mrk_no_chapter.usfm"
        val book = "mrk"
        val language = "aa"
        initForImport(book, language)
        val intent = getIntentForTestFile(testFile)

        ActivityScenario.launch<ImportUsfmActivity>(intent).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)

            checkText(R.string.title_activity_import_usfm_language, true)
            onView(withText(language)).tryPerform(click())

            //when
            matchSummaryDialog(R.string.title_import_usfm_error, book, true)
            rotateScreen(scenario)

            //then
            matchSummaryDialog(R.string.title_import_usfm_error, book, true)
            rotateScreen(scenario)
        }
    }

    @Test
    @Throws(Exception::class)
    fun markNoId() {
        //given
        val testFile = "usfm/mrk_no_id.usfm"
        val book = "mrk"
        val language = "aa"
        initForImport(book, language)
        val intent = getIntentForTestFile(testFile)

        ActivityScenario.launch<ImportUsfmActivity>(intent).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)

            checkText(R.string.title_activity_import_usfm_language, true)
            onView(withText(language)).tryPerform(click())

            thenShouldShowMissingBookNameDialog()
            rotateScreen(scenario)

            //when
            thenShouldShowMissingBookNameDialog()
            onView(withText(R.string.label_continue)).inRoot(isDialog()).tryPerform(click())
            clickOnViewText("bible-nt")
            clickOnViewText("Mark")

            //then
            matchSummaryDialog(R.string.title_processing_usfm_summary, book, false)

            rotateScreen(scenario)
            matchSummaryDialog(R.string.title_processing_usfm_summary, book, false)
        }
    }

    @Test
    @Throws(Exception::class)
    fun judeValid() {
        //given
        val testFile = "usfm/66-JUD.usfm"
        val book = "jud"
        val language = "aa"
        initForImport(book, language)
        val intent = getIntentForTestFile(testFile)

        ActivityScenario.launch<ImportUsfmActivity>(intent).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)

            checkText(R.string.title_activity_import_usfm_language, true)
            onView(withText(language)).tryPerform(click())
            matchSummaryDialog(R.string.title_processing_usfm_summary, book, false)

            rotateScreen(scenario)
            matchSummaryDialog(R.string.title_processing_usfm_summary, book, false)

            //when
            onView(withText(R.string.label_continue)).inRoot(isDialog()).tryPerform(click())

            //then
            matchImportResultsDialog()

            rotateScreen(scenario)
            matchImportResultsDialog()
        }
    }

    @Test
    @Throws(Exception::class)
    fun judeNoVerses() {
        //given
        val testFile = "usfm/jude.no_verses.usfm"
        val book = "jud"
        val language = "aa"
        initForImport(book, language)
        val intent = getIntentForTestFile(testFile)

        ActivityScenario.launch<ImportUsfmActivity>(intent).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)

            checkText(R.string.title_activity_import_usfm_language, true)

            //when
            onView(withText(language)).tryPerform(click())

            //then
            matchSummaryDialog(R.string.title_import_usfm_error, book, true)
            rotateScreen(scenario)

            matchSummaryDialog(R.string.title_import_usfm_error, book, true)
            rotateScreen(scenario)
        }
    }

    /**
     *
     */
    private fun initForImport(book: String, language: String) {
        targetTranslationID = language + "_" + book + "_text_reg"
        removeTranslation()
    }

    /**
     * cleanup old imports
     */
    private fun removeTranslation() {
        if (targetTranslationID != null) {
            translator.deleteTargetTranslation(targetTranslationID)
        }
    }

    /**
     * match expected values on summary dialog
     * @param title
     * @param book
     * @param error
     */
    private fun matchSummaryDialog(title: Int, book: String?, error: Boolean) {
        checkDialogText(title, true)
        if (book != null) {
            shouldHaveFoundBook(book)
        }
        checkForImportErrors(error)
    }

    /**
     * wait until view with text is shown
     * @param matchText
     */
    private fun clickOnViewText(matchText: String?) {
        onView(withText(matchText)).tryCheck(matches(withText(matchText)))
        onView(withText(matchText)).tryCheck(matches(withText(matchText)))
        onView(withText(matchText)).tryPerform(click())
    }

    /**
     * match expected values on summary dialog
     * @param success
     */
    private fun matchImportResultsDialog(success: Boolean = true) {
        val matchTitle = if (success) R.string.title_import_usfm_results else R.string.title_import_usfm_error
        val matchText = if (success) R.string.import_usfm_success else R.string.import_usfm_failed
        checkDialogText(matchTitle, true)
        checkDialogText(matchText, true)
    }

    /**
     * check if dialog content shows no errors
     */
    private fun checkForImportErrors(displayed: Boolean) {
        val noErrors = appContext.getString(R.string.no_error)
        checkDialogContainsText(noErrors, !displayed)
    }

    /**
     * make sure we found the book
     * @param book
     */
    private fun shouldHaveFoundBook(book: String) {
        val format = appContext.resources.getString(R.string.found_book)
        val matchText = String.format(format, book)
        checkDialogContainsText(matchText, true)
    }

    private fun thenShouldShowMissingBookNameDialog() {
        val format = appContext.getString(R.string.missing_book_name_prompt)
        val message = String.format(format, testFile?.name)
        checkDialogContainsText(message, true)
    }

    /**
     * generate intent for ImportUsfmActivity
     * @param fileName
     * @return
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun getIntentForTestFile(fileName: String): Intent {
        val intent = Intent(appContext, ImportUsfmActivity::class.java)

        tempDir = File(
            directoryProvider.cacheDir,
            System.currentTimeMillis().toString()
        ).apply {
            mkdirs()
        }

        val usfmStream = assetsProvider.open(fileName)
        testFile = File(tempDir, "testFile.usfm").apply {
            copyInputStreamToFile(usfmStream, this)
        }

        intent.putExtra(ImportUsfmActivity.EXTRA_USFM_IMPORT_FILE, testFile)
        return intent
    }
}
