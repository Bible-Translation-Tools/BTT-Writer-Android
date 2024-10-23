package com.door43.translationstudio.ui.newlanguage

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.util.Log
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.BoundedMatcher
import androidx.test.espresso.matcher.ViewMatchers
import com.door43.data.IDirectoryProvider
import com.door43.questionnaire.QuestionnaireActivity
import com.door43.questionnaire.QuestionnairePager
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import org.hamcrest.CoreMatchers
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.Question
import org.unfoldingword.door43client.models.Questionnaire
import org.unfoldingword.tools.logger.Logger
import java.lang.reflect.Field
import javax.inject.Inject

/**
 * shared methods for QuestionnaireActivity UI testing
 */
open class NewLanguageActivityUiUtils {
    private var resultCode: Field? = null
    private var resultData: Field? = null
    private var pager: QuestionnairePager? = null
    private var stringToBeTyped: String? = null

    @Inject
    @ApplicationContext
    lateinit var appContext: Context

    @Inject
    lateinit var directoryProvider: IDirectoryProvider

    @Inject
    lateinit var library: Door43Client

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Before
    fun setUp() {
        hiltRule.inject()
        Logger.flush()

        if (!library.isLibraryDeployed) {
            directoryProvider.deployDefaultLibrary()
        }

        stringToBeTyped = "Espresso";

        val questionnaires = library.index.getQuestionnaires()
        if (questionnaires.isNotEmpty()) {
            val q: Questionnaire = questionnaires[0]
            val questions = library.index.getQuestions(q.tdId)
            pager = QuestionnairePager(q)
            pager?.loadQuestions(questions)
        }
    }

    /**
     * get number of pages in questionnaire
     * @return
     */
    fun pageCount(): Int {
        return pager?.size() ?: 0
    }

