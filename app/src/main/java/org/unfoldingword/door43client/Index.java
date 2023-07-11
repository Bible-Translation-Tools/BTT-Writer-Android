package org.unfoldingword.door43client;

import org.unfoldingword.door43client.models.Catalog;
import org.unfoldingword.door43client.models.Category;
import org.unfoldingword.door43client.models.CategoryEntry;
import org.unfoldingword.door43client.models.ChunkMarker;
import org.unfoldingword.door43client.models.Question;
import org.unfoldingword.door43client.models.Questionnaire;
import org.unfoldingword.door43client.models.SourceLanguage;
import org.unfoldingword.door43client.models.TargetLanguage;
import org.unfoldingword.door43client.models.Translation;
import org.unfoldingword.door43client.models.Versification;
import org.unfoldingword.resourcecontainer.Project;
import org.unfoldingword.resourcecontainer.Resource;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines the public methods of the index
 */
public interface Index {

    /**
     * Inserts or updates a temporary target language in the library.
     *
     * Note: the result is boolean since you don't need the row id. See getTargetLanguages for more information
     *
     * @param language
     * @return
     * @throws Exception
     */
    boolean addTempTargetLanguage(TargetLanguage language) throws Exception;

    /**
     * Returns a list of source languages and when they were last modified.
     * The value is taken from the max modified resource format date within the language
     *
     * @return {slug, modified_at}
     */
    List<HashMap> listSourceLanguagesLastModified();

    /**
     * Returns a list of projects and when they were last modified
     * The value is taken from the max modified resource format date within the project
     *
     * @param languageSlug the source language who's projects will be selected. If left empty the results will include all projects in all languages.
     * @return
     */
    Map<String, Integer> listProjectsLastModified(String languageSlug);

    /**
     * Returns a translation that matches the resource container slug
     *
     * @param containerSlug
     * @return
     */
    Translation getTranslation(String containerSlug);

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
    List<Translation> findTranslations(String languageSlug, String projectSlug, String resourceSlug, String resourceType, String translateMode, int minCheckingLevel, int maxCheckingLevel);

    /**
     * Returns a list of translations that have been manually imported by the user.
     *
     * @return a list of translations
     */
    List<Translation> getImportedTranslations();

    /**
     * Returns a source language.
     *
     * @param sourceLanguageSlug
     * @return the language object or null if it does not exist
     */
    SourceLanguage getSourceLanguage(String sourceLanguageSlug);

    /**
     * Returns a list of every source language.
     *
     * @return an array of source languages
     */
    List<SourceLanguage> getSourceLanguages();

    /**
     * Returns a list of source languages in which the project exists.
     *
     * @return an array of source languages
     */
    List<SourceLanguage> getSourceLanguages(String projectSlug);

    /**
     * Returns a target language.
     * The result may be a temp target language.
     *
     * Note: does not include the row id. You don't need it
     *
     * @param targetLangaugeSlug
     * @return the language object or null if it does not exist
     */
    TargetLanguage getTargetLanguage(String targetLangaugeSlug);

    /**
     * Searches for a target language by name.
     * @param namequery
     * @return
     */
    List<TargetLanguage> findTargetLanguage(final String namequery);

    /**
     * Returns a list of every target language.
     * The result may include temp target languages.
     *
     * Note: does not include the row id. You don't need it.
     * And we are pulling from two tables so it would be confusing.
     *
     * @return
     */
    List<TargetLanguage> getTargetLanguages();

    /**
     * Returns the target language that has been assigned to a temporary target language.
     *
     * Note: does not include the row id. You don't need it
     *
     * @param tempTargetLanguageSlug the temporary target language with the assignment
     * @return the language object or null if it does not exist
     */
    TargetLanguage getApprovedTargetLanguage(String tempTargetLanguageSlug);

    /**
     * Returns a project with the option of falling back to a default language if not found
     *
     * @param sourceLanguageSlug the source language code for which the project will be returned
     * @param projectSlug the project code
     * @param enableDefaultLanguage allows this method to use the default language if no project is found in this language
     * @return the project object or null
     */
    Project getProject(String sourceLanguageSlug, String projectSlug, boolean enableDefaultLanguage);

