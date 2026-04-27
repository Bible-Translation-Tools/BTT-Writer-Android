package org.unfoldingword.door43client

import android.content.Context
import com.door43.translationstudio.network.GetRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bibletranslationtools.resourcecontainer.ContainerTools
import org.bibletranslationtools.resourcecontainer.PackageInfo
import org.bibletranslationtools.resourcecontainer.Resource
import org.bibletranslationtools.resourcecontainer.ResourceContainer
import org.bibletranslationtools.resourcecontainer.errors.InvalidRCException
import org.bibletranslationtools.resourcecontainer.errors.MissingRCException
import org.bibletranslationtools.resourcecontainer.json
import org.unfoldingword.door43client.models.Catalog
import org.unfoldingword.door43client.models.Category
import org.unfoldingword.door43client.models.SourceLanguage
import org.unfoldingword.door43client.models.TargetLanguage
import org.unfoldingword.door43client.models.toLanguage
import java.io.File
import java.io.IOException

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

    // Single-threaded dispatcher for ALL database operations.
    // This guarantees serial access to the SQLite connection pool and prevents
    // connection starvation across concurrent coroutines.
    private val dbDispatcher = Dispatchers.IO.limitedParallelism(1)

    // Separate dispatcher for network I/O so downloads never hold a DB transaction open.
    private val networkDispatcher = Dispatchers.IO

    init {
        val nameParts = databasePath.name.split("\\.".toRegex()).toTypedArray()
        val dbExt = nameParts[nameParts.size - 1]
        val databaseContext = DatabaseContext(context, databasePath.parentFile!!, dbExt)
        val dbName = databasePath.name.replaceFirst("\\.[^.]+$".toRegex(), "")

        synchronized(Companion) {
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
        synchronized(Companion) {
            sqLiteHelper?.let {
                it.close()
                sqLiteHelper = null
            }
        }
    }

    /**
     * Attaches a listener to receive log events
     */
    fun setLogger(listener: OnLogListener?) {
        this.logListener = listener ?: defaultLogListener
    }

    /**
     * Sets the host to use when injecting the global catalogs.
     * This is only valid until we migrate to the use api.
     *
     * This is also only currently used for tests
     */
    @Deprecated("This is only valid until we migrate to the use api.")
    fun setGlobalCatalogServer(host: String?) {
        this.globalCatalogHost = host
    }

    /**
     * Returns the read only index
     */
    val index: Index
        get() = library

    /**
     * Indexes the source content.
     *
     * @param url the entry resource api catalog
     * @param onProgress an optional progress listener
     */
    @Throws(Exception::class)
    suspend fun updateSources(url: String, onProgress: (Float, String?) -> Unit) {
        // Download catalog data
        val projects = withContext(networkDispatcher) {
            val data  = GetRequest(url).read()
            LegacyTools.downloadCatalog(Json.decodeFromString(data), onProgress)
        }
        // Index everything in one transaction
        withTransaction {
            LegacyTools.indexCatalog(library, projects)
        }
    }

    /**
     * Indexes the chunk markers.
     *
     * Network downloads are performed first, then all DB writes happen inside
     * a single short-lived transaction.
     */
    @Throws(Exception::class)
    suspend fun updateChunks(onProgress: (Float, String?) -> Unit) {
        // Collect chunk URLs and versification
        val (markers, versificationRowId) = withContext(dbDispatcher) {
            val result = mutableMapOf<String, String>()
            for (l in library.getSourceLanguages()) {
                for (p in library.getProjects(l.slug)) {
                    if (p.chunksUrl.isNotEmpty()) {
                        result[p.slug] = p.chunksUrl
                    }
                }
            }
            val v = library.getVersification("en", "en-US")
            result to v?.rowId
        }

        if (versificationRowId == null) {
            println("Unknown versification while downloading chunks")
            return
        }

        // Download ALL chunk data
        val downloadedChunks = withContext(networkDispatcher) {
            LegacyTools.downloadAllChunks(markers, onProgress)
        }

        // Write everything in one fast transaction
        withTransaction {
            LegacyTools.insertAllChunks(library, downloadedChunks, versificationRowId)
        }
    }

    /**
     * Updates all the global catalogs.
     *
     * @param force Should we update/insert catalogs
     * @param onProgress Progress Listener
     */
    @Throws(Exception::class)
    suspend fun updateCatalogs(force: Boolean, onProgress: (Float, String?) -> Unit) {
        if (force) {
            withContext(dbDispatcher) {
                LegacyTools.injectGlobalCatalogs(library, globalCatalogHost)
            }
        }
        val catalogs = withContext(dbDispatcher) {
            library.getCatalogs()
        }
        for (c in catalogs) {
            updateCatalog(c, onProgress)
        }
    }

    /**
     * Utility for testing
     */
    @Throws(Exception::class)
    suspend fun updateCatalog(slug: String) {
        withContext(dbDispatcher) {
            LegacyTools.injectGlobalCatalogs(library, globalCatalogHost)
        }
        val c = withContext(dbDispatcher) {
            library.getCatalog(slug)
        }
        c?.let { updateCatalog(it) }
    }

    fun updateLanguageUrl(url: String) {
        LegacyTools.setLangNamesUrl(url)
    }

    /**
     * Downloads a global catalog and indexes it.
     *
     * Pattern: download first (network), then index (DB transaction).
     */
    @Throws(Exception::class)
    private suspend fun updateCatalog(
        catalog: Catalog,
        onProgress: ((Float, String?) -> Unit) = {_,_ ->}
    ) {
        // Download catalog
        val data = withContext(networkDispatcher) {
            GetRequest(catalog.url).read()
        }

        // Index inside a short-lived transaction
        withTransaction {
            when (catalog.slug) {
                "langnames" -> {
                    library.clearTargetLanguages()
                    indexTargetLanguageCatalog(data, onProgress)
                }
                else -> throw Exception("Parsing this catalog has not been implemented")
            }
        }
    }

    @Throws(Exception::class)
    private fun indexTargetLanguageCatalog(
        data: String,
        onProgress: (Float, String?) -> Unit
    ) {
        val languages = json.decodeFromString<List<CatalogLanguage>>(data)
        languages.forEachIndexed { index, language ->
            if (!library.addTargetLanguage(language.toTargetLanguage())) {
                logListener.onWarning("Failed to add the target language: " + language.slug)
            }

            onProgress((index + 1) / languages.size.toFloat(), "langnames")

            library.yieldSafely()
        }
    }

    /**
     * Downloads a resource container.
     *
     * TRICKY: to keep the interface stable we've abstracted some things.
     * Once the api supports real resource containers this entire method can go away.
     */
    @Throws(Exception::class)
    suspend fun downloadResourceContainer(
        sourceLanguageSlug: String,
        projectSlug: String,
        resourceSlug: String
    ): ResourceContainer {
        withContext(dbDispatcher) {
            library.getResource(sourceLanguageSlug, projectSlug, resourceSlug)
        } ?: run {
            throw Exception("Unknown resource: ${sourceLanguageSlug}_${projectSlug}_$resourceSlug")
        }

        // Read resource metadata
        val (containerFormat, containerSlug) = withContext(dbDispatcher) {
            val r = library.getResource(sourceLanguageSlug, projectSlug, resourceSlug)
                ?: throw Exception("Unknown resource")
            val format = getResourceContainerFormat(r.formats)
                ?: throw Exception("Missing resource container format")
            val slug = ContainerTools.makeSlug(sourceLanguageSlug, projectSlug, resourceSlug)
            format to slug
        }

        val containerDir = File(resourceDir, containerSlug)
        val destFile = File(resourceDir, "$containerSlug.${ResourceContainer.FILE_EXTENSION}")

        FileUtil.deleteQuietly(destFile)
        FileUtil.deleteQuietly(containerDir)
        destFile.parentFile?.mkdirs()

        val url = containerFormat.url
        if (url.isEmpty()) throw Exception("Missing resource format url")

        // Download
        withContext(networkDispatcher) {
            val request = GetRequest(url)
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
        }

        val data = FileUtil.readFileToString(destFile)
        FileUtil.deleteQuietly(destFile)

        return convertLegacyResource(sourceLanguageSlug, projectSlug, resourceSlug, data)
    }

    @Throws(Exception::class)
    suspend fun convertLegacyResource(
        sourceLanguageSlug: String,
        projectSlug: String,
        resourceSlug: String,
        data: String
    ): ResourceContainer {
        val containerSlug = ContainerTools.makeSlug(sourceLanguageSlug, projectSlug, resourceSlug)
        val containerDir = File(resourceDir, containerSlug)

        // Gather all metadata from DB
        val (packageInfo, legacyUrl) = withContext(dbDispatcher) {
            val language = library.getSourceLanguage(sourceLanguageSlug)
                ?: throw Exception("Missing language")
            val project = library.getProject(sourceLanguageSlug, projectSlug)
                ?: throw Exception("Missing project")
            val categories = library.getCategories(sourceLanguageSlug, projectSlug)
            val resource = library.getResource(sourceLanguageSlug, projectSlug, resourceSlug)
                ?: throw Exception("Missing resource")
            val format = getResourceContainerFormat(resource.formats)
                ?: throw Exception("Missing resource container format")

            val mimeType = if (project.slug != "obs" && resource.type == "book") {
               "text/usx"
            } else "text/markdown"

            val packageInfo = PackageInfo(
                packageVersion = ResourceContainer.VERSION,
                modifiedAt = format.modifiedAt,
                contentMimeType = mimeType,
                language = language.toLanguage(),
                project = project.copy(categories = categories.map { it.slug }),
                resource = resource
            )

            val url = resource.legacyData[LEGACY_WORDS_ASSIGNMENTS_URL] as? String
            packageInfo to url
        }

        val content = ContainerTools.decodeContent(
            data,
            packageInfo.resource.type,
            packageInfo.resource.slug
        )

        // Download tW assignments
        val wordAssignments = try {
            if (!legacyUrl.isNullOrEmpty()) {
                withContext(networkDispatcher) {
                    try {
                        val request = GetRequest(legacyUrl)
                        val result = request.read()
                        if (request.responseCode < 300) result else null
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                }?.let { wordsData ->
                    ContainerTools.decodeWordAssignments(wordsData)
                }
            } else null
        } catch (e: Exception) {
            logListener.onWarning(e.message ?: e.toString())
            null
        }

        return ContainerTools.convertResource(
            content,
            packageInfo,
            wordAssignments,
            containerDir
        )
    }

    /**
     * Copies a valid resource container into the resource directory and adds an entry to the index.
     * If the container already exists in the system it will be overwritten.
     * Invalid containers will cause this method to return an error.
     * The container *must* be open (uncompressed).
     * Containers imported in this manner will have a flag set to indicate it was manually imported.
     */
    @Throws(Exception::class)
    suspend fun importResourceContainer(directory: File): ResourceContainer {
        val rc = ResourceContainer.load(directory)
        val destination = File(resourceDir, rc.slug)

        // Validate project
        withContext(dbDispatcher) {
            if (library.getProjectMeta(rc.project.slug) == null) {
                throw InvalidRCException("Unsupported project")
            }
        }

        deleteResourceContainer(rc.slug)
        FileUtil.copyDirectory(directory, destination, null)

        // Index the container
        withTransaction {
            val languageId = library.addSourceLanguage(SourceLanguage(rc.language))

            val categories = ArrayList<Category>()
            try {
                rc.info.project.categories.forEach { category ->
                    val existingCat = library.getCategory(rc.language.slug, category)
                    val catName = existingCat?.name ?: category
                    categories.add(Category(category, catName))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val projectId = library.addProject(rc.project, categories, languageId)
            val resource = rc.resource
            resource.addFormat(
                Resource.Format(
                    packageVersion = rc.info.packageVersion,
                    mimeType = resource.type,
                    modifiedAt = rc.modifiedAt,
                    url = "",
                    imported = true
                )
            )
            library.addResource(resource, projectId)
        }

        return openResourceContainer(
            rc.language.slug,
            rc.project.slug,
            rc.resource.slug
        )
    }

    /**
     * Exports the closed resource container.
     */
    @Throws(Exception::class)
    fun exportResourceContainer(
        destFile: File,
        languageSlug: String,
        projectSlug: String,
        resourceSlug: String
    ) {
        val slug = ContainerTools.makeSlug(languageSlug, projectSlug, resourceSlug)
        val srcDir = File(resourceDir, slug)
        val srcFile = File("$srcDir.${ResourceContainer.FILE_EXTENSION}")

        if (!srcFile.exists() && srcDir.isDirectory) ResourceContainer.close(srcDir)
        if (!srcFile.exists()) throw MissingRCException("The resource container could not be found at $srcFile")

        FileUtil.copyFile(srcFile, destFile)
    }

    /**
     * Opens a resource container archive so its contents can be read.
     * The index will be referenced to validate the resource.
     */
    @Throws(Exception::class)
    fun openResourceContainer(
        sourceLanguageSlug: String,
        projectSlug: String,
        resourceSlug: String
    ): ResourceContainer {
        library.getResource(sourceLanguageSlug, projectSlug, resourceSlug)
            ?: throw Exception("Unknown Resource")
        val containerSlug = ContainerTools.makeSlug(sourceLanguageSlug, projectSlug, resourceSlug)
        return openResourceContainer(containerSlug)
    }

    /**
     * Opens a resource container archive so its contents can be read.
     * This will NOT check with the index to validate the resource container.
     */
    @Throws(Exception::class)
    fun openResourceContainer(containerSlug: String): ResourceContainer {
        val directory = File(resourceDir, containerSlug)
        val archive = File("$directory.${ResourceContainer.FILE_EXTENSION}")

        // try to load already-opened container first
        try {
            if (directory.exists() && directory.isDirectory) {
                return ResourceContainer.load(directory)
            }
        } catch (_: Exception) {
            // ignore and fallback to archive
        }

        return ResourceContainer.open(archive, directory)
    }

    /**
     * Closes a resource container archive.
     */
    @Throws(Exception::class)
    fun closeResourceContainer(
        sourceLanguageSlug: String,
        projectSlug: String,
        resourceSlug: String
    ): File {
        library.getResource(sourceLanguageSlug, projectSlug, resourceSlug)
            ?: throw Exception("Unknown Resource")
        val containerSlug = ContainerTools.makeSlug(sourceLanguageSlug, projectSlug, resourceSlug)
        val directory = File(resourceDir, containerSlug)
        return ResourceContainer.close(directory)
    }

    /**
     * Checks when a resource container was last modified.
     */
    fun getResourceContainerLastModified(
        sourceLanguageSlug: String,
        projectSlug: String,
        resourceSlug: String
    ): Int {
        val resource = library.getResource(sourceLanguageSlug, projectSlug, resourceSlug)
        if (resource != null) {
            val format = getResourceContainerFormat(resource.formats)
            if (format != null) return format.modifiedAt
        }
        return -1
    }

    /**
     * Checks if the resource container has been downloaded.
     */
    fun resourceContainerExists(containerSlug: String): Boolean {
        val directory = File(resourceDir, containerSlug)
        val archive = File("$directory.${ResourceContainer.FILE_EXTENSION}")
        return (directory.exists() && directory.isDirectory) || (archive.exists() && archive.isFile)
    }

    fun resourceContainerExists(
        languageSlug: String,
        projectSlug: String,
        resourceSlug: String
    ): Boolean {
        val containerSlug = ContainerTools.makeSlug(languageSlug, projectSlug, resourceSlug)
        return resourceContainerExists(containerSlug)
    }

    /**
     * Deletes a resource container from the disk.
     */
    fun deleteResourceContainer(containerSlug: String) {
        val directory = File(resourceDir, containerSlug)
        val archive = File("$directory.${ResourceContainer.FILE_EXTENSION}")
        if (directory.exists() && directory.isDirectory) {
            FileUtil.deleteQuietly(directory)
        }
        if (archive.exists() && archive.isFile) {
            FileUtil.deleteQuietly(archive)
        }
    }

    /**
     * Runs [block] inside a database transaction on [dbDispatcher].
     * The transaction is committed on success and rolled back on any exception.
     * This ensures:
     *   1. All DB work is serialized on a single thread (no pool contention).
     *   2. The transaction is ALWAYS closed, even on unexpected errors.
     *   3. Network I/O is never accidentally performed inside a transaction.
     */
    private suspend fun <T> withTransaction(block: suspend () -> T): T {
        return withContext(dbDispatcher) {
            library.beginTransaction()
            try {
                val result = block()
                library.endTransaction(true)
                result
            } catch (e: Exception) {
                library.endTransaction(false)
                throw e
            }
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
         */
        private fun getResourceContainerFormat(formats: List<Resource.Format>): Resource.Format? {
            val regex = "${ResourceContainer.BASE_MIME_TYPE}\\+.+".toRegex()
            return formats.firstOrNull { it.mimeType.matches(regex) }
        }
    }
}

@Serializable
private data class CatalogLanguage(
    @SerialName("lc")
    val slug: String,
    @SerialName("ln")
    val name: String,
    @SerialName("ld")
    val direction: String,
    @SerialName("ang")
    val anglicized: String,
    @SerialName("lr")
    val region: String,
    @SerialName("gl")
    val isGateway: Boolean = false
)

private fun CatalogLanguage.toTargetLanguage() = TargetLanguage(
    slug = slug,
    name = name,
    direction = direction,
    anglicizedName = anglicized,
    region = region,
    isGatewayLanguage = isGateway
)