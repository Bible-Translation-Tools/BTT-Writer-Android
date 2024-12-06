package com.door43.translationstudio.ui

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.pressBack
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.newlanguage.NewTempLanguageActivity
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
        ActivityScenario.launch<NewTempLanguageActivity>(intent).use {
            verifyPageLayout(pageCount(), pageNum)
            fillPage(pageNum, doNext = true, requiredOnly = false, valueForBooleans = false, hideKeyboard)
            verifyPageLayout(pageCount(), pageNum + 1)

            //when
            fillPage(pageNum + 1, doNext, requiredOnly, valueForBooleans, hideKeyboard)

            //then
            verifyPageLayout(pageCount(), pageNum + 2)
        }
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
        ActivityScenario.launch<NewTempLanguageActivity>(intent).use {
            verifyPageLayout(pageCount(), pageNum)

            //when
            fillPage(pageNum, doNext, requiredOnly, valueForBooleans, hideKeyboard)

            //then
            val pageNumExpected = 1
            verifyPageLayout(pageCount(), pageNumExpected)
        }
    }


    @Test
    @Throws(Exception::class)
    fun fillAllPagesNotRequired() {
        //given

        val pageNum = 0
        val fillToPage = pageCount()
        val hideKeyboard = false
        val requiredOnly = false
        val valueForBooleans = true

        val intent = Intent(appContext, NewTempLanguageActivity::class.java)
        ActivityScenario.launch<NewTempLanguageActivity>(intent).use {
            verifyPageLayout(pageCount(), pageNum)

            //when
            fillUpToPage(fillToPage, hideKeyboard, requiredOnly, valueForBooleans, false)

            //then
            verifyPageLayout(pageCount(), fillToPage - 1)
        }
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
        ActivityScenario.launch<NewTempLanguageActivity>(intent).use {
            verifyPageLayout(pageCount(), pageNum)

            //when
            fillUpToPage(fillToPage, hideKeyboard, requiredOnly, valueForBooleans, false)

            //then
            verifyPageLayout(pageCount(), fillToPage - 1)
        }
    }

    @Test
    @Throws(Exception::class)
    fun requiredAnswerContinue() {
        //given

        val pageNum = 0

        val intent = Intent(appContext, NewTempLanguageActivity::class.java)
        ActivityScenario.launch<NewTempLanguageActivity>(intent).use {
            verifyPageLayout(pageCount(), pageNum)

            //when
            onView(withId(R.id.next_button)).tryPerform(click())
            thenShouldHaveRequiredAnswerDialog()
            onView(withText(R.string.dismiss)).tryPerform(click())

            //then
            verifyPageLayout(pageCount(), pageNum)
        }
    }

    @Test
    @Throws(Exception::class)
    fun missingAnswerContinue() {
        //given

        val pageNum = 0

        val intent = Intent(appContext, NewTempLanguageActivity::class.java)
        ActivityScenario.launch<NewTempLanguageActivity>(intent).use {
            verifyPageLayout(pageCount(), pageNum)

            //when
            onView(withId(R.id.next_button)).tryPerform(click())
            onView(withText(R.string.missing_question_answer))
                .tryCheck(matches(isDisplayed())) // dialog displayed
            onView(withText(R.string.missing_question_answer))
                .tryPerform(pressBack()) // dismiss
            onView(withText(R.string.missing_question_answer))
                .tryCheck(doesNotExist()) // dialog dismissed

            //then
            val pageNumExpected = 0
            verifyPageLayout(pageCount(), pageNumExpected)
        }
    }

    @Test
    @Throws(Exception::class)
    fun missingAnswerCancel() {
        //given

        val pageNum = 0

        val intent = Intent(appContext, NewTempLanguageActivity::class.java)
        ActivityScenario.launch<NewTempLanguageActivity>(intent).use {
            verifyPageLayout(pageCount(), pageNum)

            //when
            onView(withId(R.id.next_button)).tryPerform(click())
            onView(withText(R.string.missing_question_answer))
                .tryCheck(matches(isDisplayed())) // dialog displayed
            onView(withText(R.string.missing_question_answer))
                .tryPerform(pressBack()) // dismiss dialog
            onView(withText(R.string.missing_question_answer))
                .tryCheck(doesNotExist()) // dialog dismissed

            //then
            verifyPageLayout(pageCount(), pageNum)
        }
    }
}
