package com.door43.translationstudio.ui.newlanguage

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.UiTestUtils.onWaitForView
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
@HiltAndroidTest
class QuestionnaireActivityTest : NewLanguageActivityUtils() {

    @Test
    @Throws(Exception::class)
    fun fillPageBoolean() {
        //given

        val pageNum = 0
        val hideKeyboard = false
        val requiredOnly = false
        val valueForBooleans = false
        val doNext = true

        val intent = Intent(appContext, NewTempLanguageActivity::class.java)
        val scenario = ActivityScenario.launch<NewTempLanguageActivity>(intent)

        verifyPageLayout(pageCount(), pageNum)
        fillPage(pageNum, doNext = true, requiredOnly = false, valueForBooleans = false, hideKeyboard)
        verifyPageLayout(pageCount(), pageNum + 1)

        //when
        fillPage(pageNum + 1, doNext, requiredOnly, valueForBooleans, hideKeyboard)

        //then
        verifyPageLayout(pageCount(), pageNum + 2)

        scenario.close()
    }


    @Test
    @Throws(Exception::class)
    fun fillPageNotRequired() {
        //given

        val pageNum = 0
        val hideKeyboard = false
        val requiredOnly = false
        val valueForBooleans = false
        val doNext = true

        val intent = Intent(appContext, NewTempLanguageActivity::class.java)
        val scenario = ActivityScenario.launch<NewTempLanguageActivity>(intent)

        verifyPageLayout(pageCount(), pageNum)

        //when
        fillPage(pageNum, doNext, requiredOnly, valueForBooleans, hideKeyboard)

        //then
        val pageNumExpected = 1
        verifyPageLayout(pageCount(), pageNumExpected)

        scenario.close()
    }


    @Test
    @Throws(Exception::class)
    fun fillAllPagesNotRequired() {
        //given

        val pageNum = 0
        val fillToPage: Int = pageCount()
        val hideKeyboard = false
        val requiredOnly = false
        val valueForBooleans = true

        val intent = Intent(appContext, NewTempLanguageActivity::class.java)
        val scenario = ActivityScenario.launch<NewTempLanguageActivity>(intent)

        verifyPageLayout(pageCount(), pageNum)

        //when
        fillUpToPage(fillToPage, hideKeyboard, requiredOnly, valueForBooleans, false)

        //then
        verifyPageLayout(pageCount(), fillToPage - 1)

        scenario.close()
    }

    @Test
    @Throws(Exception::class)
    fun fillAllPagesRequiredOnly() {
        //given

        val pageNum = 0
        val fillToPage: Int = pageCount()
        val hideKeyboard = false
        val requiredOnly = true
        val valueForBooleans = true

        val intent = Intent(appContext, NewTempLanguageActivity::class.java)
        val scenario = ActivityScenario.launch<NewTempLanguageActivity>(intent)

        verifyPageLayout(pageCount(), pageNum)

        //when
        fillUpToPage(fillToPage, hideKeyboard, requiredOnly, valueForBooleans, false)

        //then
        verifyPageLayout(pageCount(), fillToPage - 1)

        scenario.close()
    }

    @Test
    @Throws(Exception::class)
    fun requiredAnswerContinue() {
        //given

        val pageNum = 0

        val intent = Intent(appContext, NewTempLanguageActivity::class.java)
        val scenario = ActivityScenario.launch<NewTempLanguageActivity>(intent)

        verifyPageLayout(pageCount(), pageNum)

        //when
        onWaitForView(ViewMatchers.withId(R.id.next_button)).perform(ViewActions.click())
        thenShouldHaveRequiredAnswerDialog()
        onWaitForView(ViewMatchers.withText(R.string.dismiss)).perform(ViewActions.click())

        //then
        verifyPageLayout(pageCount(), pageNum)

        scenario.close()
    }

    @Test
    @Throws(Exception::class)
    fun missingAnswerContinue() {
        //given

        val pageNum = 0
        val hideKeyboard = true

        val intent = Intent(appContext, NewTempLanguageActivity::class.java)
        val scenario = ActivityScenario.launch<NewTempLanguageActivity>(intent)

        verifyPageLayout(pageCount(), pageNum)

        //when
        onWaitForView(ViewMatchers.withId(R.id.next_button)).perform(ViewActions.click())
        onWaitForView(ViewMatchers.withText(R.string.missing_question_answer))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed())) // dialog displayed
        onWaitForView(ViewMatchers.withText(R.string.missing_question_answer))
            .perform(ViewActions.pressBack()) // dismiss
        onWaitForView(ViewMatchers.withText(R.string.missing_question_answer))
            .check(ViewAssertions.doesNotExist()) // dialog dismissed

        //then
        val pageNumExpected = 0
        verifyPageLayout(pageCount(), pageNumExpected)

        scenario.close()
    }

    @Test
    @Throws(Exception::class)
    fun missingAnswerCancel() {
        //given

        val pageNum = 0
        val hideKeyboard = true

        val intent = Intent(appContext, NewTempLanguageActivity::class.java)
        val scenario = ActivityScenario.launch<NewTempLanguageActivity>(intent)

        verifyPageLayout(pageCount(), pageNum)

        //when
        onWaitForView(ViewMatchers.withId(R.id.next_button)).perform(ViewActions.click())
        onWaitForView(ViewMatchers.withText(R.string.missing_question_answer))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed())) // dialog displayed
        onWaitForView(ViewMatchers.withText(R.string.missing_question_answer))
            .perform(ViewActions.pressBack()) // dismiss dialog
        onWaitForView(ViewMatchers.withText(R.string.missing_question_answer))
            .check(ViewAssertions.doesNotExist()) // dialog dismissed

        //then
        verifyPageLayout(pageCount(), pageNum)

        scenario.close()
    }
}
