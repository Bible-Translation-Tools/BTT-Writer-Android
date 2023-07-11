package org.unfoldingword.door43client.models;

import org.json.JSONException;
import org.json.JSONObject;
import org.unfoldingword.resourcecontainer.Language;

/**
 * Represents a language that a resource exists in  (for the purpose of source content)
 */
public class SourceLanguage extends Language {

    /**
     * Creates a new source language
     * @param slug the language code
     * @param name the name of the language
     * @param direction the written direction of the language
     */
    public SourceLanguage(String slug, String name, String direction) {
        super(slug, name, direction);
    }

    /**
     * Creates a new source language from a language
     * @param language the language
     */
    public SourceLanguage(Language language) {
        super(language.slug, language.name, language.direction);
    }

    /**
     * Creates a source language from json
     * @param json the language json
     * @return the new source language
     * @throws JSONException
     */
    public static SourceLanguage fromJSON(JSONObject json) throws JSONException {
        Language l = Language.fromJSON(json);
        return new SourceLanguage(l.slug, l.name, l.direction);
    }
}
