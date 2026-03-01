package org.unfoldingword.door43client.models;

import org.json.JSONException;
import org.json.JSONObject;
import org.unfoldingword.resourcecontainer.Language;

/**
 * Represents a language that a resource will be translated into
 */
public class TargetLanguage extends Language {
    public final String anglicizedName;
    public final String region;
    public final boolean isGatewayLanguage;

    /**
     * Creates a new target language
     * @param slug the language code
     * @param name the name of the language
     * @param anglicizedName the english form of the language name
     * @param direction the writen direction of the language
     * @param region the region in which the language belongs
     * @param isGatewayLanguage indicates if this target language is a gateway language
     */
    public TargetLanguage(String slug, String name, String anglicizedName, String direction, String region, boolean isGatewayLanguage) {
        super(slug, name, direction);
        this.anglicizedName = anglicizedName;
        this.region = region;
        this.isGatewayLanguage = isGatewayLanguage;
    }

    @Override
    public JSONObject toJSON() throws JSONException {
        JSONObject json = super.toJSON();
        json.put("anglicized_name", anglicizedName);
        json.put("region", region);
        json.put("is_gateway_language", isGatewayLanguage);
        return json;
    }

    /**
     * Creates a target language from json
     * @param json
     * @return
     * @throws JSONException
     */
    public static TargetLanguage fromJSON(JSONObject json) throws JSONException {
        Language l = Language.fromJSON(json);
        // TODO: 9/29/16  parse other info
        TargetLanguage targetLanguage = new TargetLanguage(l.slug,
                l.name,
                json.getString("anglicized_name"),
                l.direction,
                json.getString("region"),
                json.getBoolean("is_gateway_language"));
        return targetLanguage;
    }
}
