package com.door43.translationstudio.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.door43.data.AssetsProvider;
import com.door43.data.IDirectoryProvider;
import com.door43.data.IPreferenceRepository;
import com.door43.translationstudio.App;
import com.door43.usecases.ExportProjects;
import com.door43.util.FileUtilities;
import com.door43.util.Zip;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.unfoldingword.door43client.Door43Client;
import org.unfoldingword.door43client.models.TargetLanguage;
import org.unfoldingword.door43client.models.Translation;
import org.unfoldingword.resourcecontainer.ResourceContainer;
import org.unfoldingword.tools.logger.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;

/**
 * Created by blm on 7/25/16.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public class ExportUsfmTest {

    @Rule
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

    @Inject
    @ApplicationContext
    Context appContext;
    @Inject
    Door43Client library;
    @Inject
    IDirectoryProvider directoryProvider;
    @Inject
    Profile profile;
    @Inject
    ExportProjects exportProjects;
    @Inject
    AssetsProvider assetsProvider;
    @Inject
    IPreferenceRepository prefRepository;

    public static final String TAG = ExportUsfmTest.class.getSimpleName();
    private File tempFolder;
    private TargetLanguage targetLanguage;
    private ImportUSFM usfm;
    private File outputFile;
    private TargetTranslation targetTranslation;
    private String errorLog;

    @Before
    public void setUp() {
        errorLog = null;
        Logger.flush();

        hiltRule.inject();

        if (!library.getIsLibraryDeployed()) {
            directoryProvider.deployDefaultLibrary();
        }
        targetLanguage = library.index.getTargetLanguage("aae");
    }

    @After
    public void tearDown() {
        if (usfm != null) {
            usfm.cleanup();
        }
        FileUtilities.deleteQuietly(tempFolder);
        directoryProvider.deleteAll();
    }

    @Test
    public void test01ValidExportMarkSingle() throws Exception {
        //given
        String zipFileName = null;
        boolean separateChapters = false;
        String source = "mrk.usfm";
        importTestTranslation(source);

        //when
        ExportProjects.Result result = exportProjects.exportUSFM(
                targetTranslation,
                Uri.fromFile(outputFile)
        );

        assertTrue(result.getSuccess());

        Uri usfmOutput = result.getUri();

        //then
        verifyExportedUsfmFile(zipFileName, separateChapters, source, usfmOutput);
    }

    @Test
    public void test02ValidExportPsalmSingle() throws Exception {
        //given
        String zipFileName = null;
        boolean separateChapters = false;
        String source = "19-PSA.usfm";
        importTestTranslation(source);

        //when
        ExportProjects.Result result = exportProjects.exportUSFM(
                targetTranslation,
                Uri.fromFile(outputFile)
        );

        assertTrue(result.getSuccess());

        Uri usfmOutput = result.getUri();

        //then
        verifyExportedUsfmFile(zipFileName, separateChapters, source, usfmOutput);
    }

    @Test
    public void test03ValidExportJudeSingle() throws Exception {
        //given
        String zipFileName = null;
        boolean separateChapters = false;
        String source = "66-JUD.usfm";
        importTestTranslation(source);

        //when
        ExportProjects.Result result = exportProjects.exportUSFM(
                targetTranslation,
                Uri.fromFile(outputFile)
        );

        assertTrue(result.getSuccess());

        Uri usfmOutput = result.getUri();

        //then
        verifyExportedUsfmFile(zipFileName, separateChapters, source, usfmOutput);
    }

    @Test
    public void test04ValidExportJobSingle() throws Exception {
        //given
        String zipFileName = null;
        boolean separateChapters = false;
        String source = "18-JOB.usfm";
        importTestTranslation(source);

        //when
        ExportProjects.Result result = exportProjects.exportUSFM(
                targetTranslation,
                Uri.fromFile(outputFile)
        );

        assertTrue(result.getSuccess());

        Uri usfmOutput = result.getUri();

        //then
        verifyExportedUsfmFile(zipFileName, separateChapters, source, usfmOutput);
    }

    @Test
    public void test08ValidExportIsaiahSingle() throws Exception {
        //given
        String zipFileName = null;
        boolean separateChapters = false;
        String source = "23-ISA.usfm";
        importTestTranslation(source);

        //when
        ExportProjects.Result result = exportProjects.exportUSFM(
                targetTranslation,
                Uri.fromFile(outputFile)
        );

        assertTrue(result.getSuccess());

        Uri usfmOutput = result.getUri();

        //then
        verifyExportedUsfmFile(zipFileName, separateChapters, source, usfmOutput);
    }

    @Test
    public void test09ValidExportJeremiahSingle() throws Exception {
        //given
        String zipFileName = null;
        boolean separateChapters = false;
        String source = "24-JER.usfm";
        importTestTranslation(source);

        //when
        ExportProjects.Result result = exportProjects.exportUSFM(
                targetTranslation,
                Uri.fromFile(outputFile)
        );

        assertTrue(result.getSuccess());

        Uri usfmOutput = result.getUri();

        //then
        verifyExportedUsfmFile(zipFileName, separateChapters, source, usfmOutput);
    }

    @Test
    public void test10ValidExportLukeSingle() throws Exception {
        //given
        String zipFileName = null;
        boolean separateChapters = false;
        String source = "43-LUK.usfm";
        importTestTranslation(source);

        //when
        ExportProjects.Result result = exportProjects.exportUSFM(
                targetTranslation,
                Uri.fromFile(outputFile)
        );

        assertTrue(result.getSuccess());

        Uri usfmOutput = result.getUri();

        //then
        verifyExportedUsfmFile(zipFileName, separateChapters, source, usfmOutput);
    }

    @Test
    public void test11ValidExportJohnSingle() throws Exception {
        //given
        String zipFileName = null;
        boolean separateChapters = false;
        String source = "44-JHN.usfm";
        importTestTranslation(source);

        //when
        ExportProjects.Result result = exportProjects.exportUSFM(
                targetTranslation,
                Uri.fromFile(outputFile)
        );

        assertTrue(result.getSuccess());

        Uri usfmOutput = result.getUri();

        //then
        verifyExportedUsfmFile(zipFileName, separateChapters, source, usfmOutput);
    }

    /**
     * match all the book identifiers
     *
     * @param input
     * @param output
     */
    private void verifyBookID(String input, String output) {
        String bookTitle = extractString(input, ImportUSFM.PATTERN_BOOK_TITLE_MARKER);
        String bookLongName = extractString(input, ImportUSFM.PATTERN_BOOK_LONG_NAME_MARKER);
        String bookShortName = extractString(input, ImportUSFM.PATTERN_BOOK_ABBREVIATION_MARKER);
        String bookTitleOut = extractString(output, ImportUSFM.PATTERN_BOOK_TITLE_MARKER);
        String bookLongNameOut = extractString(output, ImportUSFM.PATTERN_BOOK_LONG_NAME_MARKER);
        String bookShortNameOut = extractString(output,
                ImportUSFM.PATTERN_BOOK_ABBREVIATION_MARKER);

        String bookID = extractString(input, ImportUSFM.ID_TAG_MARKER);
        String[] bookIdParts = bookID.split(" ");
        String bookIDOut = extractString(output, ImportUSFM.ID_TAG_MARKER);
        String[] bookIdOutParts = bookIDOut.split(" ");

        assertEquals("Input and output book titles (\\toc1) should equal",
                bookTitle.toLowerCase(), bookTitleOut.toLowerCase());
        assertEquals("Input and output book codes (\\toc3) should equal",
                bookShortName.toLowerCase(), bookShortNameOut.toLowerCase());
        assertEquals("Input and output book long name (\\toc2) should equal",
                bookLongName.toLowerCase(), bookLongNameOut.toLowerCase());
        assertEquals("Input and output book ID code (\\id) should equal",
                bookIdParts[0].toLowerCase(), bookIdOutParts[0].toLowerCase());
    }

    /**
     * match regexPattern and get string in group 1 if present
     *
     * @param text
     * @param regexPattern
     * @return
     */
    private String extractString(CharSequence text, Pattern regexPattern) {
        if (text.length() > 0) {
            // find instance
            Matcher matcher = regexPattern.matcher(text);
            String foundItem = null;
            if (matcher.find()) {
                foundItem = matcher.group(1);
                return foundItem.trim();
            }
        }

        return null;
    }

    /**
     * handles validation of exported USFM file by comparing to original imported USFM file
     *
     * @param zipFileName      - to determine if zip file was expected
     * @param separateChapters
     * @param source
     * @param usfmOutput       - actual output file
     * @throws IOException
     */
    private void verifyExportedUsfmFile(
            String zipFileName,
            boolean separateChapters,
            String source,
            Uri usfmOutput
    ) throws IOException {
        assertNotNull("exported file", usfmOutput);
        errorLog = "";

        String usfmOutputPath = usfmOutput.getPath();
        File usfmOutputFile = new File(usfmOutputPath);

        if (zipFileName == null) {
            if (!separateChapters) {
                verifySingleUsfmFile(source, usfmOutputFile);
            } else {
                fail("separate chapters without zip is not supported");
            }
        } else {
            if (separateChapters) {
                verifyUsfmZipFile(source, usfmOutputFile);
            } else {
                fail("single book with zip is not supported");
            }
        }

        if (!errorLog.isEmpty()) {
            Log.d(TAG, "Errors found:\n" + errorLog);
            fail("Errors found:\n" + errorLog);
        }
    }

    /**
     * handles validation of exported USFM zip file containing chapters by comparing to original
     * imported USFM file
     *
     * @param source
     * @param usfmOutput
     * @throws IOException
     */
    private void verifyUsfmZipFile(String source, File usfmOutput) throws IOException {

        File unzipFolder = new File(tempFolder, "scratch_test_unzip");
        FileUtilities.forceMkdir(unzipFolder);

        InputStream zipStream = new FileInputStream(usfmOutput);
        Zip.unzipFromStream(zipStream, unzipFolder);
        File[] usfmFiles = unzipFolder.listFiles();

        InputStream usfmStream = assetsProvider.open("usfm/" + source);
        String usfmInputText = FileUtilities.readStreamToString(usfmStream);

        Matcher inputMatcher = ImportUSFM.PATTERN_CHAPTER_NUMBER_MARKER.matcher(usfmInputText);

        int lastInputChapterStart = -1;
        String chapterIn = "";
        int chapterInInt = -1;
        while (inputMatcher.find()) {
            chapterIn = inputMatcher.group(1); // chapter number in input
            chapterInInt = Integer.valueOf(chapterIn);

            if (usfmFiles.length < chapterInInt) {
                addErrorMsg("chapter count " + usfmFiles.length + "' should be greater than or " +
                        "equal to chapter number '" + chapterInInt + "'\n");
            }

            if (chapterInInt > 1) {
                // verify verses in last chapter
                String inputChapter = usfmInputText.substring(lastInputChapterStart,
                        inputMatcher.start());
                String outputChapter = FileUtilities.readFileToString(usfmFiles[chapterInInt - 1]);
                verifyBookID(usfmInputText, outputChapter);
                compareVersesInChapter(chapterInInt - 1, inputChapter, outputChapter);
            }

            lastInputChapterStart = inputMatcher.end();
        }

        if (usfmFiles.length != chapterInInt + 1) {
            addErrorMsg("chapter count " + usfmFiles.length + "' should be  '" + (chapterInInt + 1) + "'\n");
        }

        // verify verses in last chapter
        String inputChapter = usfmInputText.substring(lastInputChapterStart);
        String outputChapter = FileUtilities.readFileToString(usfmFiles[chapterInInt]);
        verifyBookID(usfmInputText, outputChapter);
        compareVersesInChapter(chapterInInt, inputChapter, outputChapter);
    }

    /**
     * queue up error messages
     *
     * @param error
     */
    private void addErrorMsg(String error) {
        errorLog = error + errorLog;
    }

    /**
     * handles validation of exported USFM file by comparing to original imported USFM file
     *
     * @param source
     * @param usfmOutput
     * @throws IOException
     */
    private void verifySingleUsfmFile(String source, File usfmOutput) throws IOException {
        String usfmOutputText = FileUtilities.readFileToString(usfmOutput);

        InputStream usfmStream = assetsProvider.open("usfm/" + source);
        String usfmInputText = FileUtilities.readStreamToString(usfmStream);

        verifyBookID(usfmInputText, usfmOutputText);

        Matcher inputMatcher = ImportUSFM.PATTERN_CHAPTER_NUMBER_MARKER.matcher(usfmInputText);
        Matcher outputMatcher = ImportUSFM.PATTERN_CHAPTER_NUMBER_MARKER.matcher(usfmOutputText);

        int lastInputChapterStart = -1;
        int lastOutputChapterStart = -1;
        String chapterIn;
        int chapterInInt = -1;
        while (inputMatcher.find()) {
            chapterIn = inputMatcher.group(1); // chapter number in input
            chapterInInt = Integer.valueOf(chapterIn);

            if (outputMatcher.find()) {
                String chapterOut = outputMatcher.group(1); // chapter number in output
                int chapterOutInt = Integer.parseInt(chapterOut);
                if (chapterInInt != chapterOutInt) {
                    addErrorMsg("chapter input: " + chapterInInt + "\n does not match chapter " +
                            "output:" + chapterOutInt + "\n");
                }
            } else {
                addErrorMsg("chapter '" + chapterIn + "' missing in output\n");
                break;
            }

            if (chapterInInt > 1) {
                // verify verses in last chapter
                String inputChapter = usfmInputText.substring(lastInputChapterStart,
                        inputMatcher.start());
                String outputChapter = usfmOutputText.substring(lastOutputChapterStart,
                        outputMatcher.start());
                compareVersesInChapter(chapterInInt - 1, inputChapter, outputChapter);
            }

            lastInputChapterStart = inputMatcher.end();
            lastOutputChapterStart = outputMatcher.end();
        }

        if (outputMatcher.find()) {
            addErrorMsg("extra chapter in output: " + outputMatcher.group(1) + "\n");
        }

        // verify verses in last chapter
        String inputChapter = usfmInputText.substring(lastInputChapterStart);
        String outputChapter = usfmOutputText.substring(lastOutputChapterStart);
        compareVersesInChapter(chapterInInt, inputChapter, outputChapter);
    }

    /**
     * compares the verses in exported chapter to make sure they are in same order and have same
     * contents as imported chapter
     *
     * @param chapter
     * @param inputChapter
     * @param outputChapter
     */
    private void compareVersesInChapter(int chapter, String inputChapter, String outputChapter) {
        Matcher inputVerseMatcher = ImportUSFM.PATTERN_USFM_VERSE_SPAN.matcher(inputChapter);
        Matcher outputVerseMatcher = ImportUSFM.PATTERN_USFM_VERSE_SPAN.matcher(outputChapter);
        int lastInputVerseStart = -1;
        int lastOutputVerseStart = -1;
        String verseIn = "";
        while (inputVerseMatcher.find()) {
            verseIn = inputVerseMatcher.group(1); // verse number in input
            if (outputVerseMatcher.find()) {
                String verseOut = outputVerseMatcher.group(1); // verse number in output
                if (!verseIn.equals(verseOut)) {
                    addErrorMsg("in chapter '" + chapter + "' verse input '" + verseIn + "'\n " +
                            "does not match verse output '" + verseOut + "'\n");
                    return;
                }
            } else {
                addErrorMsg("in chapter '" + chapter + "', verse '" + verseIn + "' missing in " +
                        "output\n");
                return;
            }

            if (lastInputVerseStart > 0) {
                String inputVerse = inputChapter.substring(lastInputVerseStart,
                        inputVerseMatcher.start());
                String outputVerse = outputChapter.substring(lastOutputVerseStart,
                        outputVerseMatcher.start());
                compareVerses(chapter, verseIn, inputVerse, outputVerse);
            }

            lastInputVerseStart = inputVerseMatcher.end();
            lastOutputVerseStart = outputVerseMatcher.end();
        }

        if (outputVerseMatcher.find()) {
            addErrorMsg("In chapter '" + chapter + "' extra verse in output: '" + outputVerseMatcher.group(1) + "\n");
        }

        String inputVerse = inputChapter.substring(lastInputVerseStart);
        String outputVerse = outputChapter.substring(lastOutputVerseStart);
        compareVerses(chapter, verseIn, inputVerse, outputVerse);
    }

    /**
     * compares contents of verses
     *
     * @param chapterNum
     * @param verseIn
     * @param inputVerse
     * @param outputVerse
     */
    private void compareVerses(int chapterNum, String verseIn, String inputVerse,
                               String outputVerse) {

        String input = inputVerse;
        String output = outputVerse;

        if (input.equals(output)) {
            return;
        }

        //if not exact match, try stripping section marker and removing double new-lines

        //remove extra white space
        input = cleanUpVerse(input);
        output = cleanUpVerse(output);

        if (input.equals(output)) {
            return;
        }

        if (!input.equals(output)) {
            if (!output.equals(input + "\n")) {
                return;
            }
            if (!input.equals(output + "\n")) {
                return;
            }
            addErrorMsg("In chapter '" + chapterNum + "' verse '" + verseIn + "' verse input:\n" + input + "\n does not match output:\n" + output + "\n");
        }
    }

    public static final String CHAPTER_LABEL_MARKER = "\\\\cl\\s([^\\n]*)";
    public static final Pattern PATTERN_CHAPTER_LABEL_MARKER =
            Pattern.compile(CHAPTER_LABEL_MARKER);

    /**
     * clean up by stripping section marker and removing double new-lines
     *
     * @param text
     * @return
     */
    private String cleanUpVerse(String text) {

        Matcher chapterLabelMatcher = PATTERN_CHAPTER_LABEL_MARKER.matcher(text);
        if (chapterLabelMatcher.find()) {
            text = text.substring(0, chapterLabelMatcher.start());
        }

        text = text.replace("\\s5\n", "\n"); // remove section markers
        text = text.replace("\\s5 \n", "\n"); // remove section markers
        text = replaceAll(text, "\n\n", "\n"); // remove double new-lines
        text = replaceAll(text, "\n\n", "\n"); // remove double new-lines
        text = replaceAll(text, "\n \n", "\n"); // remove double new-lines
        return text;
    }

    /**
     * repeatedly replaces strings - useful
     *
     * @param text
     * @param target
     * @param replacement
     * @return
     */
    private String replaceAll(String text, CharSequence target, CharSequence replacement) {
        String oldText = null;
        String newText = text;

        while (!Objects.equals(newText, oldText)) {
            oldText = newText;
            newText = newText.replace(target, replacement);
        }

        return newText;
    }

    /**
     * import a usfm file to be used for export testing.
     *
     * @param source
     */
    private void importTestTranslation(String source) {
        //import USFM file to be used for testing
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
        assertNotNull("mTargetLanguage", targetLanguage);
        assertTrue("import usfm test file should succeed", usfm.isProcessSuccess());
        List<File> imports = usfm.getImportProjects();
        assertEquals("import usfm test file should succeed", 1, imports.size());

        //open import as targetTranslation
        File projectFolder = imports.get(0);
        tempFolder = projectFolder.getParentFile();
        outputFile = new File(tempFolder, "scratch_test");
        targetTranslation = TargetTranslation.open(projectFolder, null);
    }

    /**
     * gets the resource TOCs even if user has not selected one yet
     *
     * @param targetTranslation
     * @param library
     * @return
     */
    public List<Map> getResourceTOC(TargetTranslation targetTranslation, Door43Client library) {
        String sourceTranslationSlug = prefRepository.getSelectedSourceTranslationId(
                targetTranslation.getId()
        );
        // if none selected, try list of selected translations
        if (sourceTranslationSlug == null) {
            String[] sourceTranslationSlugs = prefRepository.getOpenSourceTranslations(
                    targetTranslation.getId()
            );
            if (sourceTranslationSlugs.length > 0) {
                sourceTranslationSlug = sourceTranslationSlugs[0];
            }
        }

        // last try look for any available that are loaded into memory
        // if none selected, try list of selected translations
        if (sourceTranslationSlug == null) {
            String projectId = targetTranslation.getProjectId();
            sourceTranslationSlug = getAvailableTargetTranslations(library, projectId);
        }

        return getResourceToc(library, sourceTranslationSlug);
    }

    private static List<Map> getResourceToc(Door43Client library, String sourceTranslationSlug) {
        Translation sourceTranslation = library.index.getTranslation(sourceTranslationSlug);
        ResourceContainer mSourceContainer = ContainerCache.cache(library, sourceTranslation.resourceContainerSlug);
        return (List<Map>) mSourceContainer.toc;
    }

    /**
     * find an available translation for project ID
     *
     * @param library
     * @param projectId
     * @return
     */
    private static String getAvailableTargetTranslations(Door43Client library, String projectId) {
        String sourceTranslationSlug = null;
        List<Translation> availableTranslations = library.index.findTranslations(null, projectId,
                null, "book", "all", App.MIN_CHECKING_LEVEL, -1);
        if ((availableTranslations != null) && (availableTranslations.size() > 0)) {
            for (Translation availableTranslation : availableTranslations) {
                final boolean isDownloaded =
                        library.exists(availableTranslation.resourceContainerSlug);
                if (isDownloaded) {
                    sourceTranslationSlug = availableTranslation.resourceContainerSlug;
                    break;
                }
            }
        }
        return sourceTranslationSlug;
    }

    /**
     * right size the file name length.  App expects file names under 100 to be only two digits.
     *
     * @param fileName
     * @return
     */
    public static String getRightFileNameLength(String fileName) {
        Integer numericalValue = strToInt(fileName, -1);
        if ((numericalValue >= 0) && (numericalValue < 100) && (fileName.length() != 2)) {
            fileName = "00" + fileName; // make sure has leading zeroes
            fileName = fileName.substring(fileName.length() - 2); // trim down extra leading zeros
        }
        return fileName;
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
            int retValue = Integer.parseInt(value);
            return retValue;
        } catch (Exception e) {
//            Log.d(TAG, "Cannot convert to int: " + value);
        }
        return defaultValue;
    }

}