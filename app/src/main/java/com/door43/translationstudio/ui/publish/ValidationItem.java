package com.door43.translationstudio.ui.publish;

import com.door43.translationstudio.core.TranslationFormat;

import org.unfoldingword.door43client.models.SourceLanguage;
import org.unfoldingword.door43client.models.TargetLanguage;

/**
 * A thin wrapper to represent a validation set on a translation
 */
public class ValidationItem {
    private final String title;
    private final String body;
    private final boolean range;
    private final boolean valid;
    private final boolean isFrame;
    private String targetTranslationId;
    private String chapterId;
    private String frameId;
    private TargetLanguage bodyLanguage;
    private SourceLanguage titleLanguage;
    private TranslationFormat bodyFormat;

    private ValidationItem(String title, SourceLanguage titleLanguage, String body, boolean range, boolean valid, boolean isFrame) {
        this.title = title;
        this.body = body;
        this.range = range;
        this.valid = valid;
        this.isFrame = isFrame;
        this.titleLanguage = titleLanguage;
    }

    /**
     * Generates a new valid item
     * @param title
     * @param range
     */
    public static ValidationItem generateValidFrame(String title, SourceLanguage titleLanguage, boolean range) {
        return new ValidationItem(title,titleLanguage,  "", range, true, true);
    }

    /**
     * Generates a new valid item
     * @param title
     * @param range
     */
    public static ValidationItem generateValidGroup(String title, SourceLanguage titleLanguage, boolean range) {
        return new ValidationItem(title, titleLanguage, "", range, true, false);
    }

    /**
     * Generates a new invalid item
     * @param title
     * @param body
     * @param bodyLanguage
     * @param targetTranslationId
     * @param chapterId
     * @param frameId
     */
    public static ValidationItem generateInvalidFrame(String title, SourceLanguage titleLanguage, String body, TargetLanguage bodyLanguage, TranslationFormat bodyFormat, String targetTranslationId, String chapterId, String frameId) {
        ValidationItem item = new ValidationItem(title, titleLanguage, body, false, false, true);
        item.targetTranslationId = targetTranslationId;
        item.chapterId = chapterId;
        item.frameId = frameId;
        item.bodyFormat = bodyFormat;
        item.bodyLanguage = bodyLanguage;
        return item;
    }

    /**
     * Generates a new invalid item
     * For our purposes a group can be either a chapter or a project
     * @param title
     * @param titleLanguage
     */
    public static ValidationItem generateInvalidGroup(String title, SourceLanguage titleLanguage) {
        return new ValidationItem(title, titleLanguage, "", false, false, false);
    }

    /**
     * Returns the title of the validation item
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns the body text of the validation item
     * this only applies to invalid items
     */
    public String getBody() {
        return body;
    }

    /**
     * Checks if the validation item is over a range
     */
    public boolean isRange() {
        return range;
    }

    /**
     * Checks if the validation item is valid
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * Checks if the validation items represents a frame
     */
    public boolean isFrame() {
        return isFrame;
    }

    /**
     * Returns the target translation id
     */
    public String getTargetTranslationId() {
        return targetTranslationId;
    }

    /**
     * Returns the chapter id
     */
    public String getChapterId() {
        return chapterId;
    }

    /**
     * Returns the frame id
     */
    public String getFrameId() {
        return frameId;
    }

    /**
     * Returns the translation format of the body
     */
    public TargetLanguage getBodyLanguage() {
        return bodyLanguage;
    }

    public TranslationFormat getBodyFormat() {
        return bodyFormat;
    }

    public SourceLanguage getTitleLanguage() {
        return titleLanguage;
    }
}
