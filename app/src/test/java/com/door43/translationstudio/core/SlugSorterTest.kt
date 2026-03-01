package com.door43.translationstudio.core

import org.junit.Assert
import org.junit.Test

class SlugSorterTest {
    @Test
    fun sort() {
        val sorter = SlugSorter()
        val slugs: MutableList<String> = ArrayList()

        slugs.add("01")
        slugs.add("06")
        slugs.add("02")
        slugs.add("11")
        slugs.add("back")
        slugs.add("07")
        slugs.add("front")
        slugs.add("03")
        slugs.add("reference")
        slugs.add("title")

        sorter.sort(slugs)

        Assert.assertEquals("front", slugs[0])
        Assert.assertEquals("title", slugs[1])
        Assert.assertEquals("reference", slugs[2])
        Assert.assertEquals("01", slugs[3])
        Assert.assertEquals("02", slugs[4])
        Assert.assertEquals("03", slugs[5])
        Assert.assertEquals("06", slugs[6])
        Assert.assertEquals("07", slugs[7])
        Assert.assertEquals("11", slugs[8])
        Assert.assertEquals("back", slugs[9])
    }
}
