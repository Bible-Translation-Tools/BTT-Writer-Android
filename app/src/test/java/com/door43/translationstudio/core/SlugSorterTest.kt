package com.door43.translationstudio.core

import org.junit.Assert
import org.junit.Test

class SlugSorterTest {
    @Test
    fun sort() {
        val sorter = SlugSorter()
        val slugs: List<String> = buildList {
            add("01")
            add("06")
            add("02")
            add("11")
            add("back")
            add("07")
            add("front")
            add("03")
            add("reference")
            add("title")
        }

        val sorted = sorter.sort(slugs)

        Assert.assertEquals("front", sorted[0])
        Assert.assertEquals("title", sorted[1])
        Assert.assertEquals("reference", sorted[2])
        Assert.assertEquals("01", sorted[3])
        Assert.assertEquals("02", sorted[4])
        Assert.assertEquals("03", sorted[5])
        Assert.assertEquals("06", sorted[6])
        Assert.assertEquals("07", sorted[7])
        Assert.assertEquals("11", sorted[8])
        Assert.assertEquals("back", sorted[9])
    }
}
