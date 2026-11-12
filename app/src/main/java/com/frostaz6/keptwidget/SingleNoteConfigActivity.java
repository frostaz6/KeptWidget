package com.frostaz6.keptwidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public final class SingleNoteConfigActivity extends Activity {
    private int widgetId=AppWidgetManager.INVALID_APPWIDGET_ID;
    private final List<JSONObject> notes=new ArrayList<>();
    private LinearLayout resultList;
    private TextView hint;
    private EditText search;
    private WidgetAppearance.Controls appearance;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        setResult(RESULT_CANCELED);
        widgetId=getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID);
        if(widgetId==AppWidgetManager.INVALID_APPWIDGET_ID){finish();return;}
        LinearLayout root=ScreenUi.page(this,"Single note widget");
        root.addView(ScreenUi.hint(this,
            "Choose a note or checklist. The widget lets you scroll and check off list items without opening the app."),
            ScreenUi.margins(this,0,0,0,14));
        appearance=WidgetAppearance.controls(this,root,widgetId,false);
        if (SecureStore.preferences(this).getLong("single_note_"+widgetId,-1L)>0) {
            Button apply=ScreenUi.button(this,"Save appearance (keep current note)",true);
            root.addView(apply,ScreenUi.margins(this,0,0,0,18));
            apply.setOnClickListener(v->{
                appearance.save();
                SingleNoteWidgetProvider.render(this,AppWidgetManager.getInstance(this),widgetId);
                Intent done=new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId);
                setResult(RESULT_OK,done);
                finish();
            });
        }
        search=ScreenUi.field(this,"Search your notes",InputType.TYPE_CLASS_TEXT);
        root.addView(search,ScreenUi.margins(this,0,0,0,12));
        hint=ScreenUi.hint(this,"Loading notes…");
        root.addView(hint,ScreenUi.margins(this,0,0,0,12));
        resultList=new LinearLayout(this);
        resultList.setOrientation(LinearLayout.VERTICAL);
        root.addView(resultList);
        search.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int a,int c,int after){}
            @Override public void onTextChanged(CharSequence s,int a,int before,int count){render();}
            @Override public void afterTextChanged(Editable e){}
        });
        try{
            JSONArray cached=new JSONArray(SecureStore.cachedNotes(this));
            setNotes(cached);
        }catch(Exception ignored){}
        Executors.newSingleThreadExecutor().execute(()->{
            try{
                JSONArray fetched=KeptApi.notes(this);
                SecureStore.saveNotes(this,fetched.toString());
                runOnUiThread(()->{setNotes(fetched);hint.setText("Choose a note");});
            } catch(Exception ex) {
                runOnUiThread(()->hint.setText("Offline: "+ex.getMessage()));
            }
        });
    }

    private void setNotes(JSONArray data){
        notes.clear();
        for(int i=0;i<data.length();i++){
            JSONObject n=data.optJSONObject(i);
            if(n!=null && n.optLong("id",-1)>0 && !n.optBoolean("archived")
                && !n.optBoolean("trashed"))notes.add(n);
        }
        render();
    }

    private void render(){
        if(resultList==null)return;
        resultList.removeAllViews();
        String q=search.getText().toString().toLowerCase(Locale.ROOT).trim();
        int count=0;
        for(JSONObject note:notes){
            String name=KeptApi.plain(note.optString("noteTitle"));
            if(name.isEmpty())name="Untitled";
            String preview=note.optBoolean("isCbox")?"☑ Checklist":
                KeptApi.plain(note.optString("previewText",note.optString("noteBody")));
            if(!q.isEmpty() && !(name+" "+preview).toLowerCase(Locale.ROOT).contains(q))continue;
            long id=note.optLong("id");
            Button b=ScreenUi.button(this,name+"  "+(preview.isEmpty()?"":"\n"+preview),false);
            b.setGravity(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL);
            b.setSingleLine(false);
            b.setMaxLines(3);
            resultList.addView(b,ScreenUi.margins(this,0,0,0,10));
            b.setOnClickListener(v->select(id));
            if(++count>=120)break;
        }
        if(count==0)hint.setText("No matching notes. Create a note in Kept first.");
    }

    private void select(long noteId){
        appearance.save();
        SecureStore.preferences(this).edit().putLong("single_note_"+widgetId,noteId).apply();
        SingleNoteWidgetProvider.render(this,AppWidgetManager.getInstance(this),widgetId);
        Intent done=new Intent();
        done.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId);
        setResult(RESULT_OK,done);
        finish();
    }
}
