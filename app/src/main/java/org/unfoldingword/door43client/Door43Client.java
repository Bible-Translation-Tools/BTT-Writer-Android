package org.unfoldingword.door43client;

import android.content.Context;

import com.door43.data.IDirectoryProvider;

import org.unfoldingword.resourcecontainer.ContainerTools;
import org.unfoldingword.resourcecontainer.ResourceContainer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Provides a interface to the Door43 resource api
 */

public class Door43Client {
    private final IDirectoryProvider directoryProvider;

    private final API api;
    private static String schema = null;
    /**
     * The (mostly) read only index
     */
    public final Index index;

    /**
     * Initializes a new Door43 client
     * @param context the application context
     * @param directoryProvider
     * @throws IOException
     */
    public Door43Client(Context context, IDirectoryProvider directoryProvider) throws IOException {
        this.directoryProvider = directoryProvider;

        // load schema
        if(schema == null) {
            InputStream is = context.getAssets().open("schema.sqlite");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            schema = sb.toString();
        }

        this.api = new API(
                context,
                schema,
                directoryProvider.getDatabaseFile(),
                directoryProvider.getContainersDir()
        );
        this.index = api.index();
    }

    /**
     * Attaches a listener to receive log events
     * @param listener
     */
    public void setLogger(OnLogListener listener) {
        api.setLogger(listener);
    }

    /**
     * Checks when an indexed (not downloaded) resource container was last modified.
     * This looks at the modified date in the resource format.
     * The result is the last known modification date of what's available in the api.
     *
     * @param sourceLanguageSlug
     * @param projectSlug
     * @param resourceSlug
     * @return
     */
    public int getResourceContainerLastModified(String sourceLanguageSlug, String projectSlug, String resourceSlug) {
        return api.getResourceContainerLastModified(sourceLanguageSlug, projectSlug, resourceSlug);
    }

    /**
     * Indexes the source content
     *
     * @param url the entry resource api catalog
     * @param listener an optional progress listener. This should receive progress id, total, completed
     */
    public void updateSources(String url, OnProgressListener listener) throws Exception {
        api.updateSources(url, listener);
    }

    /**
     * Indexes the supplementary catalogs
     * @param force
     * @param listener
     * @throws Exception
     */
    public void updateCatalogs(Boolean force, OnProgressListener listener) throws Exception {
        api.updateCatalogs(force, listener);
    }

    public void updateLanguageUrl(String url) throws Exception {
        api.updateLanguageUrl(url);
    }

    /**
     * Indexes the chunk markers
     * @param listener
     * @throws Exception
     */
    public void updateChunks(OnProgressListener listener) throws Exception {
        api.updateChunks(listener);
    }

    /**
     * Downloads a resource container from the api
     * @param sourceLanguageSlug
     * @param projectSlug
     * @param resourceSlug
     * @return
     * @throws Exception
     */
    public ResourceContainer download(String sourceLanguageSlug, String projectSlug, String resourceSlug) throws Exception {
        return api.downloadResourceContainer(sourceLanguageSlug, projectSlug, resourceSlug);
    }

    /**
     * Opens a resource container archive so it's contents can be read.
     * @param languageSlug
     * @param projectSlug
     * @param resourceSlug
     * @return
     * @throws Exception
     */
    public ResourceContainer open(String languageSlug, String projectSlug, String resourceSlug) throws Exception {
        return api.openResourceContainer(languageSlug, projectSlug, resourceSlug);
    }

    /**
     * Opens a resource container archive so it's contents can be read.
     * @param containerSlug
     * @return
     */
    public ResourceContainer open(String containerSlug) throws Exception {
        return api.openResourceContainer(containerSlug);
    }

    /**
     * Imports an external resource container into the client and indexes it for use.
     * @param directory the directory of the resource container to be imported
     * @return the imported resource container
     * @throws Exception
     */
    public ResourceContainer importResourceContainer(File directory) throws Exception {
        return api.importResourceContainer(directory);
    }

    /**
     * Exports the closed resource container
     * @param destFile the destination file
     * @param languageSlug
     * @param projectSlug
     * @param resourceSlug
     */
    public void exportResourceContainer(File destFile, String languageSlug, String projectSlug, String resourceSlug) throws Exception {
        api.exportResourceContainer(destFile, languageSlug, projectSlug, resourceSlug);
    }

    public Boolean getIsLibraryDeployed() {
        Boolean hasContainers = directoryProvider.getContainersDir().exists()
                && directoryProvider.getContainersDir().isDirectory()
                && directoryProvider.getContainersDir().list().length > 0;

        return index.getSourceLanguages().size() > 1 && hasContainers;
    }

    /**
     * Checks if a resource container has been downloaded
     * @param languageSlug
     * @param projectSlug
     * @param resourceSlug
     * @return
     */
    public boolean exists(String languageSlug, String projectSlug, String resourceSlug) {
        return api.resourceContainerExists(languageSlug, projectSlug, resourceSlug);
    }

    /**
     * Checks if a resource container has been downloaded
     * @param containerSlug
     * @return
     */
    public boolean exists(String containerSlug) {
        return api.resourceContainerExists(containerSlug);
    }

    /**
     * Deletes a resource container
     * @param languageSlug
     * @param projectSlug
     * @param resourceSlug
     */
    public void delete(String languageSlug, String projectSlug, String resourceSlug) {
        delete(ContainerTools.makeSlug(languageSlug, projectSlug, resourceSlug));
    }

    /**
     * Deletes a resource container
     * @param containerSlug
     */
    public void delete(String containerSlug) {
        api.deleteResourceContainer(containerSlug);
    }

    /**
     * Closes a resource container directory
     * @param sourceLanguageSlug
     * @param projectSlug
     * @param resourceSlug
     * @throws Exception
     */
    public void close(String sourceLanguageSlug, String projectSlug, String resourceSlug) throws Exception {
        api.closeResourceContainer(sourceLanguageSlug, projectSlug, resourceSlug);
    }

    /**
     * Closes the api
     */
    public void tearDown() {
        api.tearDown();
    }
}
