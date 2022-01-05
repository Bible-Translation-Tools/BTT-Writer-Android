package org.unfoldingword.resourcecontainer;

import android.util.Log;

import com.esotericsoftware.yamlbeans.YamlWriter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A set of tools for managing resource containers.
 * These are primarily for testing, internal tools or encapsulating backwards compatibility
 */
public class ContainerTools {

    private static final String TAG = ContainerTools.class.getName();

    /**
     * Reads the resource container info without opening it.
     * This will however, work on containers that are both open and closed.
     * @param containerPath path to the container archive or directory
     * @return the resource container info (package.json)
     */
    public static JSONObject inspect(File containerPath) throws Exception {
        if(!containerPath.exists()) throw new Exception("The resource container does not exist at " + containerPath.getAbsolutePath());

        ResourceContainer container;

        if(containerPath.isFile()) {
            String[] nameArray = containerPath.getName().split("\\.");
            String ext = nameArray[nameArray.length - 1];
            if(!ext.equals(ResourceContainer.fileExtension)) throw new Exception("Invalid resource container file extension");
            nameArray[nameArray.length - 1] = "";
            File containerDir = new File(containerPath.getParentFile(), containerPath.getName() + ".inspect.tmp");
            container = ResourceContainer.open(containerPath, containerDir);
            FileUtil.deleteQuietly(containerDir);
        } else {
            container = ResourceContainer.load(containerPath);
        }

        return container.info;
    }

