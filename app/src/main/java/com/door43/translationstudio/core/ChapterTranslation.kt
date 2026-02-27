package com.door43.translationstudio.core;

/**
 * Created by joel on 9/16/2015.
 */
public class ChapterTranslation {

    public final String reference;
    public final String title;
    public final String id;
    private final boolean titleFinished;
    private final boolean referenceFinished;
    private final TranslationFormat translationFormat;

    public ChapterTranslation(String title, String reference, String chapterId, boolean titleFinished, boolean referenceFinished, TranslationFormat translationFormat) {
        this.title = title;
        this.reference = reference;
        this.id = chapterId;
        this.titleFinished = titleFinished;
        this.referenceFinished = referenceFinished;
        this.translationFormat = translationFormat;
    }

    /**
     * Checks if the chapter reference is finished being translated
     * @return
     */
    public boolean isReferenceFinished() {
        return this.referenceFinished;
    }

    /**
     * Checks if the chapter title is finished being translated
     * @return
     */
    public boolean isTitleFinished() {
        return this.titleFinished;
    }

    /**
     * Returns the translation format for the chapter title and reference
     * @return
     */
    public TranslationFormat getFormat() {
        return translationFormat;
    }
}
