package com.door43.translationstudio.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.door43.data.AssetsProvider;
import com.door43.data.IDirectoryProvider;
import com.door43.translationstudio.ui.spannables.USFMVerseSpan;
import com.door43.util.FileUtilities;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.unfoldingword.door43client.Door43Client;
import org.unfoldingword.door43client.models.ChunkMarker;
import org.unfoldingword.door43client.models.TargetLanguage;
import org.unfoldingword.tools.logger.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;


/**
 * Created by blm on 4/19/16.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public class ImportUsfmTest {

    @Rule
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

    @Inject
    @ApplicationContext Context appContext;
    @Inject
    Door43Client library;
    @Inject
    IDirectoryProvider directoryProvider;
    @Inject
    Profile profile;
    @Inject
    AssetsProvider assetsProvider;

    private JSONArray expectedBooks;
    private TargetLanguage targetLanguage;
    private ImportUSFM usfm;
    private HashMap<String, List<String>> chunks;
    private String[] chapters;

    @Before
    public void setUp() {
        expectedBooks = new JSONArray();

        hiltRule.inject();

        Logger.flush();
        targetLanguage = library.index.getTargetLanguage("es");
    }

    @After
    public void tearDown() {
        if (usfm != null) {
            usfm.cleanup();
        }
    }

    @Test
    public void test01ValidImportMark() throws Exception {
        //given
        String source = "mrk.usfm";
        addExpectedBook(source, "mrk", true, false);
        boolean expectSuccess = true;
        boolean expectNoEmptyChunks = true;
        boolean expectAllVerses = true;
        int expectedVerseCount = 678;

        usfm = new ImportUSFM.Builder(
                appContext,
                directoryProvider,
                profile,
                library,
                assetsProvider
        )
                .fromRc(targetLanguage, "usfm/" + source, null)
                .build();

        assertNotNull(usfm);

        //when
        boolean success = usfm.isProcessSuccess();

        //then
        verifyResults(
                success,
                expectSuccess,
                expectedBooks,
                expectNoEmptyChunks,
                expectAllVerses,
                expectedVerseCount
        );
    }

    @Test
    public void test02ImportMarkMissingName() throws Exception {
        //given
        String source = "mrk_no_id.usfm";
        addExpectedBook(source, "", false, true); // not expecting any books to be found
        boolean expectNoEmptyChunks = true;
        boolean expectSuccess = true;
        boolean expectAllVerses = true;
        int expectedVerseCount = 0;

        usfm = new ImportUSFM.Builder(
                appContext,
                directoryProvider,
                profile,
                library,
                assetsProvider
        )
                .fromRc(targetLanguage, "usfm/" + source, null)
                .build();

        assertNotNull(usfm);

        //when
        boolean success = usfm.isProcessSuccess();

        //then
        verifyResults(success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses
                , expectedVerseCount);
    }

    @Test
    public void test03ImportMarkMissingNameForce() throws Exception {
        //given
        String source = "mrk_no_id.usfm";
        String useName = "Mrk";
        addExpectedBook(source, useName, true, false);
        boolean expectSuccess = true;
        boolean expectNoEmptyChunks = true;
        boolean expectAllVerses = true;
        int expectedVerseCount = 678;
        usfm = new ImportUSFM.Builder(
                appContext,
                directoryProvider,
                profile,
                library,
                assetsProvider
        )
                .fromRc(targetLanguage, "usfm/" + source, null)
                .build();

        InputStream usfmStream = assetsProvider.open("usfm/" + source);
        String text = FileUtilities.readStreamToString(usfmStream);

        //when
        boolean success = usfm.processText(text, source, false, useName);

        //then
        verifyResults(success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses
                , expectedVerseCount);
    }

    @Test
    public void test04ValidImportPsalms() throws Exception {
        //given
        String source = "19-PSA.usfm"; // psalms has a verse range
        addExpectedBook(source, "psa", true, false);
        boolean expectNoEmptyChunks = true;
        boolean expectSuccess = true;
        boolean expectAllVerses = false;
        int expectedVerseCount = 2461;
        usfm = new ImportUSFM.Builder(
                appContext,
                directoryProvider,
                profile,
                library,
                assetsProvider
        )
                .fromRc(targetLanguage, "usfm/" + source, null)
                .build();

        assertNotNull(usfm);

        //when
        boolean success = usfm.isProcessSuccess();

        //then
        verifyResults(success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses
                , expectedVerseCount);
    }

    @Test
    public void test05ImportMarkNoChapters() throws Exception {
        //given
        String source = "mrk_no_chapter.usfm";
        addExpectedBook(source, "mrk", false, false);
        boolean expectNoEmptyChunks = true;
        boolean expectSuccess = false;
        boolean expectAllVerses = true;
        usfm = new ImportUSFM.Builder(
                appContext,
                directoryProvider,
                profile,
                library,
                assetsProvider
        )
                .fromRc(targetLanguage, "usfm/" + source, null)
                .build();

        assertNotNull(usfm);

        //when
        boolean success = usfm.isProcessSuccess();

        //then
        verifyResults(success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses);
    }

    @Test
    public void test06ImportMarkMissingChapters() throws Exception {
        //given
        String source = "mrk_one_chapter.usfm";
        addExpectedBook(source, "mrk", false, false);
        boolean expectSuccess = true;
        boolean expectNoEmptyChunks = false;
        boolean expectAllVerses = true;
        int expectedVerseCount = 45;
        usfm = new ImportUSFM.Builder(
                appContext,
                directoryProvider,
                profile,
                library,
                assetsProvider
        )
                .fromRc(targetLanguage, "usfm/" + source, null)
                .build();

        assertNotNull(usfm);

        //when
        boolean success = usfm.isProcessSuccess();

        //then
        verifyResults(success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses
                , expectedVerseCount);
    }

    @Test
    public void test07ImportMarkNoVerses() throws Exception {
        //given
        String source = "mrk_no_verses.usfm";
        addExpectedBook(source, "mrk", false, false);
        boolean expectSuccess = false;
        boolean expectNoEmptyChunks = false;
        boolean expectAllVerses = true;
        int expectedVerseCount = 0;
        usfm = new ImportUSFM.Builder(
                appContext,
                directoryProvider,
                profile,
                library,
                assetsProvider
        )
                .fromRc(targetLanguage, "usfm/" + source, null)
                .build();

        assertNotNull(usfm);

        //when
        boolean success = usfm.isProcessSuccess();

        //then
        verifyResults(success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses
                , expectedVerseCount);
    }

    @Test
    public void test08ImportMarkMissingVerse() throws Exception {
        //given
        String source = "mrk_missing_verse.usfm";
        addExpectedBook(source, "mrk", false, false);
        boolean expectSuccess = true;
        boolean expectNoEmptyChunks = true;
        boolean expectAllVerses = false;
        int expectedVerseCount = 677;
        usfm = new ImportUSFM.Builder(
                appContext,
                directoryProvider,
                profile,
                library,
                assetsProvider
        )
                .fromRc(targetLanguage, "usfm/" + source, null)
                .build();

        assertNotNull(usfm);

        //when
        boolean success = usfm.isProcessSuccess();

        //then
        verifyResults(success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses
                , expectedVerseCount);
    }

    @Test
    public void test09ImportMarkEmptyChapter() throws Exception {
        //given
        String source = "mrk_empty_chapter.usfm";
        addExpectedBook(source, "mrk", false, false);
        boolean expectSuccess = true;
        boolean expectNoEmptyChunks = false;
        boolean expectAllVerses = true;
        int expectedVerseCount = 633;
        usfm = new ImportUSFM.Builder(
                appContext,
                directoryProvider,
                profile,
                library,
                assetsProvider
        )
                .fromRc(targetLanguage, "usfm/" + source, null)
                .build();

        assertNotNull(usfm);

        //when
        boolean success = usfm.isProcessSuccess();

        //then
        verifyResults(success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses
                , expectedVerseCount);
    }

    @Test
    public void test10ImportJudeNoVerses() throws Exception {
        //given
        String source = "jude.no_verses.usfm";
        addExpectedBook(source, "jud", false, false);
        boolean expectSuccess = false;
        boolean expectNoEmptyChunks = true;
        boolean expectAllVerses = true;
        int expectedVerseCount = 0;
        usfm = new ImportUSFM.Builder(
                appContext,
                directoryProvider,
                profile,
                library,
                assetsProvider
        )
                .fromRc(targetLanguage, "usfm/" + source, null)
                .build();

        assertNotNull(usfm);

        //when
        boolean success = usfm.isProcessSuccess();

        //then
        verifyResults(success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses
                , expectedVerseCount);
    }

    @Test
    public void test11ImportJudeNoChapter() throws Exception {
        //given
        String source = "jude.no_chapter_or_verses.usfm";
        addExpectedBook(source, "jud", false, false);
        boolean expectSuccess = false;
        boolean expectNoEmptyChunks = true;
        boolean expectAllVerses = true;
        int expectedVerseCount = 0;
        usfm = new ImportUSFM.Builder(
                appContext,
                directoryProvider,
                profile,
                library,
                assetsProvider
        )
                .fromRc(targetLanguage, "usfm/" + source, null)
                .build();

        assertNotNull(usfm);

        //when
        boolean success = usfm.isProcessSuccess();

        //then
        verifyResults(success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses
                , expectedVerseCount);
    }

    @Test
    public void test12ImportPhpNoChapter1() throws Exception {
        //given
        String source = "php_usfm_NoC1.usfm";
        addExpectedBook(source, "php", false, false);
        boolean expectSuccess = true;
        boolean expectNoEmptyChunks = false;
        boolean expectAllVerses = true;
        int expectedVerseCount = 74;
        usfm = new ImportUSFM.Builder(
                appContext,
                directoryProvider,
                profile,
                library,
                assetsProvider
        )
                .fromRc(targetLanguage, "usfm/" + source, null)
                .build();

        assertNotNull(usfm);

        //when
        boolean success = usfm.isProcessSuccess();

        //then
        verifyResults(success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses
                , expectedVerseCount);
    }

    @Test
    public void test13ImportPhpNoChapter2() throws Exception {
        //given
        String source = "php_usfm_NoC2.usfm";
        addExpectedBook(source, "php", false, false);
        boolean expectSuccess = true;
        boolean expectNoEmptyChunks = false;
        boolean expectAllVerses = true;
        int expectedVerseCount = 74;
        usfm = new ImportUSFM.Builder(
                appContext,
                directoryProvider,
                profile,
                library,
                assetsProvider
        )
                .fromRc(targetLanguage, "usfm/" + source, null)
                .build();

        assertNotNull(usfm);

        //when
        boolean success = usfm.isProcessSuccess();

        //then
        verifyResults(success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses
                , expectedVerseCount);
    }

    @Test
    public void test14ImportPhpChapter3OutOfOrder() throws Exception {
        //given
        String source = "php_usfm_C3_out_of_order.usfm";
        addExpectedBook(source, "php", false, false);
        boolean expectSuccess = false;
        boolean expectNoEmptyChunks = true;
        boolean expectAllVerses = true;
        usfm = new ImportUSFM.Builder(
                appContext,
                directoryProvider,
                profile,
                library,
                assetsProvider
        )
                .fromRc(targetLanguage, "usfm/" + source, null)
                .build();

        assertNotNull(usfm);

        //when
        boolean success = usfm.isProcessSuccess();

        //then
        verifyResults(success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses);
    }

    @Test
    public void test15ImportPhpMissingLastChapter() throws Exception {
        //given
        String source = "php_usfm_missing_last_chapter.usfm";
        addExpectedBook(source, "php", false, false);
        boolean expectSuccess = true;
        boolean expectNoEmptyChunks = false;
        boolean expectAllVerses = true;
        int expectedVerseCount = 81;
        usfm = new ImportUSFM.Builder(
                appContext,
                directoryProvider,
                profile,
                library,
                assetsProvider
        )
                .fromRc(targetLanguage, "usfm/" + source, null)
                .build();

        assertNotNull(usfm);

        //when
        boolean success = usfm.isProcessSuccess();

        //then
        verifyResults(success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses
                , expectedVerseCount);
    }

    @Test
    public void test16ImportPhpNoChapter1Marker() throws Exception {
        //given
        String source = "php_usfm_NoC1_marker.usfm";
        addExpectedBook(source, "php", false, false);
        boolean expectSuccess = true;
        boolean expectNoEmptyChunks = true;
        boolean expectAllVerses = true;
        int expectedVerseCount = 104;
        usfm = new ImportUSFM.Builder(
                appContext,
                directoryProvider,
                profile,
                library,
                assetsProvider
        )
                .fromRc(targetLanguage, "usfm/" + source, null)
                .build();

        assertNotNull(usfm);

        //when
        boolean success = usfm.isProcessSuccess();

        //then
        verifyResults(success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses
                , expectedVerseCount);
    }

    @Test
    public void test17ImportPhpNoChapter2Marker() throws Exception {
        //given
        String source = "php_usfm_NoC2_marker.usfm";
        addExpectedBook(source, "php", false, false);
        boolean expectSuccess = true;
        boolean expectNoEmptyChunks = false;
        boolean expectAllVerses = true;
        int expectedVerseCount = 104;
        usfm = new ImportUSFM.Builder(
                appContext,
                directoryProvider,
                profile,
                library,
                assetsProvider
        )
                .fromRc(targetLanguage, "usfm/" + source, null)
                .build();

        assertNotNull(usfm);

        //when
        boolean success = usfm.isProcessSuccess();

        //then
        verifyResults(success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses
                , expectedVerseCount);
    }

    @Test
    public void test18ImportPhpMissingLastChapterMarker() throws Exception {
        //given
        String source = "php_usfm_missing_last_chapter_marker.usfm";
        addExpectedBook(source, "php", true, false);
        boolean expectSuccess = true;
        boolean expectNoEmptyChunks = false;
        boolean expectAllVerses = true;
        int expectedVerseCount = 104;
        usfm = new ImportUSFM.Builder(
                appContext,
                directoryProvider,
                profile,
                library,
                assetsProvider
        )
                .fromRc(targetLanguage, "usfm/" + source, null)
                .build();

        assertNotNull(usfm);

        //when
        boolean success = usfm.isProcessSuccess();

        //then
        verifyResults(success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses
                , expectedVerseCount);
    }

    @Test
    public void test19ImportJudeOutOfOrderVerses() throws Exception {
        //given
        String source = "jude.out_order_verses.usfm";
        addExpectedBook(source, "jud", false, false);
        boolean expectSuccess = true;
        boolean expectNoEmptyChunks = true;
        boolean expectAllVerses = false;
        int expectedVerseCount = 25;
        usfm = new ImportUSFM.Builder(
                appContext,
                directoryProvider,
                profile,
                library,
                assetsProvider
        )
                .fromRc(targetLanguage, "usfm/" + source, null)
                .build();

        assertNotNull(usfm);

        //when
        boolean success = usfm.isProcessSuccess();

        //then
        verifyResults(success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses
                , expectedVerseCount);
    }

    @Test
    public void test20ImportPhpMissingInitialAndFinalVerses() throws Exception {
        //given
        String source = "php_usfm_missing_initial_and_final_vs.usfm";
        addExpectedBook(source, "php", false, false);
        boolean expectSuccess = true;
        boolean expectNoEmptyChunks = false;
        boolean expectAllVerses = false;
        int expectedVerseCount = 6;
        usfm = new ImportUSFM.Builder(
                appContext,
                directoryProvider,
                profile,
                library,
                assetsProvider
        )
                .fromRc(targetLanguage, "usfm/" + source, null)
                .build();

        assertNotNull(usfm);

        //when
        boolean success = usfm.isProcessSuccess();

        //then
        verifyResults(success, expectSuccess, expectedBooks, expectNoEmptyChunks, expectAllVerses
                , expectedVerseCount);
    }

    public void addExpectedBook(String filename, String book, boolean success,
                                boolean missingName) throws JSONException {
        JSONObject expectedBook = new JSONObject();
        expectedBook.put("filename", filename);
        expectedBook.put("book", book);
        expectedBook.put("success", success);
        expectedBook.put("missingName", missingName);
        expectedBooks.put(expectedBook);
    }

    public String getFileName(JSONObject object) throws JSONException {
        return object.getString("filename");
    }

    public String getBook(JSONObject object) throws JSONException {
        return object.getString("book");
    }

    public boolean getMissingName(JSONObject object) throws JSONException {
        return object.getBoolean("missingName");
    }

    public boolean getSuccess(JSONObject object) throws JSONException {
        return object.getBoolean("success");
    }

    public void verifyResults(boolean success, boolean expected, JSONArray expectedBooks,
                              boolean noEmptyChunks, boolean expectAllVerses) throws JSONException {
        verifyResults(success, expected, expectedBooks, noEmptyChunks, expectAllVerses, -1);
    }

    public void verifyResults(boolean success, boolean expected, JSONArray expectedBooks,
                              boolean noEmptyChunks, boolean expectAllVerses,
                              int expectedVerseCount) throws JSONException {
        String results = usfm.getResultsString();
        assertTrue("results text should not be empty", !results.isEmpty());
        assertEquals("results", expected, success);
        assertEquals("results", expected, usfm.isProcessSuccess());
        String[] resultLines = results.split("\n");

        int missingNamesCount = 0;

        for (int i = 0; i < expectedBooks.length(); i++) {
            JSONObject object = expectedBooks.getJSONObject(i);
            String fileName = getFileName(object);
            String book = getBook(object);
            boolean expectedSuccess = getSuccess(object);
            boolean missingName = getMissingName(object);
            verifyBookResults(resultLines, fileName, book, expectedSuccess, noEmptyChunks,
                    success, expectAllVerses, expectedVerseCount);
            if (missingName) {
                findMissingName(fileName);
                missingNamesCount++;
            }
        }
        List<MissingNameItem> missingNameItems = usfm.getBooksMissingNames();
        assertEquals("Missing name count should equal", missingNamesCount, missingNameItems.size());
    }

    public void findMissingName(String filename) {
        List<MissingNameItem> missingNameItems = usfm.getBooksMissingNames();
        boolean found = false;
        for (MissingNameItem missingNameItem : missingNameItems) {
            if (missingNameItem.getDescription() != null && missingNameItem.getDescription().contains(filename)) {
                found = true;
                break;
            }
        }
        assertTrue(filename + " should be missing name ", found);
    }

    /**
     * parse chunk markers (contains verses and chapters) into map of verses indexed by chapter
     *
     * @param chunks
     * @return
     */
    public boolean parseChunks(List<ChunkMarker> chunks) {
        this.chunks = new HashMap<>(); // clear old map
        try {
            for (ChunkMarker chunkMarker : chunks) {

                String chapter = chunkMarker.chapter;
                String firstVerse = chunkMarker.verse;

                List<String> verses;
                if (this.chunks.containsKey(chapter)) {
                    verses = this.chunks.get(chapter);
                } else {
                    verses = new ArrayList<>();
                    this.chunks.put(chapter, verses);
                }

                verses.add(firstVerse);
            }

            //extract chapters
            List<String> foundChapters = new ArrayList<>();
            for (String chapter : this.chunks.keySet()) {
                foundChapters.add(chapter);
            }
            Collections.sort(foundChapters);
            chapters = foundChapters.toArray(new String[foundChapters.size()]);
            ;

        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public void verifyBookResults(String[] results, String filename, String book,
                                  boolean noErrorsExpected, boolean noEmptyChunks,
                                  boolean success, boolean expectAllVerses,
                                  int expectedVerseCount) {
        String bookLine = filename;
        if (!book.isEmpty()) {
            bookLine = book.toLowerCase() + " = " + filename;
        }
        String foundBookMarker = "Found book: ";
        String expectLine = foundBookMarker + bookLine;
        boolean bookFound = false;
        int verseCount = 0;
        for (int i = 0; i < results.length; i++) {
            String line = results[i];

            if (line.contains(expectLine)) {
                boolean noErrorsFound = false;

                for (int j = i + 1; j < results.length; j++) {
                    String resultsLine = results[j];

                    int pos = resultsLine.indexOf(foundBookMarker); // if starting next book,
                    // then done
                    if (pos >= 0) {
                        break;
                    }

                    pos = resultsLine.indexOf("No errors Found");
                    if (pos >= 0) {
                        noErrorsFound = true;
                        break;
                    }
                }
                assertEquals(bookLine + " found, no errors expected " + noErrorsExpected,
                        noErrorsExpected, noErrorsFound);
                bookFound = true;
                break;
            }
        }
        assertTrue(bookLine + " not found", bookFound);

        String chunk = "";

        // verify chapters and verses
        if (success && !book.isEmpty()) {
            List<File> projects = usfm.getImportProjects();
            assertTrue("Import Projects count should be greater than zero, but is " + projects.size(), !projects.isEmpty());

            for (File project : projects) {

                List<ChunkMarker> chunks = library.index.getChunkMarkers(
                        book.toLowerCase(),
                        "en-US"
                );
                assertFalse("chunk count should not be empty", chunks.isEmpty());
                parseChunks(chunks);

                for (String chapter : chapters) {
                    // verify chapter
                    File chapterPath = new File(project, getRightChapterLength(chapter));
                    boolean exists = chapterPath.exists();
                    if (!exists) {
                        fail("Chapter missing " + chapterPath);
                    }

                    // verify chunks
                    List<String> chapterFrameSlugs = this.chunks.get(chapter);
                    for (int i = 0; i < chapterFrameSlugs.size(); i++) {
                        String chapterFrameSlug = chapterFrameSlugs.get(i);
                        int expectCount = -1;
                        if (i + 1 < chapterFrameSlugs.size()) {
                            String nextSlug = chapterFrameSlugs.get(i + 1);
                            int nextStart = Integer.parseInt(nextSlug);
                            if (nextStart > 0) {
                                expectCount = nextStart - Integer.parseInt(chapterFrameSlug);
                            }
                        }

                        File chunkPath = new File(chapterPath,
                                ExportUsfmTest.getRightFileNameLength(chapterFrameSlug) + ".txt");
                        assertTrue("Chunk missing " + chunkPath.toString(), chunkPath.exists());
                        try {
                            chunk = FileUtilities.readFileToString(chunkPath);
                            int count = getVerseCount(chunk);
                            verseCount += count;
                            if (noEmptyChunks) {
                                boolean emptyChunk = chunk.isEmpty();
                                assertFalse("Chunk is empty " + chunkPath, emptyChunk);
                                assertTrue("VerseCount should not be zero: " + count + " in chunk" +
                                        " " + chunkPath.toString(), count > 0);
                                if ((expectCount >= 0) && expectAllVerses) {
                                    assertEquals("Verse Count" + " in chunk " + chunkPath, expectCount, count);
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                            Assert.fail("Could not read chunk " + chunkPath.toString());
                        }
                    }
                }
            }
        }

        if (expectedVerseCount >= 0) {
            assertEquals("Verse counts should match", verseCount, expectedVerseCount);
        }
    }

    /**
     * right size the chapter string.  App expects chapter numbers under 100 to be only two digits.
     *
     * @param chapterN
     * @return
     */
    private String getRightChapterLength(String chapterN) {
        int chapterNInt = strToInt(chapterN, -1);
        if ((chapterNInt >= 0) && (chapterNInt < 100)) {
            chapterN = chapterN.substring(chapterN.length() - 2);
        }
        return chapterN;
    }

    /**
     * do string to integer with default value on conversion error
     *
     * @param value
     * @param defaultValue
     * @return
     */
    public static int strToInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            Log.d(ImportUsfmTest.class.getSimpleName(), "Cannot convert to int: " + value);
        }
        return defaultValue;
    }

    private static final Pattern PATTERN_USFM_VERSE_SPAN = Pattern.compile(USFMVerseSpan.PATTERN);

    /**
     * get verse count
     */
    private int getVerseCount(String text) {
        int foundVerseCount = 0;
        Matcher matcher = PATTERN_USFM_VERSE_SPAN.matcher(text);
        int currentVerse;
        int endVerseRange;

        while (matcher.find()) {

            String verse = matcher.group(1);
            int[] verseRange = getVerseRange(verse);
            if (null == verseRange) {
                break;
            }
            currentVerse = verseRange[0];
            endVerseRange = verseRange[1];

            if (endVerseRange > 0) {
                foundVerseCount += (endVerseRange - currentVerse + 1);
            } else {
                foundVerseCount++;
            }
        }
        return foundVerseCount;
    }

    /**
     * parse verse number to get range
     *
     * @param verse
     * @return
     */
    private int[] getVerseRange(String verse) {
        int[] verseRange;
        int currentVerse;
        int endVerseRange;
        try {
            int currentVers = Integer.parseInt(verse);
            verseRange = new int[]{currentVers, 0};
        } catch (NumberFormatException e) { // might be a range in format 12-13
            String[] range = verse.split("-");
            if (range.length < 2) {
                verseRange = null;
            } else {
                currentVerse = Integer.parseInt(range[0]);
                endVerseRange = Integer.parseInt(range[1]);
                verseRange = new int[]{currentVerse, endVerseRange};
            }
        }
        return verseRange;
    }


}