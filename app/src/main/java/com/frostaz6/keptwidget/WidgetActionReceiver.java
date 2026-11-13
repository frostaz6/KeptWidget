package com.frostaz6.keptwidget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.util.concurrent.Executors;

public class WidgetActionReceiver extends BroadcastReceiver {
    static final String ACTION_ROW = "com.frostaz6.keptwidget.ROW";
    static final String ACTION_ADD_NOTE = "com.frostaz6.keptwidget.ADD_NOTE";
    static final String ACTION_ADD_LIST = "com.frostaz6.keptwidget.ADD_LIST";
    static final String ACTION_REFRESH = "com.frostaz6.keptwidget.REFRESH";
    static final String ACTION_TOGGLE_COMPLETED = "com.frostaz6.keptwidget.TOGGLE_COMPLETED";
    static final String EXTRA_ACTION = "row_action";
    static final String EXTRA_ITEM = "item_id";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if(ACTION_TOGGLE_COMPLETED.equals(action)) {
            int widgetId=intent.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID,
                android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID);
            if(widgetId!=android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID) {
                String pref="show_completed_"+widgetId;
                boolean old=SecureStore.preferences(context).getBoolean(pref,false);
                SecureStore.preferences(context).edit().putBoolean(pref,!old).apply();
                SingleNoteWidgetProvider.render(context,
                    android.appwidget.AppWidgetManager.getInstance(context),widgetId);
            }
            return;
        }
        if (ACTION_REFRESH.equals(action)) {
            CollectionWidgetProvider.refreshAll(context);
            return;
        }
        if (ACTION_ADD_NOTE.equals(action) || ACTION_ADD_LIST.equals(action)) {
            Intent open = new Intent(context, SecureStore.token(context).isEmpty()
                ? LoginActivity.class : NoteEditorActivity.class);
            open.putExtra(NoteEditorActivity.EXTRA_LIST, ACTION_ADD_LIST.equals(action));
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(open);
            return;
        }
        if (!ACTION_ROW.equals(action)) return;
        long id = intent.getLongExtra(NoteEditorActivity.EXTRA_ID, -1);
        if (id < 1) return;
        if ("toggle".equals(intent.getStringExtra(EXTRA_ACTION))) {
            long item = intent.getLongExtra(EXTRA_ITEM, -1);
            if (item < 0) return;
            final PendingResult pending = goAsync();
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    KeptApi.toggleCheckbox(context.getApplicationContext(), id, item);
                    CollectionWidgetProvider.refreshAll(context);
                } catch (Exception error) {
                    new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(context, "Couldn't update checklist: " + error.getMessage(),
                            Toast.LENGTH_SHORT).show());
                    CollectionWidgetProvider.refreshAll(context);
                } finally {
                    pending.finish();
                }
            });
        } else {
            Intent open = new Intent(context, SecureStore.token(context).isEmpty()
                ? LoginActivity.class : NoteEditorActivity.class);
            open.putExtra(NoteEditorActivity.EXTRA_ID, id);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(open);
        }
    }
}
