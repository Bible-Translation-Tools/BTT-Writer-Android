package org.unfoldingword.door43client.models;

/**
 * Represents a versification system.
 * This is what chunk markers are based on.
 */
public class Versification {
    public String slug;
    public String name;
    public long rowId = -1;

    public Versification(String slug, String name) {
        this.slug = slug;
        this.name = name;
    }

}
