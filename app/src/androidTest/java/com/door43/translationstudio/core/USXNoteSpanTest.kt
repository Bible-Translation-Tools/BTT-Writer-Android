package com.door43.translationstudio.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.door43.translationstudio.IntegrationTest
import com.door43.translationstudio.ui.spannables.USXNoteSpan
import junit.framework.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
@IntegrationTest
class USXNoteSpanTest {
    @Test
    fun testParseNote() {
        val usx = """
                <note caller="+" style="f">
                  <char style="ft">Leading text </char>
                  <char style="fqa">Quoted Text </char>trailing text 
                  <char style="fqa">More quoted text </char>more trailing text
                </note>
                """.trimIndent()
        val text =
            "Leading text \"Quoted Text\" trailing text \"More quoted text\" more trailing text"
        val span = USXNoteSpan.parseNote(usx)
        Assert.assertNotNull(span)
        Assert.assertEquals(text, span!!.notes)
    }
}
