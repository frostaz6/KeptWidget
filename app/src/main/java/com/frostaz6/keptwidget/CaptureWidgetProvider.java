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
            views.setOnClickPendingIntent(R.id.capture_open, PendingIntent.getActivity(context, id,
                new Intent(context, LoginActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            views.setOnClickPendingIntent(R.id.capture_note,
                CollectionWidgetProvider.editor(context, id * 10 + 4, false));
            views.setOnClickPendingIntent(R.id.capture_list,
                CollectionWidgetProvider.editor(context, id * 10 + 5, true));
            manager.updateAppWidget(id, views);
        }
    }
}
