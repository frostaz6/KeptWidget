package com.frostaz6.keptwidget;

import android.content.Context;
import org.json.JSONObject;

/**
 * Encrypted recovery drafts for unexpected app termination, rotations, and
 * switching to the Kept web UI. Drafts are local, not server changes.
 *
 * One draft per saved note, plus separate text/checklist new-note slots.
 */
final class EditorDrafts {
    private EditorDrafts() {}

    static String key(long noteId, boolean checklist) {
        return noteId > 0 ? "note_" + noteId :
            (checklist ? "new_checklist" : "new_text");
    }

    static JSONObject load(Context c, String key) {
        try {
            String text=SecureStore.readDraft(c,key);
            return text.isBlank()?null:new JSONObject(text);
        } catch(Exception ignored){return null;}
    }

    static void save(Context c, String key, JSONObject draft) {
        if(draft==null)return;
        SecureStore.saveDraft(c,key,draft.toString());
    }

    static void clear(Context c,String key) {
        SecureStore.clearDraft(c,key);
    }

    static boolean matchesBaseline(JSONObject current,JSONObject baseline) {
        if(current==null||baseline==null)return current==null&&baseline==null;
        // A newer remote change in any editable field must be reviewed.
        for(String key:new String[]{"noteTitle","noteBody","isCbox",
            "checkBoxes","pinned","labels","bgColor"}) {
            if(!PendingSync.equivalent(current.opt(key),baseline.opt(key)))
                return false;
        }
        return true;
    }
}
