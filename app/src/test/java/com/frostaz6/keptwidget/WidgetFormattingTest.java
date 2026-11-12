package com.frostaz6.keptwidget;

import static org.junit.Assert.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Regression tests for collapsed widget previews and non-destructive sync comparisons. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
public final class WidgetFormattingTest {
    @Test public void htmlBreaksAndParagraphsPreserveOriginalLines() {
        String result=KeptApi.formatted("<div>Line one</div><div>Line two</div><div>Line three</div>");
        assertTrue(result,result.contains("Line one"));
        assertTrue(result,result.contains("Line two"));
        assertTrue("Block formatting must not collapse into one paragraph: "+result,result.contains("\n"));
        assertTrue(KeptApi.formatted("First<br>Second").contains("\n"));
    }

    @Test public void plainTextLineBreaksArePreserved() {
        assertEquals("one\ntwo\nthree",KeptApi.formatted("one\ntwo\nthree"));
        assertEquals("bread & milk",KeptApi.formatted("bread &amp; milk"));
    }

    @Test public void nestedNoteFieldsCompareStructurally() throws Exception {
        JSONObject first=new JSONObject("{\"checkBoxes\":[{\"id\":3,\"done\":false},{\"id\":5,\"done\":true}],\"pinned\":false}");
        JSONObject sameOrderIndependent=new JSONObject("{\"pinned\":false,\"checkBoxes\":[{\"done\":false,\"id\":3},{\"id\":5,\"done\":true}]}");
        JSONObject changed=new JSONObject("{\"pinned\":false,\"checkBoxes\":[{\"id\":3,\"done\":true},{\"id\":5,\"done\":true}]}");
        assertTrue(PendingSync.equivalent(first,sameOrderIndependent));
        assertFalse(PendingSync.equivalent(first,changed));
        assertTrue(PendingSync.equivalent(null,JSONObject.NULL));
    }

    @Test public void widgetOpacityIsBoundedAndColorsHaveDarkAlternatives() {
        assertEquals(0,WidgetAppearance.clamp(-20));
        assertEquals(100,WidgetAppearance.clamp(105));
        assertEquals(0xff303339,WidgetAppearance.noteColor("#FFFFFF",true));
        assertNotEquals(WidgetAppearance.noteColor("#FFAAAA",true),
            WidgetAppearance.noteColor("#FFAAAA",false));
    }
}
