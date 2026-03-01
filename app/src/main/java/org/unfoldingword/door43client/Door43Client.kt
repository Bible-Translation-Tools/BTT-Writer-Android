package org.unfoldingword.door43client

import android.content.Context
import com.door43.data.IDirectoryProvider
import org.unfoldingword.resourcecontainer.ContainerTools
import org.unfoldingword.resourcecontainer.ResourceContainer
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader

/**
 * Provides an interface to the Door43 resource API
 */
class Door43Client @Throws(IOException::class) constructor(
    context: Context,
    private val directoryProvider: IDirectoryProvider
) {
    private val api: API

    /**
     * The (mostly) read-only index
     */
    val index: Index

    companion object {
        private var schema: String? = null
    }

    init {
        if (schema == null) {
            schema = context.assets.open("schema.sqlite").use { stream ->
                BufferedReader(InputStreamReader(stream)).readText()
            }
        }

        api = API(
            context,
            schema!!,
            directoryProvider.databaseFile,
            directoryProvider.containersDir
        )
        index = api.index
    }

    /**
     * Attaches a listener to receive log events.
     */
    fun setLogger(listener: OnLogListener) {
        api.setLogger(listener)
    }

    /**
     * Checks when an indexed (not downloaded) resource container was last modified.
     * This looks at the modified date in the resource format.
     * The result is the last known modification date of what's available in the API.
     */
    fun getResourceContainerLastModified(sourceLanguageSlug: String, projectSlug: String, resourceSlug: String): Int =
        api.getResourceContainerLastModified(sourceLanguageSlug, projectSlug, resourceSlug)

    /**
     * Indexes the source content.
     *
     * @param url      the entry resource API catalog
     * @param listener an optional progress listener (receives progress id, total, completed)
     */
    @Throws(Exception::class)
    fun updateSources(url: String, listener: OnProgressListener?) {
        api.updateSources(url, listener)
    }

    /**
     * Indexes the supplementary catalogs.
     */
    @Throws(Exception::class)
    fun updateCatalogs(force: Boolean, listener: OnProgressListener?) {
        api.updateCatalogs(force, listener)
    }

    @Throws(Exception::class)
    fun updateLanguageUrl(url: String) {
        api.updateLanguageUrl(url)
    }

    /**
     * Indexes the chunk markers.
     */
    @Throws(Exception::class)
    fun updateChunks(listener: OnProgressListener?) {
        api.updateChunks(listener)
    }

    /**
     * Downloads a resource container from the API.
     */
    @Throws(Exception::class)
    fun download(sourceLanguageSlug: String, projectSlug: String, resourceSlug: String): ResourceContainer =
        api.downloadResourceContainer(sourceLanguageSlug, projectSlug, resourceSlug)

    /**
     * Opens a resource container archive so its contents can be read.
     */
    @Throws(Exception::class)
    fun open(languageSlug: String, projectSlug: String, resourceSlug: String): ResourceContainer =
        api.openResourceContainer(languageSlug, projectSlug, resourceSlug)

    /**
     * Opens a resource container archive so its contents can be read.
     */
    @Throws(Exception::class)
    fun open(containerSlug: String): ResourceContainer =
        api.openResourceContainer(containerSlug)

    /**
     * Imports an external resource container into the client and indexes it for use.
     *
     * @param directory the directory of the resource container to be imported
     * @return the imported resource container
     */
    @Throws(Exception::class)
    fun importResourceContainer(directory: File): ResourceContainer =
        api.importResourceContainer(directory)

    /**
     * Exports the closed resource container.
     *
     * @param destFile the destination file
     */
    @Throws(Exception::class)
    fun exportResourceContainer(destFile: File, languageSlug: String, projectSlug: String, resourceSlug: String) {
        api.exportResourceContainer(destFile, languageSlug, projectSlug, resourceSlug)
    }

    val isLibraryDeployed: Boolean
        get() {
            val containersDir = directoryProvider.containersDir
            val hasContainers = containersDir.exists()
                    && containersDir.isDirectory
                    && (containersDir.list()?.isNotEmpty() == true)
            return index.getSourceLanguages().size > 1 && hasContainers
        }

    /**
     * Checks if a resource container has been downloaded.
     */
    fun exists(languageSlug: String, projectSlug: String, resourceSlug: String): Boolean =
        api.resourceContainerExists(languageSlug, projectSlug, resourceSlug)

    /**
     * Checks if a resource container has been downloaded.
     */
    fun exists(containerSlug: String): Boolean =
        api.resourceContainerExists(containerSlug)

    /**
     * Deletes a resource container.
     */
    fun delete(languageSlug: String, projectSlug: String, resourceSlug: String) {
        delete(ContainerTools.makeSlug(languageSlug, projectSlug, resourceSlug))
    }

    /**
     * Deletes a resource container.
     */
    fun delete(containerSlug: String) {
        api.deleteResourceContainer(containerSlug)
    }

    /**
     * Closes a resource container directory.
     */
    @Throws(Exception::class)
    fun close(sourceLanguageSlug: String, projectSlug: String, resourceSlug: String) {
        api.closeResourceContainer(sourceLanguageSlug, projectSlug, resourceSlug)
    }

    /**
     * Closes the API.
     */
    fun tearDown() {
        api.tearDown()
    }
}