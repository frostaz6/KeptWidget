package com.frostaz6.keptwidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Edit widget appearance without removing an existing home-screen widget. */
public final class WidgetSettingsActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=ScreenUi.page(this,"Home screen widgets");
        root.addView(ScreenUi.hint(this,
            "Change dark mode, transparency and filters on widgets already on your home screen."),
            ScreenUi.margins(this,0,0,0,20));
        AppWidgetManager manager=AppWidgetManager.getInstance(this);
        int count=0;
        count+=widgets(root,manager,CollectionWidgetProvider.class,WidgetConfigActivity.class,"Note collection");
        count+=widgets(root,manager,GridCollectionWidgetProvider.class,WidgetConfigActivity.class,"Two-column grid");
        count+=widgets(root,manager,SingleNoteWidgetProvider.class,SingleNoteConfigActivity.class,"Single note");
        count+=widgets(root,manager,CaptureWidgetProvider.class,CaptureWidgetConfigActivity.class,"Quick capture");
        if(count==0){
            root.addView(ScreenUi.hint(this,
                "No widgets found. Long-press your home screen, open Widgets, and choose Kept Widget."),
                ScreenUi.margins(this,0,0,0,20));
        }
        Button pending=ScreenUi.button(this,"Offline sync status ("+PendingSync.count(this)+")",false);
        root.addView(pending,ScreenUi.margins(this,0,18,0,10));
        pending.setOnClickListener(v->startActivity(new Intent(this,SyncStatusActivity.class)));
        Button account=ScreenUi.button(this,"Account and server settings",false);
        root.addView(account,ScreenUi.margins(this,0,16,0,10));
        account.setOnClickListener(v->startActivity(new Intent(this,LoginActivity.class)));
    }

    private int widgets(LinearLayout root,AppWidgetManager manager,Class<?> provider,
                        Class<?> configure,String label) {
        int[] ids=manager.getAppWidgetIds(new ComponentName(this,provider));
        for(int id:ids) {
            Button b=ScreenUi.button(this,label+" • widget "+id+"  ⚙",false);
            root.addView(b,ScreenUi.margins(this,0,0,0,10));
            b.setOnClickListener(v->{
                Intent edit=new Intent(this,configure);
                edit.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
                startActivity(edit);
            });
        }
        return ids.length;
    }
}
