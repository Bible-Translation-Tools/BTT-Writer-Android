package org.unfoldingword.door43client.models;

import org.unfoldingword.resourcecontainer.ContainerTools;
import org.unfoldingword.resourcecontainer.Language;
import org.unfoldingword.resourcecontainer.Project;
import org.unfoldingword.resourcecontainer.Resource;
import org.unfoldingword.resourcecontainer.ResourceContainer;

/**
 * A Translation is a special abstraction of a ResourceContainer.
 * Translations are composed of a language, project, and resource.
 * As such a translation uniquely represents a single resource container,
 * though the existence of a translation does not demand the existence of a resource container.
 */

public class Translation {
    public final Language language;
    public final Project project;
    public final Resource resource;

    /**
     * The slug of the resource container represented by this translation
     */
    public final String resourceContainerSlug;

    public Translation(Language language, Project project, Resource resource) {
        this.language = language;
        this.project = project;
        this.resource = resource;

        resourceContainerSlug = ContainerTools.makeSlug(language.slug, project.slug, resource.slug);
    }

    /**
     * Creates a translation from a resource container
     * @param container
     */
    public Translation(ResourceContainer container) {
        this.language = container.language;
        this.project = container.project;
        this.resource = container.resource;

        resourceContainerSlug = ContainerTools.makeSlug(language.slug, project.slug, resource.slug);
    }
}