    /**
     * Returns a project
     *
     * @param sourceLanguageSlug the source language code for which the project will be returned
     * @param projectSlug the project code
     * @return the project object or null
     */
    Project getProject(String sourceLanguageSlug, String projectSlug);

    /**
     * Returns a list of projects available in the given language.
     *
     * @param sourceLanguageSlug the source language code for which projects will be returned
     * @return an array of projects that are available in the source language
     */
    List<Project> getProjects(String sourceLanguageSlug);

    /**
     * Returns a list of projects in the given language or (if enabled) a default language.
     * The affect is a list of all unique projects with preference given to the specified language
     *
     * @param sourceLanguageSlug the source language code for which projects will be returned
     * @param enableDefaultLanguage if true the default language will be used to fetch the remaining projects
     * @return an array of projects that are available in the source language
     */
    List<Project> getProjects(String sourceLanguageSlug, boolean enableDefaultLanguage);

    /**
     * Returns an array of categories that exist underneath the parent category.
     * The results of this method are a combination of categories and projects.
     *
     * @param parentCategoryId the category who's children will be returned. If 0 then all top level categories will be returned.
     * @param languageSlug the language in which the category titles will be displayed
     * @param translateMode limit the results to just those with the given translate mode. Leave this falsy to not filter
     * @return
     */
    List<CategoryEntry> getProjectCategories(long parentCategoryId, String languageSlug, String translateMode);

    /**
     * Returns a resource
     *
     * @param sourceLanguageSlug
     * @param projectSlug
     * @param resourceSlug
     * @return the Resource object or null if it does not exist
     */
    Resource getResource(String sourceLanguageSlug, String projectSlug, String resourceSlug);

    /**
     * Returns a list of resources available in the given project
     *
     * @param sourcelanguageSlug the language of the resource. If null then all resources of the project will be returned.
     * @param projectSlug the project who's resources will be returned
     * @return
     */
    List<Resource> getResources(String sourcelanguageSlug, String projectSlug);

    /**
     * Returns a catalog
     *
     * @param catalogSlug
     * @return the catalog object or null if it does not exist
     */
    Catalog getCatalog(String catalogSlug);

    /**
     * Returns a list of catalogs
     *
     * @return
     */
    List<Catalog> getCatalogs();

    /**
     * Returns a versification
     *
     * @param sourceLanguageSlug the language code for which the versification will be returned
     * @param versificationSlug
     * @return versification or null
     */
    Versification getVersification(String sourceLanguageSlug, String versificationSlug);

    /**
     * Returns a list of versifications
     *
     * @param sourceLanguageSlug the language code for which versifications will be returned
     * @return
     */
    List<Versification> getVersifications(String sourceLanguageSlug);

    /**
     * Returns a list of chunk markers for a project
     *
     * @param projectSlug
     * @param versificationSlug
     * @return
     */
    List<ChunkMarker> getChunkMarkers(String projectSlug, String versificationSlug);

    /**
     * Returns a questionnaire
     * @param tdId the translation database id (on the server) of the questionnaire
     * @return
     */
    Questionnaire getQuestionnaire(long tdId);

    /**
     * Returns a list of questionnaires
     *
     * @return a list of questionnaires
     */
    List<Questionnaire> getQuestionnaires();

    /**
     * Returns a list of questions in the questionnaire
     *
     * @param questionnaireTDId the parent questionnaire translation database id (server side)
     * @return a list of questions
     */
    List<Question> getQuestions(long questionnaireTDId);

    /**
     * Returns the category with it's localized title.
     * This will return null if there is no matching localized category.
     * This does not necessarily mean the category does not exist.
     *
     * @param languageSlug the language slug in which the category title will be given
     * @param slug the category slug
     * @return the category or null
     */
    Category getCategory(String languageSlug, String slug);

    /**
     * Returns a list of categories in a project
     *
     * @param languageSlug the language in which the category title will be given
     * @param projectSlug the project slug
     * @return a list of categories in the project
     */
    List<Category> getCategories(String languageSlug, String projectSlug);
}
