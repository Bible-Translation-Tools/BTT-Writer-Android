package com.door43.translationstudio

import com.door43.translationstudio.core.Migration.migrateSourceTranslationSlug
import junit.framework.TestCase.assertEquals
import org.junit.Test

class MigrationTest {

    @Test
    @Throws(Exception::class)
    fun migrateSourceTranslationSlugTest() {
        val simple = migrateSourceTranslationSlug("mat-en-udb")
        assertEquals("en_mat_udb", simple)

        val complex = migrateSourceTranslationSlug("mat-pt-br-udb")
        assertEquals("pt-br_mat_udb", complex)

        // unable to parse
        val skip = migrateSourceTranslationSlug("mat_pt-br-udb")
        assertEquals("mat_pt-br-udb", skip)

        // invalid slug
        val invalid = migrateSourceTranslationSlug("mat-udb")
        assertEquals("mat-udb", invalid)
    }
}
