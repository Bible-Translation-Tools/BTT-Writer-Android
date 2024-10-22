package com.door43.translationstudio.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.appcompat.widget.Toolbar
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.BoundedMatcher
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.di.Development
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Translator
import com.door43.util.FileUtilities.copyInputStreamToFile
import com.door43.util.FileUtilities.deleteQuietly
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.tools.logger.Logger
import java.io.File
import javax.inject.Inject

@RunWith(AndroidJUnit4::class)
@LargeTest
@HiltAndroidTest
class ImportUsfmActivityUiTest {
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
    @Development
    lateinit var assetsProvider: AssetsProvider

    private var testFile: File? = null
    private var tempDir: File? =  null
    private var targetTranslationID: String? = null

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Before
    fun setUp() {
        hiltRule.inject()
        Logger.flush()

        if (!library.isLibraryDeployed) {
            directoryProvider.deployDefaultLibrary()
        }
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

        checkDisplayState(R.string.title_activity_import_usfm_language, true)
        Espresso.onView(withText(language)).perform(ViewActions.click())
        waitWhileDisplayed(R.string.reading_usfm)

        //when
        matchSummaryDialog(R.string.title_import_usfm_error, book, false)
        rotateScreen(scenario)

        //then
        matchSummaryDialog(R.string.title_import_usfm_error, book, false)
        rotateScreen(scenario)
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

        checkDisplayState(R.string.title_activity_import_usfm_language, true)
        Espresso.onView(withText(language)).perform(ViewActions.click())
        waitWhileDisplayed(R.string.reading_usfm)
        thenShouldShowMissingBookNameDialog()
        rotateScreen(scenario)

        //when
        thenShouldShowMissingBookNameDialog()
        Espresso.onView(withText(R.string.label_continue)).perform(ViewActions.click())
        clickOnViewText("bible-nt")
        clickOnViewText("Mark")

        //then
        waitWhileDisplayed(R.string.reading_usfm)
        matchSummaryDialog(R.string.title_processing_usfm_summary, book, true)
        //rotateScreen(scenario)
        matchSummaryDialog(R.string.title_processing_usfm_summary, book, true)
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

        checkDisplayState(R.string.title_activity_import_usfm_language, true)
        Espresso.onView(withText(language)).perform(ViewActions.click())
        waitWhileDisplayed(R.string.reading_usfm)
        matchSummaryDialog(R.string.title_processing_usfm_summary, book, true)
        rotateScreen(scenario)
        matchSummaryDialog(R.string.title_processing_usfm_summary, book, true)

        //when
        Espresso.onView(withText(R.string.label_continue)).perform(ViewActions.click())

        //then
        waitWhileDisplayed(R.string.importing_usfm)
        matchImportResultsDialog(true)
        rotateScreen(scenario)
        matchImportResultsDialog(true)
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

        checkDisplayState(R.string.title_activity_import_usfm_language, true)

        //when
        Espresso.onView(withText(language)).perform(ViewActions.click())
        waitWhileDisplayed(R.string.reading_usfm)

        //then
        matchSummaryDialog(R.string.title_import_usfm_error, book, false)
        rotateScreen(scenario)
        matchSummaryDialog(R.string.title_import_usfm_error, book, false)
        rotateScreen(scenario)
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
     * @param noErrors
     */
    private fun matchSummaryDialog(title: Int, book: String?, noErrors: Boolean) {
        thenShouldHaveDialogTitle(title)
        if (book != null) {
            shouldHaveFoundBook(book)
        }
        checkForImportErrors(noErrors, title)
    }

    /**
     * wait until view with text is shown
     * @param matchText
     */
    private fun clickOnViewText(matchText: String?) {
        for (i in 0..19) { // wait until dialog is displayed
            try {
                Espresso.onView(withText(matchText))
                    .check(ViewAssertions.matches(withText(matchText)))
                break
            } catch (e: Exception) {
            }
        }
        Espresso.onView(withText(matchText))
            .check(ViewAssertions.matches(withText(matchText)))
        Espresso.onView(withText(matchText)).perform(ViewActions.click())
    }

    /**
     * match expected values on summary dialog
     * @param success
     */
    private fun matchImportResultsDialog(success: Boolean) {
        val matchTitle =
            if (success) R.string.title_import_usfm_results else R.string.title_import_usfm_error
        val matchText = if (success) R.string.import_usfm_success else R.string.import_usfm_failed
        thenShouldHaveDialogTitle(matchTitle)
        Espresso.onView((withText(matchText)))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }

    /**
     * check if dialog content shows no errors
     * @param noErrors
     */
    private fun checkForImportErrors(noErrors: Boolean, title: Int) {
        val dialogTitle = appContext.resources.getString(title)
        Espresso.onView(withText(dialogTitle))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }

    /**
     * make sure we found the book
     * @param book
     */
    private fun shouldHaveFoundBook(book: String) {
        val format = appContext.resources.getString(R.string.found_book)
        val matchText = String.format(format, book)
        Espresso.onView(withText(Matchers.containsString(matchText)))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed())) // dialog open
    }

    private fun thenShouldShowMissingBookNameDialog() {
        thenShouldHaveDialogTitle(R.string.title_activity_import_usfm_language)
    }

    private fun thenShouldHaveDialogTitle(title: Int) {
        val titleStr = appContext.resources.getString(title)
        //        for(int i = 0; i < 40; i++) { // wait until displayed
//            try {
//                onView(withId(R.id.dialog_title)).check(matches(withText(titleStr)));
//                break;
//            } catch (Exception e) {
//            }
//        }
        Espresso.onView(withText(titleStr))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed())) // dialog displayed
    }

    /**
     * since progress dialog is async, then we idle while it is shown
     * @param resource
     * @return
     */
    private fun waitWhileDisplayed(resource: Int): Boolean {
        val text = appContext.resources.getString(resource)
        return waitWhileDisplayed(text)
    }

    /**
     * since progress dialog is async, then we idle while it is shown
     * @param text
     * @return
     */
    private fun waitWhileDisplayed(text: String): Boolean {
        var done = false
        var viewSeen = false
        val maxCount = 1000 // sanity limit
        var i = 0
        while ((i < maxCount) && !done) {
            try {
                Espresso.onView(withText(text))
                    .check(ViewAssertions.matches(ViewMatchers.isCompletelyDisplayed()))
                viewSeen = true
            } catch (e: Exception) {
                done = true
            }
            i++
        }
        if (!done) {
            val msg = "Max count reached"
        }
        return viewSeen
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
        interaction.check(ViewAssertions.matches(displayState))
    }

    /**
     * generate intent for ImportUsfmActivity
     * @param fileName
     * @return
     * @throws Exception
     */
    @Throws(Exception::class)
    fun getIntentForTestFile(fileName: String): Intent {
        val intent = Intent(appContext, ImportUsfmActivity::class.java)

        tempDir = File(directoryProvider.cacheDir, System.currentTimeMillis().toString() + "").apply {
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
        //Context context = InstrumentationRegistry.getInstrumentation().getContext();
        //val orientation = appContext.resources.configuration.orientation

        scenario.onActivity {
            if (it.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        }
    }

    /**
     * get interaction for toolbar with title
     * @param resource
     * @return
     */
    private fun matchToolbarTitle(resource: Int): ViewInteraction {
        val title = appContext.resources.getString(resource)
        return matchToolbarTitle(title)
    }

    companion object {
        /**
         * get interaction for toolbar with title
         * @param title
         * @return
         */
        private fun matchToolbarTitle(
            title: CharSequence
        ): ViewInteraction {
            return Espresso.onView(
                Matchers.allOf(
                    ViewMatchers.isAssignableFrom(
                        Toolbar::class.java
                    )
                )
            )
                .check(ViewAssertions.matches(withToolbarTitle(Matchers.`is`(title))))
        }

        /**
         * get interaction for toolbar with title
         * @param title
         * @return
         */
        private fun notMatchToolbarTitle(
            title: CharSequence
        ): ViewInteraction {
            return Espresso.onView(ViewMatchers.isAssignableFrom(Toolbar::class.java))
                .check(ViewAssertions.matches(Matchers.not(withToolbarTitle(Matchers.`is`(title)))))
        }

        /**
         * match toolbar
         * @param textMatcher
         * @return
         */
        private fun withToolbarTitle(
            textMatcher: Matcher<CharSequence>
        ): Matcher<Any> {
            return object : BoundedMatcher<Any, Toolbar>(Toolbar::class.java) {
                public override fun matchesSafely(toolbar: Toolbar): Boolean {
                    return textMatcher.matches(toolbar.title)
                }
                override fun describeTo(description: Description) {
                    description.appendText("with toolbar title: ")
                    textMatcher.describeTo(description)
                }
            }
        }
    }
}
