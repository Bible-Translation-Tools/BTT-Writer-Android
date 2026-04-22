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
}