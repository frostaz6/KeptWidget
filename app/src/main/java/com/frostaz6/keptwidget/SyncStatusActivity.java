package com.frostaz6.keptwidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.Executors;

/** Explains what is pending and allows non-destructive conflict management. */
public final class SyncStatusActivity extends Activity {
    private LinearLayout list;
    private TextView overview;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=ScreenUi.page(this,"Sync status");
        root.addView(ScreenUi.hint(this,"Offline changes are encrypted on this device. "
            +"When online, Kept Widget checks the current server note before applying each queued edit. "
            +"Conflicting changes are held here instead of overwriting the server."),
            ScreenUi.margins(this,0,0,0,16));
        overview=ScreenUi.label(this,"",17,true);
        root.addView(overview,ScreenUi.margins(this,0,0,0,10));
        Button sync=ScreenUi.button(this,"Sync pending changes now",true);
        root.addView(sync,ScreenUi.margins(this,0,0,0,16));
        sync.setOnClickListener(v-> {
            sync.setEnabled(false);
            overview.setText("Checking server…");
            Executors.newSingleThreadExecutor().execute(()-> {
                boolean ok=PendingSync.flush(this);
                runOnUiThread(()->{
                    sync.setEnabled(true);
                    render();
                    if(!ok)overview.setText("Kept is unavailable. Changes remain queued.");
                });
            });
        });
        list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
        Button ke = ScreenUi.button(this,"Open Kept in browser",false);
        root.addView(ke,ScreenUi.margins(this,0,16,0,0));
        ke.setOnClickListener(v->
            startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(SecureStore.baseUrl(this)))));
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if(list!=null)render();
    }

    private void render() {
        int count=PendingSync.count(this), conflicts=PendingSync.conflicts(this);
        overview.setText(count==0?"All queued edits have synchronized.":
            count+" pending change(s) · "+conflicts+" conflict(s)");
        list.removeAllViews();
        JSONArray pending=PendingSync.entries(this);
        for(int i=0;i<pending.length();i++){
            JSONObject op=pending.optJSONObject(i);
            if(op==null)continue;
            String type=op.optString("kind");
            String id=op.optString("id");
            String name="create".equals(type)?"New note":
                "check".equals(type)?"Checklist item":"Note edit";
            String target=op.has("noteId")?" · note "+op.optLong("noteId"):"";
            String state=op.optString("state");
            TextView description=ScreenUi.label(this,name+target+"\n"+
                ("conflict".equals(state)?"⚠ Conflict: "+op.optString("error",
                    "A newer change was found on the server."):
                    "⏳ Waiting for network / synchronization"),15,false);
            description.setPadding(ScreenUi.dp(this,9),ScreenUi.dp(this,10),ScreenUi.dp(this,9),ScreenUi.dp(this,10));
            list.addView(description,ScreenUi.margins(this,0,8,0,2));
            if("conflict".equals(state)){
                TextView advice=ScreenUi.hint(this,
                    "Review the server note before discarding your local edit. Retry only if you know the server field is unchanged.");
                list.addView(advice,ScreenUi.margins(this,4,0,0,6));
                Button retry=ScreenUi.button(this,"Check again",false);
                list.addView(retry,ScreenUi.margins(this,0,0,0,5));
                retry.setOnClickListener(v->{PendingSync.retryConflict(this,id);render();});
            }
            Button discard=ScreenUi.button(this,"Discard this local queued change",false);
            list.addView(discard,ScreenUi.margins(this,0,0,0,8));
            discard.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle("Discard offline change?")
                .setMessage("This only discards the local unsynced edit. It won't delete any note on your Kept server.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Discard",(dialog,which)->{
                    PendingSync.discard(this,id);
                    render();
                }).show());
        }
    }
}
