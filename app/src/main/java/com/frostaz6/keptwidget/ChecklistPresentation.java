package com.frostaz6.keptwidget;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Consistent Keep-style unfinished/completed checklist grouping for widgets. */
final class ChecklistPresentation {
    private ChecklistPresentation(){}

    static final class Selection {
        final List<JSONObject> rows;
        final int completed;
        final int remaining;
        Selection(List<JSONObject> rows,int completed,int remaining) {
            this.rows=rows;this.completed=completed;this.remaining=remaining;
        }
    }

    /** Always list unfinished entries first and collapse the completed group by default. */
    static Selection select(JSONArray source,boolean showCompleted) {
        List<JSONObject> unchecked=new ArrayList<>();
        List<JSONObject> done=new ArrayList<>();
        for(int i=0;source!=null&&i<source.length();i++){
            JSONObject item=source.optJSONObject(i);
            if(item==null)continue;
            if(item.optBoolean("done"))done.add(item);
            else unchecked.add(item);
        }
        List<JSONObject> display=new ArrayList<>(unchecked);
        if(showCompleted)display.addAll(done);
        return new Selection(display,done.size(),unchecked.size());
    }
}
