package com.door43.translationstudio.core

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.App
import com.door43.translationstudio.IntegrationTest
import com.door43.usecases.ExportProjects
import com.door43.util.FileUtilities
import com.door43.util.Zip
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.test.KoinTest
import org.koin.test.inject
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.TargetLanguage
import org.unfoldingword.door43client.models.Translation
import org.unfoldingword.resourcecontainer.ResourceContainer
import org.unfoldingword.tools.logger.Logger
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Created by blm on 7/25/16.
 */
@RunWith(AndroidJUnit4::class)
@IntegrationTest
class ExportUsfmTest : KoinTest {

    private val appContext: Context by inject()
    private val library: Door43Client by inject()
    private val directoryProvider: IDirectoryProvider by inject()
    private val profile: Profile by inject()
    private val exportProjects: ExportProjects by inject()
    private val assetsProvider: AssetsProvider by inject()
    private val prefRepository: IPreferenceRepository by inject()

    private var tempFolder: File? = null
    private var targetLanguage: TargetLanguage? = null
    private var usfm: ProcessUSFM? = null
    private var outputFile: File? = null
    private var targetTranslation: TargetTranslation? = null
    private var errorLog: String? = null

    @Before
    fun setUp() {
        errorLog = null
        Logger.flush()

        targetLanguage = library.index.getTargetLanguage("aae")
    }

    @After
    fun tearDown() {
        usfm?.cleanup()
        FileUtilities.deleteQuietly(tempFolder)
    }

    @Test
    @Throws(Exception::class)
    fun test01ValidExportMarkSingle() {
        //given
        val zipFileName: String? = null
        val separateChapters = false
        val source = "mrk.usfm"
        importTestTranslation(source)

        //when
        targetTranslation?.let { translation ->
            val result = exportProjects.exportUSFM(
                translation,
                Uri.fromFile(outputFile)
            )

            Assert.assertTrue(result.success)

            val usfmOutput = result.uri

            //then
            verifyExportedUsfmFile(zipFileName, separateChapters, source, usfmOutput)
        }
    }

    @Test
    @Throws(Exception::class)
    fun test02ValidExportPsalmSingle() {
        //given
        val zipFileName: String? = null
        val separateChapters = false
        val source = "19-PSA.usfm"
        importTestTranslation(source)

        //when
        targetTranslation?.let { translation ->
            val result = exportProjects.exportUSFM(
                translation,
                Uri.fromFile(outputFile)
            )

            Assert.assertTrue(result.success)

            val usfmOutput = result.uri

            //then
            verifyExportedUsfmFile(zipFileName, separateChapters, source, usfmOutput)
        }
    }

    @Test
    @Throws(Exception::class)
    fun test03ValidExportJudeSingle() {
        //given
        val zipFileName: String? = null
        val separateChapters = false
        val source = "66-JUD.usfm"
        importTestTranslation(source)

        //when
        targetTranslation?.let { translation ->
            val result = exportProjects.exportUSFM(
                translation,
                Uri.fromFile(outputFile)
            )

            Assert.assertTrue(result.success)

            val usfmOutput = result.uri

            //then
            verifyExportedUsfmFile(zipFileName, separateChapters, source, usfmOutput)
        }
    }

    @Test
    @Throws(Exception::class)
    fun test04ValidExportJobSingle() {
        //given
        val zipFileName: String? = null
        val separateChapters = false
        val source = "18-JOB.usfm"
        importTestTranslation(source)

        //when
        targetTranslation?.let { translation ->
            val result = exportProjects.exportUSFM(
                translation,
                Uri.fromFile(outputFile)
            )

            Assert.assertTrue(result.success)

            val usfmOutput = result.uri

            //then
            verifyExportedUsfmFile(zipFileName, separateChapters, source, usfmOutput)
        }
    }

