package org.unfoldingword.door43client.models;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a questionnaire that can be completed in the app
 */
public class Questionnaire {
    public final String languageSlug;
    public final String languageName;
    public final String languageDirection;
    public final long tdId;
    public final Map<String, Long> dataFields;

    /**
     *
     * @param languageSlug the language code
     * @param languageName the name of the language in which this questionnaire is presented
     * @param languageDirection the written direction of the language
     * @param tdId the translation database id (server side)
     * @param dataFields a map of question ids that represent certain language data. e.g. language name, region etc.
     */
    public Questionnaire(String languageSlug, String languageName, String languageDirection, long tdId, Map<String, Long> dataFields) {
        this.languageSlug = languageSlug;
        this.languageName = languageName;
        this.languageDirection = languageDirection;
        this.tdId = tdId;
        this.dataFields = dataFields;
    }

}
