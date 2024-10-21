package com.door43.translationstudio.core;

import static org.junit.Assert.assertEquals;

import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.door43.data.AssetsProvider;
import com.door43.di.Development;
import com.door43.usecases.ParseMergeConflicts;
import com.door43.util.FileUtilities;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.unfoldingword.tools.logger.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;

/**
 * Created by blm on 7/25/16.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public class MergeConflictsParseTest {

    @Rule
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

    @Inject
    @Development
    AssetsProvider assetsProvider;

    public static final String TAG = MergeConflictsParseTest.class.getSimpleName();
    private String testText;
    private String expectedText;
    private final List<String> expectedTexts = new ArrayList<>();
    private final List<String> parsedText = new ArrayList<>();
    private int expectedConflictCount;
    private int foundConflictCount;
    private List<CharSequence> lastMergeConflictCards;

    @Before
    public void setUp() {
        hiltRule.inject();
        Logger.flush();
    }

    @After
    public void tearDown() {
    }

    @Test
    public void test01ProcessFullConflict() throws Exception {
        //given
        String testId = "merge/full_conflict";
        expectedConflictCount = 2;

        //when
        doRenderMergeConflicts(testId);

        //then
        verifyRenderText("test01ProcessFullConflict");
    }

    @Test
    public void test02ProcessTwoConflict() throws Exception {
        //given
        String testId = "merge/two_conflict";
        expectedConflictCount = 2;

        //when
        doRenderMergeConflicts(testId);

        //then
        verifyRenderText("test02ProcessTwoConflict");
    }

    @Test
    public void test03ProcessPartialConflict() throws Exception {
        //given
        String testId = "merge/partial_conflict";
        expectedConflictCount = 2;

        //when
        doRenderMergeConflicts(testId);

        //then
        verifyRenderText("test03ProcessPartialConflict");
    }

    @Test
    public void test04ProcessNestedHeadConflict() throws Exception {
        //given
        String testId = "merge/head_nested_conflict";
        expectedConflictCount = 3;

        //when
        doRenderMergeConflicts(testId);

        //then
        verifyRenderText("test04ProcessNestedHeadConflict");
    }

    @Test
    public void test05ProcessNestedTailConflict() throws Exception {
        //given
        String testId = "merge/tail_nested_conflict";
        expectedConflictCount = 3;

        //when
        doRenderMergeConflicts(testId);

        //then
        verifyRenderText("test05ProcessNestedTailConflict");
    }

    @Test
    public void test06ProcessNotFullNestedConflict() throws Exception {
        //given
        String testId = "merge/not_full_double_nested_conflict";
        expectedConflictCount = 4;

        //when
        doRenderMergeConflicts(testId);

        //then
        verifyRenderText("test06ProcessNotFullNestedConflict");
    }

    @Test
    public void test07ProcessNotFullEndNestedConflict() throws Exception {
        //given
        String testId = "merge/not_full_double_nested_conflict_end";
        expectedConflictCount = 4;

        //when
        doRenderMergeConflicts(testId);

        //then
        verifyRenderText("test07ProcessNotFullEndNestedConflict");
    }

    @Test
    public void test08ProcessNoConflict() throws Exception {
        //given
        String testTextFile = "merge/partial_conflict_part1.data";
        String expectTextFile = "merge/partial_conflict_part1.data";
        expectedConflictCount = 1;

        //when
        doRenderMergeConflicts(testTextFile, expectTextFile);

        //then
        verifyRenderText("test08ProcessNoConflict");
    }

    @Test
    public void test09DetectTwoMergeConflict() throws Exception {
        //given
        String testFile = "merge/two_conflict_raw.data";
        boolean expectedConflict = true;

        //when
        boolean conflicted = doDetectMergeConflict(testFile);

        //then
        assertEquals(expectedConflict, conflicted);
    }

    @Test
    public void test10DetectFullMergeConflict() throws Exception {
        //given
        String testFile = "merge/full_conflict_raw.data";
        boolean expectedConflict = true;

        //when
        boolean conflicted = doDetectMergeConflict(testFile);

        //then
        assertEquals(expectedConflict, conflicted);
    }

    @Test
    public void test11DetectPartialMergeConflict() throws Exception {
        //given
        String testFile = "merge/partial_conflict_raw.data";
        boolean expectedConflict = true;

        //when
        boolean conflicted = doDetectMergeConflict(testFile);

        //then
        assertEquals(expectedConflict, conflicted);
    }

    @Test
    public void test12DetectNoMergeConflict() throws Exception {
        //given
        String testFile = "merge/two_conflict_part1.data";
        boolean expectedConflict = false;

        //when
        boolean conflicted = doDetectMergeConflict(testFile);

        //then
        assertEquals(expectedConflict, conflicted);
    }

    @Test
    public void test13ProcessFullFiveWayNestedConflict() throws Exception {
        //given
        String testId = "merge/full_five_way_nested";
        expectedConflictCount = 5;

        //when
        doRenderMergeConflicts(testId);

        //then
        verifyRenderText("test13ProcessFullFiveWayNestedConflict");
    }

    @Test
    public void test14ProcessNotFullFiveWayNestedConflict() throws Exception {
        //given
        String testId = "merge/not_full_five_way_nested";
        expectedConflictCount = 5;

        //when
        doRenderMergeConflicts(testId);

        //then
        verifyRenderText("test14ProcessNotFullFiveWayNestedConflict");
    }

    @Test
    public void test15ProcessNotFullFiveWayNestedConflictComplex() throws Exception {
        //given
        String testId = "merge/not_full_five_way_nested_complex";
        expectedConflictCount = 5;

        //when
        doRenderMergeConflicts(testId);

        //then
        verifyRenderText("test15ProcessNotFullFiveWayNestedConflictComplex");
    }

    private void verifyRenderText(String id) {

        assertEquals("merge counts should be the same", expectedTexts.size(), parsedText.size());

        //sort to make sure in same order
        Collections.sort(parsedText);
        Collections.sort(expectedTexts);

        for (int i = 0; i < parsedText.size(); i++) {
            String got = parsedText.get(i);
            String expected = expectedTexts.get(i);
            verifyProcessedText(id + ": Conflict text " + i, expected, got);
        }
    }

    private void doRenderMergeConflicts( String testId) throws IOException {
        for(int i = 0; i < expectedConflictCount; i++) {
            String text = doRenderMergeConflict(testId, i + 1);
            parsedText.add(text);
            expectedTexts.add(expectedText);
        }

        if(expectedConflictCount != foundConflictCount) {
            for (int i = 0; i < lastMergeConflictCards.size(); i++) {
                CharSequence conflict = lastMergeConflictCards.get(i);
                Log.d(TAG, "conflict card " + i + ":\n" + conflict );
            }
            assertEquals("conflict count", expectedConflictCount, foundConflictCount);
        }
    }

    private void doRenderMergeConflicts(String testTextFile, String expectTextFile ) throws IOException {
        String text = doRenderMergeConflict(1, testTextFile, expectTextFile );
        parsedText.add(text);
        expectedTexts.add(expectedText);

        if(lastMergeConflictCards.size() > 1) {
            text = doRenderMergeConflict(2, testTextFile, expectTextFile);
            parsedText.add(text);
            expectedTexts.add(expectedText);
        }
    }

    private boolean doDetectMergeConflict(String testFile) throws IOException {
        InputStream testTextStream = assetsProvider.open(testFile);
        testText = FileUtilities.readStreamToString(testTextStream);
        Assert.assertNotNull(testText);
        Assert.assertFalse(testText.isEmpty());

        return MergeConflictsHandler.isMergeConflicted(testText);
    }

    private String doRenderMergeConflict(String testId, int sourceGroup) throws IOException {
        String testTextFile = testId+ "_raw.data";
        String expectTextFile = testId+ "_part" + sourceGroup + ".data";
        return doRenderMergeConflict(sourceGroup, testTextFile, expectTextFile);
    }

    private String doRenderMergeConflict(int sourceGroup, String testTextFile, String expectTextFile) throws IOException {
        InputStream testTextStream = assetsProvider.open(testTextFile);
        testText = FileUtilities.readStreamToString(testTextStream);
        Assert.assertNotNull(testText);
        Assert.assertFalse(testText.isEmpty());
        InputStream testExpectedStream = assetsProvider.open(expectTextFile);
        expectedText = FileUtilities.readStreamToString(testExpectedStream);
        Assert.assertNotNull(expectedText);
        Assert.assertFalse(expectedText.isEmpty());

        lastMergeConflictCards = ParseMergeConflicts.INSTANCE.execute(testText);
        foundConflictCount = lastMergeConflictCards.size();
        if(foundConflictCount >= sourceGroup) {
            return lastMergeConflictCards.get(sourceGroup - 1).toString();
        }
        return null;
    }

    private void verifyProcessedText(String id, String expectedText, String out) {
        Assert.assertNotNull(id, out);
        Assert.assertFalse(id, out.isEmpty());
        if(!out.equals(expectedText)) {
            Log.e(TAG, "error in: " + id);
            if(out.length() != expectedText.length()) {
                Log.e(TAG, "expected length " + expectedText.length() + " but got length " + out.length());
            }

            for( int ptr = 0; ; ptr++) {
                if(ptr >= out.length()) {
                    Log.e(TAG, "expected extra text at position " + ptr + ": '" + expectedText.substring(ptr) + "'");
                    if (ptr < expectedText.length()) {
                        Log.e(TAG, "character: '" + expectedText.charAt(ptr) + "', " + Character.codePointAt(expectedText, ptr) );
                    }
                    break;
                }
                if(ptr >= expectedText.length()) {
                    Log.e(TAG, "not expected extra text at position " + ptr + ": '" + out.substring(ptr) + "'");
                    Log.e(TAG, "character: '" + out.charAt(ptr) + "', " + Character.codePointAt(out, ptr));
                    break;
                }

                char cOut = out.charAt(ptr);
                char cExpect = expectedText.charAt(ptr);
                if(cOut != cExpect) {
                    Log.e(TAG, "expected different at position " + ptr );
                    Log.e(TAG, "expected sub match: '" + expectedText.substring(ptr) + "'");
                    Log.e(TAG, "but got sub match: '" + out.substring(ptr) + "'");
                    Log.e(TAG, "expected character: '" + expectedText.charAt(ptr) + "', " + Character.codePointAt(expectedText, ptr) );
                    Log.e(TAG, "but got character: '" + out.charAt(ptr) + "', " + Character.codePointAt(out, ptr));
                    Log.e(TAG, "expected: '" + expectedText + "'");
                    Log.e(TAG, "but got: '" + out + "'");
                    break;
                }
            }
            Log.e(TAG, "error in: " + id);
        }
        if(!out.equals(expectedText)) {
            assertEquals(id, out, expectedText);
        }
    }
}