package com.door43.translationstudio;

import androidx.test.filters.SmallTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.door43.translationstudio.ui.spannables.USXNoteSpan;

import org.junit.Test;
import org.junit.runner.RunWith;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class USXNoteSpanTest {

    @Test
    public void testParseNote() {
        String usx = """
                <note caller="+" style="f">
                  <char style="ft">Leading text </char>
                  <char style="fqa">Quoted Text </char>trailing text\s
                  <char style="fqa">More quoted text </char>more trailing text
                </note>""";
        String text = "Leading text \"Quoted Text\" trailing text \"More quoted text\" more trailing text";
        USXNoteSpan span = USXNoteSpan.parseNote(usx);
        assertNotNull(span);
        assertEquals(text, span.getNotes());
    }
}
