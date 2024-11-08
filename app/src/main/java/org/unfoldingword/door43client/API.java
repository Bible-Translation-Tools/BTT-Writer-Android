package org.unfoldingword.door43client;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.unfoldingword.door43client.models.Catalog;
import org.unfoldingword.door43client.models.Category;
import org.unfoldingword.door43client.models.Question;
import org.unfoldingword.door43client.models.Questionnaire;
import org.unfoldingword.door43client.models.SourceLanguage;
import org.unfoldingword.door43client.models.TargetLanguage;
import org.unfoldingword.resourcecontainer.ContainerTools;
import org.unfoldingword.resourcecontainer.Project;
import org.unfoldingword.resourcecontainer.Resource;
import org.unfoldingword.resourcecontainer.ResourceContainer;
import org.unfoldingword.resourcecontainer.errors.InvalidRCException;
import org.unfoldingword.resourcecontainer.errors.MissingRCException;
import org.unfoldingword.resourcecontainer.errors.RCException;
import org.unfoldingword.tools.http.GetRequest;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by joel on 8/30/16.
 */
class API {
    public static final String LEGACY_WORDS_ASSIGNMENTS_URL = "words_assignments_url";
    private static final OnLogListener defaultLogListener;
    private static SQLiteHelper sqLiteHelper = null;

    static {
        defaultLogListener = new OnLogListener() {
            @Override
            public void onInfo(String message) {
            }

            @Override
            public void onWarning(String message) {
            }

            @Override
            public void onError(String message, Exception ex) {
            }
        };
    }

    private final File resourceDir;
    private final Library library;
    private String globalCatalogHost = null;
    private OnLogListener logListener = defaultLogListener;

    /**
     * Initializes the new api client
     * @param context the application context
     * @param schema the database schema for the index
     * @param databasePath the name of the database where information will be indexed
     * @param resourceDir the directory where resource containers will be stored
     */
    public API(Context context, String schema, File databasePath, File resourceDir) throws IOException {
        this.resourceDir = resourceDir;
        String[] nameParts = databasePath.getName().split("\\.");
        String dbExt = nameParts[nameParts.length - 1];
        DatabaseContext databaseContext = new DatabaseContext(context, databasePath.getParentFile(), dbExt);
        String dbName = databasePath.getName().replaceFirst("\\.[^\\.]+$", "");
        synchronized (this) {
            if (sqLiteHelper == null) {
                sqLiteHelper = new SQLiteHelper(databaseContext, schema, dbName);
            }
        }
        this.library = new Library(sqLiteHelper);
    }

    /**
     * Performs closing operations.
     * e.g. closing the db, etc.
     */
    public void tearDown() {
        if(sqLiteHelper != null) {
            sqLiteHelper.close();
            sqLiteHelper = null;
        }
    }

    /**
     * Attaches a listener to receive log events
     * @param listener
     */
    public void setLogger(OnLogListener listener) {
        if(listener == null) {
            this.logListener = defaultLogListener;
        } else {
            this.logListener = listener;
        }
    }

    /**
     * Sets the host to use when injecting the global catalogs.
     * This is only valid until we migrate to the use api.
     *
     * This is also only currently used for tests
     * @param host
     */
    @Deprecated
    public void setGlobalCatalogServer(String host) {
        this.globalCatalogHost = host;
    }

    /**
     * Returns the read only index
     * @return
     */
    public Index index() {
        return library;
    }

    /**
     * Indexes the source content
     *
     * @param url the entry resource api catalog
     * @param listener an optional progress listener. This should receive progress id, total, completed
     */
    public void updateSources(String url, final OnProgressListener listener) throws Exception {
        library.beginTransaction();
        try {
            GetRequest getPrimaryCatalog = new GetRequest(new URL(url));
            String data = getPrimaryCatalog.read();
            // process legacy catalog data
            LegacyTools.processCatalog(library, data, listener);
        } catch(Exception e) {
            library.endTransaction(false);
            throw e;
        }
        library.endTransaction(true);
    }

