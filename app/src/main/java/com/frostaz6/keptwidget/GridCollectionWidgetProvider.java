package com.frostaz6.keptwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

/** Two-column alternative to the vertical note collection widget. */
public final class GridCollectionWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context,AppWidgetManager manager,int[] ids){
        for(int id:ids)render(context,manager,id);
    }

    static void render(Context context,AppWidgetManager manager,int id){
        RemoteViews views=new RemoteViews(context.getPackageName(),R.layout.widget_grid_collection);
        String name=SecureStore.preferences(context).getString("title_"+id,"Kept");
        views.setTextViewText(R.id.widget_header,name);
        WidgetAppearance.applyCollectionHeader(context,views,R.id.widget_root,id);
        views.setTextViewText(R.id.widget_empty,SecureStore.token(context).isEmpty()
            ? "Sign in to Kept Widget to view notes." : "No notes found. Tap + to create one.");
        Intent adapter=new Intent(context,NoteRemoteViewsService.class);
        adapter.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
        adapter.setData(Uri.parse("keptwidget://grid/"+id));
        views.setRemoteAdapter(R.id.widget_grid_notes,adapter);
        views.setEmptyView(R.id.widget_grid_notes,R.id.widget_empty);

        Intent row=new Intent(context,WidgetRouterActivity.class)
            .setAction(WidgetActionReceiver.ACTION_ROW);
        views.setPendingIntentTemplate(R.id.widget_grid_notes,
            PendingIntent.getActivity(context,id,row,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE));
        views.setOnClickPendingIntent(R.id.widget_header,PendingIntent.getActivity(context,
            id+800000,new Intent(context,NotesActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        views.setOnClickPendingIntent(R.id.widget_add_text,
            CollectionWidgetProvider.editor(context,id*14+1,false));
        views.setOnClickPendingIntent(R.id.widget_add_list,
            CollectionWidgetProvider.editor(context,id*14+2,true));
        views.setOnClickPendingIntent(R.id.widget_refresh,
            CollectionWidgetProvider.action(context,id*14+3,WidgetActionReceiver.ACTION_REFRESH));
        manager.updateAppWidget(id,views);
        manager.notifyAppWidgetViewDataChanged(id,R.id.widget_grid_notes);
    }

    @Override public void onDeleted(Context context,int[] ids){
        for(int id:ids) {
            SecureStore.preferences(context).edit().remove("filter_"+id).remove("title_"+id).apply();
            WidgetAppearance.clear(context,id);
        }
    }
}
