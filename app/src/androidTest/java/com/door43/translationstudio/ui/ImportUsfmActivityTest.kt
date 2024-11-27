package com.door43.translationstudio.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Translator
import com.door43.util.FileUtilities.copyInputStreamToFile
import com.door43.util.FileUtilities.deleteQuietly
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.hamcrest.Matchers
import org.hamcrest.Matchers.containsString
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

        dismissANRSystemDialog()
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

        val scenario = ActivityScenario.launch<ImportUsfmActivity>(intent)
        scenario.moveToState(Lifecycle.State.RESUMED)

        checkDisplayState(R.string.title_activity_import_usfm_language, true)
        onView(withText(language)).perform(click())

        //when
        matchSummaryDialog(R.string.title_import_usfm_error, book)
        rotateScreen(scenario)

        //then
        matchSummaryDialog(R.string.title_import_usfm_error, book)
        rotateScreen(scenario)

        scenario.close()
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

        val scenario = ActivityScenario.launch<ImportUsfmActivity>(intent)
        scenario.moveToState(Lifecycle.State.RESUMED)

        checkDisplayState(R.string.title_activity_import_usfm_language, true)
        onView(withText(language)).perform(click())

        thenShouldShowMissingBookNameDialog()
        rotateScreen(scenario)

        //when
        thenShouldShowMissingBookNameDialog()
        onView(withText(R.string.label_continue)).inRoot(isDialog()).perform(click())
        clickOnViewText("bible-nt")
        clickOnViewText("Mark")

        //then
        matchSummaryDialog(R.string.title_processing_usfm_summary, book)

        rotateScreen(scenario)
        matchSummaryDialog(R.string.title_processing_usfm_summary, book)

        scenario.close()
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

        val scenario = ActivityScenario.launch<ImportUsfmActivity>(intent)
        scenario.moveToState(Lifecycle.State.RESUMED)

        checkDisplayState(R.string.title_activity_import_usfm_language, true)
        onView(withText(language)).perform(click())
        matchSummaryDialog(R.string.title_processing_usfm_summary, book)

        rotateScreen(scenario)
        matchSummaryDialog(R.string.title_processing_usfm_summary, book)

        //when
        onView(withText(R.string.label_continue)).inRoot(isDialog()).perform(click())

        //then
        matchImportResultsDialog(true)

        rotateScreen(scenario)
        matchImportResultsDialog(true)

        scenario.close()
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

        val scenario = ActivityScenario.launch<ImportUsfmActivity>(intent)
        scenario.moveToState(Lifecycle.State.RESUMED)

        checkDisplayState(R.string.title_activity_import_usfm_language, true)

        //when
        onView(withText(language)).perform(click())

        //then
        matchSummaryDialog(R.string.title_import_usfm_error, book)
        rotateScreen(scenario)

        matchSummaryDialog(R.string.title_import_usfm_error, book)
        rotateScreen(scenario)

        scenario.close()
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
     */
    private fun matchSummaryDialog(title: Int, book: String?) {
        thenShouldHaveDialogTitle(title)
        if (book != null) {
            shouldHaveFoundBook(book)
        }
        checkForImportErrors(title)
    }

    /**
     * wait until view with text is shown
     * @param matchText
     */
    private fun clickOnViewText(matchText: String?) {
        for (i in 0..19) { // wait until dialog is displayed
            try {
                onView(withText(matchText)).check(matches(withText(matchText)))
                break
            } catch (_: Exception) {
            }
        }
        onView(withText(matchText)).check(matches(withText(matchText)))
        onView(withText(matchText)).perform(click())
    }

    /**
     * match expected values on summary dialog
     * @param success
     */
    private fun matchImportResultsDialog(success: Boolean) {
        val matchTitle = if (success) R.string.title_import_usfm_results else R.string.title_import_usfm_error
        val matchText = if (success) R.string.import_usfm_success else R.string.import_usfm_failed
        thenShouldHaveDialogTitle(matchTitle)
        onView((withText(matchText)))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    /**
     * check if dialog content shows no errors
     */
    private fun checkForImportErrors(title: Int) {
        onView(withText(title))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    /**
     * make sure we found the book
     * @param book
     */
    private fun shouldHaveFoundBook(book: String) {
        val format = appContext.resources.getString(R.string.found_book)
        val matchText = String.format(format, book)
        onView(withText(containsString(matchText)))
            .inRoot(isDialog())
            .check(matches(isDisplayed())) // dialog open
    }

    private fun thenShouldShowMissingBookNameDialog() {
        thenShouldHaveDialogTitle(R.string.title_activity_import_usfm_language)
    }

    private fun thenShouldHaveDialogTitle(title: Int) {
        onView(withText(title))
            .inRoot(isDialog())
            .check(matches(isDisplayed())) // dialog displayed
    }

    /**
     * make sure text is displayed or not displayed in view
     * @param resource
     * @param displayed
     */
    private fun checkDisplayState(resource: Int, displayed: Boolean) {
        val text = appContext.resources.getString(resource)
        checkDisplayState(text, displayed)
    }

    /**
     * make sure text is displayed or not displayed in view
     * @param text
     * @param displayed
     */
    private fun checkDisplayState(text: String, displayed: Boolean) {
        val interaction = Espresso.onView(withText(text))
        var displayState = ViewMatchers.isCompletelyDisplayed()
        if (!displayed) {
            displayState = Matchers.not(displayState)
        }
        interaction.check(matches(displayState))
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


    /**
     * force page orientation change
     */
    private fun rotateScreen(scenario: ActivityScenario<ImportUsfmActivity>) {
        scenario.onActivity {
            if (it.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        }
    }

    private fun dismissANRSystemDialog() {
        val device = UiDevice.getInstance(getInstrumentation())
        // If running the device in English Locale
        var waitButton = device.findObject(UiSelector().textContains("wait"))
        if (waitButton.exists()) {
            waitButton.click()
        }
        // If running the device in Japanese Locale
        waitButton = device.findObject(UiSelector().textContains("待機"))
        if (waitButton.exists()) {
            waitButton.click()
        }
    }
}
