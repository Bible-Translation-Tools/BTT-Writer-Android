package com.door43.translationstudio.core

object BibleCodes {

    private val ntBookList = listOf(
        "mat", "mrk", "luk", "jhn", "act", "rom", "1co", "2co",
        "gal", "eph", "php", "col", "1th", "2th", "1ti", "2ti",
        "tit", "phm", "heb", "jas", "1pe", "2pe", "1jn", "2jn",
        "3jn", "jud", "rev"
    )

    private val otBookList = listOf(
        "gen", "exo", "lev", "num", "deu", "jos", "jdg", "rut",
        "1sa", "2sa", "1ki", "2ki", "1ch", "2ch", "ezr", "neh",
        "est", "job", "psa", "pro", "ecc", "sng", "isa", "jer",
        "lam", "ezk", "dan", "hos", "jol", "amo", "oba", "jon",
        "mic", "nah", "hab", "zep", "hag", "zec", "mal"
    )

    fun getNtBooks(): Array<String> = ntBookList.toTypedArray()

    fun getOtBooks(): Array<String> = otBookList.toTypedArray()

    fun getBibleBooks(): Array<String> {
        return (otBookList + ntBookList).toTypedArray()
    }
}