package com.frostaz6.keptwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

public class CollectionWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) render(context, manager, id);
    }

    static void render(Context context, AppWidgetManager manager, int id) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_collection);
        String header = SecureStore.preferences(context).getString("title_" + id, "Kept");
        views.setTextViewText(R.id.widget_header, header);
        views.setTextViewText(R.id.widget_empty, SecureStore.token(context).isEmpty()
            ? "Sign in to Kept Widget to show your notes." : "No notes yet, or unable to sync. Tap ↻ to retry.");

        Intent adapter = new Intent(context, NoteRemoteViewsService.class);
        adapter.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        adapter.setData(Uri.parse("keptwidget://collection/" + id));
        views.setRemoteAdapter(R.id.widget_notes, adapter);
        views.setEmptyView(R.id.widget_notes, R.id.widget_empty);

        Intent row = new Intent(context, WidgetActionReceiver.class).setAction(WidgetActionReceiver.ACTION_ROW);
        PendingIntent rowTemplate = PendingIntent.getBroadcast(context, id, row,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        views.setPendingIntentTemplate(R.id.widget_notes, rowTemplate);

        views.setOnClickPendingIntent(R.id.widget_header,
            PendingIntent.getActivity(context, id, new Intent(context, LoginActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        views.setOnClickPendingIntent(R.id.widget_add_text, action(context, id * 10 + 1, WidgetActionReceiver.ACTION_ADD_NOTE));
        views.setOnClickPendingIntent(R.id.widget_add_list, action(context, id * 10 + 2, WidgetActionReceiver.ACTION_ADD_LIST));
        views.setOnClickPendingIntent(R.id.widget_refresh, action(context, id * 10 + 3, WidgetActionReceiver.ACTION_REFRESH));
        manager.updateAppWidget(id, views);
        manager.notifyAppWidgetViewDataChanged(id, R.id.widget_notes);
    }

    static PendingIntent action(Context context, int requestCode, String action) {
        Intent intent = new Intent(context, WidgetActionReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, CollectionWidgetProvider.class));
        for (int id : ids) render(context, manager, id);
        int[] quick = manager.getAppWidgetIds(new ComponentName(context, CaptureWidgetProvider.class));
        if (quick.length > 0) new CaptureWidgetProvider().onUpdate(context, manager, quick);
    }

    @Override public void onDeleted(Context context, int[] ids) {
        for (int id : ids) SecureStore.preferences(context).edit()
            .remove("filter_" + id).remove("title_" + id).apply();
    }
}
