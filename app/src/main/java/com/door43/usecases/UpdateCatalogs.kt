package com.door43.usecases

import android.content.Context
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.R
import org.bibletranslationtools.logger.Logger
import org.bibletranslationtools.resourcecatalog.ResourceCatalogClient
import org.bibletranslationtools.resourcecatalog.library.models.Catalog
import org.bibletranslationtools.resourcecatalog.library.models.CatalogType

class UpdateCatalogs(
    private val context: Context,
    private val catalogClient: ResourceCatalogClient,
    private val prefRepository: IPreferenceRepository
) {
    data class Result(val success: Boolean, val addedCount: Int)

    suspend fun execute(
        force: Boolean,
        onProgress: (Float, String?) -> Unit = {_,_->}
    ): Result {
        var addedCount = 0
        var success = false

        var targetLanguages = catalogClient.library.getTargetLanguages()
        val initialLanguages = HashSet<String>()
        for (l in targetLanguages) {
            initialLanguages.add(l.slug)
        }

        Logger.i(this.javaClass.simpleName, "Initial target languages count: " + targetLanguages.size)
        Logger.i(
            this.javaClass.simpleName,
            "Unique target languages slug count: " + initialLanguages.size
        )

        try {
            val catalogs = if (force) {
                val languageUrl = prefRepository.getDefaultPref(
                    IPreferenceRepository.KEY_PREF_LANGUAGES_URL,
                    context.resources.getString(R.string.pref_default_language_url)
                )
                val tempLanguagesUrl = "https://td.unfoldingword.org/api/templanguages/"
                val approvedLanguagesUrl = "https://td.unfoldingword.org/api/templanguages/assignment/changed/"

                buildList {
                    add(Catalog(CatalogType.TARGET_LANGUAGES, languageUrl, 0))
                    add(Catalog(CatalogType.TEMP_LANGUAGES, tempLanguagesUrl, 0))
                    add(Catalog(CatalogType.APPROVED_LANGUAGES, approvedLanguagesUrl, 0))
                }
            } else emptyList()
            catalogClient.updateCatalogs(catalogs) { value, message ->
                onProgress(value, message)
            }
            success = true
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (success) {
            targetLanguages = catalogClient.library.getTargetLanguages()
            Logger.i(
                this.javaClass.simpleName,
                "Final target languages count: " + targetLanguages.size
            )
            for (l in targetLanguages) {
                if (!initialLanguages.contains(l.slug)) {
                    addedCount++
                    Logger.i(
                        this.javaClass.simpleName,
                        "New target languages " + addedCount + ": " + l.slug
                    )
                }
            }
        }

        return Result(success, addedCount)
    }
}