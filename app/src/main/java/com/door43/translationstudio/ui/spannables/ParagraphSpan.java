package com.door43.translationstudio.ui.spannables;

public class ParagraphSpan extends Span {

    ParagraphSpan(CharSequence humanReadable, CharSequence machineReadable) {
        super(humanReadable, machineReadable);
        super.setClickable(false);
    }
}
