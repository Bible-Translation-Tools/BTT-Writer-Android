package com.door43.translationstudio.core

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.unfoldingword.tools.logger.Logger

/**
 * Created by blm on 4/14/16.
 */
class MissingNameItem(
    val description: String?,
    val invalidName: String?,
    val contents: String?
) {
    fun toJson(): JSONObject? {
        try {
            val jsonObject = JSONObject()
            jsonObject.putOpt("description", description)
            jsonObject.putOpt("invalidName", invalidName)
            jsonObject.putOpt("contents", contents)

            return jsonObject
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    companion object {
        val TAG: String = MissingNameItem::class.java.simpleName

        fun generate(jsonObject: JSONObject): MissingNameItem? {
            try {
                val description = getOpt(jsonObject, "description") as String
                val invalidName = getOpt(jsonObject, "invalidName") as String
                val contents = getOpt(jsonObject, "contents") as String
                return MissingNameItem(description, invalidName, contents)
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }

        fun toJsonArray(array: List<MissingNameItem>): JSONArray {
            val jsonArray = JSONArray()
            for (item in array) {
                jsonArray.put(item.toJson())
            }
            return jsonArray
        }

        fun fromJsonString(jsonStr: String): List<MissingNameItem>? {
            try {
                val jsonArray = JSONArray(jsonStr)
                return fromJsonArray(jsonArray)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }

        @Throws(JSONException::class)
        fun fromJsonArray(jsonArray: JSONArray): List<MissingNameItem> {
            val array = arrayListOf<MissingNameItem>()

            try {
                for (i in 0 until jsonArray.length()) {
                    val json = jsonArray.getJSONObject(i)
                    generate(json)?.let(array::add)
                }
            } catch (e: Exception) {
                Logger.e(TAG, "could not parse item", e)
            }
            return array
        }

        private fun getOpt(json: JSONObject, key: String): Any? {
            try {
                if (json.has(key)) {
                    return json[key]
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }
    }
}


