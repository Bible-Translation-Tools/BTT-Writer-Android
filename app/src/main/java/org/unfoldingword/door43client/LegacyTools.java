package org.unfoldingword.door43client;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.unfoldingword.door43client.models.Catalog;
import org.unfoldingword.door43client.models.Category;
import org.unfoldingword.door43client.models.ChunkMarker;
import org.unfoldingword.door43client.models.SourceLanguage;
import org.unfoldingword.door43client.models.Versification;
import org.unfoldingword.resourcecontainer.ContainerTools;
import org.unfoldingword.resourcecontainer.Project;
import org.unfoldingword.resourcecontainer.Resource;
import org.unfoldingword.resourcecontainer.ResourceContainer;
import org.unfoldingword.tools.http.GetRequest;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by joel on 9/19/16.
 */
class LegacyTools {

    /** Defines a configurable languages URL to be used when updating catalogs */
    public static String LANG_NAMES_URL = "https://langnames-temp.walink.org/langnames.json";

    /**
     *
     * @param library
     * @param host the host to use for the catalogs
     * @throws Exception
     */
    public static void injectGlobalCatalogs(Library library, String host) throws Exception {
        host = host != null && !host.trim().isEmpty() ? host : "https://td.unfoldingword.org";

        library.addCatalog(new Catalog("langnames", LANG_NAMES_URL, 0));
        // TRICKY: the trailing / is required on these urls
        library.addCatalog(new Catalog("new-language-questions", host + "/api/questionnaire/", 0));
        library.addCatalog(new Catalog("temp-langnames", host + "/api/templanguages/", 0));
        // TRICKY: this catalog should always be indexed after langnames and temp-langnames otherwise the linking will fail!
        library.addCatalog(new Catalog("approved-temp-langnames", host + "/api/templanguages/assignment/changed/", 0));
    }

    public static void processCatalog(Library library, String data, OnProgressListener listener) throws Exception {
        JSONArray projects = new JSONArray(data);
        for(int i = 0; i < projects.length(); i ++) {
            JSONObject pJson = projects.getJSONObject(i);
            if(listener != null) {
                if(!listener.onProgress(pJson.getString("slug"), projects.length(), i + 1)) break;
            }
            downloadSourceLanguages(library, pJson, null);
            library.yieldSafely();
        }

        // tA
        updateTA(library, listener);
    }

    /**
     * Pads a slug to 2 significant digits.
     * Examples:
     * '1'    -> '01'
     * '001'  -> '01'
     * '12'   -> '12'
     * '123'  -> '123'
     * '0123' -> '123'
     * Words are not padded:
     * 'a' -> 'a'
     * '0word' -> '0word'
     * And as a matter of consistency:
     * '0'  -> '00'
     * '00' -> '00'
     *
     * @param slug the slug to be normalized
     * @return the normalized slug
     */
    public static String normalizeSlug(String slug) throws Exception {
        if(slug == null || slug.isEmpty()) throw new Exception("slug cannot be an empty string");
        if(!isInteger(slug)) return slug;
        slug = slug.replaceAll("^(0+)", "").trim();
        while(slug.length() < 2) {
            slug = "0" + slug;
        }
        return slug;
    }

