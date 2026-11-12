package com.frostaz6.keptwidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;

/** Appearance options for the compact quick-capture widget. */
public final class CaptureWidgetConfigActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setResult(RESULT_CANCELED);
        int id=getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID);
        if(id==AppWidgetManager.INVALID_APPWIDGET_ID){finish();return;}
        LinearLayout root=ScreenUi.page(this,"Quick capture appearance");
        root.addView(ScreenUi.hint(this,"Choose dark or light mode and widget transparency."),
            ScreenUi.margins(this,0,0,0,18));
        WidgetAppearance.Controls appearance=WidgetAppearance.controls(this,root,id,false);
        Button save=ScreenUi.button(this,"Save widget appearance",true);
        root.addView(save,ScreenUi.margins(this,0,12,0,0));
        save.setOnClickListener(v->{
            appearance.save();
            AppWidgetManager manager=AppWidgetManager.getInstance(this);
            new CaptureWidgetProvider().onUpdate(this,manager,new int[]{id});
            Intent done=new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
            setResult(RESULT_OK,done);
            finish();
        });
    }
}
