package com.frostaz6.keptwidget;

import static org.junit.Assert.*;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Keep-style completed item grouping without relying on a network server. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
public final class ChecklistPresentationTest {
    @Test public void completedItemsCollapseByDefault() throws Exception {
        JSONArray src=new JSONArray("[{\"id\":1,\"done\":true},"+
            "{\"id\":2,\"done\":false},{\"id\":3,\"done\":true},{\"id\":4,\"done\":false}]");
        ChecklistPresentation.Selection collapsed=ChecklistPresentation.select(src,false);
        assertEquals(2,collapsed.completed);
        assertEquals(2,collapsed.remaining);
        assertEquals(2,collapsed.rows.size());
        assertEquals(2L,collapsed.rows.get(0).optLong("id"));
        assertEquals(4L,collapsed.rows.get(1).optLong("id"));
    }

    @Test public void expandedGroupShowsCompletedBelowUnchecked() throws Exception {
        JSONArray src=new JSONArray("[{\"id\":1,\"done\":true},"+
            "{\"id\":2,\"done\":false},{\"id\":3,\"done\":true}]");
        ChecklistPresentation.Selection expanded=ChecklistPresentation.select(src,true);
        assertEquals(3,expanded.rows.size());
        assertEquals(2L,expanded.rows.get(0).optLong("id"));
        assertEquals(1L,expanded.rows.get(1).optLong("id"));
        assertEquals(3L,expanded.rows.get(2).optLong("id"));
        assertEquals(2,expanded.completed);
    }

    @Test public void noItemsProducesNoCompletedFooter() throws Exception {
        ChecklistPresentation.Selection none=ChecklistPresentation.select(new JSONArray(),false);
        assertEquals(0,none.completed);
        assertTrue(none.rows.isEmpty());
    }
}