    /**
     * Checks if a string is an integer
     * @param s
     * @return
     */
    protected static boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
        } catch(NumberFormatException e) {
            return false;
        } catch(NullPointerException e) {
            return false;
        }
        return true;
    }

    /**
     * Downloads the tA projects
     * @param library
     * @param listener
     * @throws Exception
     */
    private static void updateTA(Library library, OnProgressListener listener) throws Exception {
        String[] urls = new String[]{
                "https://api.unfoldingword.org/ta/txt/1/en/audio_2.json",
                "https://api.unfoldingword.org/ta/txt/1/en/checking_1.json",
                "https://api.unfoldingword.org/ta/txt/1/en/checking_2.json",
                "https://api.unfoldingword.org/ta/txt/1/en/gateway_3.json",
                "https://api.unfoldingword.org/ta/txt/1/en/intro_1.json",
                "https://api.unfoldingword.org/ta/txt/1/en/process_1.json",
                "https://api.unfoldingword.org/ta/txt/1/en/translate_1.json",
                "https://api.unfoldingword.org/ta/txt/1/en/translate_2.json"
        };
        for(int i = 0; i < urls.length; i ++) {
            downloadTA(library, urls[i]);
            if(listener != null) {
                if(!listener.onProgress("ta", urls.length, i + 1)) break;
            }
            library.yieldSafely();
        }
    }

    /**
     * Downloads the tA projects
     * Continues from updateTA()
     *
     * @param library
     * @param url
     * @throws Exception
     */
    private static void downloadTA(Library library, String url) throws Exception {
        GetRequest get = new GetRequest(new URL(url));
        String data = get.read();
        if(get.getResponseCode() != 200) throw new Exception(get.getResponseMessage());
        JSONObject ta = new JSONObject(data);

        // add language (right now only english)
        long languageId = library.addSourceLanguage(new SourceLanguage("en", "English", "ltr"));

        // add project
        String rawSlug = ta.getJSONObject("meta").getString("manual").replaceAll("\\_", "-");
        String name  = (rawSlug.charAt(0) + "").toUpperCase() + rawSlug.substring(1) + " Manual";
        Project p = new Project("ta-" + rawSlug, name, 0);
        List<Category> categories = new ArrayList<>();
        categories.add(new Category("ta", "translationAcademy"));
        long projectId = library.addProject(p, categories, languageId);

        // add resource
        String slug = "vol " + ta.getJSONObject("meta").getString("volume");
        String resourceName = "Volume " + ta.getJSONObject("meta").getString("volume");
        String type = new String("man");

        // compile status
        String checkingLevel = ta.getJSONObject("meta").getJSONObject("status").getString("checking_level");
        String comments = ta.getJSONObject("meta").getJSONObject("status").getString("comments");
        String pubDate = ta.getJSONObject("meta").getJSONObject("status").getString("publish_date");
        String license = ta.getJSONObject("meta").getJSONObject("status").getString("license");
        String version = ta.getJSONObject("meta").getJSONObject("status").getString("version");

        Resource resource = new Resource(slug, resourceName, type, "gl", checkingLevel, version);
        resource.comments = comments;
        resource.pubDate = pubDate;
        resource.license = license;

        String packageVersion = ResourceContainer.version;
        String mimType = ContainerTools.typeToMime("man");
        int modifiedAt = ta.getJSONObject("meta").getInt("mod");
        Resource.Format resourceFormat = new Resource.Format(packageVersion, mimType, modifiedAt, url, false);

        resource.addFormat(resourceFormat);
        library.addResource(resource, projectId);
    }

    /**
     * This will download the source languages for a project.
     * Some of the project info is mixed with languages
     * so we are creating the projects and langauges here
     *
     * @param library
     * @param pJson the project json
     * @param listener
     * @throws Exception
     */
    private static void downloadSourceLanguages(Library library, JSONObject pJson, OnProgressListener listener) throws Exception {
        GetRequest request = new GetRequest(new URL(pJson.getString("lang_catalog")));
        String response = request.read();
        if(request.getResponseCode() != 200) throw new Exception(request.getResponseMessage());

        String chunksUrl = "";
        if(!pJson.getString("slug").toLowerCase().equals("obs")) {
            chunksUrl = "https://api.unfoldingword.org/bible/txt/1/" + pJson.getString("slug") + "/chunks.json";
        }

        JSONArray languages = new JSONArray(response);
        for(int i = 0; i < languages.length(); i ++) {
            JSONObject lJson = languages.getJSONObject(i);

            if(listener != null) {
                if(!listener.onProgress(lJson.getJSONObject("language").getString("slug") + pJson.getString("slug"), languages.length(), i + 1)) break;
            }

            SourceLanguage sl = new SourceLanguage(lJson.getJSONObject("language").getString("slug"),
                    lJson.getJSONObject("language").getString("name"),
                    lJson.getJSONObject("language").getString("direction"));
            long languageId = library.addSourceLanguage(sl);

            // TODO: retrieve the correct versification name(s) from the source language
            library.addVersification(new Versification("en-US", "American English"), languageId);

            Project project = new Project(pJson.getString("slug"),
                    lJson.getJSONObject("project").getString("name"),
                    pJson.getInt("sort"));
            project.description = lJson.getJSONObject("project").getString("desc");
            project.chunksUrl = chunksUrl;
            List<Category> categories = new ArrayList<>();
            if(pJson.has("meta")) {
                for(int j = 0; j < pJson.getJSONArray("meta").length(); j ++) {
                    String slug = pJson.getJSONArray("meta").getString(j);
                    categories.add(new Category(slug, lJson.getJSONObject("project").getJSONArray("meta").getString(j)));
                }
            }

            long projectId = library.addProject(project, categories, languageId);

            downloadResources(library, projectId, pJson, languageId, lJson);
            library.yieldSafely();
        }
    }

    /**
     * Downloads resources for a project.
     * This will split notes and questions into their own resource.
     * words are added as a new project.
     *
     * @param library
     * @param projectId
     * @param pJson
     * @param languageId
     * @param lJson
     * @throws Exception
     */
    private static void downloadResources(Library library, long projectId, JSONObject pJson, long languageId, JSONObject lJson) throws Exception {
        GetRequest request = new GetRequest(new URL(lJson.getString("res_catalog")));
        String response = request.read();
        if(request.getResponseCode() != 200) throw new Exception(request.getResponseMessage());

        JSONArray resources = new JSONArray(response);
        for(int i = 0; i < resources.length(); i ++) {
            JSONObject rJson = resources.getJSONObject(i);

            String translateMode;
            switch(rJson.getString("slug").toLowerCase()) {
                case "obs":
                case "ulb":
                    translateMode = "all";
                    break;
                default:
                    translateMode = "gl";
            }

            rJson.getJSONObject("status").put("translate_mode", translateMode);
            rJson.getJSONObject("status").put("pub_date", rJson.getJSONObject("status").getString("publish_date"));
            rJson.put("type", "book");
            Resource resource = Resource.fromJSON(rJson);
            resource._legacyData.put(API.LEGACY_WORDS_ASSIGNMENTS_URL, rJson.getString("tw_cat"));

            Resource.Format format = new Resource.Format(ResourceContainer.version, ContainerTools.typeToMime("book"), rJson.getInt("date_modified"), rJson.getString("source"), false);
            resource.addFormat(format);

            long resourceId = library.addResource(resource, projectId);

            // coerce notes to resource
            if(rJson.has("notes") && !rJson.getString("notes").isEmpty()) {
                rJson.getJSONObject("status").put("translate_mode", "gl");
                rJson.put("slug", "tn");
                rJson.put("name", "translationNotes");
                rJson.put("type", "help");
                List<Map> sourceTranslations = new ArrayList();
                Map<String, Object> tnSourceTranslation = new HashMap();
                tnSourceTranslation.put("language_slug", lJson.getJSONObject("language").getString("slug"));
                tnSourceTranslation.put("resource_slug", "tn");
                tnSourceTranslation.put("version", resource.version);
                sourceTranslations.add(tnSourceTranslation);
                rJson.getJSONObject("status").put("source_translations", sourceTranslations);

                Resource tnResource = (Resource) Resource.fromJSON(rJson);
                Resource.Format tnFormat = new Resource.Format(ResourceContainer.version, ContainerTools.typeToMime("help"), rJson.getInt("date_modified"), rJson.getString("notes"), false);
                tnResource.addFormat(tnFormat);
                library.addResource(tnResource, projectId);
            }

            // coerce questions to resource
            if(rJson.has("checking_questions") && !rJson.getString("checking_questions").isEmpty()) {
                rJson.getJSONObject("status").put("translate_mode", "gl");
                rJson.put("slug", "tq");
                rJson.put("name", "translationQuestions");
                rJson.put("type", "help");

                List<Map> sourceTranslations = new ArrayList();
                Map<String, Object> tnSourceTranslation = new HashMap();
                tnSourceTranslation.put("language_slug", lJson.getJSONObject("language").getString("slug"));
                tnSourceTranslation.put("resource_slug", "tq");
                tnSourceTranslation.put("version", resource.version);
                sourceTranslations.add(tnSourceTranslation);
                rJson.getJSONObject("status").put("source_translations", sourceTranslations);

                Resource tqResource = (Resource) Resource.fromJSON(rJson);
                Resource.Format tqFormat = new Resource.Format(ResourceContainer.version, ContainerTools.typeToMime("help"), rJson.getInt("date_modified"), rJson.getString("checking_questions"), false);
                tqResource.addFormat(tqFormat);
                library.addResource(tqResource, projectId);
            }

            // add words project (this is insert/update so it will only be added once)
            // TRICKY: obs tw has not been unified with bible tw yet so we add it as separate project.
            if(rJson.has("terms") && !rJson.getString("terms").isEmpty()) {
                String slug = pJson.getString("slug").equals("obs") ? "bible-obs" : "bible";
                String name = "translationWords" + (pJson.getString("slug").equals("obs") ? " OBS" : "");
                Project wordsProject = new Project(slug, name, 100);
                long wordsProjectId = library.addProject(wordsProject, null, languageId);

                // add resource to words project
                rJson.getJSONObject("status").put("translate_mode", "gl");
                rJson.put("slug", "tw");
                rJson.put("name", "translationWords");
                rJson.put("type", "dict");

                List<Map> sourceTranslations = new ArrayList();
                Map<String, Object> twSourceTranslation = new HashMap();
                twSourceTranslation.put("language_slug", lJson.getJSONObject("language").getString("slug"));
                twSourceTranslation.put("resource_slug", "tw");
                twSourceTranslation.put("version", resource.version);
                sourceTranslations.add(twSourceTranslation);
                rJson.getJSONObject("status").put("source_translations", sourceTranslations);

                Resource twResource = (Resource) Resource.fromJSON(rJson);
                Resource.Format twFormat = new Resource.Format(ResourceContainer.version, ContainerTools.typeToMime("dict"), rJson.getInt("date_modified"), rJson.getString("terms"), false);
                twResource.addFormat(twFormat);
                library.addResource(twResource, wordsProjectId);
            }
            library.yieldSafely();
        }
    }

    /**
     * Downloads chunks for a project
     * @param library
     * @param chunksUrl
     * @param projectSlug
     * @throws Exception
     */
    private static void downloadChunks(Library library, String chunksUrl, String projectSlug) throws Exception {
        // TODO: pull the correct versification slug from the data. For now there is only one versification
        Versification v = library.getVersification("en", "en-US");
        if(v != null) {
            GetRequest request = new GetRequest(new URL(chunksUrl));
            String data = request.read();
            JSONArray chunks = new JSONArray(data);
            for(int i = 0; i < chunks.length(); i ++) {
                JSONObject chunk = chunks.getJSONObject(i);
                ChunkMarker cm = new ChunkMarker(chunk.getString("chp"), chunk.getString("firstvs"));
                library.addChunkMarker(cm, projectSlug, v.rowId);
                library.yieldSafely();
            }
        } else {
            System.console().writer().write("Unknown versification while downloading chunks for project " + projectSlug);
        }
    }

    /**
     * Converts a json object to a hash map
     * http://stackoverflow.com/questions/21720759/convert-a-json-string-to-a-hashmap
     * @param json
     * @return
     * @throws JSONException
     */
    public static Map<String, Object> jsonToMap(JSONObject json) throws JSONException {
        Map<String, Object> retMap = new HashMap<String, Object>();

        if(json != JSONObject.NULL) {
            retMap = toMap(json);
        }
        return retMap;
    }

    public static Map<String, Object> toMap(JSONObject object) throws JSONException {
        Map<String, Object> map = new HashMap<String, Object>();

        Iterator<String> keysItr = object.keys();
        while(keysItr.hasNext()) {
            String key = keysItr.next();
            Object value = object.get(key);

            if(value instanceof JSONArray) {
                value = toList((JSONArray) value);
            }

            else if(value instanceof JSONObject) {
                value = toMap((JSONObject) value);
            }
            map.put(key, value);
        }
        return map;
    }

    public static List<Object> toList(JSONArray array) throws JSONException {
        List<Object> list = new ArrayList<Object>();
        for(int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            if(value instanceof JSONArray) {
                value = toList((JSONArray) value);
            }

            else if(value instanceof JSONObject) {
                value = toMap((JSONObject) value);
            }
            list.add(value);
        }
        return list;
    }

    public static void processChunks(Library library, OnProgressListener listener) throws Exception {
        // TRICKY: currently all chunk markers are defined according to the english versification system
        Map<String, String> markers = new HashMap<>();
        for(SourceLanguage l:library.getSourceLanguages()) {
            for(Project p:library.getProjects(l.slug)) {
                if(p.chunksUrl != null && !p.chunksUrl.isEmpty()) {
                    markers.put(p.slug, p.chunksUrl);
                }
            }
        }

        int pos = 0;
        for(String key:markers.keySet()) {
            downloadChunks(library, markers.get(key), key);
            pos ++;
            if(listener != null) {
                if(!listener.onProgress("chunk_markers", markers.size(), pos)) break;
            }
        }
    }
}
