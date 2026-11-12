package com.frostaz6.keptwidget;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Fetches the full note (not the truncated card preview) so every list item can scroll. */
public final class SingleNoteRemoteViewsService extends RemoteViewsService {
    @Override public RemoteViewsFactory onGetViewFactory(Intent intent) {
        int id=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID);
        return new Factory(getApplicationContext(),id);
    }

    private static final class Factory implements RemoteViewsFactory {
        private final Context context;
        private final int widgetId;
        private long noteId;
        private final List<JSONObject> items=new ArrayList<>();
        private boolean checklist;
        Factory(Context c,int id) {context=c;widgetId=id;}
        @Override public void onCreate(){}

        @Override public synchronized void onDataSetChanged(){
            noteId=SecureStore.preferences(context).getLong("single_note_"+widgetId,-1L);
            items.clear();
            if(noteId<1 || SecureStore.token(context).isEmpty())return;
            JSONObject note=null;
            try {
                note=KeptApi.note(context,noteId);
                SecureStore.saveSingleNote(context,noteId,note.toString());
            } catch(Exception ignored) {
                try { note=new JSONObject(SecureStore.singleNote(context,noteId));}
                catch(Exception ignoredAgain) {return;}
            }
            if(note.optBoolean("locked") && !note.optBoolean("lockedContentAvailable",false)) {
                JSONObject locked=new JSONObject();
                try {locked.put("data","🔒 Locked note — open Kept to unlock");}
                catch(Exception ignored){}
                items.add(locked);
                checklist=false;
            } else if(note.optBoolean("isCbox")) {
                checklist=true;
                JSONArray list=note.optJSONArray("checkBoxes");
                for(int i=0;list!=null && i<list.length();i++){
                    JSONObject item=list.optJSONObject(i);
                    if(item!=null)items.add(item);
                }
            } else {
                checklist=false;
                JSONObject body=new JSONObject();
                try {body.put("data",KeptApi.formatted(note.optString("noteBody"))); }
                catch(Exception ignored){}
                items.add(body);
            }
            SingleNoteWidgetProvider.updateTitle(context,widgetId,note);
        }

        @Override public synchronized int getCount(){return items.size();}

        @Override public synchronized RemoteViews getViewAt(int position) {
            if(position<0||position>=items.size())return null;
            JSONObject item=items.get(position);
            RemoteViews row=new RemoteViews(context.getPackageName(),R.layout.widget_single_row);
            boolean done=item.optBoolean("done");
            String body=checklist? KeptApi.formatted(item.optString("data")):item.optString("data");
            boolean dark=WidgetAppearance.dark(context,widgetId);
            row.setTextViewText(R.id.single_item_text,body);
            row.setViewVisibility(R.id.single_checkbox,checklist?View.VISIBLE:View.GONE);
            if(checklist)row.setTextViewText(R.id.single_checkbox,done?"☑":"☐");
            row.setTextColor(R.id.single_item_text,done?0xff8a8d88:WidgetAppearance.text(dark));
            row.setTextColor(R.id.single_checkbox,WidgetAppearance.accent(dark));
            if(!checklist)row.setInt(R.id.single_item_text,"setMaxLines",120);

            Intent open=new Intent();
            open.putExtra(NoteEditorActivity.EXTRA_ID,noteId);
            open.putExtra(WidgetActionReceiver.EXTRA_ACTION,"open");
            row.setOnClickFillInIntent(R.id.single_item_text,open);
            row.setOnClickFillInIntent(R.id.single_item_root,open);
            if(checklist && item.has("id")){
                Intent toggle=new Intent();
                toggle.putExtra(NoteEditorActivity.EXTRA_ID,noteId);
                toggle.putExtra(WidgetActionReceiver.EXTRA_ITEM,item.optLong("id",-1));
                toggle.putExtra(WidgetActionReceiver.EXTRA_ACTION,"toggle");
                row.setOnClickFillInIntent(R.id.single_checkbox,toggle);
            }
            return row;
        }
        @Override public RemoteViews getLoadingView(){return null;}
        @Override public int getViewTypeCount(){return 1;}
        @Override public synchronized long getItemId(int pos){
            if(pos<0||pos>=items.size())return pos;
            return items.get(pos).optLong("id",pos);
        }
        @Override public boolean hasStableIds(){return true;}
        @Override public synchronized void onDestroy(){items.clear();}
    }
}
