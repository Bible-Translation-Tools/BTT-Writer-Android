package com.door43.translationstudio.core

import android.content.Context
import android.content.pm.PackageInfo
import android.net.Uri
import android.text.TextUtils
import com.door43.OnProgressListener
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.spannables.USFMVerseSpan
import com.door43.util.FileUtilities
import com.door43.util.FileUtilities.deleteQuietly
import com.door43.util.FileUtilities.forceMkdir
import com.door43.util.FileUtilities.getExtension
import com.door43.util.FileUtilities.getFilename
import com.door43.util.FileUtilities.readFileToString
import com.door43.util.FileUtilities.readStreamToString
import com.door43.util.FileUtilities.writeStringToFile
import com.door43.util.Zip
import com.door43.util.sortNumerically
import com.door43.util.sortNumericallyComparator
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.ChunkMarker
import org.unfoldingword.door43client.models.TargetLanguage
import org.unfoldingword.resourcecontainer.Resource
import org.unfoldingword.tools.logger.Logger
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Locale
import java.util.regex.Pattern

/**
 * For processing USFM input file or zip files into importable package.
 */
class ProcessUSFM {
    private var context: Context
    private val directoryProvider: IDirectoryProvider
    private val profile: Profile
    private val library: Door43Client
    private val assetsProvider: AssetsProvider
    private var targetLanguage: TargetLanguage? = null
    private var progressListener: OnProgressListener? = null

    private var tempDir: File? = null
    private var tempDest: File? = null
    private var tempSrc: File? = null

    private var projectFolder: File? = null

    /** base folder for all the projects */
    private var projectsFolder: File? = null

    private var chapter: String? = null
    private var lastChapter = 0

    /** raw list of files found in expanded package */
    private val sourceFiles = arrayListOf<File>()
    private val chunks: HashMap<String, List<String>> = hashMapOf()

    /** get array of the imported project folders */
    val importProjects: List<File> = arrayListOf()
    private val errors = arrayListOf<String>()
    /** descriptions of books from raw list */
    private val foundBooks = arrayListOf<String>()
    private var currentBook = 0

    private var bookName: String? = null
    private var bookShortName: String? = null

    /** was processing successful overall */
    var isProcessSuccess = false
        private set
    private var currentChapter = 0
    private var chapterCount = 1
    /** get list of books that we cant find valid names (resource IDs) for */
    val booksMissingNames: List<MissingNameItem> = arrayListOf()
    private val chapters = arrayListOf<String>()

    private constructor(
        context: Context,
        directoryProvider: IDirectoryProvider,
        profile: Profile,
        library: Door43Client,
        assetsProvider: AssetsProvider,
        targetLanguage: TargetLanguage?,
        progressListener: OnProgressListener?
    ) {
        this.context = context
        this.directoryProvider = directoryProvider
        this.profile = profile
        this.library = library
        this.assetsProvider = assetsProvider
        this.targetLanguage = targetLanguage
        this.progressListener = progressListener
    }

    /**
     * constructor
     * @param context
     * @param targetLanguage
     */
    private constructor(
        context: Context,
        directoryProvider: IDirectoryProvider,
        profile: Profile,
        library: Door43Client,
        assetsProvider: AssetsProvider,
        targetLanguage: TargetLanguage,
        file: File,
        progressListener: OnProgressListener?
    ): this(
        context,
        directoryProvider,
        profile,
        library,
        assetsProvider,
        targetLanguage,
        progressListener
    ) {
        createTempFolders()
        readFile(file)
    }

    private constructor(
        context: Context,
        directoryProvider: IDirectoryProvider,
        profile: Profile,
        library: Door43Client,
        assetsProvider: AssetsProvider,
        targetLanguage: TargetLanguage,
        uri: Uri,
        progressListener: OnProgressListener?
    ): this(
        context,
        directoryProvider,
        profile,
        library,
        assetsProvider,
        targetLanguage,
        progressListener
    ) {
        createTempFolders()
        readUri(uri)
    }

    private constructor(
        context: Context,
        directoryProvider: IDirectoryProvider,
        profile: Profile,
        library: Door43Client,
        assetsProvider: AssetsProvider,
        targetLanguage: TargetLanguage,
        rcPath: String,
        progressListener: OnProgressListener?
    ): this(
        context,
        directoryProvider,
        profile,
        library,
        assetsProvider,
        targetLanguage,
        progressListener
    ) {
        createTempFolders()
        readResourceFile(rcPath)
    }

    private constructor(
        context: Context,
        directoryProvider: IDirectoryProvider,
        profile: Profile,
        library: Door43Client,
        assetsProvider: AssetsProvider,
        jsonString: String?
    ): this(context, directoryProvider, profile, library, assetsProvider, stringToJson(jsonString))

    @Throws(Exception::class)
    private constructor(
        context: Context,
        directoryProvider: IDirectoryProvider,
        profile: Profile,
        library: Door43Client,
        assetsProvider: AssetsProvider,
        json: JSONObject?
    ): this(context, directoryProvider, profile, library, assetsProvider, null, null) {
        this.targetLanguage = TargetLanguage.fromJSON(getOptJsonObject(json!!, "TargetLanguage"))
        this.tempDir = getOptFile(json, "TempDir")
        this.projectsFolder = getOptFile(json, "TempOutput")
        this.tempDest = getOptFile(json, "TempDest")
        this.tempSrc = getOptFile(json, "TempSrc")
        this.projectFolder = getOptFile(json, "ProjectFolder")
        this.chapter = getOptString(json, "Chapter")
        this.sourceFiles.addAll(fromJsonArrayToFiles(getOptJsonArray(json, "SourceFiles")!!))

        this.importProjects as ArrayList
        this.importProjects.addAll(fromJsonArrayToFiles(getOptJsonArray(json, "ImportProjects")!!))
        this.errors.addAll(fromJsonArrayToStrings(getOptJsonArray(json, "Errors")!!))
        this.foundBooks.addAll(fromJsonArrayToStrings(getOptJsonArray(json, "FoundBooks")!!))
        this.currentBook = getOptInteger(json, "CurrentBook")!!
        this.bookName = getOptString(json, "BookName")
        this.bookShortName = getOptString(json, "BookShortName")
        this.isProcessSuccess = getOptBoolean(json, "Success")!!
        this.currentChapter = getOptInteger(json, "CurrentChapter")!!
        this.chapterCount = getOptInteger(json, "ChapterCount")!!

        this.booksMissingNames as ArrayList
        this.booksMissingNames.addAll(MissingNameItem.fromJsonArray(getOptJsonArray(json, "MissingNames")!!))
    }