    /**
     * Indexes the chunk markers
     * @param listener
     * @throws Exception
     */
    public void updateChunks(OnProgressListener listener) throws Exception {
        library.beginTransaction();
        try {
            LegacyTools.processChunks(library, listener);
        } catch (Exception e) {
            library.endTransaction(false);
            throw e;
        }
        library.endTransaction(true);
    }

    /**
     * Updates all the global catalogs
     * @param force Should we update/insert catalogs
     * @param listener Progress Listener
     * @throws Exception Any exception
     */
    public void updateCatalogs(Boolean force, OnProgressListener listener) throws Exception {
        if (force) {
            // inject missing global catalogs
            LegacyTools.injectGlobalCatalogs(library, globalCatalogHost);
        }
        List<Catalog> catalogs = library.getCatalogs();
        for(Catalog c:catalogs) {
            updateCatalog(c, listener);
        }
    }

    /**
     * Utility for testing
     *
     * @param slug
     * @throws Exception
     */
    public void updateCatalog(String slug) throws Exception{
        LegacyTools.injectGlobalCatalogs(library, globalCatalogHost);
        Catalog c = library.getCatalog(slug);
        updateCatalog(c, null);
    }

    public void updateLanguageUrl(String url) {
        LegacyTools.setLangNamesUrl(url);
    }

    /**
     * Downloads a global catalog and indexes it.
     *
     * @param catalog the catalog being updated
     * @param listener an optional progress listener. This should receive progress id, total, completed
     */
    private void updateCatalog(Catalog catalog, OnProgressListener listener) throws Exception {
        if(catalog == null) throw new Exception("Unknown catalog");
        GetRequest request = new GetRequest(new URL(catalog.url));
        String data = request.read();
        library.beginTransaction();
        try {
            switch (catalog.slug) {
                case "langnames":
                    library.clearTargetLanguages();
                    indexTargetLanguageCatalog(data, listener);
                    break;
                case "new-language-questions":
                    library.clearNewLanguageQuestions();
                    indexNewLanguageQuestionsCatalog(data, listener);
                    break;
                case "temp-langnames":
                    library.clearTempLanguages();
                    indexTempLanguagesCatalog(data, listener);
                    break;
                case "approved-temp-langnames":
                    library.clearApprovedTempLanguages();
                    indexApprovedTempLanguagesCatalog(data, listener);
                    break;
                default:
                    throw new Exception("Parsing this catalog has not been implemented");
            }
        } catch (Exception e) {
            library.endTransaction(false);
            throw e;
        }
        library.endTransaction(true);
    }

    /**
     * parses the target language catalog and indexes it
     * @param data
     * @param listener
     */
    private void indexTargetLanguageCatalog(String data, OnProgressListener listener) throws Exception {
        JSONArray languages = new JSONArray(data);
        for(int i = 0; i < languages.length(); i ++) {
            JSONObject l = languages.getJSONObject(i);
            boolean isGateway = l.has("gl") ? l.getBoolean("gl") : false;
            TargetLanguage language = new TargetLanguage(l.getString("lc"), l.getString("ln"),
                    l.getString("ang"), l.getString("ld"), l.getString("lr"), isGateway);
            if(!library.addTargetLanguage(language)) {
                logListener.onWarning("Failed to add the target language: " + language.slug);
            }
            if(listener != null) {
                if(!listener.onProgress("langnames", languages.length(), i + 1)) break;
            }
            library.yieldSafely();
        }
    }

