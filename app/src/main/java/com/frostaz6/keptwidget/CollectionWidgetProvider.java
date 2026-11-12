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
        WidgetAppearance.applyCollectionHeader(context,views,R.id.widget_root,id);
        views.setTextViewText(R.id.widget_empty, SecureStore.token(context).isEmpty()
            ? "Sign in to Kept Widget to show your notes." : "No notes yet, or unable to sync. Tap ↻ to retry.");

        Intent adapter = new Intent(context, NoteRemoteViewsService.class);
        adapter.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        adapter.setData(Uri.parse("keptwidget://collection/" + id));
        views.setRemoteAdapter(R.id.widget_notes, adapter);
        views.setEmptyView(R.id.widget_notes, R.id.widget_empty);

        Intent row = new Intent(context, WidgetRouterActivity.class).setAction(WidgetActionReceiver.ACTION_ROW);
        PendingIntent rowTemplate = PendingIntent.getActivity(context, id, row,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        views.setPendingIntentTemplate(R.id.widget_notes, rowTemplate);

        views.setOnClickPendingIntent(R.id.widget_header,
            PendingIntent.getActivity(context, id, new Intent(context, NotesActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        views.setOnClickPendingIntent(R.id.widget_add_text, editor(context, id * 10 + 1, false));
        views.setOnClickPendingIntent(R.id.widget_add_list, editor(context, id * 10 + 2, true));
        views.setOnClickPendingIntent(R.id.widget_refresh, action(context, id * 10 + 3, WidgetActionReceiver.ACTION_REFRESH));
        manager.updateAppWidget(id, views);
        manager.notifyAppWidgetViewDataChanged(id, R.id.widget_notes);
    }

    static PendingIntent editor(Context context, int requestCode, boolean isList) {
        Intent intent = new Intent(context, NoteEditorActivity.class);
        intent.putExtra(NoteEditorActivity.EXTRA_LIST, isList);
        return PendingIntent.getActivity(context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
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
        int[] grid = manager.getAppWidgetIds(new ComponentName(context, GridCollectionWidgetProvider.class));
        for (int id : grid) GridCollectionWidgetProvider.render(context, manager, id);
        SingleNoteWidgetProvider.refreshAll(context);
    }

    @Override public void onDeleted(Context context, int[] ids) {
        for (int id : ids) SecureStore.preferences(context).edit()
            .remove("filter_" + id).remove("title_" + id).remove("sort_" + id).apply();
        for (int id: ids) WidgetAppearance.clear(context,id);
    }
}
