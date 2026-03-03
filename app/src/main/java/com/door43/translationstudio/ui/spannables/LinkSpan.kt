package com.door43.translationstudio.ui.spannables

class LinkSpan(
    val title: String,
    val address: String,
    val type: String
) : Span(title, address) {

    /**
     * Changes the title of the link
     * @param newTitle the new title to be set
     */
    fun setTitle(newTitle: String) {
        humanReadable = newTitle
    }
}
