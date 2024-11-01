package com.door43.translationstudio.core

/**
 * Provides some tools for migrating stuff from older versions of the app
 */
object Migration {
    /**
     * Converts an old slug to the new format.
     * The conversion will occur if the old slug does not contain any underscores (the delimiter for new slugs)
     *
     * Feb 27, 2017
     * Note: this update is based on the resource container format v0.1
     *
     * old: project-lang-resource
     * new: lang-project-resource
     *
     * @return the migrated slug
     */
    @JvmStatic
    fun migrateSourceTranslationSlug(slug: String?): String? {
        if (slug == null) return null
        if (slug.contains("_")) return slug

        val pieces = slug.split("-".toRegex())
        if (pieces.size < 3) return slug // cannot process

        val project = pieces[0]
        val resource = pieces[pieces.size - 1]
        var language = ""
        for (i in 1 until pieces.size - 1) {
            if (language != "") language += "-"
            language += pieces[i]
        }

        return language + "_" + project + "_" + resource
    }
}