    /**
     * Parses the new language questions catalog and indexes it
     * @param data
     * @param listener
     */
    private void indexNewLanguageQuestionsCatalog(String data, OnProgressListener listener) throws Exception {
        JSONObject obj = new JSONObject(data);
        JSONArray languages = obj.getJSONArray("languages");
        for(int i = 0; i < languages.length(); i ++) {
            JSONObject qJson = languages.getJSONObject(i);
            Map<String, Long> dataFields = new HashMap<>();
            if(qJson.has("language_data")) {
                JSONObject dataFieldJson = qJson.getJSONObject("language_data");
                Iterator<String> keyIter = dataFieldJson.keys();
                while (keyIter.hasNext()) {
                    String key = keyIter.next();
                    dataFields.put(key, dataFieldJson.getLong(key));
                }
            }
            Questionnaire questionnaire = new Questionnaire(qJson.getString("slug"),
                    qJson.getString("name"),
                    qJson.getString("dir"),
                    qJson.getLong("questionnaire_id"),
                    dataFields);
            long questionnaireId = library.addQuestionnaire(questionnaire);

            // add questions
            for(int j = 0; j < qJson.getJSONArray("questions").length(); j ++) {
                JSONObject questionJson = qJson.getJSONArray("questions").getJSONObject(j);
                long dependsOnId = questionJson.isNull("depends_on") ? -1 : questionJson.getLong("depends_on");
                Question question = new Question(questionJson.getString("text"),
                        questionJson.getString("help"),
                        questionJson.getBoolean("required"),
                        Question.InputType.get(questionJson.getString("input_type")),
                        questionJson.getInt("sort"),
                        dependsOnId, questionJson.getLong("id"));
                library.addQuestion(question, questionnaireId);

                // broadcast itemized progress if there is only one questionnaire
                if(languages.length() == 1 && listener != null) {
                    if(!listener.onProgress("new-language-questions", qJson.getJSONArray("questions").length(), j + 1)) break;
                }
                library.yieldSafely();
            }
            // broadcast overall progress if there are multiple questionnaires.
            if(languages.length() > 1 && listener != null) {
                if(!listener.onProgress("new-language-questions", qJson.getJSONArray("questions").length(), i + 1)) break;
            }
            library.yieldSafely();
        }
    }

    /**
     * Parses the temporary language codes catalog and indexes it
     * @param data
     * @param listener
     */
    private void indexTempLanguagesCatalog(String data, OnProgressListener listener) throws Exception {
        JSONArray languages = new JSONArray(data);
        for(int i = 0; i < languages.length(); i ++) {
            JSONObject l = languages.getJSONObject(i);
            boolean isGateway = l.has("gl") ? l.getBoolean("gl") : false;
            TargetLanguage language = new TargetLanguage(l.getString("lc"), l.getString("ln"),
                    l.getString("ang"), l.getString("ld"), l.getString("lr"), isGateway);
            if(!library.addTempTargetLanguage(language)) {
                logListener.onWarning("Failed to add the temp target language: " + language.slug);
            }
            if(listener != null) {
                if(!listener.onProgress("temp-langnames", languages.length(), i + 1)) break;
            }
            library.yieldSafely();
        }
    }

