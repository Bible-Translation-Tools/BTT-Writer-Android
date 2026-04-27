package com.door43.translationstudio.core.manifest

import com.door43.util.FileUtilities
import kotlinx.io.IOException
import java.io.File

class ManifestAccessor(private val manifestFile: File) {
    var manifest = generate()
        private set

    /**
     * Generates a new manifest object.
     * If a manifest file already exists it will be loaded otherwise it will be created.
     * @return the manifest object or throw if the manifest could not be created
     */
    private fun generate(): Manifest {
        if (!manifestFile.exists()) {
            manifestFile.parentFile?.mkdirs()
        }
        if (!manifestFile.isFile) {
            try {
                manifestFile.createNewFile()
            } catch (e: IOException) {
                e.printStackTrace()
                throw RuntimeException("Could not create manifest file at: ${manifestFile.absolutePath}", e)
            }
        }
        return load()
    }

    /**
     * Reads the manifest file from the disk
     */
    fun load(): Manifest {
        return try {
            val contents = FileUtilities.readFileToString(manifestFile)
            if (contents.isNotBlank()) {
                manifestJson.decodeFromString(contents)
            } else {
                buildManifest{}
            }
        } catch (e: IOException) {
            e.printStackTrace()
            buildManifest{}
        }
    }

    /**
     * Saves the manifest to the disk
     */
    fun save() {
        try {
            val jsonStr = manifestJson.encodeToString(manifest)
            FileUtilities.writeStringToFile(manifestFile, jsonStr)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * Updates and save the manifest to the disk
     */
    fun save(manifest: Manifest) {
        this.manifest = manifest
        save()
    }

    /**
     * Reloads the manifest from the disk
     */
    fun reload() {
        manifest = load()
    }

    /**
     * Deletes the manifest file
     */
    fun delete() {
        manifestFile.delete()
        manifest = buildManifest{}
    }
}