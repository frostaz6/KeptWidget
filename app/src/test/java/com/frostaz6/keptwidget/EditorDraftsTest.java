package com.frostaz6.keptwidget;

import static org.junit.Assert.*;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Recover unsaved changes without accidentally accepting stale server edits. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
public class EditorDraftsTest {
    @Test public void separateNewNoteAndChecklistDrafts() {
        assertEquals("new_text",EditorDrafts.key(-1,false));
        assertEquals("new_checklist",EditorDrafts.key(-1,true));
        assertEquals("note_42",EditorDrafts.key(42,true));
        assertEquals("note_42",EditorDrafts.key(42,false));
    }

    @Test public void sameBaselineIsSafeToOfferForRecovery() throws Exception {
        JSONObject server=new JSONObject("{\"noteTitle\":\"Groceries\",\"noteBody\":\"Milk<br>Bread\","+
            "\"checkBoxes\":[{\"id\":3,\"data\":\"Apples\",\"done\":false}],\"pinned\":false}");
        JSONObject stored=new JSONObject(server.toString());
        assertTrue(EditorDrafts.matchesBaseline(server,stored));
    }

    @Test public void changedRemoteCheckBoxIsNotAcceptedAsAnUnchangedBaseline() throws Exception {
        JSONObject old=new JSONObject("{\"checkBoxes\":[{\"id\":1,\"done\":false}]}");
        JSONObject updated=new JSONObject("{\"checkBoxes\":[{\"id\":1,\"done\":true}]}");
        assertFalse(EditorDrafts.matchesBaseline(updated,old));
    }

    @Test public void renamedOrRecoloredRemoteNoteNeedsReview() throws Exception {
        JSONObject old=new JSONObject("{\"noteTitle\":\"A\",\"bgColor\":\"#FFFFFF\"}");
        JSONObject newer=new JSONObject("{\"noteTitle\":\"B\",\"bgColor\":\"#FFFFFF\"}");
        assertFalse(EditorDrafts.matchesBaseline(newer,old));
        newer.put("noteTitle","A");
        newer.put("bgColor","#FFEEDD");
        assertFalse(EditorDrafts.matchesBaseline(newer,old));
    }
}
