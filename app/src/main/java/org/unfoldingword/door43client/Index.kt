package org.unfoldingword.door43client

import org.unfoldingword.door43client.models.Catalog
import org.unfoldingword.door43client.models.Category
import org.unfoldingword.door43client.models.CategoryEntry
import org.unfoldingword.door43client.models.ChunkMarker
import org.unfoldingword.door43client.models.Question
import org.unfoldingword.door43client.models.Questionnaire
import org.unfoldingword.door43client.models.SourceLanguage
import org.unfoldingword.door43client.models.TargetLanguage
import org.unfoldingword.door43client.models.Translation
import org.unfoldingword.door43client.models.Versification
import org.unfoldingword.resourcecontainer.Project
import org.unfoldingword.resourcecontainer.Resource

/**
 * Defines the public methods of the index
 */
interface Index {

    /**
     * Inserts or updates a temporary target language in the library.
     *
     * Note: the result is boolean since you don't need the row id. See getTargetLanguages for more information
     *
     * @param language
     * @return
     * @throws Exception
     */
    @Throws(Exception::class)
    fun addTempTargetLanguage(language: TargetLanguage): Boolean

    /**
     * Returns a list of source languages and when they were last modified.
     * The value is taken from the max modified resource format date within the language
     *
     * @return {slug, modified_at}
     */
    fun listSourceLanguagesLastModified(): List<Map<String, Any>>

    /**
     * Returns a list of projects and when they were last modified
     * The value is taken from the max modified resource format date within the project
     *
     * @param languageSlug the source language who's projects will be selected. If left empty the results will include all projects in all languages.
     * @return
     */
    fun listProjectsLastModified(languageSlug: String?): Map<String, Int>

    /**
     * Returns a translation that matches the resource container slug
     *
     * @param containerSlug
     * @return
     */
    fun getTranslation(containerSlug: String): Translation?

    /**
     * Returns a list of translations available for the project
     *
     * @param languageSlug the language these translations are available in. Leave null for all.
     * @param projectSlug the project for whom these translations are available. Leave null for all
     * @param resourceSlug the resource for whom these translations are available. Leave null for all
     * @param resourceType the resource type allowed for returned translations. Leave null for all.
     * @param translateMode limit the results to just those with the given translate mode. Leave null for all
     * @param minCheckingLevel the minimum checking level allowed for returned translations. Use 0 for no minimum
     * @param maxCheckingLevel the maximum checking level allowed for returned translations. Use -1 for no maximum
     * @return a list of matching translations
     */
    fun findTranslations(
        languageSlug: String?,
        projectSlug: String?,
        resourceSlug: String?,
        resourceType: String?,
        translateMode: String?,
        minCheckingLevel: Int,
        maxCheckingLevel: Int
    ): List<Translation>

    /**
     * Returns a list of translations that have been manually imported by the user.
     *
     * @return a list of translations
     */
    fun getImportedTranslations(): List<Translation>

    /**
     * Returns a source language.
     *
     * @param sourceLanguageSlug
     * @return the language object or null if it does not exist
     */
    fun getSourceLanguage(sourceLanguageSlug: String): SourceLanguage?

    /**
     * Inserts or updates a source language in the library.
     *
     * @param language
     * @return the id of the source language row
     * @throws Exception
     */
    @Throws(Exception::class)
    fun addSourceLanguage(language: SourceLanguage): Long

    /**
     * Returns a list of every source language.
     *
     * @return an array of source languages
     */
    fun getSourceLanguages(): List<SourceLanguage>

    /**
     * Returns a list of source languages in which the project exists.
     *
     * @return an array of source languages
     */
    fun getSourceLanguages(projectSlug: String): List<SourceLanguage>

    /**
     * Returns a target language.
     * The result may be a temp target language.
     *
     * Note: does not include the row id. You don't need it
     *
     * @param targetLanguageSlug
     * @return the language object or null if it does not exist
     */
    fun getTargetLanguage(targetLanguageSlug: String): TargetLanguage?

    /**
     * Inserts or updates a target language in the library.
     * Note: the result is boolean since you don't need the row id. See getTargetLanguages for more information
     *
     * @param language
     * @return
     * @throws Exception
     */
    @Throws(Exception::class)
    fun addTargetLanguage(language: TargetLanguage): Boolean

    /**
     * Searches for a target language by name.
     * @param nameQuery
     * @return
     */
    fun findTargetLanguage(nameQuery: String): List<TargetLanguage>

    /**
     * Returns a list of every target language.
     * The result may include temp target languages.
     *
     * Note: does not include the row id. You don't need it.
     * And we are pulling from two tables so it would be confusing.
     *
     * @return
     */
    fun getTargetLanguages(): List<TargetLanguage>

    /**
     * Returns the target language that has been assigned to a temporary target language.
     *
     * Note: does not include the row id. You don't need it
     *
     * @param tempTargetLanguageSlug the temporary target language with the assignment
     * @return the language object or null if it does not exist
     */
    fun getApprovedTargetLanguage(tempTargetLanguageSlug: String): TargetLanguage?