    class Builder(
        private val context: Context,
        private val directoryProvider: IDirectoryProvider,
        private val profile: Profile,
        private val library: Door43Client,
        private val assetsProvider: AssetsProvider
    ) {
        private var progressListener: OnProgressListener? = null
        private var targetLanguage: TargetLanguage? = null
        private var jsonString: String? = null
        private var json: JSONObject? = null
        private var file: File? = null
        private var uri: Uri? = null
        private var rcPath: String? = null

        fun fromFile(
            targetLanguage: TargetLanguage,
            file: File,
            progressListener: OnProgressListener? = null
        ): Builder {
            this.targetLanguage = targetLanguage
            this.file = file
            this.progressListener = progressListener
            return this
        }

        fun fromUri(
            targetLanguage: TargetLanguage,
            uri: Uri,
            progressListener: OnProgressListener? = null
        ): Builder {
            this.targetLanguage = targetLanguage
            this.uri = uri
            this.progressListener = progressListener
            return this
        }

        fun fromRc(
            targetLanguage: TargetLanguage,
            filePath: String,
            progressListener: OnProgressListener? = null
        ): Builder {
            this.targetLanguage = targetLanguage
            this.rcPath = filePath
            this.progressListener = progressListener
            return this
        }

        /**
         * rebuild object from JSON string
         * @param jsonStr
         * @return
         */
        fun fromJsonString(jsonStr: String): Builder {
            this.jsonString = jsonStr
            return this
        }

        /**
         * rebuild object from JSON
         * @param json
         * @return
         */
        fun fromJson(json: JSONObject?): Builder {
            this.json = json
            return this
        }

        fun build(): ProcessUSFM? {
            return try {
                when {
                    jsonString != null -> ProcessUSFM(
                        context,
                        directoryProvider,
                        profile,
                        library,
                        assetsProvider,
                        jsonString
                    )
                    json != null -> ProcessUSFM(
                        context,
                        directoryProvider,
                        profile,
                        library,
                        assetsProvider,
                        json
                    )
                    targetLanguage != null && file != null -> ProcessUSFM(
                        context,
                        directoryProvider,
                        profile,
                        library,
                        assetsProvider,
                        targetLanguage!!,
                        file!!,
                        progressListener
                    )
                    targetLanguage != null && uri != null -> ProcessUSFM(
                        context,
                        directoryProvider,
                        profile,
                        library,
                        assetsProvider,
                        targetLanguage!!,
                        uri!!,
                        progressListener
                    )
                    targetLanguage != null && rcPath != null -> ProcessUSFM(
                        context,
                        directoryProvider,
                        profile,
                        library,
                        assetsProvider,
                        targetLanguage!!,
                        rcPath!!,
                        progressListener
                    )
                    else -> null
                }
            } catch (e: Exception) {
                Logger.w(
                    ProcessUSFM::class.java.name,
                    "Failed to build ImportUSFM instance", e
                )
                null
            }
        }
    }

