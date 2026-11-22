package com.frostaz6.keptwidget;

import static org.junit.Assert.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
public final class NotesLayoutTest {
    @Test public void foldInnerScreenUsesMoreColumns() {
        assertEquals(1,NotesActivity.columnsForWidth(390,false));
        assertEquals(2,NotesActivity.columnsForWidth(390,true));
        assertEquals(3,NotesActivity.columnsForWidth(750,true));
        assertEquals(1,NotesActivity.columnsForWidth(200,true));
    }

    @Test public void multilineTextTakesMoreRoomInMasonryColumns() throws Exception {
        JSONObject shortNote=new JSONObject().put("noteTitle","Short")
            .put("noteBody","Buy milk");
        JSONObject longNote=new JSONObject().put("noteTitle","Long")
            .put("noteBody","Milk<br>Bread<br>Cheese<br>Eggs<br>Carrots<br>Spinach");
        assertTrue(NotesActivity.estimatedCardHeight(longNote)>
            NotesActivity.estimatedCardHeight(shortNote));
    }

    @Test public void completedListRowsDoNotInflateVisualHeight() throws Exception {
        JSONObject note=new JSONObject().put("isCbox",true)
            .put("checkBoxes",new JSONArray("[{\"id\":1,\"done\":true},"+
                "{\"id\":2,\"done\":true},{\"id\":3,\"done\":false}]"));
        JSONObject another=new JSONObject().put("isCbox",true)
            .put("checkBoxes",new JSONArray("[{\"id\":1,\"done\":false},"+
                "{\"id\":2,\"done\":false},{\"id\":3,\"done\":false}]"));
        assertTrue(NotesActivity.estimatedCardHeight(another)>
            NotesActivity.estimatedCardHeight(note));
    }
}
