package com.door43.translationstudio.ui.newlanguage;

import android.content.Intent;

import com.door43.translationstudio.R;

import org.junit.Test;


import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.pressBack;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import androidx.test.filters.LargeTest;

@LargeTest
public class QuestionnaireActivityUiTest extends NewLanguageActivityUiUtils {

    @Test
    public void fillPageBoolean() throws Exception {

        //given
        int pageNum = 0;
        boolean hideKeyboard = false;
        boolean requiredOnly = false;
        boolean valueForBooleans = false;
        boolean doNext = true;
        mActivityRule.launchActivity(new Intent());
        verifyPageLayout(pageCount(), pageNum);
        fillPage(pageNum, true, false, false, hideKeyboard);
        verifyPageLayout(pageCount(), pageNum + 1);

        //when
        fillPage(pageNum + 1, doNext, requiredOnly, valueForBooleans, hideKeyboard);

        //then
        verifyPageLayout(pageCount(), pageNum + 2);
    }


    @Test
    public void fillPageNotRequired() throws Exception {

        //given
        int pageNum = 0;
        boolean hideKeyboard = false;
        boolean requiredOnly = false;
        boolean valueForBooleans = false;
        boolean doNext = true;
        mActivityRule.launchActivity(new Intent());
        verifyPageLayout(pageCount(), pageNum);

        //when
        fillPage(pageNum, doNext, requiredOnly, valueForBooleans, hideKeyboard);

        //then
        int pageNumExpected = 1;
        verifyPageLayout(pageCount(), pageNumExpected);
    }


    @Test
    public void fillAllPagesNotRequired() throws Exception {

        //given
        int pageNum = 0;
        int fillToPage = pageCount();
        boolean hideKeyboard = false;
        boolean requiredOnly = false;
        boolean valueForBooleans = true;
        mActivityRule.launchActivity(new Intent());
        verifyPageLayout(pageCount(), pageNum);

        //when
        fillUpToPage(fillToPage, hideKeyboard, requiredOnly, valueForBooleans, false);

        //then
        verifyPageLayout(pageCount(), fillToPage - 1);
    }

    @Test
    public void fillAllPagesRequiredOnly() throws Exception {

        //given
        int pageNum = 0;
        int fillToPage = pageCount();
        boolean hideKeyboard = false;
        boolean requiredOnly = true;
        boolean valueForBooleans = true;
        mActivityRule.launchActivity(new Intent());
        verifyPageLayout(pageCount(), pageNum);

        //when
        fillUpToPage(fillToPage, hideKeyboard, requiredOnly, valueForBooleans, false);

        //then
        verifyPageLayout(pageCount(), fillToPage - 1);
    }

    @Test
    public void requiredAnswerContinue() throws Exception {

        //given
        int pageNum = 0;
        mActivityRule.launchActivity(new Intent());
        verifyPageLayout(pageCount(), pageNum);

        //when
        onView(withId(R.id.next_button)).perform(click());
        thenShouldHaveRequiredAnswerDialog();
        onView(withText(R.string.dismiss)).perform(click());

        //then
        verifyPageLayout(pageCount(), pageNum);
    }

    @Test
    public void missingAnswerContinue() throws Exception {

        //given
        int pageNum = 0;
        boolean hideKeyboard = true;
        mActivityRule.launchActivity(new Intent());
        verifyPageLayout(pageCount(), pageNum);

        //when
        onView(withId(R.id.next_button)).perform(click());
        onView(withText(R.string.missing_question_answer)).check(matches(isDisplayed())); // dialog displayed
        onView(withText(R.string.missing_question_answer)).perform(pressBack()); // dismiss
        onView(withText(R.string.missing_question_answer)).check(doesNotExist()); // dialog dismissed

        //then
        int pageNumExpected = 0;
        verifyPageLayout(pageCount(), pageNumExpected);
    }

    @Test
    public void missingAnswerCancel() throws Exception {

        //given
        int pageNum = 0;
        boolean hideKeyboard = true;
        mActivityRule.launchActivity(new Intent());
        verifyPageLayout(pageCount(), pageNum);

        //when
        onView(withId(R.id.next_button)).perform(click());
        onView(withText(R.string.missing_question_answer)).check(matches(isDisplayed())); // dialog displayed
        onView(withText(R.string.missing_question_answer)).perform(pressBack()); // dismiss dialog
        onView(withText(R.string.missing_question_answer)).check(doesNotExist()); // dialog dismissed

        //then
        verifyPageLayout(pageCount(), pageNum);
    }

}