    /**
     * generate JSON from object
     * @return
     */
    fun toJson(): JSONObject? {
        try {
            val json = JSONObject()
            json.putOpt("TempDir", tempDir)
            json.putOpt("TempOutput", projectsFolder)
            json.putOpt("TempDest", tempDest)
            json.putOpt("TempSrc", tempSrc)
            json.putOpt("ProjectFolder", projectFolder)
            json.putOpt("SourceFiles", toJsonFileArray(sourceFiles))
            json.putOpt("ImportProjects", toJsonFileArray(importProjects))
            json.putOpt("Errors", toJsonStringArray(errors))
            json.putOpt("FoundBooks", toJsonStringArray(foundBooks))
            json.putOpt("TargetLanguage", targetLanguage!!.toJSON())
            json.putOpt("CurrentBook", currentBook)
            json.putOpt("Success", isProcessSuccess)
            json.putOpt("MissingNames", MissingNameItem.toJsonArray(booksMissingNames))
            json.putOpt("CurrentChapter", currentChapter)
            json.putOpt("ChapterCount", chapterCount)
            json.putOpt("BookName", bookName)
            json.putOpt("BookShortName", bookShortName)
            json.putOpt("Chapter", chapter)

            return json
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * used to keep list of books that are missing names (valid resource IDs)
     * @param description
     * @param invalidName
     * @param contents
     */
    private fun addBookMissingName(description: String?, invalidName: String?, contents: String?) {
        booksMissingNames as ArrayList
        booksMissingNames.add(MissingNameItem(description, invalidName, contents))
    }

    /**
     * will update the status by calling listener. Will display text and update
     * the percent complete
     * @param text
     */
    private fun updateStatus(text: String) {
        var status = text
        var fileCount = sourceFiles.size
        if (fileCount < 1) {
            fileCount = 1
        }

        val max = 100
        val importAmountDone = currentBook.toFloat() / fileCount
        val bookAmountDone = currentChapter.toFloat() / (chapterCount + 2)
        val percentage = max * (importAmountDone + bookAmountDone / fileCount)
        val percentDone = Math.round(percentage)

        if (!isMissing(bookShortName)) {
            status = "$bookShortName - $status"
        }
        progressListener?.onProgress(percentDone, max, status)
    }

    /**
     * will update the status by calling listener.  Will display string resource and update
     * the percent complete
     * @param resource
     */
    private fun updateStatus(resource: Int) {
        val status = context.resources.getString(resource)
        updateStatus(status)
    }

    /**
     * will update the status by calling listener.  Will build status string using resource as string format
     * and applying data to it. Will also update the percent complete.
     * @param resource
     * @param data
     */
    private fun updateStatus(resource: Int, data: String) {
        val format = context.resources.getString(resource)
        updateStatus(String.format(format, data))
    }

    /**
     * get processing results as multi-line string
     */
    val resultsString: String
        get() {
            normalizeBookQueue()
            normalizeMessageQueue()
            var results = ""
            val format = context.resources.getString(R.string.found_book)
            for (i in 0..currentBook) {
                val bookName = foundBooks[i]
                val bookNameCleaned = getCleanedBookName(format, bookName)
                var errors = errors[i]
                if (errors.isEmpty()) {
                    errors = context.resources.getString(R.string.no_error)
                }
                val currentResults = "\n ${(i + 1)} - $bookNameCleaned \n $errors"
                results = results + currentResults + "\n"
            }
            return results
        }

    /**
     * cleanup uri escape characters
     * @param format
     * @param bookName
     * @return
     */
    private fun getCleanedBookName(format: String, bookName: String?): String {
        var cleaned = bookName
        val parts = bookName!!.split("%3A".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (parts.size == 2) { //look for URI prefix
            cleaned = "SD_CARD/" + parts[1]
        }
        cleaned = Uri.decode(cleaned)

        return String.format(format, cleaned)
    }

    /**
     * set book name
     * @param bookShortName
     * @param bookName
     */
    private fun setBookName(bookShortName: String, bookName: String) {
        normalizeBookQueue()
        var description = bookName
        if (bookShortName.isNotEmpty()) {
            description = "$bookShortName = $bookName"
        }
        foundBooks[currentBook] = description
    }

    /**
     * add error to error list
     *
     * @param resource
     * @param error
     */
    private fun addError(resource: Int, error: String?) {
        val format = context.resources.getString(resource)
        val newError = String.format(format, error)
        addError(newError)
    }

    /**
     * add error to error list
     *
     * @param resource
     * @param first
     * @param second
     */
    private fun addError(resource: Int, first: String?, second: String) {
        val format = context.resources.getString(resource)
        val newError = String.format(format, first, second)
        addError(newError)
    }

    /**
     * add error to error list
     *
     * @param resource
     */
    private fun addError(resource: Int) {
        val newError = context.resources.getString(resource)
        addError(newError)
    }

    /**
     * add error to error list
     *
     * @param error
     */
    private fun addError(error: String) {
        addMessage(error, true)
    }

    /**
     * add message to error list
     *
     * @param message
     */
    private fun addMessage(message: String, error: Boolean) {
        normalizeMessageQueue()
        var errors = errors[currentBook]
        if (errors.isNotEmpty()) {
            errors += "\n"
        }
        val format =
            context.resources.getString(if (error) R.string.error_prefix else R.string.warning_prefix)
        val newError = String.format(format, message)
        this.errors[currentBook] = errors + newError
        if (error) {
            Logger.e(TAG, newError)
        } else {
            Logger.w(TAG, newError)
        }
    }

    private fun normalizeMessageQueue() {
        while (errors.size <= currentBook) {
            errors.add("")
        }
    }

    private fun normalizeBookQueue() {
        while (foundBooks.size <= currentBook) {
            foundBooks.add("")
        }
    }

    /**
     * add warning to error list
     *
     * @param error
     */
    private fun addWarning(error: String) {
        addMessage(error, false)
    }

    /**
     * add warning to error list
     *
     * @param resource
     * @param error
     */
    private fun addWarning(resource: Int, error: String) {
        val format = context.resources.getString(resource)
        val newWarning = String.format(format, error)
        addWarning(newWarning)
    }

    /**
     * add warning to error list
     *
     * @param resource
     * @param val1
     * @param val2
     */
    private fun addWarning(resource: Int, val1: String, val2: String?) {
        val format = context.resources.getString(resource)
        val newWarning = String.format(format, val1, val2)
        addWarning(newWarning)
    }

    /**
     * unpack and import documents from zip stream
     *
     * @param stream
     * @return
     */
    private fun readZipStream(stream: InputStream): Boolean {
        var successOverall = true
        var success: Boolean

        updateStatus(R.string.initializing_import)

        try {
            Zip.unzipFromStream(stream, tempSrc)
            tempSrc?.listFiles()?.forEach {
                addFilesInFolder(it)
            }
            Logger.i(TAG, "found files: " + TextUtils.join("\n", sourceFiles))

            currentBook = 0
            while (currentBook < sourceFiles.size) {
                currentChapter = 0
                val file = sourceFiles[currentBook]
                val name = file.name
                updateStatus(R.string.found_book, name)
                success = processBook(file)
                if (!success) {
                    addError(R.string.could_not_parse, getShortFilePath(file.toString()))
                }
                successOverall = successOverall && success
                currentBook++
            }
            currentBook = sourceFiles.size - 1 // set to last book
        } catch (e: Exception) {
            Logger.e(TAG, "error reading stream ", e)
            addError(R.string.zip_read_error)
            successOverall = false
        }

        updateStatus(R.string.finished_loading)
        isProcessSuccess = successOverall
        return successOverall
    }

    /**
     * import single file
     *
     * @param file
     * @return
     */
    private fun readFile(file: File) {
        var success: Boolean
        updateStatus(R.string.initializing_import)

        try {
            val ext = file.extension
            val zip = "zip".equals(ext, ignoreCase = true)
            if (!zip) {
                success = processBook(file)
            } else {
                FileInputStream(file).use { stream ->
                    success = readZipStream(stream)
                }
            }
        } catch (e: Exception) {
            addError(R.string.file_read_error_detail, file.toString())
            success = false
        }
        updateStatus(R.string.finished_loading)
        isProcessSuccess = success
    }

    /**
     * import file from uri, if it is a zip file, then all files in zip will be imported
     *
     * @param uri
     * @return
     */
    private fun readUri(uri: Uri) {
        var success = false
        updateStatus(R.string.initializing_import)

        val path = FileUtilities.getUriDisplayName(context, uri)

        try {
            val ext = getExtension(path)
            val zip = "zip".equals(ext, ignoreCase = true)

            context.contentResolver.openInputStream(uri)?.use { stream ->
                if (!zip) {
                    val text = readStreamToString(stream)
                    success = processBook(text, path)
                } else {
                    success = readZipStream(stream)
                }
            }
        } catch (e: Exception) {
            addError(R.string.file_read_error_detail, path)
            success = false
        }
        updateStatus(R.string.finished_loading)
        isProcessSuccess = success
    }

    /**
     * import file from resource. if it is a zip file, then all files in zip will be imported
     *
     * @param fileName
     * @return
     */
    private fun readResourceFile(fileName: String) {
        var success: Boolean
        updateStatus(R.string.initializing_import)
        val ext = getExtension(fileName).lowercase(Locale.getDefault())
        val zip = "zip" == ext

        try {
            assetsProvider.open(fileName).use { stream ->
                if (!zip) {
                    val text = readStreamToString(stream)
                    success = processBook(text, getFilename(fileName))
                } else {
                    success = readZipStream(stream)
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "error reading $fileName", e)
            success = false
        }
        updateStatus(R.string.finished_loading)
        isProcessSuccess = success
    }

    class ParsedChunks(
        val chunks: HashMap<String, List<String>>,
        val chapters: List<String>,
        val success: Boolean
    )

    /**
     * process single document and create a project
     *
     * @param file
     * @return
     */
    private fun processBook(file: File): Boolean {
        var success: Boolean
        try {
            val book = readFileToString(file)
            success = processBook(book, file.toString())
        } catch (e: Exception) {
            Logger.e(TAG, "error reading book $file", e)
            addError(R.string.error_reading_file, file.toString())
            success = false
        }
        return success
    }

    fun processText(
        book: String,
        name: String,
        promptForName: Boolean,
        useName: String?
    ): Boolean {
        booksMissingNames as ArrayList
        booksMissingNames.clear()

        currentBook = foundBooks.size
        val success = processBook(book, name, promptForName, useName)
        isProcessSuccess = success
        return success
    }

    private fun processBook(
        book: String,
        name: String,
        promptForName: Boolean = true,
        useName: String? = null
    ): Boolean {
        var successOverall: Boolean
        var success: Boolean
        bookShortName = ""
        val description = getShortFilePath(name)
        setBookName("", description)
        try {
            currentChapter = 0
            chapterCount = 1

            extractBookID(book)

            // boolean hasSections = isPresent(book, PATTERN_SECTION_MARKER);
            val hasVerses = isPresent(book)

            if (useName != null) {
                bookShortName = useName
            }

            if (isMissing(bookShortName)) {
                addError(R.string.missing_book_short_name)
                addBookMissingName(name, null, book)
                return promptForName
            }

            bookShortName = bookShortName!!.lowercase(Locale.getDefault())

            setBookName(bookShortName!!, description)

            if (!hasVerses) {
                addError(R.string.no_verse)
                return false
            }

            tempDest = File(projectsFolder, bookShortName!!)
            projectFolder = File(tempDest, bookShortName + "-" + targetLanguage?.slug)

            if (isMissing(bookName)) {
                addError(R.string.missing_book_name)
                bookName = bookShortName
            }

            val versifications = library.index.getVersifications("en")
            val markers = library.index.getChunkMarkers(bookShortName, versifications[0].slug)
            val haveChunksList = markers.isNotEmpty()

            if (!haveChunksList) { // no chunk list
                // TODO: 4/13/16 add support for processing by sections
                addWarning(R.string.no_chunk_list, bookShortName!!)
                addBookMissingName(bookName, bookShortName, book)
                return promptForName
            } else { // has chunks
                val parsedChunks = parseChunks(markers)
                chapters.clear()
                chapters.addAll(parsedChunks.chapters)

                chapters.sortNumerically()

                val sortedChunks = parsedChunks.chunks.toSortedMap(sortNumericallyComparator)
                    .mapValues { (_, value) -> value.sortedWith(sortNumericallyComparator) }

                chunks.clear()
                chunks.putAll(sortedChunks)
                chapterCount = chapters.size

                success = extractChaptersFromBook(book)
                successOverall = success
            }

            if (successOverall) {
                currentChapter = (chapterCount + 1)
                updateStatus(R.string.building_manifest)

                success = buildManifest()
                successOverall = success
            }

            if (successOverall) {
                importProjects as ArrayList
                importProjects.add(projectFolder!!)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "error parsing book", e)
            return false
        }
        return successOverall
    }

    fun getShortFilePath(name: String): String {
        val pos = name.indexOf(tempSrc.toString()) // try to strip off temp folder path

        return if (pos >= 0) {
            name.substring(pos + tempSrc.toString().length + 1)
        } else { // otherwise we use just file name
            val parts = name.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (parts.isNotEmpty()) {
                parts[parts.size - 1]
            }
            name
        }
    }

    private fun extractBookID(book: String) {
        bookName = extractString(book, PATTERN_BOOK_TITLE_MARKER)
        bookShortName = extractString(book, PATTERN_BOOK_ABBREVIATION_MARKER)

        val idString = extractString(book, ID_TAG_MARKER)
        if (null != idString) {
            val tags = idString.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (tags.isNotEmpty()) {
                bookShortName = tags[0]
            }
        }
    }

    /**
     * create the manifest for a project
     *
     * @throws JSONException
     */
    @Throws(JSONException::class)
    private fun buildManifest(): Boolean {
        val pInfo: PackageInfo
        try {
            pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val projectId = bookShortName
            val resourceSlug = Resource.REGULAR_SLUG
            TargetTranslation.create(
                context,
                profile.nativeSpeaker,
                TranslationFormat.USFM,
                targetLanguage,
                projectId,
                ResourceType.TEXT,
                resourceSlug,
                pInfo,
                projectFolder
            )
        } catch (e: Exception) {
            addError(R.string.file_write_error)
            Logger.e(TAG, "failed to build manifest", e)
            return false
        }
        return true
    }

    /**
     * extract chapters in book
     *
     * @param text
     * @return
     */
    private fun extractChaptersFromBook(text: CharSequence): Boolean {
        chapter = null
        lastChapter = 0

        val pattern = PATTERN_CHAPTER_NUMBER_MARKER
        val matcher = pattern.matcher(text)
        var lastIndex = 0
        var section: CharSequence
        var successOverall = true
        var success: Boolean
        var foundChapter = false

        while (matcher.find()) {
            foundChapter = true

            // get section before this chapter marker
            section = text.subSequence(lastIndex, matcher.start())

            val chapterNumber = matcher.group(1) ?: "0" // chapter number for next section

            currentChapter = chapterNumber.toInt()
            if (currentChapter > chapters.size) { //make sure in range
                break
            }

            if (currentChapter <= 0) { // skip till we get to chapter 1
                continue
            }

            val expectedChapter = lastChapter + 1
            if (currentChapter != expectedChapter) { // if out of order
                if (currentChapter > expectedChapter) { // if gap
                    success = processChapterGap(section, lastChapter, currentChapter)
                    lastChapter = currentChapter - 1
                } else {
                    Logger.e(TAG, "out of order chapter $chapter after $lastChapter")
                    addError(
                        R.string.chapter_out_of_order,
                        chapter,
                        lastChapter.toString()
                    )
                    return false
                }
            } else {
                success = breakUpChapter(section, chapter)
            }

            successOverall = success

            if (!success) {
                break
            }

            lastChapter++
            chapter = chapterNumber // chapter number for next section
            lastIndex = matcher.end()
        }

        if (!foundChapter) { // if no chapters found
            Logger.e(TAG, "no chapters")
            addError(R.string.no_chapter)
            return false
        }

        if (successOverall) {
            section = text.subSequence(lastIndex, text.length) // get last section
            success = breakUpChapter(section, chapter)
            lastChapter = chapter!!.toInt()
            successOverall = success
        }

        if (successOverall) {
            currentChapter = chapter!!.toInt()
            if (chapter == null || currentChapter != chapters.size) {
                if (currentChapter < chapters.size) {
                    success = processChapterGap("", currentChapter + 1, chapters.size + 1)
                    successOverall = success
                } else {
                    val lastChapter = if (chapter != null) chapter else "(null)"
                    addWarning(
                        R.string.chapter_count_invalid,
                        chapters.size.toString(),
                        lastChapter
                    )
                    return false
                }
            }
        }
        return successOverall
    }

    /**
     * handle missing chapters in book
     * @param section
     * @param missingStart
     * @param missingEnd
     * @return
     */
    private fun processChapterGap(
        section: CharSequence,
        missingStart: Int,
        missingEnd: Int
    ): Boolean {
        var start = missingStart
        if (start <= 0) { // if first chapter is missing, then we start processing there
            start = 1
            Logger.w(TAG, "missing chapter $start")
            addWarning(R.string.missing_chapter_n, start.toString())
        }

        val success = breakUpChapter(section, start.toString())

        for (i in start + 1 until missingEnd) { // skip missing gaps
            Logger.w(TAG, "missing chapter $i")
            addWarning(R.string.missing_chapter_n, i.toString())
            breakUpChapter("", i.toString())
        }
        return success
    }

    /**
     * break up chapter into sections based on chunk list
     *
     * @param text
     * @return
     */
    private fun breakUpChapter(text: CharSequence, currentChapterStr: String?): Boolean {
        var successOverall = true
        var success = true

        // remove CRLF and replace with newlines
        val cleanedString =
            text.toString().replace("\r\n".toRegex(), "\n")

        if (!isMissing(currentChapterStr)) {
            try {
                val chapter = getChapterFolderName(currentChapterStr)
                if (null == chapter) {
                    addError(R.string.could_not_find_chapter, currentChapterStr)
                    return false
                }

                // TODO: 11/1/16 search for title and sub-title
//                PATTERN_CHAPTER_TITLE_MARKER = Pattern.compile(CHAPTER_TITLE_MARKER);
//                PATTERN_CHAPTER_SUB_TITLE_MARKER = Pattern.compile(CHAPTER_SUB_TITLE_MARKER);
                val verseBreaks = getVerseBreaks(chapter)

                val currentChapter = chapter.toInt()
                updateStatus(
                    R.string.processing_chapter,
                    (chapterCount - currentChapter + 1).toString()
                )

                var lastFirst: String? = null
                var i = 0
                while (i < verseBreaks.size && success) {
                    val first = verseBreaks[i]
                    success = extractVerses(chapter, cleanedString, lastFirst, first)
                    successOverall = success
                    lastFirst = first
                    i++
                }
                if (successOverall) {
                    success = extractVerses(chapter, cleanedString, lastFirst, END_MARKER.toString())
                    successOverall = success
                }
            } catch (e: Exception) {
                Logger.e(TAG, "error parsing chapter $currentChapterStr", e)
                addError(R.string.could_not_parse_chapter, currentChapterStr)
                return false
            }
        } else { // save stuff before first chapter
            val strippedUSFMFrontTags = removeKnownUSFMTags(cleanedString)
            if (strippedUSFMFrontTags.isNotEmpty()) {
                success = saveSection("front", "intro", strippedUSFMFrontTags)
                successOverall = success
            }
            val chapterFront = "front"
            success = bookName?.let { saveSection(chapterFront, "title", it) } ?: false
            successOverall = successOverall && success
        }
        return successOverall
    }

    /**
     * get the chapter name with the appropriate zero padding expected by app
     * @param findChapter
     * @return
     */
    private fun getChapterFolderName(findChapter: String?): String? {
        try {
            val chapter = Util.strToInt(findChapter, -1)
            if (chapter > 0) { // first check in expected location
                val chapterN = chapters[chapter - 1]
                if (Util.strToInt(chapterN, -1) == chapter) {
                    return getRightFileNameLength(chapterN)
                }
            }

            for (chapterN in chapters) { //search for chapter match
                if (Util.strToInt(chapterN, -1) == chapter) {
                    return getRightFileNameLength(chapterN)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        addError(R.string.could_not_find_chapter, findChapter)
        return null
    }

    /**
     * get the file name to use for verse chunk
     * @param findChapter
     * @param firstVerse
     * @return
     */
    private fun getChunkFileName(findChapter: String, firstVerse: String): String {
        val chunks = getVerseBreaks(findChapter)
        for (i in chunks.indices) {
            val firstVerseFile = chunks[i]
            if (Util.strToInt(firstVerse, 0) == Util.strToInt(firstVerseFile, 0)) {
                return getRightFileNameLength(firstVerseFile)
            }
        }

        return firstVerse // if not found, use same as chapter id
    }

    /**
     * get the array of verse chunks
     * @param findChapter
     * @return
     */
    private fun getVerseBreaks(findChapter: String): List<String> {
        var chapter = findChapter
        if (chunks.containsKey(chapter)) {
            return chunks[chapter]!!
        }

        // Add first leading zero and search
        chapter = "0$chapter"
        if (chunks.containsKey(chapter)) {
            return chunks[chapter]!!
        }

        // Add second leading zero and search
        chapter = "0$chapter"
        if (chunks.containsKey(chapter)) {
            return chunks[chapter]!!
        }

        //try removing leading spaces
        chapter = findChapter
        while (chapter.isNotEmpty() && chapter[0] == '0') {
            chapter = chapter.substring(1)
            if (chunks.containsKey(chapter)) {
                return chunks[chapter]!!
            }
        }

        addError(R.string.could_not_find_chapter, findChapter)
        return listOf()
    }

    /**
     * extract verses in range of start to end into new section
     *
     * @param chapter
     * @param text
     * @param start
     * @param end
     * @return
     */
    private fun extractVerses(
        chapter: String,
        text: CharSequence,
        start: String?,
        end: String
    ): Boolean {
        if (null == start) { // skip over stuff before verse 1 for now
            // TODO: 11/1/16 save stuff before verse one
            if (!isMissing(chapter)) {
                val pattern = PATTERN_USFM_VERSE_SPAN
                val matcher = pattern.matcher(text)
                if (matcher.find()) {
                    val verseStart = matcher.start()
                    if (verseStart > 0) {
                        var chapterTitleStart = 0
                        val chapterTitlePattern = PATTERN_CHAPTER_TITLE_MARKER
                        val chapterTitleMatcher = chapterTitlePattern.matcher(text)
                        if (chapterTitleMatcher.find()) {
                            chapterTitleStart = chapterTitleMatcher.start(1)
                        }

                        val title = text.subSequence(chapterTitleStart, verseStart)
                        getChapterFolderName(chapter)?.let {
                            saveSection(it, "title", title)
                        }
                    }
                }
            }
            return true
        }

        val startVerse = start.toInt()
        val endVerse = end.toInt()
        return extractVerseRange(chapter, text, startVerse, endVerse, start)
    }

    /**
     * extract verses in range of start to end into new section
     *
     * @param chapter
     * @param text
     * @param start
     * @param end
     * @param firstVerse
     * @return
     */
    private fun extractVerseRange(
        chapter: String,
        text: CharSequence,
        start: Int,
        end: Int,
        firstVerse: String
    ): Boolean {
        var successOverall = true
        val success: Boolean
        if (!isMissing(chapter)) {
            val pattern = PATTERN_USFM_VERSE_SPAN
            val matcher = pattern.matcher(text)
            var lastIndex = 0
            var section = ""
            var currentVerse = 0
            var foundVerseCount = 0
            var endVerseRange = 0
            var done = false
            var matchesFound = false
            var pretext: CharSequence = ""
            while (matcher.find()) {
                matchesFound = true

                if (currentVerse >= end) {
                    done = true
                    break
                }

                if (currentVerse >= start) {
                    while (true) { // find the end of the section
                        if (endVerseRange > 0) {
                            foundVerseCount += (endVerseRange - currentVerse + 1)
                        } else {
                            foundVerseCount++
                        }

                        val verse = matcher.group(1)
                        val verseRange = verse?.let(::getVerseRange) ?: break
                        currentVerse = verseRange[0]
                        endVerseRange = verseRange[1]

                        var results = splitAtVerseEnd(text, lastIndex, matcher.start())
                        section = section + pretext + results.verse
                        pretext = results.extra
                        lastIndex = matcher.start() // update end of chunk

                        if (currentVerse >= end) {
                            break
                        }

                        val found = matcher.find()
                        if (!found) { // we have reached the end, use this verse
                            results = splitAtVerseEnd(text, lastIndex, text.length)
                            section = section + pretext + results.verse
                            pretext = ""
                            foundVerseCount++
                            break
                        }
                    }

                    done = true
                    break
                }

                val verse = matcher.group(1)
                val verseRange = verse?.let(::getVerseRange) ?: return false
                currentVerse = verseRange[0]
                endVerseRange = verseRange[1]

                val results = splitAtVerseEnd(text, lastIndex, matcher.start())
                pretext = results.extra
                lastIndex = matcher.start()
            }

            if (!done && matchesFound && (currentVerse >= start) && (currentVerse < end)) {
                val results = splitAtVerseEnd(text, lastIndex, text.length)
                section = section + pretext + results.verse
            }

            if (start != 0) { // text before first verse is not a concern
                var delta = foundVerseCount - (end - start)
                if (section.isEmpty()) {
                    val format =
                        context.resources.getString(R.string.could_not_find_verses_in_chapter)
                    val msg = String.format(format, start, end - 1, chapter)
                    addWarning(msg)
                } else if ((end != END_MARKER) && (delta != 0)) {
                    val format: String
                    if (delta < 0) {
                        delta = -delta
                        format = context.resources.getString(R.string.missing_verses_in_chapter)
                    } else {
                        format = context.resources.getString(R.string.extra_verses_in_chapter)
                    }
                    val msg = String.format(format, delta, start, end - 1, chapter)
                    addWarning(msg)
                }
            }

            val chunkFileName = getChunkFileName(chapter, firstVerse)
            success = getChapterFolderName(chapter)?.let {
                saveSection(it, chunkFileName, section)
            } ?: false
            successOverall = success
        }
        return successOverall
    }

    /**
     * check for verse terminator
     * @return
     */
    private fun splitAtVerseEnd(text: CharSequence, start: Int, end: Int): VerseSplitResults {
        val verseStr = text.subSequence(start, end).toString()
        val sectionEnd = "\\s5\n"
        val pos = verseStr.indexOf(sectionEnd)
        if (pos >= 0) {
            val verseStart = verseStr.substring(0, pos)
            val extra = verseStr.substring(pos + sectionEnd.length)
            return VerseSplitResults(verseStart, extra)
        }
        return VerseSplitResults(verseStr, "")
    }

    internal inner class VerseSplitResults(val verse: String, val extra: String)

    /**
     * get verse range
     * @param verse
     * @return
     */
    private fun getVerseRange(verse: String): IntArray? {
        var verseRange: IntArray?
        val endVerseRange: Int
        try {
            val currentVerse = verse.toInt()
            verseRange = intArrayOf(currentVerse, 0)
        } catch (e: NumberFormatException) { // might be a range in format 12-13
            val range = verse.split("-".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (range.size < 2) {
                verseRange = null
            } else {
                val currentVerse = range[0].toInt()
                endVerseRange = range[1].toInt()
                verseRange = intArrayOf(currentVerse, endVerseRange)
            }
        }

        return verseRange
    }

    /**
     * save section (chunk) to file in chapter folder
     *
     * @param chapter
     * @param fileName
     * @param section
     * @return
     */
    private fun saveSection(chapter: String, fileName: String, section: CharSequence): Boolean {
        val chapterFolder = File(projectFolder, chapter)
        try {
            val cleanChunk = removePattern(section)
            forceMkdir(chapterFolder)
            val output = File(chapterFolder, "$fileName.txt")
            writeStringToFile(output, cleanChunk)
            return true
        } catch (e: Exception) {
            Logger.e(TAG, "error parsing chapter ${this.chapter}", e)
            addError(R.string.file_write_for_verse, "$chapter/$fileName")
            return false
        }
    }

    /**
     * test if CharSequence is null or empty
     *
     * @param text
     * @return
     */
    private fun isMissing(text: CharSequence?): Boolean {
        return text.isNullOrEmpty()
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
            val foundItem: String?
            if (matcher.find()) {
                foundItem = matcher.group(1)
                return foundItem.trim()
            }
        }
        return null
    }

    /**
     * match regexPattern for USFM line and remove line if present
     *
     * @param text
     * @return
     */
    private fun removeKnownUSFMTags(text: CharSequence): String {
        if (text.isNotEmpty()) {
            val usfmTagPattern = "\\\\(\\w+)\\s([^\\n\\\\]*)"
            val regexPattern = Pattern.compile(usfmTagPattern)

            // find instance
            val matcher = regexPattern.matcher(text)
            var cleaned: CharSequence = ""
            var lastPos = 0
            while (matcher.find()) {
                val usfmTag: CharSequence? = matcher.group(1)

                if (usfmTag == "c"
                    || usfmTag == "id"
                    || usfmTag == "ide"
                    || usfmTag == "h"
                    || usfmTag == "toc1"
                    || usfmTag == "toc2"
                    || usfmTag == "toc3"
                    || usfmTag == "mt"
                    || usfmTag == "p"
                ) {
                    val before = text.subSequence(lastPos, matcher.start())
                    lastPos = matcher.end()
                    if (lastPos < text.length) {
                        val c = text[lastPos]
                        if (c == '\n') {
                            lastPos++
                        }
                    }

                    cleaned = TextUtils.concat(cleaned, before)
                }
            }
            if (lastPos < text.length) {
                cleaned = TextUtils.concat(cleaned, text.subSequence(lastPos, text.length))
            }
            val trimmed = trimWhiteSpace(cleaned)
            return trimmed.toString()
        }
        return ""
    }

    /**
     * trims leading and trailing white space
     * @param text
     * @return
     */
    private fun trimWhiteSpace(text: CharSequence): CharSequence {
        var pos = 0
        while (pos < text.length) {
            val c = text[pos]
            if ((c == ' ') || (c == '\n')) {
                pos++
            } else {
                break
            }
        }

        if (pos >= text.length) {
            return ""
        }

        var trimmed = text.subSequence(pos, text.length)

        pos = trimmed.length
        while (pos > 0) {
            val c = trimmed[pos - 1]
            if ((c == ' ') || (c == '\n')) {
                pos--
            } else {
                break
            }
        }

        if (pos == 0) {
            return ""
        }

        val trimmedEnd = trimmed.subSequence(0, pos)
        trimmed = trimmedEnd
        if ((trimmed.isNotEmpty())
            && (trimmed[trimmed.length - 1] != '\n')
        ) {
            trimmed = TextUtils.concat(trimmed, "\n") // make sure last line is terminated
        }

        return trimmed
    }

    /**
     * remove pattern if present in text
     *
     * @param text
     * @return
     */
    private fun removePattern(text: CharSequence): String {
        var out = ""
        val matcher = PATTERN_SECTION_MARKER.matcher(text)
        var lastIndex = 0
        while (matcher.find()) {
            out += text.subSequence(
                lastIndex,
                matcher.start()
            ) // get section before this chunk marker
            lastIndex = matcher.end()
        }
        out += text.subSequence(lastIndex, text.length) // get last section
        return out
    }

    /**
     * test to see if regex pattern is present in text
     *
     * @param text
     * @return
     */
    private fun isPresent(text: CharSequence): Boolean {
        if (text.isNotEmpty()) {
            // find instance
            val matcher = PATTERN_USFM_VERSE_SPAN.matcher(text)
            if (matcher.find()) {
                return true
            }
        }

        return false
    }

    /**
     * create the necessary temp folders for unzipped source and output
     */
    private fun createTempFolders() {
        tempDir = File(
            directoryProvider.cacheDir,
            System.currentTimeMillis().toString()
        )
        tempDir!!.mkdirs()
        tempSrc = File(tempDir, "source")
        tempSrc!!.mkdirs()
        projectsFolder = File(tempDir, "output")
        projectsFolder!!.mkdirs()
    }

    /**
     * cleanup working directory
     */
    fun cleanup() {
        deleteQuietly(tempDir)
    }

    /**
     * add file and files in sub-folders to list of files to process
     *
     * @param usfm
     * @return
     */
    private fun addFilesInFolder(usfm: File): Boolean {
        Logger.i(TAG, "processing folder: $usfm")

        if (usfm.isDirectory) {
            val subFiles = usfm.listFiles()
            if (subFiles != null) {
                for (subFile in subFiles) {
                    addFilesInFolder(subFile)
                }
                Logger.i(TAG, "found files: $subFiles")
            }
        } else {
            addFile(usfm)
        }
        return true
    }

    /**
     * add file to list of files to process
     *
     * @param usfm
     * @return
     */
    private fun addFile(usfm: File): Boolean {
        Logger.i(TAG, "processing file: $usfm")
        sourceFiles.add(usfm)
        return true
    }

    /**
     * parse chunk markers (contains verses and chapters) into map of verses indexed by chapter
     *
     * @param markers
     * @return
     */
    private fun parseChunks(markers: List<ChunkMarker>): ParsedChunks {
        val chunks: HashMap<String, List<String>> = hashMapOf()
        val chapters: List<String>

        for (marker in markers) {
            val chapter = marker.chapter
            val firstVerse = marker.verse

            val verses = if (chunks.containsKey(chapter)) {
                chunks[chapter]!!
            } else {
                val verses = arrayListOf<String>()
                chunks[chapter] = verses
                verses
            }

            verses as ArrayList
            verses.add(firstVerse)
        }

        //extract chapters
        val foundChapters = arrayListOf<String>()
        for (chapter in chunks.keys) {
            if (Util.strToInt(chapter, 0) > 0) {
                foundChapters.add(chapter)
            }
        }
        foundChapters.sortWith(sortNumericallyComparator)
        chapters = foundChapters
        val success = chapters.isNotEmpty() && chunks.isNotEmpty()

        return ParsedChunks(chunks, chapters, success)
    }

    /**
     * right size the file name length.  App expects file names under 100 to be only two digits.
     * @param fileName
     * @return
     */
    private fun getRightFileNameLength(fileName: String): String {
        var name = fileName
        val numericalValue = Util.strToInt(name, -1)
        if (numericalValue in 0..99 && name.length != 2) {
            name = "00$name" // make sure has leading zeroes
            name = name.substring(name.length - 2) // trim down extra leading zeros
        }
        return name
    }

    companion object {
        val TAG: String = ProcessUSFM::class.java.simpleName
        private const val CHAPTER_TITLE_MARKER: String = "\\\\cl\\s([^\\n]*)"
        val PATTERN_CHAPTER_TITLE_MARKER: Pattern = Pattern.compile(CHAPTER_TITLE_MARKER)
        private const val CHAPTER_SUB_TITLE_MARKER: String = "\\\\cl\\s([^\\n]*)"
        val PATTERN_CHAPTER_SUB_TITLE_MARKER: Pattern = Pattern.compile(CHAPTER_SUB_TITLE_MARKER)
        private const val BOOK_TITLE_MARKER: String = "\\\\toc1\\s([^\\n]*)"
        @JvmField
        val PATTERN_BOOK_TITLE_MARKER: Pattern = Pattern.compile(BOOK_TITLE_MARKER)
        private const val ID_TAG: String = "\\\\id\\s([^\\n]*)"
        @JvmField
        val ID_TAG_MARKER: Pattern = Pattern.compile(ID_TAG)
        private const val BOOK_LONG_NAME_MARKER: String = "\\\\toc2\\s([^\\n]*)"
        @JvmField
        val PATTERN_BOOK_LONG_NAME_MARKER: Pattern = Pattern.compile(BOOK_LONG_NAME_MARKER)
        private const val BOOK_ABBREVIATION_MARKER: String = "\\\\toc3\\s([^\\n]*)"
        @JvmField
        val PATTERN_BOOK_ABBREVIATION_MARKER: Pattern = Pattern.compile(BOOK_ABBREVIATION_MARKER)
        private const val SECTION_MARKER: String = "\\\\s5([^\\n]*)"
        private val PATTERN_SECTION_MARKER: Pattern = Pattern.compile(SECTION_MARKER)
        private const val CHAPTER_NUMBER_MARKER: String = "\\\\c\\s(\\d+(-\\d+)?)\\s"
        @JvmField
        val PATTERN_CHAPTER_NUMBER_MARKER: Pattern = Pattern.compile(CHAPTER_NUMBER_MARKER)
        @JvmField
        val PATTERN_USFM_VERSE_SPAN: Pattern = Pattern.compile(USFMVerseSpan.PATTERN)
        const val END_MARKER: Int = 999999
        const val FIRST_VERSE: String = "first_verse"
        const val FILE_NAME: String = "file_name"

        private fun getOptInteger(json: JSONObject, key: String): Int? {
            return getOpt(json, key) as Int?
        }

        private fun getOptBoolean(json: JSONObject, key: String): Boolean? {
            return getOpt(json, key) as Boolean?
        }

        private fun getOptFile(json: JSONObject, key: String): File? {
            val path = getOptString(json, key)
            if (path != null) {
                return File(path)
            }
            return null
        }

        private fun getOptString(json: JSONObject, key: String): String? {
            return getOpt(json, key) as String?
        }

        private fun getOptJsonObject(json: JSONObject, key: String): JSONObject? {
            try {
                val obj = getOpt(json, key)
                return obj as JSONObject?
            } catch (e: Exception) {
                return JSONObject()
            }
        }

        private fun getOptJsonArray(json: JSONObject, key: String): JSONArray? {
            try {
                val obj = getOpt(json, key)
                return obj as JSONArray?
            } catch (e: Exception) {
                return JSONArray()
            }
        }

        private fun getOpt(json: JSONObject, key: String): Any? {
            try {
                if (json.has(key)) {
                    val obj = json[key]
                    return obj
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }

        private fun toJsonFileArray(array: List<File>): JSONArray {
            val jsonArray = JSONArray()
            for (item in array) {
                jsonArray.put(item.toString())
            }
            return jsonArray
        }

        private fun fromJsonArrayToFiles(jsonStr: String): List<File> {
            try {
                val jsonArray = JSONArray(jsonStr)
                return fromJsonArrayToFiles(jsonArray)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return listOf()
        }

        @Throws(JSONException::class)
        private fun fromJsonArrayToFiles(jsonArray: JSONArray): List<File> {
            val array = arrayListOf<File>()

            for (i in 0 until jsonArray.length()) {
                val path = jsonArray.getString(i)
                val file = File(path)
                array.add(file)
            }
            return array
        }

        private fun toJsonStringArray(array: List<String>): JSONArray {
            val jsonArray = JSONArray()
            for (item in array) {
                jsonArray.put(item)
            }
            return jsonArray
        }

        private fun fromJsonArrayToStrings(jsonStr: String): List<String> {
            try {
                val jsonArray = JSONArray(jsonStr)
                return fromJsonArrayToStrings(jsonArray)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return listOf()
        }

        @Throws(JSONException::class)
        private fun fromJsonArrayToStrings(jsonArray: JSONArray): List<String> {
            val array = arrayListOf<String>()

            for (i in 0 until jsonArray.length()) {
                val text = jsonArray.getString(i)
                array.add(text)
            }
            return array
        }

        private fun stringToJson(jsonStr: String?): JSONObject? {
            if (jsonStr == null) return null
            return try {
                JSONObject(jsonStr)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}