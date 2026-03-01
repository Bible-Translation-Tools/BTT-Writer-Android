package org.unfoldingword.door43client.models

import org.json.JSONException
import org.json.JSONObject
import org.unfoldingword.resourcecontainer.Language

/**
 * Represents a language that a resource will be translated into
 */
class TargetLanguage(
    slug: String,
    name: String,
    val anglicizedName: String,
    direction: String,
    val region: String,
    val isGatewayLanguage: Boolean
) : Language(slug, name, direction) {

    @Throws(JSONException::class)
    override fun toJSON(): JSONObject {
        val json = super.toJSON()
        json.put("anglicized_name", anglicizedName)
        json.put("region", region)
        json.put("is_gateway_language", isGatewayLanguage)
        return json
    }

    companion object {
        /**
         * Creates a target language from JSON
         * @param json
         * @return
         * @throws JSONException
         */
        @Throws(JSONException::class)
        fun fromJSON(json: JSONObject): TargetLanguage {
            val l = Language.fromJSON(json)
            // TODO: 9/29/16  parse other info
            return TargetLanguage(
                l.slug,
                l.name,
                json.getString("anglicized_name"),
                l.direction,
                json.getString("region"),
                json.getBoolean("is_gateway_language")
            )
        }
    }
}