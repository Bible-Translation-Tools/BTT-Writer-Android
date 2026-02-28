package com.door43.translationstudio.core

/**
 * A utility for sorting slugs in resource container.
 * TRICKY: we sort slightly differently than defined in the spec
 * to ease the translation process.
 */
class SlugSorter {

    private companion object {
        const val NUMERIC_WEIGHT = 6
        const val NON_NUMERIC_WEIGHT = 7
    }

    /**
     * Sorts the slugs in place and returns the list
     * @param slugs the list of slugs to sort
     */
    fun sort(slugs: MutableList<String>): List<String> {
        slugs.sortWith(Comparator { left, right ->
            val leftWeight = getWeight(left)
            val rightWeight = getWeight(right)

            when {
                leftWeight != rightWeight -> leftWeight.compareTo(rightWeight)
                leftWeight == NUMERIC_WEIGHT -> {
                    val leftInt = left.toIntOrNull() ?: 0
                    val rightInt = right.toIntOrNull() ?: 0
                    leftInt.compareTo(rightInt)
                }
                leftWeight == NON_NUMERIC_WEIGHT -> left.compareTo(right)
                else -> 0
            }
        })
        return slugs
    }

    /**
     * Sorts an array of slugs and returns as a list
     */
    fun sort(slugs: Array<String>): List<String> {
        return sort(slugs.toMutableList())
    }

    /**
     * Returns the relative weight of the slug sort.
     * Smaller values float to the top.
     */
    private fun getWeight(slug: String): Int {
        return when (slug) {
            "front" -> 1
            "title" -> 2
            "sub-title" -> 3
            "intro" -> 4
            "reference" -> 5
            "summary" -> 8
            "back" -> 9
            else -> {
                if (slug.toIntOrNull() != null) NUMERIC_WEIGHT else NON_NUMERIC_WEIGHT
            }
        }
    }
}