    @Test
    @Throws(Exception::class)
    fun test08ValidExportIsaiahSingle() {
        //given
        val zipFileName: String? = null
        val separateChapters = false
        val source = "23-ISA.usfm"
        importTestTranslation(source)

        //when
        targetTranslation?.let { translation ->
            val result = exportProjects.exportUSFM(
                translation,
                Uri.fromFile(outputFile)
            )

            Assert.assertTrue(result.success)

            val usfmOutput = result.uri

            //then
            verifyExportedUsfmFile(zipFileName, separateChapters, source, usfmOutput)
        }
    }

    @Test
    @Throws(Exception::class)
    fun test09ValidExportJeremiahSingle() {
        //given
        val zipFileName: String? = null
        val separateChapters = false
        val source = "24-JER.usfm"
        importTestTranslation(source)

        //when
        targetTranslation?.let { translation ->
            val result = exportProjects.exportUSFM(
                translation,
                Uri.fromFile(outputFile)
            )

            Assert.assertTrue(result.success)

            val usfmOutput = result.uri

            //then
            verifyExportedUsfmFile(zipFileName, separateChapters, source, usfmOutput)
        }
    }

    @Test
    @Throws(Exception::class)
    fun test10ValidExportLukeSingle() {
        //given
        val zipFileName: String? = null
        val separateChapters = false
        val source = "43-LUK.usfm"
        importTestTranslation(source)

        //when
        targetTranslation?.let { translation ->
            val result = exportProjects.exportUSFM(
                translation,
                Uri.fromFile(outputFile)
            )

            Assert.assertTrue(result.success)

            val usfmOutput = result.uri

            //then
            verifyExportedUsfmFile(zipFileName, separateChapters, source, usfmOutput)
        }
    }

    @Test
    @Throws(Exception::class)
    fun test11ValidExportJohnSingle() {
        //given
        val zipFileName: String? = null
        val separateChapters = false
        val source = "44-JHN.usfm"
        importTestTranslation(source)

        //when
        targetTranslation?.let { translation ->
            val result = exportProjects.exportUSFM(
                translation,
                Uri.fromFile(outputFile)
            )

            Assert.assertTrue(result.success)

            val usfmOutput = result.uri

            //then
            verifyExportedUsfmFile(zipFileName, separateChapters, source, usfmOutput)
        }
    }

    /**
     * match all the book identifiers
     * 
     * @param input
     * @param output
     */
    private fun verifyBookID(input: String, output: String) {
        val bookTitle = extractString(input, ProcessUSFM.PATTERN_BOOK_TITLE_MARKER)
        val bookLongName = extractString(input, ProcessUSFM.PATTERN_BOOK_LONG_NAME_MARKER)
        val bookShortName = extractString(input, ProcessUSFM.PATTERN_BOOK_ABBREVIATION_MARKER)
        val bookTitleOut = extractString(output, ProcessUSFM.PATTERN_BOOK_TITLE_MARKER)
        val bookLongNameOut = extractString(output, ProcessUSFM.PATTERN_BOOK_LONG_NAME_MARKER)
        val bookShortNameOut = extractString(
            output,
            ProcessUSFM.PATTERN_BOOK_ABBREVIATION_MARKER
        )

        val bookID = extractString(input, ProcessUSFM.ID_TAG_MARKER)
        val bookIdParts: Array<String?> =
            bookID!!.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val bookIDOut = extractString(output, ProcessUSFM.ID_TAG_MARKER)
        val bookIdOutParts: Array<String?> =
            bookIDOut!!.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        Assert.assertEquals(
            "Input and output book titles (\\toc1) should equal",
            bookTitle!!.lowercase(Locale.getDefault()),
            bookTitleOut!!.lowercase(Locale.getDefault())
        )
        Assert.assertEquals(
            "Input and output book codes (\\toc3) should equal",
            bookShortName!!.lowercase(Locale.getDefault()),
            bookShortNameOut!!.lowercase(Locale.getDefault())
        )
        Assert.assertEquals(
            "Input and output book long name (\\toc2) should equal",
            bookLongName!!.lowercase(Locale.getDefault()),
            bookLongNameOut!!.lowercase(Locale.getDefault())
        )
        Assert.assertEquals(
            "Input and output book ID code (\\id) should equal",
            bookIdParts[0]!!.lowercase(Locale.getDefault()),
            bookIdOutParts[0]!!.lowercase(Locale.getDefault())
        )
    }

