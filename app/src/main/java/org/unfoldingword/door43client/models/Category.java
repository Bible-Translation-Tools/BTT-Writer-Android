package org.unfoldingword.door43client.models;

/**
 * Represents a project category. e.g. a group of projects.
 */
public class Category {
    public final String slug;
    public final String name;

    /**
     *
     * @param slug the category code
     * @param name the name of the category
     */
    public Category(String slug, String name) {
        this.name = name;
        this.slug = slug;
    }

}
