package com.door43.usecases

import android.text.TextUtils
import com.door43.translationstudio.core.MergeConflictsHandler
import org.unfoldingword.tools.logger.Logger
import java.util.regex.Pattern

object ParseMergeConflicts {
    private const val MERGE_CONFLICT_INNER = "<<<<<<<\\s+HEAD\\n([^<>]*)=======\\n([^<>]*)>>>>>>>.*\\n"
    private const val MERGE_CONFLICT_FALLBACK = "<<<<<<<\\s+HEAD\\n([^<>]*)(=======\\n)?([^<>]*)>>>>>>>.*\\n"
    private const val MERGE_CONFLICT_MIDDLE = "=======.*\\n"

    private val mergeConflictPatternInner = Pattern.compile(MERGE_CONFLICT_INNER)
    private val mergeConflictPatternFallback = Pattern.compile(MERGE_CONFLICT_FALLBACK)
    private val mergeConflictPatternMiddle = Pattern.compile(MERGE_CONFLICT_MIDDLE)

    private val mergeConflictItems = arrayListOf<CharSequence>()

    fun execute(searchText: String): List<CharSequence> {
        mergeConflictItems.clear()
        val fullMergeConflict = false
        var found: Boolean = parseMergeConflicts(searchText)
        if (found) {
            // look for nested changes
            var changeFound = true
            while (changeFound) {
                changeFound = false

                var i = 0
                while (i < mergeConflictItems.size) {
                    val mergeText = mergeConflictItems[i]
                    val mergeConflicted = MergeConflictsHandler.isMergeConflicted(mergeText)
                    if (mergeConflicted) {
                        changeFound = true
                        found = parseMergeConflicts(mergeText)
                        if (found) {
                            mergeConflictItems.removeAt(i) // remove the original since it has been split
                            i-- // back up
                        } else {
                            Logger.e(
                                this::class.java.simpleName,
                                "Failed to extract merge conflict from: $mergeText"
                            )
                        }
                    }
                    i++
                }
            }
        } else { // no merge conflict found
            mergeConflictItems.add(searchText) // only one card found
        }

        // remove duplicates
        for (i in mergeConflictItems.indices) {
            val conflict1 = mergeConflictItems[i]
            var j = i + 1
            while (j < mergeConflictItems.size) {
                val conflict2 = mergeConflictItems[j]
                if (conflict1 == conflict2) {
                    mergeConflictItems.removeAt(j)
                    j-- // backup
                }
                j++
            }
        }

        return mergeConflictItems
    }

    /**
     * parse nested merge conflicts
     *
     * @param searchText
     * @return
     */
    private fun parseMergeConflicts(searchText: CharSequence): Boolean {
        var startPos = 0
        var haveFirstPartOnly = false
        val fullMergeConflict = false

        var matcher = mergeConflictPatternInner.matcher(searchText)
        var found = matcher.find()
        if (!found) {
            matcher = mergeConflictPatternFallback.matcher(searchText)
            found = matcher.find()
        }
        if (!found) {
            return false
        }

        val headTexts = arrayListOf<CharSequence>()
        val mTailText = arrayListOf<CharSequence>()

        while (found) {
            var text: CharSequence? = matcher.group(1)
            if (text == null) {
                text = ""
            }
            var middles = extractMiddles(text, headTexts.size)
            for (i in middles.indices) {
                if (headTexts.size <= i) {
                    headTexts.add("")
                }
                headTexts[i] = TextUtils.concat(
                    headTexts[i],
                    searchText.subSequence(startPos, matcher.start()),
                    middles[i]
                )
            }

            text = matcher.group(2)
            if ((text == null) && (mTailText.size <= 0)) {
                haveFirstPartOnly = true
            } else {
                if (text == null) {
                    text = ""
                }

                middles = extractMiddles(text, mTailText.size)
                for (i in middles.indices) {
                    if (mTailText.size <= i) {
                        mTailText.add("")
                    }
                    mTailText[i] = TextUtils.concat(
                        mTailText[i],
                        searchText.subSequence(startPos, matcher.start()),
                        middles[i]
                    )
                }
            }

            startPos = matcher.end()
            found = matcher.find(startPos)
        }

        val endText = searchText.subSequence(startPos, searchText.length)

        for (i in headTexts.indices) {
            val headText = TextUtils.concat(headTexts[i], endText)
            mergeConflictItems.add(headText)
        }

        for (i in mTailText.indices) {
            val tailText = TextUtils.concat(mTailText[i], endText)
            mergeConflictItems.add(tailText)
        }

        return true
    }

    /**
     * Normally there is just one middle divider, but in multiway merges there can be more.
     * So we check for these.
     * @param searchText
     * @param desiredCount
     * @return
     */
    private fun extractMiddles(searchText: CharSequence, desiredCount: Int): List<CharSequence> {
        val middles = lookForMultipleMiddles(searchText)
        middles as MutableList

        // if no middle markers found, we just use the searchText
        if (middles.isEmpty()) {
            middles.add(searchText)
        }

        // normalize the middles count to match previous
        while (middles.size < desiredCount) {
            middles.add(middles[0]) // clone
        }

        return middles
    }

    /**
     * search for middle markers within text
     * @param searchText
     * @return
     */
    private fun lookForMultipleMiddles(searchText: CharSequence?): List<CharSequence> {
        val middles = arrayListOf<CharSequence>()

        if (searchText == null) {
            return middles
        }
        val mergeConflicted = MergeConflictsHandler.isMergeConflicted(searchText)
        if (mergeConflicted) { // if we have more unprocessed merges, then skip
            return middles
        }

        val mMatcher = mergeConflictPatternMiddle.matcher(searchText)
        var startPos = 0
        var text: CharSequence?
        while (mMatcher.find(startPos)) {
            text = searchText.subSequence(startPos, mMatcher.start())
            middles.add(text)
            startPos = mMatcher.end()
        }

        text = searchText.subSequence(startPos, searchText.length)
        middles.add(text)

        return middles
    }
}