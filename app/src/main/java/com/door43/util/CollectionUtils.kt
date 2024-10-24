package com.door43.util

import com.door43.translationstudio.core.Util

val sortNumericallyComparator = Comparator<String> { o1, o2 ->
    val lhInt = getIdOrder(o1)
    val rhInt = getIdOrder(o2)
    lhInt.compareTo(rhInt)
}

fun Array<String>.sortNumerically() {
    sortWith(sortNumericallyComparator)
}

fun ArrayList<String>.sortNumerically() {
    sortWith(sortNumericallyComparator)
}

private fun getIdOrder(id: String): Int {
    // if not numeric, then will move to top of list and leave order unchanged
    return Util.strToInt(id, -1)
}