    /**
     * Returns a project with the option of falling back to a default language if not found
     *
     * @param sourceLanguageSlug the source language code for which the project will be returned
     * @param projectSlug the project code
     * @param enableDefaultLanguage allows this method to use the default language if no project is found in this language
     * @return the project object or null
     */
    fun getProject(
        sourceLanguageSlug: String,
        projectSlug: String,
        enableDefaultLanguage: Boolean = false
    ): Project?

    /**
     * Returns a list of projects in the given language or (if enabled) a default language.
     * The affect is a list of all unique projects with preference given to the specified language
     *
     * @param sourceLanguageSlug the source language code for which projects will be returned
     * @param enableDefaultLanguage if true the default language will be used to fetch the remaining projects
     * @return an array of projects that are available in the source language
     */
    fun getProjects(
        sourceLanguageSlug: String,
        enableDefaultLanguage: Boolean = false
    ): List<Project>

    /**
     * Inserts or updates a project in the library
     *
     * @param project
     * @param categories this is the category branch that the project will attach to
     * @param sourceLanguageId the parent source language row id
     * @return the id of the project row
     * @throws Exception
     */
    @Throws(Exception::class)
    fun addProject(project: Project, categories: List<Category>?, sourceLanguageId: Long): Long

    /**
     * Returns an array of categories that exist underneath the parent category.
     * The results of this method are a combination of categories and projects.
     *
     * @param parentCategoryId the category who's children will be returned. If 0 then all top level categories will be returned.
     * @param languageSlug the language in which the category titles will be displayed
     * @param translateMode limit the results to just those with the given translate mode. Leave this falsy to not filter
     * @return
     */
    fun getProjectCategories(
        parentCategoryId: Long,
        languageSlug: String,
        translateMode: String?
    ): List<CategoryEntry>

    /**
     * Returns a resource
     *
     * @param sourceLanguageSlug
     * @param projectSlug
     * @param resourceSlug
     * @return the Resource object or null if it does not exist
     */
    fun getResource(sourceLanguageSlug: String, projectSlug: String, resourceSlug: String): Resource?

    /**
     * Inserts or updates a resource in the library.
     *
     * @param resource the resource being indexed
     * @param projectId the parent project row id
     * @return the id of the resource row
     * @throws Exception
     */
    @Throws(Exception::class)
    fun addResource(resource: Resource, projectId: Long): Long

    /**
     * Returns a list of resources available in the given project
     *
     * @param sourceLanguageSlug the language of the resource. If null then all resources of the project will be returned.
     * @param projectSlug the project whose resources will be returned
     * @return
     */
    fun getResources(sourceLanguageSlug: String?, projectSlug: String): List<Resource>

    /**
     * Returns a catalog
     *
     * @param catalogSlug
     * @return the catalog object or null if it does not exist
     */
    fun getCatalog(catalogSlug: String): Catalog?

    /**
     * Inserts or updates a catalog in the library.
     *
     * @param catalog
     * @return the id of the catalog
     * @throws Exception
     */
    @Throws(Exception::class)
    fun addCatalog(catalog: Catalog): Long

    /**
     * Returns a list of catalogs
     *
     * @return
     */
    fun getCatalogs(): List<Catalog>

    /**
     * Returns a versification
     *
     * @param sourceLanguageSlug the language code for which the versification will be returned
     * @param versificationSlug
     * @return versification or null
     */
    fun getVersification(sourceLanguageSlug: String, versificationSlug: String): Versification?

    /**
     * Inserts or updates a versification in the library.
     *
     * @param versification
     * @param sourceLanguageId the parent source language row id
     * @return the id of the versification or -1
     * @throws Exception
     */
    @Throws(Exception::class)
    fun addVersification(versification: Versification, sourceLanguageId: Long): Long

    /**
     * Returns a list of versifications
     *
     * @param sourceLanguageSlug the language code for which versifications will be returned
     * @return
     */
    fun getVersifications(sourceLanguageSlug: String): List<Versification>

    /**
     * Returns a list of chunk markers for a project
     *
     * @param projectSlug
     * @param versificationSlug
     * @return
     */
    fun getChunkMarkers(projectSlug: String, versificationSlug: String): List<ChunkMarker>

    /**
     * Returns a questionnaire
     * @param tdId the translation database id (on the server) of the questionnaire
     * @return
     */
    fun getQuestionnaire(tdId: Long): Questionnaire?

    /**
     * Returns a list of questionnaires
     *
     * @return a list of questionnaires
     */
    fun getQuestionnaires(): List<Questionnaire>

    /**
     * Returns a list of questions in the questionnaire
     *
     * @param questionnaireTDId the parent questionnaire translation database id (server side)
     * @return a list of questions
     */
    fun getQuestions(questionnaireTDId: Long): List<Question>

    /**
     * Returns the category with it's localized title.
     * This will return null if there is no matching localized category.
     * This does not necessarily mean the category does not exist.
     *
     * @param languageSlug the language slug in which the category title will be given
     * @param slug the category slug
     * @return the category or null
     */
    fun getCategory(languageSlug: String, slug: String): Category?

    /**
     * Returns a list of categories in a project
     *
     * @param languageSlug the language in which the category title will be given
     * @param projectSlug the project slug
     * @return a list of categories in the project
     */
    fun getCategories(languageSlug: String, projectSlug: String): List<Category>

    /**
     * Temporary ends the transaction to let other threads run.
     */
    fun yieldSafely()
}