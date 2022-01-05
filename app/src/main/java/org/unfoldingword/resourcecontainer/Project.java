package org.unfoldingword.resourcecontainer;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents a project to be translated
 */
public class Project {
    public final String slug;
    public final String name;
    public final int sort;

    /**
     * The url to the project icon
     */
    public String icon = "";

    /**
     * the url to the project chunks definition
     */
    public String chunksUrl = "";

    /**
     * A description of the project
     */
    public String description = "";

    /**
     * The language this project belongs to
     * This is a convenience property
     */
    public String languageSlug = "";

    /**
     *
     * @param slug the project code
     * @param name the name of the project
     * @param sort the sorting order of the project
     */
    public Project(String slug, String name, int sort) {
        this.slug = slug;
        this.name = name;
        this.sort = sort;
    }

    /**
     * Returns the object serialized to json
     * @return
     */
    public JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("slug", slug);
        json.put("name", name);
        json.put("sort", sort);
        json.put("desc", deNull(description));
        json.put("icon", deNull(icon));
        json.put("chunks_url", deNull(chunksUrl));
        return json;
    }

    /**
     * Turns null values to empty strings
     * @param value
     * @return
     */
    private static String deNull(String value) {
        if(value == null) value = "";
        return value;
    }

    /**
     * Creates a new project from json
     * @param json
     * @return
     * @throws JSONException
     */
    public static Project fromJSON(JSONObject json) throws JSONException {
        if(!json.has("slug")) throw new JSONException("Missing project field: slug");
        if(!json.has("name")) throw new JSONException("Missing project field: name");
        if(!json.has("sort")) throw new JSONException("Missing project field: sort");

        if(json == null) throw new JSONException("Invalid json");
        Project p = new Project(json.getString("slug"),
                json.getString("name"),
                json.getInt("sort"));

        if(json.has("icon")) p.icon = deNull(json.getString("icon"));
        if(json.has("chunks_url")) p.chunksUrl = deNull(json.getString("chunks_url"));
        if(json.has("desc")) p.description = deNull(json.getString("desc"));
        return p;
    }
}
