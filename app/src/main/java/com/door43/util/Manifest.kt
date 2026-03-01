package com.door43.util

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * This class handles the management of a manifest file.
 *
 */
class Manifest private constructor(private val manifestFile: File) {

    private var manifest = JSONObject()

    /**
     *
     * @param key
     * @return an empty string if the key is invalid
     */
    fun getString(key: String): String {
        return try {
            manifest.getString(key)
        } catch (e: JSONException) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * Checks if the key exists in the manifest
     * @param key
     * @return
     */
    fun has(key: String): Boolean {
        return manifest.has(key)
    }

    /**
     *
     * @param key
     * @return
     * @throws JSONException
     */
    @Throws(JSONException::class)
    fun getInt(key: String): Int {
        return manifest.getInt(key)
    }

    /**
     *
     * @param key
     * @return an empty JSON object if the key is invalid
     */
    fun getJSONObject(key: String): JSONObject {
        return try {
            manifest.getJSONObject(key)
        } catch (e: JSONException) {
            e.printStackTrace()
            JSONObject()
        }
    }

    /**
     *
     * @param key
     * @return an empty JSON array if the key is invalid
     */
    fun getJSONArray(key: String): JSONArray {
        return try {
            manifest.getJSONArray(key)
        } catch (e: JSONException) {
            // e.printStackTrace()
            JSONArray()
        }
    }

    /**
     * Adds an element to the manifest
     * @param key
     * @param json
     */
    fun put(key: String, json: JSONObject) {
        try {
            manifest.put(key, json)
            save()
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    /**
     * Adds an element to the manifest
     * @param key
     * @param obj
     */
    fun put(key: String, obj: Any) {
        try {
            manifest.put(key, obj)
            save()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Adds an element to the manifest
     * @param key
     * @param json
     */
    fun put(key: String, json: JSONArray) {
        try {
            manifest.put(key, json)
            save()
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    /**
     * Adds an element to the manifest
     * @param key
     * @param value
     */
    fun put(key: String, value: Int) {
        try {
            manifest.put(key, value)
            save()
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    /**
     * Adds an element to the manifest
     * @param key
     * @param value
     */
    fun put(key: String, value: String) {
        try {
            manifest.put(key, value)
            save()
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    /**
     * Removes an element from the manifest
     * @param key
     */
    fun remove(key: String) {
        manifest.remove(key)
        save()
    }

    /**
     * Saves the manifest to the disk
     */
    fun save() {
        try {
            FileUtilities.writeStringToFile(manifestFile, manifest.toString())
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * Deletes the manifest file
     */
    private fun delete() {
        manifestFile.delete()
        manifest = JSONObject()
    }

    /**
     * Reads the manifest file from the disk
     */
    fun load() {
        var contents = ""
        try {
            contents = FileUtilities.readFileToString(manifestFile)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        if (contents.isEmpty()) {
            manifest = JSONObject()
        } else {
            try {
                manifest = JSONObject(contents)
            } catch (e: JSONException) {
                e.printStackTrace()
                manifest = JSONObject()
            }
        }
    }

    /**
     * Uniquely merges the values of the array into the manifest
     * @param newArray
     * @param key
     */
    fun join(newArray: JSONArray?, key: String?) {
        if (newArray != null && key != null) {
            try {
                if (!manifest.has(key)) {
                    manifest.put(key, newArray)
                } else {
                    val array = manifest.getJSONArray(key)
                    for (i in 0 until newArray.length()) {
                        val obj = newArray.get(i)
                        if (!hasValueInArray(array, obj)) {
                            array.put(obj)
                        }
                    }
                    manifest.put(key, array)
                }
                save()
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Uniquely merges the keys of the object into the manifest
     * @param newObj
     * @param key
     */
    fun join(newObj: JSONObject?, key: String?) {
        if (newObj != null && key != null) {
            try {
                if (!manifest.has(key)) {
                    manifest.put(key, newObj)
                } else {
                    val obj = manifest.getJSONObject(key)
                    val newKeys = newObj.keys()
                    while (newKeys.hasNext()) {
                        val newObjKey = newKeys.next()
                        if (!obj.has(newObjKey)) {
                            obj.put(newObjKey, newObj.get(newObjKey))
                        }
                    }
                    manifest.put(key, obj)
                }
                save()
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        const val MANIFEST_JSON = "manifest.json"

        /**
         * Generates a new manifest object.
         * If a manifest file already exists it will be loaded otherwise it will be created.
         * @param directory the directory in which the manifest file exists
         * @return the manifest object or null if the manifest could not be created
         */
        fun generate(directory: File): Manifest {
            val file = File(directory, MANIFEST_JSON)
            if (!file.exists()) {
                file.parentFile?.mkdirs()
            }
            if (!file.isFile) {
                try {
                    file.createNewFile()
                } catch (e: IOException) {
                    e.printStackTrace()
                    throw RuntimeException("Could not create manifest file at: ${file.absolutePath}", e)
                }
            }
            val m = Manifest(file)
            m.load()
            return m
        }

        /**
         * Checks if a value exists in the array
         * @param array
         * @param value
         * @return
         */
        private fun hasValueInArray(array: JSONArray?, value: Any?): Boolean {
            if (value != null && array != null) {
                try {
                    for (i in 0 until array.length()) {
                        if (value == array.get(i)) {
                            return true
                        }
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
            return false
        }

        /**
         * Checks if a value exist for the key
         * @param json
         * @param key
         * @return
         */
        fun valueExists(json: JSONObject, key: String): Boolean {
            try {
                if (json.has(key)) {
                    return when (val obj = json.get(key)) {
                        is String -> obj.isNotEmpty()
                        is JSONArray -> obj.length() > 0
                        is JSONObject -> obj.keys().hasNext()
                        else -> true
                    }
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            return false
        }

        /**
         * Removes a string value from an array
         * @param array
         * @param value
         */
        fun removeValue(array: JSONArray, value: String): JSONArray {
            val updatedArray = JSONArray()
            for (i in 0 until array.length()) {
                try {
                    val content = array.getString(i)
                    if (content != value) {
                        updatedArray.put(content)
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
            return updatedArray
        }
    }
}