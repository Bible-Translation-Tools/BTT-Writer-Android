package com.door43.translationstudio.ui.spannables

import org.unfoldingword.tools.logger.Logger
import java.util.regex.Pattern

class PassageLinkSpan(
    title: String,
    var address: String
) : Span(title, address) {

    private var _title: String = title
    fun getTitle(): String = _title

    var languageId: String? = null
        private set

    var projectId: String? = null
        private set

    lateinit var chapterId: String
        private set

    lateinit var frameId: String
        private set

    companion object {
        // e.g. [[:en:bible:notes:gen:01:03|1:5]]
        val PATTERN: Pattern = Pattern.compile("\\[\\[:(((?!]]).)*)\\|(((?!]]).)*)]]")
    }

    init {
        explodeAddress(address)
    }

    /**
     * Changes the title of the passage link
     * @param title the new title
     */
    fun setTitle(title: String) {
        this._title = title
        humanReadable = title
    }

    /**
     * Breaks the address apart into its components
     * @param address the link address to explode
     */
    private fun explodeAddress(address: String) {
        val parts = address.split(":")
        if (parts.size == 6 && parts[1] == "bible") {
            // example: en:bible:notes:gen:03:04
            languageId = parts[0]
            projectId = parts[3]
            chapterId = parts[4]
            frameId = parts[5]
        } else if (parts.size == 5 && parts[3] == "frames") {
            // example: en:obs:notes:frames:01-11
            val chapterFrame = parts[4].split("-")
            if (chapterFrame.size == 2) {
                languageId = parts[0]
                projectId = parts[1]
                chapterId = chapterFrame[0]
                frameId = chapterFrame[1]
            }
        } else {
            Logger.w(this.javaClass.name, "invalid passage link address $address")
        }
    }

    // /**
    //  * Returns the human-readable name of the link
    //  * @param rawLink
    //  * @return
    //  */
    // fun parseLink(rawLink: String) {
    //     val matcher = PATTERN.matcher(rawLink)
    //     while(matcher.find()) {
    //         title = matcher.group(3)
    //         address = matcher.group(2)
    //         explodeAddress(address)
    //     }
    // }
}
