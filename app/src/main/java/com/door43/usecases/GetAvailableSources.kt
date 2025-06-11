package com.door43.usecases

import com.door43.OnProgressListener
import com.door43.translationstudio.App
import com.door43.translationstudio.core.BibleCodes
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.Translation
import java.util.TreeMap
import javax.inject.Inject

class GetAvailableSources @Inject constructor(
    private val library: Door43Client
) {
    data class Result(
        val sources: List<Translation>,
        val byLanguage: Map<String, List<Int>>,
        val otBooks: Map<String, List<Int>>,
        val ntBooks: Map<String, List<Int>>,
        val otherBooks: Map<String, List<Int>>,
    )

    fun execute(prefix: String, progressListener: OnProgressListener? = null): Result {
        val max = 100

        val ntBookList = BibleCodes.getNtBooks()
        val otBookList = BibleCodes.getOtBooks()

        progressListener?.onProgress(-1, max, "")

        val sources = library.index.findTranslations(
            null,
            null,
            null,
            "book",
            null,
            App.MIN_CHECKING_LEVEL,
            -1
        )

        val tw = library.index.findTranslations(
            null,
            null,
            null,
            "dict",
            null,
            App.MIN_CHECKING_LEVEL,
            -1
        )

        sources.addAll(tw)

//        02/20/2017 - for now we are disabling updating of TA since a major change coming up could break the app
//        List<Translation> man = library.index.findTranslations(null, null, null, "man", null, App.MIN_CHECKING_LEVEL, -1);
//        availableTranslations.addAll(man);

        val byLanguage = TreeMap<String, ArrayList<Int>>()
        val maxProgress = sources.size

        // initialize NT book list
        val ntBooks = LinkedHashMap<String, ArrayList<Int>>()
        for (book in ntBookList) {
            val books = arrayListOf<Int>()
            ntBooks[book] = books
        }

        // initialize OT book list
        val otBooks = LinkedHashMap<String, ArrayList<Int>>()
        for (book in otBookList) {
            val books = arrayListOf<Int>()
            otBooks[book] = books
        }
        val otherBooks = LinkedHashMap<String, ArrayList<Int>>()

        for (i in 0 until maxProgress) {
            val t: Translation = sources[i]

            if (i % 16 == 0) {
                progressListener?.onProgress(i, maxProgress, prefix)
            }

            val language = t.language.slug

            //add to language
            if (byLanguage.containsKey(language)) {
                byLanguage[language]!!.add(i)
            } else {
                val translations = arrayListOf<Int>()
                translations.add(i)
                byLanguage[language] = translations
            }

            //add to book list
            val book = t.project.slug

            if (ntBooks.containsKey(book)) { // if NT book
                ntBooks[book]!!.add(i)
            } else if (otBooks.containsKey(book)) { // if OT book
                otBooks[book]!!.add(i)
            } else { // other
                if ("ta-" != book.substring(0, 3)) {
                    addToOtherBook(otherBooks, i, book)
                }
            }
        }

        return Result(
            sources,
            byLanguage,
            otBooks,
            ntBooks,
            otherBooks
        )
    }

    /**
     * add index to book list
     * @param booksList
     * @param index
     * @param book
     */
    private fun addToOtherBook(
        booksList: LinkedHashMap<String, ArrayList<Int>>,
        index: Int,
        book: String
    ) {
        if (booksList.containsKey(book)) { // if book already present, add source to it
            booksList[book]!!.add(index)
        } else {
            val books = arrayListOf<Int>() // if book not present, create new book entry
            books.add(index)
            booksList[book] = books
        }
    }
}