package com.frostaz6.keptwidget;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Regression coverage for the Enter-to-next-item behavior in the native editor. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=33)
public final class ChecklistKeyboardTest {
    @Test public void enterAtEndStartsAnEmptyItem() {
        NoteEditorActivity.TextSplit split =
            NoteEditorActivity.splitChecklistText("Buy milk",8,8);
        assertEquals("Buy milk",split.first);
        assertEquals("",split.second);
    }

    @Test public void enterInMiddleSplitsItemsAtCaret() {
        NoteEditorActivity.TextSplit split =
            NoteEditorActivity.splitChecklistText("Buy bread today",9,9);
        assertEquals("Buy bread",split.first);
        assertEquals(" today",split.second);
    }

    @Test public void enterAtBeginningMovesTextIntoNewItem() {
        NoteEditorActivity.TextSplit split =
            NoteEditorActivity.splitChecklistText("Take out trash",0,0);
        assertEquals("",split.first);
        assertEquals("Take out trash",split.second);
    }

    @Test public void enterWithSelectedTextReplacesSelectionWithItemBoundary() {
        NoteEditorActivity.TextSplit split =
            NoteEditorActivity.splitChecklistText("Buy old milk",4,7);
        assertEquals("Buy ",split.first);
        assertEquals(" milk",split.second);
    }

    @Test public void outOfRangeSelectionsAreSafe() {
        NoteEditorActivity.TextSplit split =
            NoteEditorActivity.splitChecklistText("Bread",-1,999);
        assertEquals("",split.first);
        assertEquals("",split.second);
    }
    @Test public void preserveExistingEmptyChecklistEntries() {
        org.junit.Assert.assertTrue(NoteEditorActivity.shouldPersistRow("",true));
        org.junit.Assert.assertTrue(NoteEditorActivity.shouldPersistRow("  ",true));
        org.junit.Assert.assertFalse(NoteEditorActivity.shouldPersistRow("",false));
        org.junit.Assert.assertFalse(NoteEditorActivity.shouldPersistRow(" ",false));
        org.junit.Assert.assertTrue(NoteEditorActivity.shouldPersistRow("Rx",false));
    }

}
