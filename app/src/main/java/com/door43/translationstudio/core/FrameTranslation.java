package com.door43.translationstudio.core;

/**
 * Represents a translation of a frame
 */
public class FrameTranslation {
    public final String id;
    public final String body;
    private final String chapterId;
    private final boolean finished;
    private final TranslationFormat format;

    public FrameTranslation(String frameId, String chapterId, String body, TranslationFormat format, boolean finished) {
        this.chapterId = chapterId;
        this.body = body;
        this.format = format;
        this.id = frameId;
        this.finished = finished;
    }

    /**
     * Returns the title of the frame translation
     * @return
     */
    public String getTitle() {
        // get verse range
        int[] verses = Frame.getVerseRange(body, format);
        String title;
        if (verses.length == 1) {
            title = verses[0] + "";
        } else if (verses.length == 2) {
            title = verses[0] + "-" + verses[1];
        } else {
            title = Integer.parseInt(id) + "";
        }
        return title;
    }

    /**
     * Returns the complex chapter-frame id
     * @return
     */
    public String getComplexId() {
        return chapterId + "-" + id;
    }

    /**
     * Returns the id of the chapter to which this frame belongs
     * @return
     */
    public String getChapterId() {
        return chapterId;
    }

    /**
     * Returns the format of the text
     * @return
     */
    public TranslationFormat getFormat() {
        return format;
    }

    /**
     * Checks if the translation is finished
     * @return
     */
    public boolean isFinished() {
        return finished;
    }

}
