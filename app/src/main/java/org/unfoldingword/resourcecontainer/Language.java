package org.unfoldingword.resourcecontainer;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents a single language
 */

public class Language implements Comparable {
    public final String slug;
    public final String name;
    public final String direction;

    /**
     * Creates a new language
     * @param slug the language code
     * @param name the name of the language
     * @param direction the written direction of the language
     */
    public Language(String slug, String name, String direction) {
        this.slug = slug;
        this.name = name;
        this.direction = direction;
    }

    /**
     * Returns the object serialized to json
     * @return
     */
    public JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("slug", slug);
        json.put("name", name);
        json.put("direction", direction);
        return json;
    }

    /**
     * Compares a string or another language with this language
     * @param object
     * @return
     */
    @Override
    public int compareTo(Object object) {
        String slug2;
        if(object instanceof String) {
            slug2 = (String)object;
        } else if(object instanceof Language) {
            slug2 = ((Language) object).slug;
        } else if(object == null) {
            return 1;
        } else {
            // assume language is always greater than a non-language
            Log.w("Language", "Unexpected comparable: " + object.toString());
            return 1;
        }
        return this.slug.compareToIgnoreCase(slug2);
    }

    /**
     * Creates a new language from json
     * @param json
     * @return
     * @throws JSONException
     */
    public static Language fromJSON(JSONObject json) throws JSONException {
        if(json == null) throw new JSONException("Invalid json");

        if(!json.has("slug")) throw new JSONException("Missing language field: slug");
        if(!json.has("name")) throw new JSONException("Missing language field: name");
        if(!json.has("direction")) throw new JSONException("Missing language field: direction");

        return new Language(json.getString("slug"), json.getString("name"), json.getString("direction"));
    }
}