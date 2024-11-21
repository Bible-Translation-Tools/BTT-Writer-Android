package com.door43.usecases

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ParseMergeConflictsTest {

    @Test
    fun `test parse merge conflicts exist`() {
        val conflictText = """
            This is normal text
            <<<<<<< HEAD
            This is a local change
            =======
            This is a remote change
            >>>>>>> refs/heads/new
            
        """.trimIndent()

        val conflictItems = ParseMergeConflicts.execute(conflictText)

        assertEquals(2, conflictItems.size)

        assertNotNull(conflictItems.singleOrNull { it.contains("This is a local change") })
        assertNotNull(conflictItems.singleOrNull { it.contains("This is a remote change") })
    }
}