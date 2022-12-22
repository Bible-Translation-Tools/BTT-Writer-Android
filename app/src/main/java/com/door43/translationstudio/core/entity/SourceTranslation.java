package com.door43.translationstudio.core.entity;

import org.unfoldingword.door43client.models.Translation;
import org.unfoldingword.resourcecontainer.Language;
import org.unfoldingword.resourcecontainer.Project;
import org.unfoldingword.resourcecontainer.Resource;
import org.unfoldingword.resourcecontainer.ResourceContainer;

public class SourceTranslation extends Translation {

    private int modifiedTimestamp = -1;

    public SourceTranslation(Language language, Project project, Resource resource) {
        super(language, project, resource);
    }

    public SourceTranslation(ResourceContainer container) {
        super(container);
    }

    public SourceTranslation(Translation translation, int modifiedTimestamp) {
        super(translation.language, translation.project, translation.resource);
        this.modifiedTimestamp = modifiedTimestamp;
    }

    public int getModifiedTimestamp() {
        return modifiedTimestamp;
    }

    public void setModifiedTime(int timestamp) {
        modifiedTimestamp = timestamp;
    }
}