    /**
     * Parses the approved temporary language codes catalog and indexes it
     * @param data
     * @param listener
     */
    private void indexApprovedTempLanguagesCatalog(String data, OnProgressListener listener) throws Exception {
        JSONArray languages = new JSONArray(data);
        for(int i = 0; i < languages.length(); i ++) {
            JSONObject l = languages.getJSONObject(i);
            Iterator<String> keys = l.keys();
            while(keys.hasNext()) {
                String key = keys.next();
                if(!library.setApprovedTargetLanguage(key, l.getString(key))) {
                    logListener.onWarning("Failed to approve the temp target language: " + key + " as " + l.getString(key));
                }
            }
            if(listener != null) {
                if(!listener.onProgress("approved-temp-langnames", languages.length(), i + 1)) break;
            }
            library.yieldSafely();
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
    public ResourceContainer downloadResourceContainer(String sourceLanguageSlug, String projectSlug, String resourceSlug) throws Exception {
        File path = downloadFutureCompatibleResourceContainer(sourceLanguageSlug, projectSlug, resourceSlug);

        // migrate to resource container
        Resource r = library.getResource(sourceLanguageSlug, projectSlug, resourceSlug);
        if(r == null) {
            FileUtil.deleteQuietly(path);
            throw new Exception("Unknown resource");
        }
        String data = FileUtil.readFileToString(path);

        // clean downloaded file
        FileUtil.deleteQuietly(path);
        return convertLegacyResource(sourceLanguageSlug, projectSlug, resourceSlug, data);
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
    public File downloadFutureCompatibleResourceContainer(String sourceLanguageSlug, String projectSlug, String resourceSlug) throws Exception {
        Resource r = library.getResource(sourceLanguageSlug, projectSlug, resourceSlug);
        if(r == null) throw new Exception("Unknown resource");
        Resource.Format containerFormat = getResourceContainerFormat(r.formats);
        if(containerFormat == null) throw new Exception("Missing resource container format");
        String containerSlug = ContainerTools.makeSlug(sourceLanguageSlug, projectSlug, resourceSlug);
        File containerDir = new File(resourceDir, containerSlug);
        File destFile = new File(resourceDir, containerSlug + "." + ResourceContainer.fileExtension);

        FileUtil.deleteQuietly(destFile);
        FileUtil.deleteQuietly(containerDir);

        destFile.getParentFile().mkdirs();
        if(containerFormat.url == null || containerFormat.url.isEmpty()) throw new Exception("Missing resource format url");
        GetRequest request = new GetRequest(new URL(containerFormat.url));
        try {
            request.download(destFile);
        } catch(Exception e) {
            FileUtil.deleteQuietly(destFile);
            throw e;
        }
        if(request.getResponseCode() != 200) {
            FileUtil.deleteQuietly(destFile);
            throw new Exception(request.getResponseMessage());
        }

        return destFile;
    }

    /**
     * Returns the first resource container format found in the list.
     * E.g. the array may contain binary formats such as pdf, mp3, etc. This basically filters those.
     *
     * @param formats a list of resource formats
     * @return
     */
    private static Resource.Format getResourceContainerFormat(List<Resource.Format> formats) {
        for(Resource.Format f:formats) {
            if(f.mimeType.matches(ResourceContainer.baseMimeType + "\\+.+")) {
                return f;
            }
        }
        return null;
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
    @Deprecated
    public ResourceContainer convertLegacyResource(String sourceLanguageSlug, String projectSlug, String resourceSlug, String data) throws Exception {
        String containerSlug = ContainerTools.makeSlug(sourceLanguageSlug, projectSlug, resourceSlug);
        File containerDir = new File(resourceDir, containerSlug);

        SourceLanguage language = library.getSourceLanguage(sourceLanguageSlug);
        if(language == null) throw new Exception("Missing language");
        JSONObject lJson = language.toJSON();

        Project project = library.getProject(sourceLanguageSlug, projectSlug);
        if(project == null) throw new Exception("Missing project");
        JSONObject pJson = project.toJSON();
        JSONArray pCatJson = new JSONArray();
        List<Category> categories = library.getCategories(sourceLanguageSlug, projectSlug);
        for(Category cat:categories) {
            pCatJson.put(cat.slug);
        }
        pJson.put("categories", pCatJson);

        Resource resource = library.getResource(sourceLanguageSlug, projectSlug, resourceSlug);
        if(resource == null) throw new Exception("Missing resource");
        Resource.Format format = getResourceContainerFormat(resource.formats);
        if(format == null) throw new Exception("Missing resource container format");
        JSONObject rJson = resource.toJSON();

        JSONObject properties = new JSONObject();
        properties.put("language", lJson);
        properties.put("project", pJson);
        properties.put("resource", rJson);
        properties.put("modified_at", format.modifiedAt);

        // grab the tW assignments
        if(resource._legacyData.containsKey(LEGACY_WORDS_ASSIGNMENTS_URL)
                && resource._legacyData.get(LEGACY_WORDS_ASSIGNMENTS_URL) != null
                && !resource._legacyData.get(LEGACY_WORDS_ASSIGNMENTS_URL).equals("")) {
            GetRequest request = new GetRequest(new URL((String)resource._legacyData.get(LEGACY_WORDS_ASSIGNMENTS_URL)));
            String wordsData = null;
            try {
                wordsData = request.read();
            } catch (Exception e) {
                e.printStackTrace();
            }
            if(wordsData != null && request.getResponseCode() < 300) {
                try {
                    JSONObject words = new JSONObject(wordsData);
                    JSONObject assignmentsJson = new JSONObject();
                    for(int c = 0; c < words.getJSONArray("chapters").length(); c ++) {
                        JSONObject chapter = words.getJSONArray("chapters").getJSONObject(c);
                        JSONObject chapterAssignment = new JSONObject();
                        for(int f = 0; f < chapter.getJSONArray("frames").length(); f ++) {
                            JSONObject frame = chapter.getJSONArray("frames").getJSONObject(f);
                            JSONArray frameAssignment = new JSONArray();
                            for(int w = 0; w < frame.getJSONArray("items").length(); w ++) {
                                JSONObject word = frame.getJSONArray("items").getJSONObject(w);
                                String twProjSlug = projectSlug.equals("obs") ? "bible-obs" : "bible";
                                frameAssignment.put("//" + twProjSlug + "/tw/" + word.getString("id"));
                            }
                            chapterAssignment.put(LegacyTools.normalizeSlug(frame.getString("id")), frameAssignment);
                        }
                        assignmentsJson.put(LegacyTools.normalizeSlug(chapter.getString("id")), chapterAssignment);
                    }
                    properties.put("tw_assignments", assignmentsJson);
                } catch (Exception e) {
                    logListener.onWarning(e.getMessage());
                }
            }
        }

        return ContainerTools.convertResource(data, containerDir, properties);
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
    public ResourceContainer importResourceContainer(File directory) throws Exception {
        ResourceContainer rc = ResourceContainer.load(directory);
        File destination = new File(resourceDir, rc.slug);

        // validate project
        // TRICKY: we currently only support importing known projects. Only the language and resource can vary.
        JSONObject meta = library.getProjectMeta(rc.project.slug);
        if(meta == null) throw new InvalidRCException("Unsupported project");

        if(!rc.info.has("project")) throw new InvalidRCException("Missing field: project");

        // delete the old container
        deleteResourceContainer(rc.slug);

        // copy new container
        FileUtil.copyDirectory(directory, destination, null);

        // add entry to the index
        Exception indexError = null;
        library.beginTransaction();
        try {
            long languageId = library.addSourceLanguage(new SourceLanguage(rc.language));

            // build categories
            List<Category> categories = new ArrayList<>();
            try {
                if(rc.info.has("project") && rc.info.getJSONObject("project").has("categories")) {
                    JSONArray catJson = rc.info.getJSONObject("project").getJSONArray("categories");
                    for (int i = 0; i < catJson.length(); i++) {
                        String catSlug = catJson.getString(i);
                        // use known name if available
                        Category existingCat = library.getCategory(rc.language.slug, catSlug);
                        String catName = existingCat == null ? catSlug : existingCat.name;
                        categories.add(new Category(catSlug, catName));
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

            long projectId = library.addProject(rc.project, categories, languageId);
            Resource resource = rc.resource;
            resource.addFormat(new Resource.Format(rc.info.getString("package_version"), resource.type, rc.modifiedAt, "", true));
            library.addResource(resource, projectId);
        } catch (Exception e) {
            indexError = e;
        }
        library.endTransaction(indexError == null);
        if(indexError != null) throw indexError;

        return openResourceContainer(rc.language.slug, rc.project.slug, rc.resource.slug);
    }

    /**
     * Exports the closed resource container
     * @param destFile the destination file
     * @param languageSlug
     * @param projectSlug
     * @param resourceSlug
     */
    public void exportResourceContainer(File destFile, String languageSlug, String projectSlug, String resourceSlug) throws Exception {
        String slug = ContainerTools.makeSlug(languageSlug, projectSlug, resourceSlug);
        File srcDir = new File(resourceDir, slug);
        File srcFile = new File(srcDir + "." + ResourceContainer.fileExtension);

        // create closed rc
        if(!srcFile.exists() && srcDir.isDirectory()) ResourceContainer.close(srcDir);
        if(!srcFile.exists()) throw new MissingRCException("The resource container could not be found at " + srcFile);

        FileUtil.copyFile(srcFile, destFile);
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
    public ResourceContainer openResourceContainer(String sourceLanguageSlug, String projectSlug, String resourceSlug) throws Exception {
        Resource resource = library.getResource(sourceLanguageSlug, projectSlug, resourceSlug);
        if(resource == null) {
            throw new Exception("Unknown Resource");
        }
        String containerSlug = ContainerTools.makeSlug(sourceLanguageSlug, projectSlug, resourceSlug);
        return openResourceContainer(containerSlug);
    }

    /**
     * Opens a resource container archive so it's contents can be read.
     * This will NOT check with the index to validate the resource container.
     * @param containerSlug
     * @return
     * @throws Exception
     */
    public ResourceContainer openResourceContainer(String containerSlug) throws Exception {
        File directory = new File(resourceDir, containerSlug);
        File archive = new File(directory + "." + ResourceContainer.fileExtension);

        // try to load already opened container first
        try {
            if(directory.exists() && directory.isDirectory()) {
                return ResourceContainer.load(directory);
            }
        } catch (Exception e) {}

        // open archive as last resource
        return ResourceContainer.open(archive, directory);
    }

    /**
     * Closes a resource container archive.
     *
     * @param sourceLanguageSlug
     * @param projectSlug
     * @param resourceSlug
     * @return the path to the closed container
     */
    public File closeResourceContainer(String sourceLanguageSlug, String projectSlug, String resourceSlug) throws Exception {
        Resource resource = library.getResource(sourceLanguageSlug, projectSlug, resourceSlug);
        if(resource == null) {
            throw new Exception("Unknown Resource");
        }
        String containerSlug = ContainerTools.makeSlug(sourceLanguageSlug, projectSlug, resourceSlug);
        File directory = new File(resourceDir, containerSlug);
        return ResourceContainer.close(directory);
    }

    /**
     * Checks when a resource container was last modified.
     * @param sourceLanguageSlug
     * @param projectSlug
     * @param resourceSlug
     * @return
     */
    public int getResourceContainerLastModified(String sourceLanguageSlug, String projectSlug, String resourceSlug) {
        Resource resource = library.getResource(sourceLanguageSlug, projectSlug, resourceSlug);
        if(resource != null) {
            Resource.Format format = getResourceContainerFormat(resource.formats);
            if(format != null) return format.modifiedAt;
        }
        return -1;
    }

    /**
     * Checks if the resource container has been downloaded
     * @param containerSlug
     * @return
     */
    public boolean resourceContainerExists(String containerSlug) {
        File directory = new File(resourceDir, containerSlug);
        File archive = new File(directory + "." + ResourceContainer.fileExtension);
        return (directory.exists() && directory.isDirectory()) || (archive.exists() && archive.isFile());
    }

    /**
     * Checks if the resource container has been downloaded
     * @param languageSlug
     * @param projectSlug
     * @param resourceSlug
     * @return
     */
    public boolean resourceContainerExists(String languageSlug, String projectSlug, String resourceSlug) {
        String containerSlug = ContainerTools.makeSlug(languageSlug, projectSlug, resourceSlug);
        return resourceContainerExists(containerSlug);
    }

    /**
     * Deletes a resource container from the disk
     * @param containerSlug
     */
    public void deleteResourceContainer(String containerSlug) {
        File directory = new File(resourceDir, containerSlug);
        File archive = new File(directory + "." + ResourceContainer.fileExtension);
        if(directory.exists() && directory.isDirectory()) {
            FileUtil.deleteQuietly(directory);
        }
        if(archive.exists() && archive.isFile()) {
            FileUtil.deleteQuietly(archive);
        }
    }
}
