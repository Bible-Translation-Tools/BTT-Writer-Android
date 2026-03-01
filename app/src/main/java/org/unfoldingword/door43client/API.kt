package org.unfoldingword.door43client

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.unfoldingword.door43client.models.Catalog
import org.unfoldingword.door43client.models.Category
import org.unfoldingword.door43client.models.Question
import org.unfoldingword.door43client.models.Questionnaire
import org.unfoldingword.door43client.models.SourceLanguage
import org.unfoldingword.door43client.models.TargetLanguage
import org.unfoldingword.resourcecontainer.ContainerTools
import org.unfoldingword.resourcecontainer.Resource
import org.unfoldingword.resourcecontainer.ResourceContainer
import org.unfoldingword.resourcecontainer.errors.InvalidRCException
import org.unfoldingword.resourcecontainer.errors.MissingRCException
import org.unfoldingword.tools.http.GetRequest
import java.io.File
import java.io.IOException
import java.net.URL

/**
 * Created by joel on 8/30/16.
 */
internal class API @Throws(IOException::class) constructor(
    context: Context,
    schema: String,
    databasePath: File,
    private val resourceDir: File
) {

    private val library: Library
    private var globalCatalogHost: String? = null
    private var logListener: OnLogListener = defaultLogListener

    init {
        val nameParts = databasePath.name.split("\\.".toRegex()).toTypedArray()
        val dbExt = nameParts[nameParts.size - 1]
        val databaseContext = DatabaseContext(context, databasePath.parentFile!!, dbExt)
        val dbName = databasePath.name.replaceFirst("\\.[^.]+$".toRegex(), "")

        synchronized(this) {
            if (sqLiteHelper == null) {
                sqLiteHelper = SQLiteHelper(databaseContext, schema, dbName)
            }
        }
        this.library = Library(sqLiteHelper!!)
    }

    /**
     * Performs closing operations.
     * e.g. closing the db, etc.
     */
    fun tearDown() {
        sqLiteHelper?.let {
            it.close()
            sqLiteHelper = null
        }
    }

    /**
     * Attaches a listener to receive log events
     * @param listener
     */
    fun setLogger(listener: OnLogListener?) {
        this.logListener = listener ?: defaultLogListener
    }

    /**
     * Sets the host to use when injecting the global catalogs.
     * This is only valid until we migrate to the use api.
     *
     * This is also only currently used for tests
     * @param host
     */
    @Deprecated("This is only valid until we migrate to the use api.")
    fun setGlobalCatalogServer(host: String?) {
        this.globalCatalogHost = host
    }

    /**
     * Returns the read only index
     * @return
     */
    val index: Index
        get() = library

    /**
     * Indexes the source content
     *
     * @param url the entry resource api catalog
     * @param listener an optional progress listener. This should receive progress id, total, completed
     */
    @Throws(Exception::class)
    fun updateSources(url: String, listener: OnProgressListener?) {
        library.beginTransaction()
        try {
            val getPrimaryCatalog = GetRequest(URL(url))
            val data = getPrimaryCatalog.read()
            // process legacy catalog data
            LegacyTools.processCatalog(library, data, listener)
        } catch (e: Exception) {
            library.endTransaction(false)
            throw e
        }
        library.endTransaction(true)
    }

    /**
     * Indexes the chunk markers
     * @param listener
     * @throws Exception
     */
    @Throws(Exception::class)
    fun updateChunks(listener: OnProgressListener?) {
        library.beginTransaction()
        try {
            LegacyTools.processChunks(library, listener)
        } catch (e: Exception) {
            library.endTransaction(false)
            throw e
        }
        library.endTransaction(true)
    }

    /**
     * Updates all the global catalogs
     * @param force Should we update/insert catalogs
     * @param listener Progress Listener
     * @throws Exception Any exception
     */
    @Throws(Exception::class)
    fun updateCatalogs(force: Boolean, listener: OnProgressListener?) {
        if (force) {
            // inject missing global catalogs
            LegacyTools.injectGlobalCatalogs(library, globalCatalogHost)
        }
        val catalogs = library.getCatalogs()
        for (c in catalogs) {
            updateCatalog(c, listener)
        }
    }

    /**
     * Utility for testing
     *
     * @param slug
     * @throws Exception
     */
    @Throws(Exception::class)
    fun updateCatalog(slug: String) {
        LegacyTools.injectGlobalCatalogs(library, globalCatalogHost)
        val c = library.getCatalog(slug)
        updateCatalog(c, null)
    }

    fun updateLanguageUrl(url: String) {
        LegacyTools.setLangNamesUrl(url)
    }

    /**
     * Downloads a global catalog and indexes it.
     *
     * @param catalog the catalog being updated
     * @param listener an optional progress listener. This should receive progress id, total, completed
     */
    @Throws(Exception::class)
    private fun updateCatalog(catalog: Catalog?, listener: OnProgressListener?) {
        if (catalog == null) throw Exception("Unknown catalog")
        val request = GetRequest(URL(catalog.url))
        val data = request.read()
        library.beginTransaction()
        try {
            when (catalog.slug) {
                "langnames" -> {
                    library.clearTargetLanguages()
                    indexTargetLanguageCatalog(data, listener)
                }
                "new-language-questions" -> {
                    library.clearNewLanguageQuestions()
                    indexNewLanguageQuestionsCatalog(data, listener)
                }
                "temp-langnames" -> {
                    library.clearTempLanguages()
                    indexTempLanguagesCatalog(data, listener)
                }
                "approved-temp-langnames" -> {
                    library.clearApprovedTempLanguages()
                    indexApprovedTempLanguagesCatalog(data, listener)
                }
                else -> throw Exception("Parsing this catalog has not been implemented")
            }
        } catch (e: Exception) {
            library.endTransaction(false)
            throw e
        }
        library.endTransaction(true)
    }

    /**
     * parses the target language catalog and indexes it
     * @param data
     * @param listener
     */
    @Throws(Exception::class)
    private fun indexTargetLanguageCatalog(data: String, listener: OnProgressListener?) {
        val languages = JSONArray(data)
        for (i in 0 until languages.length()) {
            val l = languages.getJSONObject(i)
            val isGateway = if (l.has("gl")) l.getBoolean("gl") else false
            val language = TargetLanguage(
                l.getString("lc"), l.getString("ln"),
                l.getString("ang"), l.getString("ld"), l.getString("lr"), isGateway
            )
            if (!library.addTargetLanguage(language)) {
                logListener.onWarning("Failed to add the target language: " + language.slug)
            }
            if (listener != null) {
                if (!listener.onProgress("langnames", languages.length(), i + 1)) break
            }
            library.yieldSafely()
        }
    }

    /**
     * Parses the new language questions catalog and indexes it
     * @param data
     * @param listener
     */
    @Throws(Exception::class)
    private fun indexNewLanguageQuestionsCatalog(data: String, listener: OnProgressListener?) {
        val obj = JSONObject(data)
        val languages = obj.getJSONArray("languages")
        for (i in 0 until languages.length()) {
            val qJson = languages.getJSONObject(i)
            val dataFields = HashMap<String, Long>()
            if (qJson.has("language_data")) {
                val dataFieldJson = qJson.getJSONObject("language_data")
                val keyIter = dataFieldJson.keys()
                while (keyIter.hasNext()) {
                    val key = keyIter.next()
                    dataFields[key] = dataFieldJson.getLong(key)
                }
            }
            val questionnaire = Questionnaire(
                qJson.getString("slug"),
                qJson.getString("name"),
                qJson.getString("dir"),
                qJson.getLong("questionnaire_id"),
                dataFields
            )
            val questionnaireId = library.addQuestionnaire(questionnaire)

            // add questions
            val questionsArray = qJson.getJSONArray("questions")
            for (j in 0 until questionsArray.length()) {
                val questionJson = questionsArray.getJSONObject(j)
                val dependsOnId = if (questionJson.isNull("depends_on")) -1L else questionJson.getLong("depends_on")
                val question = Question(
                    questionJson.getString("text"),
                    questionJson.getString("help"),
                    questionJson.getBoolean("required"),
                    Question.InputType.get(questionJson.getString("input_type")),
                    questionJson.getInt("sort"),
                    dependsOnId, questionJson.getLong("id")
                )
                library.addQuestion(question, questionnaireId)

                // broadcast itemized progress if there is only one questionnaire
                if (languages.length() == 1 && listener != null) {
                    if (!listener.onProgress("new-language-questions", questionsArray.length(), j + 1)) break
                }
                library.yieldSafely()
            }
            // broadcast overall progress if there are multiple questionnaires.
            if (languages.length() > 1 && listener != null) {
                if (!listener.onProgress("new-language-questions", questionsArray.length(), i + 1)) break
            }
            library.yieldSafely()
        }
    }

    /**
     * Parses the temporary language codes catalog and indexes it
     * @param data
     * @param listener
     */
    @Throws(Exception::class)
    private fun indexTempLanguagesCatalog(data: String, listener: OnProgressListener?) {
        val languages = JSONArray(data)
        for (i in 0 until languages.length()) {
            val l = languages.getJSONObject(i)
            val isGateway = if (l.has("gl")) l.getBoolean("gl") else false
            val language = TargetLanguage(
                l.getString("lc"), l.getString("ln"),
                l.getString("ang"), l.getString("ld"), l.getString("lr"), isGateway
            )
            if (!library.addTempTargetLanguage(language)) {
                logListener.onWarning("Failed to add the temp target language: " + language.slug)
            }
            if (listener != null) {
                if (!listener.onProgress("temp-langnames", languages.length(), i + 1)) break
            }
            library.yieldSafely()
        }
    }

    /**
     * Parses the approved temporary language codes catalog and indexes it
     * @param data
     * @param listener
     */
    @Throws(Exception::class)
    private fun indexApprovedTempLanguagesCatalog(data: String, listener: OnProgressListener?) {
        val languages = JSONArray(data)
        for (i in 0 until languages.length()) {
            val l = languages.getJSONObject(i)
            val keys = l.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (!library.setApprovedTargetLanguage(key, l.getString(key))) {
                    logListener.onWarning("Failed to approve the temp target language: $key as ${l.getString(key)}")
                }
            }
            if (listener != null) {
                if (!listener.onProgress("approved-temp-langnames", languages.length(), i + 1)) break
            }
            library.yieldSafely()
        }
    }

    /**
     * Downloads a resource container.
     *
     * TRICKY: to keep the interface stable we've abstracted some things.
     * once the api supports real resource containers this entire method can go away and be replace
     * with downloadContainer_Future (which should be renamed to downloadContainer).
     * convertLegacyResourceToContainer will also become deprecated at that time though it may be handy to keep around.
     *
     * @param sourceLanguageSlug
     * @param projectSlug
     * @param resourceSlug
     * @return The new resource container
     */
    @Throws(Exception::class)
    fun downloadResourceContainer(sourceLanguageSlug: String, projectSlug: String, resourceSlug: String): ResourceContainer {
        val path = downloadFutureCompatibleResourceContainer(sourceLanguageSlug, projectSlug, resourceSlug)

        // migrate to resource container
        val r = library.getResource(sourceLanguageSlug, projectSlug, resourceSlug)
        if (r == null) {
            FileUtil.deleteQuietly(path)
            throw Exception("Unknown resource")
        }
        val data = FileUtil.readFileToString(path)

        // clean downloaded file
        FileUtil.deleteQuietly(path)
        return convertLegacyResource(sourceLanguageSlug, projectSlug, resourceSlug, data)
    }

    /**
     * Downloads a resource container.
     * This expects a correctly formatted resource container
     * and will download it directly to the disk
     *
     * once the api can deliver proper resource containers this method
     * should be renamed to downloadContainer and the current downloadResourceContainer method removed.
     *
     * @param sourceLanguageSlug
     * @param projectSlug
     * @param resourceSlug
     * @return the path to the downloaded resource container
     */
    @Throws(Exception::class)
    fun downloadFutureCompatibleResourceContainer(sourceLanguageSlug: String, projectSlug: String, resourceSlug: String): File {
        val r = library.getResource(sourceLanguageSlug, projectSlug, resourceSlug)
            ?: throw Exception("Unknown resource")
        val containerFormat = getResourceContainerFormat(r.formats)
            ?: throw Exception("Missing resource container format")

        val containerSlug = ContainerTools.makeSlug(sourceLanguageSlug, projectSlug, resourceSlug)
        val containerDir = File(resourceDir, containerSlug)
        val destFile = File(resourceDir, "$containerSlug.${ResourceContainer.fileExtension}")

        FileUtil.deleteQuietly(destFile)
        FileUtil.deleteQuietly(containerDir)

        destFile.parentFile?.mkdirs()
        val url = containerFormat.url
        if (url.isNullOrEmpty()) throw Exception("Missing resource format url")

        val request = GetRequest(URL(url))
        try {
            request.download(destFile)
        } catch (e: Exception) {
            FileUtil.deleteQuietly(destFile)
            throw e
        }
        if (request.responseCode != 200) {
            FileUtil.deleteQuietly(destFile)
            throw Exception(request.responseMessage)
        }

        return destFile
    }

    /**
     * Converts a legacy resource catalog into a resource container.
     * The container will be placed in.
     *
     * This will be deprecated once the api is updated to support proper resource containers.
     *
     * @param sourceLanguageSlug
     * @param projectSlug
     * @param resourceSlug
     * @param data the legacy data that will be converted
     * @return
     */
    @Deprecated("This will be deprecated once the api is updated to support proper resource containers.")
    @Throws(Exception::class)
    fun convertLegacyResource(sourceLanguageSlug: String, projectSlug: String, resourceSlug: String, data: String): ResourceContainer {
        val containerSlug = ContainerTools.makeSlug(sourceLanguageSlug, projectSlug, resourceSlug)
        val containerDir = File(resourceDir, containerSlug)

        val language = library.getSourceLanguage(sourceLanguageSlug) ?: throw Exception("Missing language")
        val lJson = language.toJSON()

        val project = library.getProject(sourceLanguageSlug, projectSlug) ?: throw Exception("Missing project")
        val pJson = project.toJSON()
        val pCatJson = JSONArray()
        val categories = library.getCategories(sourceLanguageSlug, projectSlug)
        for (cat in categories) {
            pCatJson.put(cat.slug)
        }
        pJson.put("categories", pCatJson)

        val resource = library.getResource(sourceLanguageSlug, projectSlug, resourceSlug) ?: throw Exception("Missing resource")
        val format = getResourceContainerFormat(resource.formats) ?: throw Exception("Missing resource container format")
        val rJson = resource.toJSON()

        val properties = JSONObject().apply {
            put("language", lJson)
            put("project", pJson)
            put("resource", rJson)
            put("modified_at", format.modifiedAt)
        }

        // grab the tW assignments
        val legacyUrl = resource._legacyData[LEGACY_WORDS_ASSIGNMENTS_URL] as? String
        if (!legacyUrl.isNullOrEmpty()) {
            val request = GetRequest(URL(legacyUrl))
            var wordsData: String? = null
            try {
                wordsData = request.read()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (wordsData != null && request.responseCode < 300) {
                try {
                    val words = JSONObject(wordsData)
                    val assignmentsJson = JSONObject()
                    val chapters = words.getJSONArray("chapters")
                    for (c in 0 until chapters.length()) {
                        val chapter = chapters.getJSONObject(c)
                        val chapterAssignment = JSONObject()
                        val frames = chapter.getJSONArray("frames")
                        for (f in 0 until frames.length()) {
                            val frame = frames.getJSONObject(f)
                            val frameAssignment = JSONArray()
                            val items = frame.getJSONArray("items")
                            for (w in 0 until items.length()) {
                                val word = items.getJSONObject(w)
                                val twProjSlug = if (projectSlug == "obs") "bible-obs" else "bible"
                                frameAssignment.put("//$twProjSlug/tw/${word.getString("id")}")
                            }
                            chapterAssignment.put(LegacyTools.normalizeSlug(frame.getString("id")), frameAssignment)
                        }
                        assignmentsJson.put(LegacyTools.normalizeSlug(chapter.getString("id")), chapterAssignment)
                    }
                    properties.put("tw_assignments", assignmentsJson)
                } catch (e: Exception) {
                    logListener.onWarning(e.message ?: e.toString())
                }
            }
        }

        return ContainerTools.convertResource(data, containerDir, properties)
    }

    /**
     * Copies a valid resource container into the resource directory and adds an entry to the index.
     * If the container already exists in the system it will be overwritten.
     * Invalid containers will cause this method to return an error.
     * The container *must* be open (uncompressed). This is in preparation for v0.2 of the rc spec.
     * Containers imported in this manner will have a flag set to indicate it was manually imported.
     *
     * @param directory the path to the resource container directory that will be imported
     * @return the imported resource container
     */
    @Throws(Exception::class)
    fun importResourceContainer(directory: File): ResourceContainer {
        val rc = ResourceContainer.load(directory)
        val destination = File(resourceDir, rc.slug)

        // validate project
        // TRICKY: we currently only support importing known projects. Only the language and resource can vary.
        if (library.getProjectMeta(rc.project.slug) == null) throw InvalidRCException("Unsupported project")
        if (!rc.info.has("project")) throw InvalidRCException("Missing field: project")

        // delete the old container
        deleteResourceContainer(rc.slug)

        // copy new container
        FileUtil.copyDirectory(directory, destination, null)

        // add entry to the index
        var indexError: Exception? = null
        library.beginTransaction()
        try {
            val languageId = library.addSourceLanguage(SourceLanguage(rc.language))

            // build categories
            val categories = ArrayList<Category>()
            try {
                if (rc.info.has("project") && rc.info.getJSONObject("project").has("categories")) {
                    val catJson = rc.info.getJSONObject("project").getJSONArray("categories")
                    for (i in 0 until catJson.length()) {
                        val catSlug = catJson.getString(i)
                        // use known name if available
                        val existingCat = library.getCategory(rc.language.slug, catSlug)
                        val catName = existingCat?.name ?: catSlug
                        categories.add(Category(catSlug, catName))
                    }
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }

            val projectId = library.addProject(rc.project, categories, languageId)
            val resource = rc.resource
            resource.addFormat(Resource.Format(rc.info.getString("package_version"), resource.type, rc.modifiedAt, "", true))
            library.addResource(resource, projectId)
        } catch (e: Exception) {
            indexError = e
        }
        library.endTransaction(indexError == null)
        if (indexError != null) throw indexError

        return openResourceContainer(rc.language.slug, rc.project.slug, rc.resource.slug)
    }

    /**
     * Exports the closed resource container
     * @param destFile the destination file
     * @param languageSlug
     * @param projectSlug
     * @param resourceSlug
     */
    @Throws(Exception::class)
    fun exportResourceContainer(destFile: File, languageSlug: String, projectSlug: String, resourceSlug: String) {
        val slug = ContainerTools.makeSlug(languageSlug, projectSlug, resourceSlug)
        val srcDir = File(resourceDir, slug)
        val srcFile = File("$srcDir.${ResourceContainer.fileExtension}")

        // create closed rc
        if (!srcFile.exists() && srcDir.isDirectory) ResourceContainer.close(srcDir)
        if (!srcFile.exists()) throw MissingRCException("The resource container could not be found at $srcFile")

        FileUtil.copyFile(srcFile, destFile)
    }

    /**
     * Opens a resource container archive so it's contents can be read.
     * The index will be referenced to validate the resource and retrieve the container type.
     *
     * @param sourceLanguageSlug
     * @param projectSlug
     * @param resourceSlug
     * @return
     */
    @Throws(Exception::class)
    fun openResourceContainer(sourceLanguageSlug: String, projectSlug: String, resourceSlug: String): ResourceContainer {
        library.getResource(sourceLanguageSlug, projectSlug, resourceSlug)
            ?: throw Exception("Unknown Resource")
        val containerSlug = ContainerTools.makeSlug(sourceLanguageSlug, projectSlug, resourceSlug)
        return openResourceContainer(containerSlug)
    }

    /**
     * Opens a resource container archive so it's contents can be read.
     * This will NOT check with the index to validate the resource container.
     * @param containerSlug
     * @return
     * @throws Exception
     */
    @Throws(Exception::class)
    fun openResourceContainer(containerSlug: String): ResourceContainer {
        val directory = File(resourceDir, containerSlug)
        val archive = File("$directory.${ResourceContainer.fileExtension}")

        // try to load already opened container first
        try {
            if (directory.exists() && directory.isDirectory) {
                return ResourceContainer.load(directory)
            }
        } catch (e: Exception) {
            // ignore and fallback to archive
        }

        // open archive as last resource
        return ResourceContainer.open(archive, directory)
    }

    /**
     * Closes a resource container archive.
     *
     * @param sourceLanguageSlug
     * @param projectSlug
     * @param resourceSlug
     * @return the path to the closed container
     */
    @Throws(Exception::class)
    fun closeResourceContainer(sourceLanguageSlug: String, projectSlug: String, resourceSlug: String): File {
        library.getResource(sourceLanguageSlug, projectSlug, resourceSlug)
            ?: throw Exception("Unknown Resource")
        val containerSlug = ContainerTools.makeSlug(sourceLanguageSlug, projectSlug, resourceSlug)
        val directory = File(resourceDir, containerSlug)
        return ResourceContainer.close(directory)
    }

    /**
     * Checks when a resource container was last modified.
     * @param sourceLanguageSlug
     * @param projectSlug
     * @param resourceSlug
     * @return
     */
    fun getResourceContainerLastModified(sourceLanguageSlug: String, projectSlug: String, resourceSlug: String): Int {
        val resource = library.getResource(sourceLanguageSlug, projectSlug, resourceSlug)
        if (resource != null) {
            val format = getResourceContainerFormat(resource.formats)
            if (format != null) return format.modifiedAt
        }
        return -1
    }

    /**
     * Checks if the resource container has been downloaded
     * @param containerSlug
     * @return
     */
    fun resourceContainerExists(containerSlug: String): Boolean {
        val directory = File(resourceDir, containerSlug)
        val archive = File("$directory.${ResourceContainer.fileExtension}")
        return (directory.exists() && directory.isDirectory) || (archive.exists() && archive.isFile)
    }

    /**
     * Checks if the resource container has been downloaded
     * @param languageSlug
     * @param projectSlug
     * @param resourceSlug
     * @return
     */
    fun resourceContainerExists(languageSlug: String, projectSlug: String, resourceSlug: String): Boolean {
        val containerSlug = ContainerTools.makeSlug(languageSlug, projectSlug, resourceSlug)
        return resourceContainerExists(containerSlug)
    }

    /**
     * Deletes a resource container from the disk
     * @param containerSlug
     */
    fun deleteResourceContainer(containerSlug: String) {
        val directory = File(resourceDir, containerSlug)
        val archive = File("$directory.${ResourceContainer.fileExtension}")
        if (directory.exists() && directory.isDirectory) {
            FileUtil.deleteQuietly(directory)
        }
        if (archive.exists() && archive.isFile) {
            FileUtil.deleteQuietly(archive)
        }
    }

    companion object {
        const val LEGACY_WORDS_ASSIGNMENTS_URL = "words_assignments_url"

        private val defaultLogListener = object : OnLogListener {
            override fun onInfo(message: String) {}
            override fun onWarning(message: String) {}
            override fun onError(message: String, ex: Exception) {}
        }

        @Volatile
        private var sqLiteHelper: SQLiteHelper? = null

        /**
         * Returns the first resource container format found in the list.
         * E.g. the array may contain binary formats such as pdf, mp3, etc. This basically filters those.
         *
         * @param formats a list of resource formats
         * @return
         */
        private fun getResourceContainerFormat(formats: List<Resource.Format>): Resource.Format? {
            val regex = "${ResourceContainer.baseMimeType}\\+.+".toRegex()
            return formats.firstOrNull { it.mimeType.matches(regex) }
        }
    }
}