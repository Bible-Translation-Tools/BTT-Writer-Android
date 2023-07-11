package org.unfoldingword.door43client.models;

/**
 * Represents a global catalog
 */
public class Catalog {
    public final String slug;
    public final String url;
    public final int modifiedAt;

    /**
     *
     * @param slug the catalog code
     * @param url the url where the catalog exists
     * @param modifiedAt when the catalog was last modified
     */
    public Catalog(String slug, String url, int modifiedAt) {
        this.slug = slug;
        this.url = url;
        this.modifiedAt = modifiedAt;
    }

}