    /**
     * force page orientation change
     */
    private fun rotateScreen(scenario: ActivityScenario<NewTempLanguageActivity>) {
        scenario.onActivity {
            if (it.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        }
    }

    /**
     * check values for answers returned by activity
     * @param answers
     * @param expectedAnswer
     * @throws Exception
     */
    @Throws(Exception::class)
    fun verifyAnswers(answers: JSONArray, expectedAnswer: Boolean) {
        for (i in 0 until answers.length()) {
            val answer = answers.getJSONObject(i)
            val id = answer.getInt("question_id")
            val answerText = answer.getString("answer")

            val questionnaireQuestion = findQuestion(id.toLong())
            val expected = if (questionnaireQuestion!!.inputType == Question.InputType.String) {
                generateAnswerForQuestion(id.toLong())
            } else {
                if (expectedAnswer) "true" else "false"
            }
            Assert.assertEquals("Question $id mismatch", expected, answerText)
        }
    }

    /**
     * get activity results data and convert to JSON
     * @param currentActivity
     * @param key
     * @return
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     * @throws JSONException
     */
    @Throws(NoSuchFieldException::class, IllegalAccessException::class, JSONException::class)
    fun getJsonData(currentActivity: QuestionnaireActivity?, key: String?): JSONObject {
        val resultData = getResultData(currentActivity)
        val jsonDataStr = resultData.getStringExtra(key)
        return JSONObject(jsonDataStr)
    }

    /**
     * setup to capture the final results of activity
     * @throws NoSuchFieldException
     */
    @Throws(NoSuchFieldException::class)
    fun setupForCaptureOfResultData() {
//        try {
//            getQuestionPages();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
        resultData = Activity::class.java.getDeclaredField("mResultData") //NoSuchFieldException
        resultData?.isAccessible = true
        resultCode = Activity::class.java.getDeclaredField("mResultCode") //NoSuchFieldException
        resultCode?.isAccessible = true
    }

    /**
     * cleanup after setupForCaptureOfResultData
     * @throws NoSuchFieldException
     */
    @Throws(NoSuchFieldException::class)
    fun clearResultData() {
        resultData?.isAccessible = false
        resultData = null
        resultCode?.isAccessible = false
        resultCode = null
    }

    /**
     * get activity results data
     * @param currentActivity
     * @return
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    fun getResultData(currentActivity: QuestionnaireActivity?): Intent {
        return resultData!![currentActivity] as Intent
    }

    /**
     * get activity results code
     * @param currentActivity
     * @return
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    fun getResultCode(currentActivity: QuestionnaireActivity?): Int {
        return resultCode?.getInt(currentActivity) ?: -1
    }

    /**
     * verify that missing required answer dialog is displayed
     */
    fun thenShouldHaveRequiredAnswerDialog() {
        val vi = Espresso.onView(ViewMatchers.withText(R.string.missing_question_answer))
        vi.check(ViewAssertions.matches(ViewMatchers.withText(R.string.missing_question_answer)))
    }

    /**
     * verify that missing non-required answer dialog is displayed
     */
    fun thenShouldHaveNewLanguageDialog() {
        Assert.assertNotNull(Espresso.onView(ViewMatchers.withText(R.string.title_activity_language_selector)))
        val prompt = appContext.resources.getString(R.string.new_language_confirmation)
        val promptParts = prompt.split("\"".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        Assert.assertNotNull(Espresso.onView(ViewMatchers.withText(promptParts[0])))
    }

    /**
     * verify that missing non-required answer dialog is displayed
     */
    fun thenShouldHaveMissingAnswerDialog() {
        Assert.assertNotNull(Espresso.onView(ViewMatchers.withText(R.string.answers_missing_title)))
        Assert.assertNotNull(Espresso.onView(ViewMatchers.withText(R.string.answers_missing_continue)))
    }

    /**
     * iteratively fill all the question pages with canned answers
     *
     * @param doDone
     * @param requiredOnly
     * @param valueForBooleans
     * @param hideKeyboard
     */
    fun fillAllPagesAndRotate(
        doDone: Boolean,
        requiredOnly: Boolean,
        valueForBooleans: Boolean,
        hideKeyboard: Boolean,
        scenario: ActivityScenario<NewTempLanguageActivity>
    ) {
        val pageCount = pageCount()

        for (i in 0 until pageCount - 1) {
            fillPage(i, false, requiredOnly, valueForBooleans, hideKeyboard)
            rotateScreen(scenario)
            verifyPageLayout(pageCount, i)

            Espresso.onView(ViewMatchers.withId(R.id.next_button)).perform(ViewActions.click())
            verifyPageLayout(pageCount, i + 1)
        }

        fillPage(pageCount - 1, false, requiredOnly, valueForBooleans, hideKeyboard)

        if (doDone) {
            Espresso.onView(ViewMatchers.withId(R.id.done_button)).perform(ViewActions.click())
        }
    }

    /**
     * iteratively fill all the question pages with canned answers
     *
     * @param doDone
     * @param requiredOnly
     * @param valueForBooleans
     * @param hideKeyboard
     */
    fun fillAllPages(
        doDone: Boolean,
        requiredOnly: Boolean,
        valueForBooleans: Boolean,
        hideKeyboard: Boolean
    ) {
        val pageCount = pageCount()

        for (i in 0 until pageCount - 1) {
            fillPage(i, true, requiredOnly, valueForBooleans, hideKeyboard)
            verifyPageLayout(pageCount, i + 1)
        }

        fillPage(pageCount - 1, false, requiredOnly, valueForBooleans, hideKeyboard)

        if (doDone) {
            Espresso.onView(ViewMatchers.withId(R.id.done_button)).perform(ViewActions.click())
        }
    }

    /**
     * fill pagesup to fillToPage
     * @param fillToPage
     * @param hideKeyboard
     * * @param requiredOnly
     * @param valueForBooleans
     */
    fun fillUpToPage(
        fillToPage: Int,
        hideKeyboard: Boolean,
        requiredOnly: Boolean,
        valueForBooleans: Boolean,
        doNext: Boolean
    ) {
        for (i in 0 until fillToPage - 1) {
            Log.d(TAG, "Filling page: " + (i + 1))
            verifyNavButtonSettings(pageCount(), i)
            fillPage(i, true, requiredOnly, valueForBooleans, hideKeyboard)
            Log.d(TAG, "Now on page: $i")
        }
        Log.d(TAG, "Filling page: $fillToPage")
        verifyNavButtonSettings(pageCount(), fillToPage - 1)
        fillPage(fillToPage - 1, doNext, requiredOnly, valueForBooleans, hideKeyboard)
    }

    /**
     * fill a page of questions with canned answers
     * @param pageNum
     * @param doNext
     * @param requiredOnly
     * @param valueForBooleans
     * @param hideKeyboard
     */
    fun fillPage(
        pageNum: Int,
        doNext: Boolean,
        requiredOnly: Boolean,
        valueForBooleans: Boolean,
        hideKeyboard: Boolean
    ) {
        val questions = pager?.getPage(pageNum)!!
            .getQuestions()

        for (i in questions.indices) {
            val question = questions[i]
            if (question.isRequired || !requiredOnly) {
                if (question.inputType == Question.InputType.Boolean) {
                    val value = valueForBooleans
                    setBoolean(pageNum, i, value)
                } else {
                    val text = generateAnswerForQuestion(question.tdId)
                    addEditText(pageNum, i, text, hideKeyboard)
                }
            } else if (question.dependsOn == 0L) {
                // skip not required
                if (question.inputType == Question.InputType.Boolean) {
                    verifyButtons(pageNum, i, yesChecked = false, noChecked = false)
                } else {
                    verifyAnswer(pageNum, i, "")
                }
            }
        }

        if (doNext) {
            Espresso.onView(ViewMatchers.withId(R.id.next_button)).perform(ViewActions.click())
        }
    }

    /**
     * create canned string answer for question number
     * @param questionId
     * @return
     */
    private fun generateAnswerForQuestion(questionId: Long): String {
        return String.format("A-%d", questionId)
    }

    /**
     * create canned boolean answer for question number
     * @param questionNum
     * @return
     */
    private fun generateAnswerForBoolean(questionNum: Int): Boolean {
        return questionNum % 2 == 1
    }

    /**
     * verify answer in question
     * @param pageNum
     * @param questionNum
     * @param text
     */
    private fun verifyAnswer(pageNum: Int, questionNum: Int, text: String?) {
        val question = pager?.getPage(pageNum)!!.getQuestion(questionNum)
        val questionText = question!!.text
        val interaction = Espresso.onView(
            Matchers.allOf(
                ViewMatchers.withId(R.id.edit_text),
                ViewMatchers.hasSibling(ViewMatchers.withText(questionText))
            )
        )
        Espresso.onView(ViewMatchers.withId(R.id.recycler_view))
            .perform(RecyclerViewActions.scrollToPosition<RecyclerView.ViewHolder>(questionNum))
        interaction.check(ViewAssertions.matches(ViewMatchers.withText(text)))
    }

    /**
     * enter answer into question
     * @param pageNum
     * @param questionNum
     * @param newText
     * @param hideKeyboard
     */
    private fun addEditText(
        pageNum: Int,
        questionNum: Int,
        newText: String?,
        hideKeyboard: Boolean
    ) {
        verifyAnswer(pageNum, questionNum, "")
        val question = pager?.getPage(pageNum)!!.getQuestion(questionNum)
        val questionText = question!!.text
        val interaction = Espresso.onView(
            Matchers.allOf(
                ViewMatchers.withId(R.id.edit_text),
                ViewMatchers.hasSibling(ViewMatchers.withText(questionText))
            )
        )
        Espresso.onView(ViewMatchers.withId(R.id.recycler_view))
            .perform(RecyclerViewActions.scrollToPosition<RecyclerView.ViewHolder>(questionNum))
        interaction.perform(ViewActions.typeText(newText))
        interaction.check(ViewAssertions.matches(ViewMatchers.withHint(question.help))) // doesn't seem to work on second question
        if (hideKeyboard) {
            interaction.perform(ViewActions.closeSoftKeyboard())
        }
        interaction.check(ViewAssertions.matches(ViewMatchers.withText(newText)))
    }

    /**
     * verify check state of radio button
     * @param pageNum
     * @param questionNum
     * @param resource
     * @param isChecked
     */
    private fun verifyButton(pageNum: Int, questionNum: Int, resource: Int, isChecked: Boolean) {
        val questionText = pager?.getPage(pageNum)!!.getQuestion(questionNum)!!.text
        val parent = Matchers.allOf(
            ViewMatchers.withClassName(CoreMatchers.endsWith("RadioGroup")),
            ViewMatchers.hasSibling(ViewMatchers.withText(questionText))
        )

        val interaction = Espresso.onView(
            Matchers.allOf(
                ViewMatchers.withId(resource),
                ViewMatchers.withParent(parent)
            )
        )
        if (isChecked) {
            interaction.check(ViewAssertions.matches(ViewMatchers.isChecked()))
        } else {
            interaction.check(ViewAssertions.matches(Matchers.not(ViewMatchers.isChecked())))
        }
    }

    /**
     * verify check states of boolean answer
     * @param pageNum
     * @param questionNum
     * @param yesChecked
     * @param noChecked
     */
    private fun verifyButtons(
        pageNum: Int,
        questionNum: Int,
        yesChecked: Boolean,
        noChecked: Boolean
    ) {
        verifyButton(pageNum, questionNum, R.id.radio_button_yes, yesChecked)
        verifyButton(pageNum, questionNum, R.id.radio_button_no, noChecked)
    }

    /**
     * set state for boolean answer
     * @param pageNum
     * @param questionNum
     * @param value
     */
    private fun setBoolean(pageNum: Int, questionNum: Int, value: Boolean) {
        verifyButtons(pageNum, questionNum, yesChecked = false, noChecked = false) // should not be set yet
        val questionText = pager?.getPage(pageNum)?.getQuestion(questionNum)?.text
        val parent = Matchers.allOf(
            ViewMatchers.withClassName(CoreMatchers.endsWith("RadioGroup")),
            ViewMatchers.hasSibling(ViewMatchers.withText(questionText))
        )
        Espresso.onView(ViewMatchers.withId(R.id.recycler_view))
            .perform(RecyclerViewActions.scrollToPosition<RecyclerView.ViewHolder>(questionNum))
        val resource = if (value) R.id.radio_button_yes else R.id.radio_button_no
        val oppositeResource = if (!value) R.id.radio_button_yes else R.id.radio_button_no
        val interaction = Espresso.onView(
            Matchers.allOf(
                ViewMatchers.withId(resource),
                ViewMatchers.withParent(parent)
            )
        )
        interaction.perform(ViewActions.click())
        interaction.check(ViewAssertions.matches(ViewMatchers.isChecked()))
        Espresso.onView(
            Matchers.allOf(
                ViewMatchers.withId(oppositeResource),
                ViewMatchers.withParent(parent)
            )
        ).check(ViewAssertions.matches(Matchers.not(ViewMatchers.isChecked())))
    }

    /**
     * find question for ID
     * @param id
     * @return
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun findQuestion(id: Long): Question? {
        return pager?.getQuestion(id)
    }

    /**
     * verify layout of page (title and navigation buttons)
     * @param pageCount
     * @param pageNum
     */
    fun verifyPageLayout(pageCount: Int, pageNum: Int) {
        thenTitlePageCountShouldMatch(pageCount, pageNum)
        verifyNavButtonSettings(pageCount, pageNum)
    }

    /**
     * verify title for page
     * @param pageCount
     * @param pageNum
     */
    private fun thenTitlePageCountShouldMatch(pageCount: Int, pageNum: Int) {
        val titleFormat = appContext.resources.getString(R.string.questionnaire_title)

        val title = String.format(titleFormat, pageNum + 1, pageCount)
        matchToolbarTitle(title)
    }

    /**
     * verify title for page
     * @param pageCount
     * @param pageNum
     */
    fun thenTitlePageCountShouldNotMatch(pageCount: Int, pageNum: Int) {
        val titleFormat = appContext.resources.getString(R.string.questionnaire_title)

        val title = String.format(titleFormat, pageNum + 1, pageCount)
        notMatchToolbarTitle(title)
    }

    /**
     * verify navigation buttons displayed on page
     * @param pageCount
     * @param pageNum
     */
    private fun verifyNavButtonSettings(pageCount: Int, pageNum: Int) {
        if (pageNum == 0) {
            Espresso.onView(ViewMatchers.withId(R.id.previous_button))
                .check(ViewAssertions.matches(Matchers.not(ViewMatchers.isDisplayed())))
        } else {
            Espresso.onView(ViewMatchers.withId(R.id.previous_button))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        }

        if (pageNum < pageCount - 1) {
            Espresso.onView(ViewMatchers.withId(R.id.next_button))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
            Espresso.onView(ViewMatchers.withId(R.id.done_button))
                .check(ViewAssertions.matches(Matchers.not(ViewMatchers.isDisplayed())))
        } else {
            Espresso.onView(ViewMatchers.withId(R.id.done_button))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
            Espresso.onView(ViewMatchers.withId(R.id.next_button))
                .check(ViewAssertions.matches(Matchers.not(ViewMatchers.isDisplayed())))
        }
    }

    companion object {
        val TAG: String = NewLanguageActivityUiUtils::class.java.simpleName

        /**
         * get interaction for toolbar with title
         * @param title
         * @return
         */
        private fun matchToolbarTitle(
            title: CharSequence
        ): ViewInteraction {
            return Espresso.onView(ViewMatchers.isAssignableFrom(Toolbar::class.java))
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