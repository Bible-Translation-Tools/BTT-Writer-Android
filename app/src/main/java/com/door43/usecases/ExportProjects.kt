package com.door43.usecases

import android.content.Context
import android.net.Uri
import android.util.Log
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.R
import com.door43.translationstudio.core.FrameTranslation
import com.door43.translationstudio.core.PdfPrinter
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.core.TranslationType
import com.door43.translationstudio.core.Translator.Companion.TSTUDIO_EXTENSION
import com.door43.translationstudio.core.Translator.Companion.ZIP_EXTENSION
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.core.Util
import com.door43.util.FileUtilities
import com.door43.util.RepoUtils
import com.door43.util.Zip
import dagger.hilt.android.qualifiers.ApplicationContext
import org.eclipse.jgit.errors.TransportException
import org.json.JSONArray
import org.json.JSONObject
import org.unfoldingword.door43client.Door43Client
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.PrintStream
import java.util.Locale
import javax.inject.Inject

class ExportProjects @Inject constructor(
    @ApplicationContext private val context: Context,
    private val directoryProvider: IDirectoryProvider,
    private val library: Door43Client,
    private val typography: Typography
) {

    /**
     * Exports a single target translation in .tstudio format to File
     * @param targetTranslation
     * @param outputFile
     */
    @Throws(Exception::class)
    fun exportProject(targetTranslation: TargetTranslation, outputFile: File) {
        exportProject(targetTranslation, Uri.fromFile(outputFile))
    }

    /**
     * Exports a single target translation in .tstudio format to OutputStream
     * @param targetTranslation
     * @param fileUri
     */
    fun exportProject(
        targetTranslation: TargetTranslation,
        fileUri: Uri,
        recoverBadRepo: Boolean = true
    ): Result {
        var success = false
        val tempDir = directoryProvider.createTempDir()
        try {
            targetTranslation.commitSync(".", false)

            val manifestJson = buildArchiveManifest(targetTranslation)
            val manifestFile = File(tempDir, "manifest.json")
            manifestFile.createNewFile()
            directoryProvider.writeStringToFile(manifestFile, manifestJson.toString())

            context.contentResolver.openOutputStream(fileUri).use { out ->
                Zip.zipToStream(
                    arrayOf(manifestFile, targetTranslation.path), out
                )
            }
            success = true
        } catch (e: TransportException) {
            if (recoverBadRepo) {
                // fix corrupt repo and try again
                RepoUtils.recover(targetTranslation)
                return exportProject(targetTranslation, fileUri, false)
            }
            success = true
        } catch (e: Exception) {
            Log.e(this::class.simpleName, "Failed to export project", e)
        } finally {
            FileUtilities.deleteQuietly(tempDir)
        }

        return Result(fileUri, success, ExportType.PROJECT)
    }

    @Throws(Exception::class)
    fun exportProject(projectDir: File, outputFile: File) {
        if (!isValidArchiveExtension(outputFile.path)) {
            throw Exception("Output file must have '$TSTUDIO_EXTENSION' or '$ZIP_EXTENSION' extension")
        }
        if (!projectDir.exists()) {
            throw Exception("Project directory doesn't exist.")
        }

        FileOutputStream(outputFile).use { outputStream ->
            Zip.zipToStream(arrayOf(projectDir), outputStream)
        }
    }

    /**
     * Exports a target translation as a USFM file
     * @param targetTranslation
     * @param fileUri
     */
    fun exportUSFM(targetTranslation: TargetTranslation, fileUri: Uri): Result {
        val tempDir = directoryProvider.createTempDir()

        val success = try {
            tempDir.mkdirs()
            val chapters = targetTranslation.chapterTranslations

            val bookData = BookData.generate(targetTranslation, library)
            val bookCode = bookData.bookCode
            val bookTitle = bookData.bookTitle
            val bookName = bookData.bookName
            val languageId = bookData.languageId
            val languageName = bookData.languageName

            val tempFile = directoryProvider.createTempFile("output", dir = tempDir)

            PrintStream(tempFile).use { ps ->
                val id = "\\id $bookCode $bookTitle, $bookName, $languageId, $languageName"
                ps.println(id)
                val ide = "\\ide usfm"
                ps.println(ide)
                val h = "\\h $bookTitle"
                ps.println(h)
                val bookID = "\\toc1 $bookTitle"
                ps.println(bookID)
                val bookNameID = "\\toc2 $bookName"
                ps.println(bookNameID)
                val shortBookID = "\\toc3 $bookCode"
                ps.println(shortBookID)
                val mt = "\\mt $bookTitle"
                ps.println(mt)

                for (chapter in chapters) {
                    // TRICKY: the translation format doesn't matter for exporting
                    val frames = targetTranslation.getFrameTranslations(
                        chapter.id,
                        TranslationFormat.DEFAULT
                    )
                    if (frames.isEmpty()) continue

                    val chapterInt = Util.strToInt(chapter.id, 0)
                    if (chapterInt != 0) {
                        ps.println("\\s5") // section marker
                        val chapterNumber = "\\c " + chapter.id
                        ps.println(chapterNumber)
                    }

                    if (chapter.title != null && chapter.title.isNotEmpty()) {
                        val chapterTitle = "\\cl " + chapter.title
                        ps.println(chapterTitle)
                    }

                    if (chapter.reference != null && chapter.reference.isNotEmpty()) {
                        val chapterRef = "\\cd " + chapter.reference
                        ps.println(chapterRef)
                    }

                    ps.println("\\p") // paragraph marker

                    val frameList = sortFrameTranslations(frames)
                    var startChunk = 0
                    if (frameList.isNotEmpty()) {
                        val frame = frameList[0]
                        val verseID =
                            Util.strToInt(frame.id, 0)
                        if ((verseID == 0)) {
                            startChunk++
                        }
                    }

                    for (i in startChunk until frameList.size) {
                        val frame = frameList[i]
                        val text = frame.body

                        if (i > startChunk) {
                            ps.println("\\s5") // section marker
                        }
                        ps.print(text)
                    }
                }
                context.contentResolver.openOutputStream(fileUri).use { output ->
                    FileInputStream(tempFile).use { input ->
                        val buffer = ByteArray(1024)
                        var length: Int
                        while ((input.read(buffer).also { length = it }) > 0) {
                            output!!.write(buffer, 0, length)
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(this::class.simpleName, "Failed to export USFM file", e)
            false
        } finally {
            FileUtilities.deleteQuietly(tempDir)
        }

        return Result(fileUri, success, ExportType.USFM)
    }

    /**
     * Exports a target translation as a PDF file
     * @param targetTranslation
     * @param fileUri
     * @return output file
     */
    fun exportPDF(
        targetTranslation: TargetTranslation,
        fileUri: Uri,
        includeImages: Boolean,
        includeIncompleteFrames: Boolean,
        imagesDir: File?
    ): Result {
        val success = try {
            val fontPath = typography.getAssetPath(TranslationType.TARGET)
            val fontSize = typography.getFontSize(TranslationType.TARGET)
            val licenseFontName = context.getString(R.string.pref_default_translation_typeface)
            val licenseFontPath = "assets/fonts/$licenseFontName"
            val targetLanguageRtl = "rtl" == targetTranslation.targetLanguageDirection
            val printer = PdfPrinter(
                context, targetTranslation, targetTranslation.format, fontPath,
                fontSize, targetLanguageRtl, licenseFontPath, imagesDir, directoryProvider,
                library
            )
            printer.includeMedia(includeImages)
            printer.includeIncomplete(includeIncompleteFrames)
            val pdf = printer.print()
            if (pdf.exists()) {
                context.contentResolver.openOutputStream(fileUri).use { output ->
                    pdf.inputStream().use { input ->
                        val buffer = ByteArray(1024)
                        var length: Int
                        while ((input.read(buffer).also { length = it }) > 0) {
                            output!!.write(buffer, 0, length)
                        }
                    }
                }
                FileUtilities.deleteQuietly(pdf)
            }
            true
        } catch (e: Exception) {
            Log.e(this::class.simpleName, "Failed to export PDF file", e)
            false
        }

        return Result(fileUri, success, ExportType.PDF)
    }

    /**
     * creates a JSON object that contains the manifest.
     * @param targetTranslation
     * @return
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun buildArchiveManifest(targetTranslation: TargetTranslation): JSONObject {
        targetTranslation.commit()

        // build manifest
        val manifestJson = JSONObject()
        val generatorJson = JSONObject()
        generatorJson.put("name", GENERATOR_NAME)
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        generatorJson.put("build", pInfo.versionCode)
        manifestJson.put("generator", generatorJson)
        manifestJson.put("package_version", TSTUDIO_PACKAGE_VERSION)
        manifestJson.put("timestamp", Util.unixTime())
        val translationsJson = JSONArray()
        val translationJson = JSONObject()
        translationJson.put("path", targetTranslation.id)
        translationJson.put("id", targetTranslation.id)
        translationJson.put("commit_hash", targetTranslation.commitHash)
        translationJson.put("direction", targetTranslation.targetLanguageDirection)
        translationJson.put("target_language_name", targetTranslation.targetLanguageName)
        translationsJson.put(translationJson)
        manifestJson.put("target_translations", translationsJson)
        return manifestJson
    }

    /**
     * Check if file extension is supported archive extension
     * @param fileName
     * @return boolean
     */
    private fun isValidArchiveExtension(fileName: String): Boolean {
        val isTstudio = FileUtilities.getExtension(fileName)
            .equals(TSTUDIO_EXTENSION, ignoreCase = true)
        val isZip = FileUtilities.getExtension(fileName)
            .equals(ZIP_EXTENSION, ignoreCase = true)

        return isTstudio || isZip
    }

    data class Result(
        val uri: Uri,
        val success: Boolean,
        val exportType: ExportType
    )

    enum class ExportType {
        PROJECT,
        USFM,
        PDF
    }

    /**
     * class to extract book data as well as default USFM output file name
     */
    class BookData private constructor(
        targetTranslation: TargetTranslation,
        library: Door43Client,
    ) {
        val defaultUSFMFileName: String
        val bookCode: String = targetTranslation.projectId.uppercase(Locale.getDefault())
        val languageId: String = targetTranslation.targetLanguageId
        val languageName: String = targetTranslation.targetLanguageName
        var bookName: String
            private set
        var bookTitle: String
            private set

        init {
            val projectTranslation = targetTranslation.projectTranslation
            // TODO refactor
            val project = library.index.getProject(
                languageId,
                targetTranslation.projectId,
                true
            )

            bookName = bookCode
            if ((project != null) && (project.name != null)) {
                bookName = project.name
            }

            bookTitle = ""
            if (projectTranslation != null) {
                val title = projectTranslation.title
                if (title != null) {
                    bookTitle = title.trim()
                }
            }
            if (bookTitle.isEmpty()) {
                bookTitle = bookName
            }

            if (bookTitle.isEmpty()) {
                bookTitle = bookCode
            }

            // generate file name
            defaultUSFMFileName = languageId + "_" + bookCode + "_" + bookName + ".usfm"
        }

        companion object {
            fun generate(
                targetTranslation: TargetTranslation,
                library: Door43Client
            ): BookData {
                return BookData(targetTranslation, library)
            }
        }
    }

    companion object {
        private const val GENERATOR_NAME = "ts-android"
        private const val TSTUDIO_PACKAGE_VERSION = 2

        /**
         * sort the frames
         * @param frames
         * @return
         */
        fun sortFrameTranslations(frames: Array<FrameTranslation>): ArrayList<FrameTranslation> {
            // sort frames
            val frameList = ArrayList(listOf(*frames))
            frameList.sortWith { lhs, rhs ->
                val lhInt = getChunkOrder(lhs.id)
                val rhInt = getChunkOrder(rhs.id)
                lhInt.compareTo(rhInt)
            }
            return frameList
        }

        /**
         *
         * @param chunkID
         * @return
         */
        private fun getChunkOrder(chunkID: String): Int {
            // special treatment for chunk 00 to move to end of list
            if ("00" == chunkID) {
                return 99999
            }
            if ("back".equals(chunkID, ignoreCase = true)) {
                // back is moved to very end
                return 9999999
            }
            // if not numeric, then will move to top of list and leave order unchanged
            return Util.strToInt(chunkID, -1)
        }
    }
}