    /**
     * match regexPattern and get string in group 1 if present
     * 
     * @param text
     * @param regexPattern
     * @return
     */
    private fun extractString(text: CharSequence, regexPattern: Pattern): String? {
        if (text.isNotEmpty()) {
            // find instance
            val matcher = regexPattern.matcher(text)
            var foundItem: String? = null
            if (matcher.find()) {
                foundItem = matcher.group(1)
                return foundItem.trim { it <= ' ' }
            }
        }

        return null
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
    @Throws(IOException::class)
    private fun verifyExportedUsfmFile(
        zipFileName: String?,
        separateChapters: Boolean,
        source: String?,
        usfmOutput: Uri?
    ) {
        Assert.assertNotNull("exported file", usfmOutput)
        errorLog = ""

        val usfmOutputPath = usfmOutput!!.path
        val usfmOutputFile = File(usfmOutputPath)

        if (zipFileName == null) {
            if (!separateChapters) {
                verifySingleUsfmFile(source, usfmOutputFile)
            } else {
                Assert.fail("separate chapters without zip is not supported")
            }
        } else {
            if (separateChapters) {
                verifyUsfmZipFile(source, usfmOutputFile)
            } else {
                Assert.fail("single book with zip is not supported")
            }
        }

        if (!errorLog!!.isEmpty()) {
            Log.d(TAG, "Errors found:\n$errorLog")
            Assert.fail("Errors found:\n$errorLog")
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
    @Throws(IOException::class)
    private fun verifyUsfmZipFile(source: String?, usfmOutput: File?) {
        val unzipFolder = File(tempFolder, "scratch_test_unzip")
        FileUtilities.forceMkdir(unzipFolder)

        val zipStream: InputStream = FileInputStream(usfmOutput)
        Zip.unzipFromStream(zipStream, unzipFolder)
        val usfmFiles = unzipFolder.listFiles()

        val usfmStream = assetsProvider.open("usfm/$source")
        val usfmInputText: String = FileUtilities.readStreamToString(usfmStream)

        val inputMatcher = ProcessUSFM.PATTERN_CHAPTER_NUMBER_MARKER.matcher(usfmInputText)

        var lastInputChapterStart = -1
        var chapterIn: String? = ""
        var chapterInInt = -1
        while (inputMatcher.find()) {
            chapterIn = inputMatcher.group(1) // chapter number in input
            chapterInInt = chapterIn.toInt()

            if (usfmFiles!!.size < chapterInInt) {
                addErrorMsg(
                    "chapter count " + usfmFiles.size + "' should be greater than or " +
                            "equal to chapter number '" + chapterInInt + "'\n"
                )
            }

            if (chapterInInt > 1) {
                // verify verses in last chapter
                val inputChapter = usfmInputText.substring(
                    lastInputChapterStart,
                    inputMatcher.start()
                )
                val outputChapter: String =
                    FileUtilities.readFileToString(usfmFiles[chapterInInt - 1])
                verifyBookID(usfmInputText, outputChapter)
                compareVersesInChapter(chapterInInt - 1, inputChapter, outputChapter)
            }

            lastInputChapterStart = inputMatcher.end()
        }

        if (usfmFiles!!.size != chapterInInt + 1) {
            addErrorMsg("chapter count " + usfmFiles.size + "' should be  '" + (chapterInInt + 1) + "'\n")
        }

        // verify verses in last chapter
        val inputChapter = usfmInputText.substring(lastInputChapterStart)
        val outputChapter: String = FileUtilities.readFileToString(usfmFiles[chapterInInt])
        verifyBookID(usfmInputText, outputChapter)
        compareVersesInChapter(chapterInInt, inputChapter, outputChapter)
    }

    /**
     * queue up error messages
     * 
     * @param error
     */
    private fun addErrorMsg(error: String?) {
        errorLog = error + errorLog
    }

    /**
     * handles validation of exported USFM file by comparing to original imported USFM file
     * 
     * @param source
     * @param usfmOutput
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun verifySingleUsfmFile(source: String?, usfmOutput: File) {
        val usfmOutputText: String = FileUtilities.readFileToString(usfmOutput)

        val usfmStream = assetsProvider.open("usfm/$source")
        val usfmInputText: String = FileUtilities.readStreamToString(usfmStream)

        verifyBookID(usfmInputText, usfmOutputText)

        val inputMatcher = ProcessUSFM.PATTERN_CHAPTER_NUMBER_MARKER.matcher(usfmInputText)
        val outputMatcher = ProcessUSFM.PATTERN_CHAPTER_NUMBER_MARKER.matcher(usfmOutputText)

        var lastInputChapterStart = -1
        var lastOutputChapterStart = -1
        var chapterIn: String?
        var chapterInInt = -1
        while (inputMatcher.find()) {
            chapterIn = inputMatcher.group(1) // chapter number in input
            chapterInInt = chapterIn.toInt()

            if (outputMatcher.find()) {
                val chapterOut = outputMatcher.group(1) // chapter number in output
                val chapterOutInt = chapterOut!!.toInt()
                if (chapterInInt != chapterOutInt) {
                    addErrorMsg(
                        "chapter input: " + chapterInInt + "\n does not match chapter " +
                                "output:" + chapterOutInt + "\n"
                    )
                }
            } else {
                addErrorMsg("chapter '$chapterIn' missing in output\n")
                break
            }

            if (chapterInInt > 1) {
                // verify verses in last chapter
                val inputChapter = usfmInputText.substring(
                    lastInputChapterStart,
                    inputMatcher.start()
                )
                val outputChapter = usfmOutputText.substring(
                    lastOutputChapterStart,
                    outputMatcher.start()
                )
                compareVersesInChapter(chapterInInt - 1, inputChapter, outputChapter)
            }

            lastInputChapterStart = inputMatcher.end()
            lastOutputChapterStart = outputMatcher.end()
        }

        if (outputMatcher.find()) {
            addErrorMsg("extra chapter in output: " + outputMatcher.group(1) + "\n")
        }

        // verify verses in last chapter
        val inputChapter = usfmInputText.substring(lastInputChapterStart)
        val outputChapter = usfmOutputText.substring(lastOutputChapterStart)
        compareVersesInChapter(chapterInInt, inputChapter, outputChapter)
    }

    /**
     * compares the verses in exported chapter to make sure they are in same order and have same
     * contents as imported chapter
     * 
     * @param chapter
     * @param inputChapter
     * @param outputChapter
     */
    private fun compareVersesInChapter(chapter: Int, inputChapter: String, outputChapter: String) {
        val inputVerseMatcher = ProcessUSFM.PATTERN_USFM_VERSE_SPAN.matcher(inputChapter)
        val outputVerseMatcher = ProcessUSFM.PATTERN_USFM_VERSE_SPAN.matcher(outputChapter)
        var lastInputVerseStart = -1
        var lastOutputVerseStart = -1
        var verseIn: String? = ""
        while (inputVerseMatcher.find()) {
            verseIn = inputVerseMatcher.group(1) // verse number in input
            if (outputVerseMatcher.find()) {
                val verseOut = outputVerseMatcher.group(1) // verse number in output
                if (verseIn != verseOut) {
                    addErrorMsg(
                        "in chapter '" + chapter + "' verse input '" + verseIn + "'\n " +
                                "does not match verse output '" + verseOut + "'\n"
                    )
                    return
                }
            } else {
                addErrorMsg(
                    "in chapter '" + chapter + "', verse '" + verseIn + "' missing in " +
                            "output\n"
                )
                return
            }

            if (lastInputVerseStart > 0) {
                val inputVerse = inputChapter.substring(
                    lastInputVerseStart,
                    inputVerseMatcher.start()
                )
                val outputVerse = outputChapter.substring(
                    lastOutputVerseStart,
                    outputVerseMatcher.start()
                )
                compareVerses(chapter, verseIn, inputVerse, outputVerse)
            }

            lastInputVerseStart = inputVerseMatcher.end()
            lastOutputVerseStart = outputVerseMatcher.end()
        }

        if (outputVerseMatcher.find()) {
            addErrorMsg(
                "In chapter '$chapter' extra verse in output: '" + outputVerseMatcher.group(
                    1
                ) + "\n"
            )
        }

        val inputVerse = inputChapter.substring(lastInputVerseStart)
        val outputVerse = outputChapter.substring(lastOutputVerseStart)
        compareVerses(chapter, verseIn, inputVerse, outputVerse)
    }

    /**
     * compares contents of verses
     * 
     * @param chapterNum
     * @param verseIn
     * @param inputVerse
     * @param outputVerse
     */
    private fun compareVerses(
        chapterNum: Int, verseIn: String?, inputVerse: String,
        outputVerse: String
    ) {
        var input = inputVerse
        var output = outputVerse

        if (input == output) {
            return
        }

        //if not exact match, try stripping section marker and removing double new-lines

        //remove extra white space
        input = cleanUpVerse(input)
        output = cleanUpVerse(output)

        if (input != output) {
            if (output != input + "\n") {
                return
            }
            if (input != output + "\n") {
                return
            }
            addErrorMsg("In chapter '$chapterNum' verse '$verseIn' verse input:\n$input\n does not match output:\n$output\n")
        }
    }

    /**
     * clean up by stripping section marker and removing double new-lines
     * 
     * @param text
     * @return
     */
    private fun cleanUpVerse(text: String): String {
        var text = text
        val chapterLabelMatcher: Matcher = PATTERN_CHAPTER_LABEL_MARKER.matcher(text)
        if (chapterLabelMatcher.find()) {
            text = text.substring(0, chapterLabelMatcher.start())
        }

        text = text.replace("\\s5\n", "\n") // remove section markers
        text = text.replace("\\s5 \n", "\n") // remove section markers
        text = replaceAll(text, "\n\n", "\n") // remove double new-lines
        text = replaceAll(text, "\n\n", "\n") // remove double new-lines
        text = replaceAll(text, "\n \n", "\n") // remove double new-lines
        return text
    }

    /**
     * repeatedly replaces strings - useful
     * 
     * @param text
     * @param target
     * @param replacement
     * @return
     */
    private fun replaceAll(
        text: String,
        target: String,
        replacement: String
    ): String {
        var oldText: String? = null
        var newText: String = text

        while (newText != oldText) {
            oldText = newText
            newText = newText.replace(target, replacement)
        }

        return newText
    }

    /**
     * import a usfm file to be used for export testing.
     * 
     * @param source
     */
    private fun importTestTranslation(source: String?) {
        //import USFM file to be used for testing
        targetLanguage?.let { language ->
            usfm = ProcessUSFM.Builder(
                appContext,
                directoryProvider,
                profile,
                library,
                assetsProvider
            )
                .fromRc(language, "usfm/$source", null)
                .build()

            Assert.assertNotNull(usfm)
            Assert.assertNotNull("mTargetLanguage", language)
            Assert.assertTrue("import usfm test file should succeed", usfm!!.isProcessSuccess)
            val imports: List<File> = usfm!!.importProjects
            Assert.assertEquals("import usfm test file should succeed", 1, imports.size.toLong())

            //open import as targetTranslation
            val projectFolder = imports[0]
            tempFolder = projectFolder.parentFile
            outputFile = File(tempFolder, "scratch_test")
            targetTranslation = TargetTranslation.open(projectFolder, null)
        }
    }

    /**
     * gets the resource TOCs even if user has not selected one yet
     * 
     * @param targetTranslation
     * @param library
     * @return
     */
    fun getResourceTOC(
        targetTranslation: TargetTranslation,
        library: Door43Client
    ): MutableList<MutableMap<*, *>?>? {
        var sourceTranslationSlug = prefRepository.getSelectedSourceTranslationId(
            targetTranslation.id
        )
        // if none selected, try list of selected translations
        if (sourceTranslationSlug == null) {
            val sourceTranslationSlugs: List<String> = prefRepository.getOpenSourceTranslations(
                targetTranslation.id
            )
            if (sourceTranslationSlugs.isNotEmpty()) {
                sourceTranslationSlug = sourceTranslationSlugs[0]
            }
        }

        // last try look for any available that are loaded into memory
        // if none selected, try list of selected translations
        if (sourceTranslationSlug == null) {
            val projectId = targetTranslation.projectId
            sourceTranslationSlug = getAvailableTargetTranslations(library, projectId)
        }

        return getResourceToc(library, sourceTranslationSlug!!)
    }

    companion object {
        val TAG: String = ExportUsfmTest::class.java.simpleName
        const val CHAPTER_LABEL_MARKER: String = "\\\\cl\\s([^\\n]*)"
        val PATTERN_CHAPTER_LABEL_MARKER: Pattern = Pattern.compile(CHAPTER_LABEL_MARKER)

        private fun getResourceToc(
            library: Door43Client,
            sourceTranslationSlug: String
        ): MutableList<MutableMap<*, *>?>? {
            val sourceTranslation: Translation? = library.index.getTranslation(sourceTranslationSlug)
            val mSourceContainer: ResourceContainer =
                ContainerCache.cache(library, sourceTranslation!!.resourceContainerSlug)!!
            return mSourceContainer.toc as MutableList<MutableMap<*, *>?>?
        }

        /**
         * find an available translation for project ID
         * 
         * @param library
         * @param projectId
         * @return
         */
        private fun getAvailableTargetTranslations(
            library: Door43Client,
            projectId: String?
        ): String? {
            var sourceTranslationSlug: String? = null
            val availableTranslations: MutableList<Translation> = library.index.findTranslations(
                null, projectId,
                null, "book", "all", App.MIN_CHECKING_LEVEL, -1
            ).toMutableList()
            if (availableTranslations.isNotEmpty()) {
                for (availableTranslation in availableTranslations) {
                    val isDownloaded: Boolean =
                        library.exists(availableTranslation.resourceContainerSlug)
                    if (isDownloaded) {
                        sourceTranslationSlug = availableTranslation.resourceContainerSlug
                        break
                    }
                }
            }
            return sourceTranslationSlug
        }

        /**
         * right size the file name length.  App expects file names under 100 to be only two digits.
         * 
         * @param fileName
         * @return
         */
        fun getRightFileNameLength(fileName: String): String {
            var fileName = fileName
            val numericalValue: Int = strToInt(fileName, -1)
            if ((numericalValue >= 0) && (numericalValue < 100) && (fileName.length != 2)) {
                fileName = "00$fileName" // make sure has leading zeroes
                fileName = fileName.substring(fileName.length - 2) // trim down extra leading zeros
            }
            return fileName
        }

        /**
         * do string to integer with default value on conversion error
         * 
         * @param value
         * @param defaultValue
         * @return
         */
        fun strToInt(value: String, defaultValue: Int): Int {
            try {
                val retValue = value.toInt()
                return retValue
            } catch (e: Exception) {
//            Log.d(TAG, "Cannot convert to int: " + value);
            }
            return defaultValue
        }
    }
}