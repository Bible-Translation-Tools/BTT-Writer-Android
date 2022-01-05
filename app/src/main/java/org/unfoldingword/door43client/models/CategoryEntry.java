package org.unfoldingword.door43client.models;

/**
 * Represents a single entry in the list of project/categories
 * i.e. when you are choosing project to translate.
 */
public class CategoryEntry {

    public final Type entryType;
    public final long id;
    public final String slug;
    public final String name;
    public final String sourceLanguageSlug;
    public final long parentCategoryId;

    /**
     *
     * @param entryType the type of entry this is e.g. a project or category
     * @param id the db id of the project/category
     * @param slug the slug of the project/category
     * @param name the human readable name of the project/category
     * @param sourceLanguageSlug the slug of the source language in which the name is given (e.g. the name is translated in German, or French)
     * @param parentCategoryId the db id of the parent category id (only used when the entry type is category
     */
    public CategoryEntry(Type entryType, long id, String slug, String name, String sourceLanguageSlug, long parentCategoryId) {

        this.entryType = entryType;
        this.id = id;
        this.slug = slug;
        this.name = name;
        this.sourceLanguageSlug = sourceLanguageSlug;
        this.parentCategoryId = parentCategoryId;
    }

    public enum Type {
        PROJECT,
        CATEGORY
    }
}
