package com.frostaz6.keptwidget;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Computes a single checklist change against the freshest server note.
 *
 * Kept currently has no atomic note revisions, so this prevents locally-known
 * conflicts and preserves all other items without claiming server-side CAS.
 */
final class ChecklistMutation {
    private ChecklistMutation() {}

    static final class Decision {
        final JSONArray items;
        final boolean needsWrite;
        final String conflict;
        Decision(JSONArray items, boolean needsWrite, String conflict) {
            this.items=items;
            this.needsWrite=needsWrite;
            this.conflict=conflict;
        }
        boolean isConflict() { return conflict != null; }
    }

    /** baselineDone is null for legacy v0.3 operations whose intent was ambiguous. */
    static Decision apply(JSONArray fresh,long itemId,Boolean baselineDone,boolean desired)
            throws JSONException {
        return apply(fresh,itemId,baselineDone,desired,"","");
    }

    /**
     * Kept does not expose atomic note revisions. Be conservative when the
     * note changed after the offline checkbox was captured: don't guess
     * whether another writer touched this item and then changed it back.
     */
    static Decision apply(JSONArray fresh,long itemId,Boolean baselineDone,boolean desired,
                          String baselineUpdatedAt,String currentUpdatedAt) throws JSONException {
        if (fresh == null) return new Decision(null,false,"The checklist no longer exists.");
        JSONArray copy=new JSONArray(fresh.toString());
        for(int i=0;i<copy.length();i++){
            JSONObject item=copy.optJSONObject(i);
            if(item==null || item.optLong("id",-1L)!=itemId)continue;
            boolean current=item.optBoolean("done");
            if(current==desired)return new Decision(copy,false,null); // Already applied.
            if(baselineDone==null)
                return new Decision(copy,false,"A legacy offline checkbox edit needs review.");
            if(baselineUpdatedAt!=null && currentUpdatedAt!=null
                && !baselineUpdatedAt.isEmpty() && !currentUpdatedAt.isEmpty()
                && !baselineUpdatedAt.equals(currentUpdatedAt))
                return new Decision(copy,false,"The note changed on another device since this checkbox was queued.");
            if(current!=baselineDone)
                return new Decision(copy,false,"This checkbox changed on another device.");
            item.put("done",desired);
            return new Decision(copy,true,null);
        }
        return new Decision(copy,false,"The checklist item was removed on another device.");
    }
}