    /**
     * TODO: this will eventually be abstracted to call makeContainer after processing the data
     * Converts a legacy resource into a resource container.
     * Rejects with an error if the container exists.
     *
     * @param data the raw resource data
     * @param directory the destination directory
     * @param props properties of the resource content
     * @return the newly converted container
     */
    public static ResourceContainer convertResource(String data, File directory, JSONObject props) throws Exception {
        // TODO: 9/28/16 now that we have language, project, and resource classes we can pass these in as parameters
        if(!props.has("language") || !props.has("project") || !props.has("resource")
                || !props.getJSONObject("resource").has("type")) throw new Exception("Missing required parameters");

        JSONObject project = props.getJSONObject("project");
        JSONObject language = props.getJSONObject("language");
        JSONObject resource = props.getJSONObject("resource");

        // fix keys
        if(!language.has("direction") && language.has("dir")) {
            language.put("direction", language.getString("dir"));
            language.remove("dir");
        }

        String mimeType;
        if(!project.getString("slug").equals("obs") && resource.getString("type").equals("book")) {
            mimeType = "text/usx";
        } else {
            mimeType = "text/markdown";
        }
        String chunkExt = mimeType.equals("text/usx") ? "usx" : "md";

        File containerArchive = new File(directory.getParentFile(), directory.getName() + "." + ResourceContainer.fileExtension);
        if(containerArchive.exists()) throw new Exception("Resource container already exists");

        try {
            // clean opened container
            FileUtil.deleteQuietly(directory);
            directory.mkdirs();

            // package.json
            JSONObject packageData = new JSONObject();
            packageData.put("package_version", ResourceContainer.version);
            packageData.put("modified_at", props.get("modified_at"));
            packageData.put("content_mime_type", mimeType);
            packageData.put("language", language);
            packageData.put("project", project);
            packageData.put("resource", resource);
            packageData.put("chunk_status", new JSONArray());
            // TRICKY: JSONObject.toString escapes slashes / so we must un-escape them
            FileUtil.writeStringToFile(new File(directory, "package.json"), packageData.toString(2).replace("\\", ""));

            // license
            // TODO: use a proper license based on the resource license
            FileUtil.writeStringToFile(new File(directory, "LICENSE.md"), resource.getJSONObject("status").getString("license"));

            // content
            File contentDir = new File(directory, "content");
            contentDir.mkdirs();
            Map config = new HashMap();
            List toc = new ArrayList();

            // front matter
            if(!resource.getString("type").equals("help") && !resource.getString("type").equals("dict")) {
                // TRICKY: helps do not have a translatable title
                File frontDir = new File(contentDir, "front");
                frontDir.mkdirs();
                FileUtil.writeStringToFile(new File(frontDir, "title." + chunkExt), project.getString("name").trim());
//                Map frontToc = new HashMap();
//                frontToc.put("chapter", "front");
//                frontToc.put("chunks", new String[]{"title"});
//                toc.add(frontToc);
            }

            // main content
            if(resource.getString("type").equals("book")) {
                JSONObject json = new JSONObject(data);
                config.put("content", new HashMap());
                if(project.getString("slug").equals("obs")) {
                    // add obs images
                    Map mediaConfig = new HashMap();
                    mediaConfig.put("mime_type", "image/jpg");
                    mediaConfig.put("size", 37620940);
                    mediaConfig.put("url", "https://api.unfoldingword.org/obs/jpg/1/en/obs-images-360px.zip");
                    config.put("media", mediaConfig);
                }
                for(int c = 0; c < json.getJSONArray("chapters").length(); c ++) {
                    JSONObject chapter = json.getJSONArray("chapters").getJSONObject(c);
                    Map chapterConfig = new HashMap();
//                    Map chapterTOC = new HashMap();
                    String chapterNumber = normalizeSlug(chapter.getString("number"));
//                    chapterTOC.put("chapter", chapterNumber);
//                    chapterTOC.put("chunks", new ArrayList());
                    File chapterDir = new File(contentDir, chapterNumber);
                    chapterDir.mkdirs();

                    // chapter title
//                    ((List)chapterTOC.get("chunks")).add("title");
                    if(chapter.has("title") && !chapter.getString("title").isEmpty()) {
                        FileUtil.writeStringToFile(new File(chapterDir, "title." + chunkExt), chapter.getString("title"));
                    } else {
                        String title = localizeChapterTitle(language.getString("slug"), chapterNumber);
                        FileUtil.writeStringToFile(new File(chapterDir, "title." + chunkExt), title);
                    }

                    // frames
                    for(int f = 0; f < chapter.getJSONArray("frames").length(); f ++) {
                        JSONObject frame = chapter.getJSONArray("frames").getJSONObject(f);
                        String frameSlug = normalizeSlug(frame.getString("id").split("-")[1].trim());
                        if(frameSlug.equals("00")) {
                            // fix for chunk 00.txt bug
                            Pattern versePattern = Pattern.compile("<verse\\s+number=\"(\\d+(-\\d+)?)\"\\s+style=\"v\"\\s*\\/>");
                            String frameText = frame.getString("text");
                            // TRICKY: the json encoding escapes double quotes so we need to un-escape them.
                            frameText = frameText.replace("\\\"", "\"");
                            Matcher match = versePattern.matcher(frameText);
                            if(match.find()) {
                                String firstVerseRange = match.group(1);
                                // TRICKY: verses can be num-num
                                frameSlug = normalizeSlug(firstVerseRange.split("-")[0]);
                            }
                        }

                        // build chunk config
                        List questions = new ArrayList();
                        List notes = new ArrayList();
                        List images = new ArrayList();
                        List words = new ArrayList();
                        // TODO: 9/13/16 add questions, notes, and images to the config for the chunk
                        if(props.has("tw_assignments")) {
                            try {
                                // TRICKY: we use the un-normalized slug here so we don't break word assignments
                                JSONArray slugs = props.getJSONObject("tw_assignments").getJSONObject(chapterNumber).getJSONArray(frameSlug);
                                for(int s = 0; s < slugs.length(); s ++) {
                                    words.add(slugs.get(s));
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        if(questions.size() > 0 || notes.size() > 0 || images.size() > 0 || words.size() > 0) {
                            chapterConfig.put(frameSlug, new HashMap());
                        }
                        if(questions.size() > 0) {
                            ((HashMap)chapterConfig.get(frameSlug)).put("questions", questions);
                        }
                        if(notes.size() > 0) {
                            ((HashMap)chapterConfig.get(frameSlug)).put("notes", notes);
                        }
                        if(images.size() > 0) {
                            ((HashMap)chapterConfig.get(frameSlug)).put("images", images);
                        }
                        if(words.size() > 0) {
                            ((HashMap)chapterConfig.get(frameSlug)).put("words", words);
                        }

//                        ((List)chapterTOC.get("chunks")).add(frameSlug);
                        FileUtil.writeStringToFile(new File(chapterDir, frameSlug + "." + chunkExt), frame.getString("text"));
                    }

                    // chapter reference
                    if(chapter.has("ref") && !chapter.getString("ref").isEmpty()) {
//                        ((List)chapterTOC.get("chunks")).add("reference");
                        FileUtil.writeStringToFile(new File(chapterDir, "title." + chunkExt), chapter.getString("title"));
                    }
                    if(chapterConfig.size() > 0) {
                        ((Map)config.get("content")).put(chapterNumber, chapterConfig);
                    }
//                    toc.add(chapterTOC);
                }
            } else if(resource.getString("type").equals("help")) {
                JSONArray json = new JSONArray(data);
                if(resource.getString("slug").equals("tn")) {
                    for(int c = 0; c < json.length(); c ++) {
                        JSONObject chunk = json.getJSONObject(c);
                        if(!chunk.has("tn")) continue;
                        String[] slugs = chunk.getString("id").split("-");
                        if(slugs.length != 2) continue;
                        String chapterSlug = normalizeSlug(slugs[0]);
                        String chunkSlug = normalizeSlug(slugs[1]);

                        // fix for chunk 00.txt bug
                        if(chunkSlug.equals("00")) continue;

                        File chapterDir = new File(contentDir, chapterSlug);
                        chapterDir.mkdirs();
                        String body = "";
                        for(int n = 0; n < chunk.getJSONArray("tn").length(); n ++) {
                            JSONObject note = chunk.getJSONArray("tn").getJSONObject(n);
                            body += "\n\n#" + note.getString("ref") + "\n\n" + note.getString("text");
                        }
                        if(!body.trim().isEmpty()) {
                            FileUtil.writeStringToFile(new File(chapterDir, chunkSlug + "." + chunkExt), body.trim());
                        }
                    }
                } else if(resource.getString("slug").equals("tq")) {
                    ObjectReader reader = convertTQ(data);
                    List<Object> chapters = reader.keys();
                    for(Object chapter:chapters) {
                        List<Object> chunks = reader.get(chapter).keys();
                        for(Object chunk:chunks) {
                            File chunkFile = new File(new File(contentDir, (String)chapter), chunk + "." + chunkExt);
                            chunkFile.getParentFile().mkdirs();
                            FileUtil.writeStringToFile(chunkFile, reader.get(chapter).get(chunk).toString());
                        }
                    }
                } else {
                    throw new Exception("Unsupported resource " + resource.getString("slug"));
                }
            } else if(resource.getString("type").equals("dict")) {
                JSONArray json = new JSONArray(data);
                for(int w = 0; w < json.length(); w ++) {
                    JSONObject word = json.getJSONObject(w);
                    if(!word.has("id")) continue;
                    File wordDir = new File(contentDir, word.getString("id"));
                    wordDir.mkdirs();
                    String body = "#" + word.getString("term") + "\n\n" + word.getString("def");
                    FileUtil.writeStringToFile(new File(wordDir, "01." + chunkExt), body);

                    Map<String, Object> wordConfig = new HashMap();
                    wordConfig.put("def_title", word.getString("def_title"));

                    if(JSONHasLength(word, "cf")) {
                        wordConfig.put("see_also", new ArrayList());
                        for(int i = 0; i < word.getJSONArray("cf").length(); i ++) {
                            // TRICKY: some id's have the title attached it it like: eve|Eve
                            String[] parts = word.getJSONArray("cf").getString(i).split("\\|");
                            String id = parts[0].toLowerCase();
                            if(!((List)wordConfig.get("see_also")).contains(id)) {
                                ((List) wordConfig.get("see_also")).add(id);
                            }
                        }
                    }
                    if(JSONHasLength(word, "aliases")) {
                        wordConfig.put("aliases", new ArrayList());
                        for(int i = 0; i < word.getJSONArray("aliases").length(); i ++) {
                            String[] aliases = word.getJSONArray("aliases").getString(i).split(",");
                            for(String alias:aliases) {
                                ((List) wordConfig.get("aliases")).add(alias.trim());
                            }
                        }
                    }
                    if(JSONHasLength(word, "ex")) {
                        wordConfig.put("examples", new ArrayList());
                        for(int i = 0; i < word.getJSONArray("ex").length(); i ++) {
                            JSONObject example = word.getJSONArray("ex").getJSONObject(i);
                            if(example.has("ref")) {
                                ((List) wordConfig.get("examples")).add(example.getString("ref"));
                            }
                        }
                    }
                    config.put(word.getString("id"), wordConfig);
                }
            } else if(resource.getString("type").equals("man")) {
                JSONObject json = new JSONObject(data);

                Map tocMap = new HashMap();
                config.put("content", new HashMap());

                for(int a = 0; a < json.getJSONArray("articles").length(); a ++) {
                    JSONObject article = json.getJSONArray("articles").getJSONObject(a);
                    Map articleConfig = new HashMap();
                    List recommended = new ArrayList();
                    List dependencies = new ArrayList();

                    // TRICKY: fix the id's
                    String slug = article.getString("id").replaceAll("\\_", "-");
                    if(article.has("recommend") && !article.isNull("recommend")) {
                        for(int i = 0; i < article.getJSONArray("recommend").length(); i ++) {
                            recommended.add(article.getJSONArray("recommend").getString(i).replaceAll("\\_", "-"));
                        }
                    }
                    if(article.has("depend") && !article.isNull("depend")) {
                        for(int i = 0; i < article.getJSONArray("depend").length(); i ++) {
                            dependencies.add(article.getJSONArray("depend").getString(i).replaceAll("\\_", "-"));
                        }
                    }
                    File articleDir = new File(contentDir, slug);
                    articleDir.mkdirs();

                    // article title
                    FileUtil.writeStringToFile(new File(articleDir, "title." + chunkExt), article.getString("title"));

                    // article sub-title
                    FileUtil.writeStringToFile(new File(articleDir, "sub-title." + chunkExt), article.getString("question"));

                    // article body
                    FileUtil.writeStringToFile(new File(articleDir, "01." + chunkExt), article.getString("text"));

                    // only non-empty config
                    if(recommended.size() > 0 || dependencies.size() > 0) {
                        articleConfig.put("recommended", recommended);
                        articleConfig.put("dependencies", dependencies);
                        ((HashMap<String, Object>)config.get("content")).put(slug, articleConfig);
                    }

                    Map articleTOC = new HashMap();
                    articleTOC.put("chapter", slug);
                    List chunkTOC = new ArrayList();
                    chunkTOC.add("title");
                    chunkTOC.add("sub-title");
                    chunkTOC.add("01");
                    articleTOC.put("chunks", chunkTOC);

                    tocMap.put(article.getString("id"), articleTOC);
                }

                // build toc from what we see in the api
                Pattern linkPattern = Pattern.compile("\\[[^\\[\\]]*\\]\\s*\\(([^\\(\\)]*)\\)", Pattern.DOTALL);
                Matcher match = linkPattern.matcher(json.getString("toc"));
                while(match.find()) {
                    String key = match.group(1);
                    Object val = tocMap.get(key);
                    if(val != null) toc.add(val);
                }
            } else {
                throw new Exception("Unsupported resource container type " + resource.getString("type"));
            }

            // write config
            YamlWriter configWriter = null;
            try {
                configWriter = new YamlWriter(new FileWriter(new File(contentDir, "config.yml")));
                configWriter.write(config);
            } finally {
                if(configWriter != null) configWriter.close();
            }

            // write toc
            if(toc.size() > 0) {
                YamlWriter tocWriter = null;
                try {
                    tocWriter = new YamlWriter(new FileWriter(new File(contentDir, "toc.yml")));
                    tocWriter.write(toc);
                } catch (Exception e) {
                    throw e;
                } finally {
                    if (tocWriter != null) tocWriter.close();
                }
            }

        } catch (Exception e) {
            FileUtil.deleteQuietly(directory);
            throw e;
        }

        return ResourceContainer.load(directory);
    }

    /**
     * Returns a localized chapter title. e.g. "Chapter 1"
     * If the language does not have a match a default localization will be used.
     *
     * If chapter_number is a number it will be parsed as an int to strip leading 0's
     * @param languageSlug the language into which the chapter title will be localized
     * @param chapterNumber the chapter number that is being localized
     * @return the localized chapter title
     */
    public static String localizeChapterTitle(String languageSlug, String chapterNumber) {
        Map<String, String> translations = new HashMap<>();
        translations.put("ar", "الفصل %");
        translations.put("en", "Chapter %");
        translations.put("ru", "Глава %");
        translations.put("hu", "%. fejezet");
        translations.put("sr-Latin", "Поглавље %");
        translations.put("default", "Chapter %");

        String title = translations.get(languageSlug);
        if(title == null) title = translations.get("default");
        try {
            int num = Integer.parseInt(chapterNumber);
            return title.replace("%", num + "");
        } catch (NumberFormatException e) {
            return title.replace("%", chapterNumber);
        }
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
     * Returns a localized chapter title. e.g. "Chapter 1"
     * If the language does not have a match a default localization will be used.
     *
     * If chapter_number is a number it will be parsed as an int to strip leading 0's
     * @param languageSlug the language into which the chapter title will be localized
     * @param chapterNumber the chapter number that is being localized
     * @return the localized chapter title
     */
    public static String localizeChapterTitle(String languageSlug, int chapterNumber) {
        return localizeChapterTitle(languageSlug, chapterNumber + "");
    }

    /**
     * Checks if a value in the json object has a length e.g. is a JSONArray with length > 0
     * @param json
     * @param key
     * @return
     */
    private static boolean JSONHasLength(JSONObject json, String key) {
        try {
            return json.has(key) && json.optJSONArray(key) != null && json.getJSONArray(key).length() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Returns a properly formatted container slug.
     * @param languageSlug
     * @param projectSlug
     * @param resourceSlug
     * @return
     */
    public static String makeSlug(String languageSlug, String projectSlug, String resourceSlug) {
        if(languageSlug == null || languageSlug.isEmpty()
                || projectSlug == null || projectSlug.isEmpty()
                || resourceSlug == null || resourceSlug.isEmpty()) throw new InvalidParameterException("Invalid resource container slug parameters");
        return languageSlug + ResourceContainer.slugDelimiter + projectSlug + ResourceContainer.slugDelimiter + resourceSlug;
    }

    /**
     * Breaks a resource container slug into it's delimited sections.
     * Those are language, project, resource.
     *
     * @param resourceContainerSlug
     * @return
     */
    public static String[] explodeSlug(String resourceContainerSlug) {
        return resourceContainerSlug.split(ResourceContainer.slugDelimiter);
    }

    /**
     * Retrieves the resource container type by parsing the resource container mime type.
     * @param mimeType
     * @return The mime type. e.g. "application/tsrc+type"
     */
    public static String mimeToType(String mimeType) {
        return mimeType.split("\\+")[1];
    }

    /**
     * Returns a resource container mime type based on the given container type.
     * @param resourceType the resource container type. e.g. "book", "help" etc.
     * @return The mime type. e.g. "application/tsrc+type"
     */
    public static String typeToMime(String resourceType) {
        return ResourceContainer.baseMimeType + "+" + resourceType;
    }

    /**
     * Converts the old tQ format into the new format.
     * @param tqstring the string to convert
     * @return an ObjectReader for ingesting the converted data
     * @throws JSONException
     */
    public static ObjectReader convertTQ(String tqstring) throws JSONException {
        ObjectReader reader = new ObjectReader(new JSONArray(tqstring));
        Map<String, Map> normalizedChapters = new HashMap<>();
        for(ObjectReader chapter:reader) {
            if(chapter.get("cq").value() == null) continue;
            try {
                String chapterId = normalizeSlug((String) chapter.get("id").value());
                Map<String, String> normalizedChunks = new HashMap<>();
                for(ObjectReader question:chapter.get("cq")) {
                    String text = "\n\n#" + question.get("q") + "\n\n" + question.get("a");
                    for(ObjectReader ref:question.get("ref")) {
                        String[] slugs = ref.toString().split("-");
                        if(slugs.length != 2) continue;
                        String chunkId = normalizeSlug(slugs[1]);
                        String old = normalizedChunks.get(chunkId);
                        normalizedChunks.put(chunkId, (old != null ? old.trim() : "") + text);
                    }
                }
                normalizedChapters.put(chapterId, normalizedChunks);
            } catch(Exception e) {
                Log.d(TAG, "tQ parsing failed");
                e.printStackTrace();
            }
        }
        return new ObjectReader(normalizedChapters);
    }
}
