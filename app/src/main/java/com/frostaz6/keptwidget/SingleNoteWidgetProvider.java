package com.frostaz6.keptwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONObject;

/** Configurable scrollable single-note widget, like Google Keep's single note widget. */
public final class SingleNoteWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for(int id : ids) render(context, manager, id);
    }

    static void render(Context context, AppWidgetManager manager, int id) {
        long noteId=SecureStore.preferences(context).getLong("single_note_"+id,-1L);
        RemoteViews views=new RemoteViews(context.getPackageName(),R.layout.widget_single);
        if(noteId>0){
            try {
                String cached=SecureStore.singleNote(context,noteId);
                JSONObject note=new JSONObject(cached);
                views.setTextViewText(R.id.single_title, KeptApi.plain(note.optString("noteTitle","Untitled")));
            }catch(Exception ignored){
                views.setTextViewText(R.id.single_title,"Kept note");
            }
        } else {
            views.setTextViewText(R.id.single_title,"Choose a note");
        }
        Intent adapter=new Intent(context,SingleNoteRemoteViewsService.class);
        adapter.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
        adapter.setData(Uri.parse("keptwidget://single/"+id));
        views.setRemoteAdapter(R.id.single_items,adapter);
        views.setEmptyView(R.id.single_items,R.id.single_empty);
        Intent row=new Intent(context,WidgetRouterActivity.class).setAction(WidgetActionReceiver.ACTION_ROW);
        views.setPendingIntentTemplate(R.id.single_items, PendingIntent.getActivity(context, id*12+1,
            row,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE));
        if(noteId>0){
            Intent edit=new Intent(context,NoteEditorActivity.class)
                .putExtra(NoteEditorActivity.EXTRA_ID,noteId);
            views.setOnClickPendingIntent(R.id.single_title,
                PendingIntent.getActivity(context,id*12+2,edit,
                    PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
            views.setOnClickPendingIntent(R.id.single_edit,
                PendingIntent.getActivity(context,id*12+3,edit,
                    PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        }
        Intent refresh=new Intent(context,WidgetActionReceiver.class)
            .setAction(WidgetActionReceiver.ACTION_REFRESH);
        views.setOnClickPendingIntent(R.id.single_refresh,
            PendingIntent.getBroadcast(context,id*12+4,refresh,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        manager.updateAppWidget(id,views);
        manager.notifyAppWidgetViewDataChanged(id,R.id.single_items);
    }

    static void updateTitle(Context context,int widgetId,JSONObject note) {
        RemoteViews views=new RemoteViews(context.getPackageName(),R.layout.widget_single);
        views.setTextViewText(R.id.single_title,
            KeptApi.plain(note.optString("noteTitle","Untitled")));
        AppWidgetManager.getInstance(context).partiallyUpdateAppWidget(widgetId,views);
    }

    static void refreshAll(Context context) {
        AppWidgetManager manager=AppWidgetManager.getInstance(context);
        int[] ids=manager.getAppWidgetIds(new ComponentName(context,SingleNoteWidgetProvider.class));
        for(int id:ids) render(context,manager,id);
    }

    @Override public void onDeleted(Context context,int[] ids) {
        for(int id:ids) SecureStore.preferences(context).edit().remove("single_note_"+id).apply();
    }
}
