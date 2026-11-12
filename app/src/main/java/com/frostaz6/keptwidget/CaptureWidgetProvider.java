package com.frostaz6.keptwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class CaptureWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_capture);
            WidgetAppearance.applyBackground(context, views, R.id.capture_root, id);
            boolean dark = WidgetAppearance.dark(context, id);
            views.setTextColor(R.id.capture_open, WidgetAppearance.text(dark));
            views.setTextColor(R.id.capture_note, WidgetAppearance.accent(dark));
            views.setTextColor(R.id.capture_list, WidgetAppearance.accent(dark));
            views.setOnClickPendingIntent(R.id.capture_open, PendingIntent.getActivity(context, id,
                new Intent(context, NotesActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            views.setOnClickPendingIntent(R.id.capture_note,
                CollectionWidgetProvider.editor(context, id * 10 + 4, false));
            views.setOnClickPendingIntent(R.id.capture_list,
                CollectionWidgetProvider.editor(context, id * 10 + 5, true));
            manager.updateAppWidget(id, views);
        }
    }

    @Override public void onDeleted(Context context,int[] ids) {
        for (int id:ids) WidgetAppearance.clear(context,id);
    }
}
