package com.frostaz6.keptwidget;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class NoteRemoteViewsService extends RemoteViewsService {
    @Override public RemoteViewsFactory onGetViewFactory(Intent intent) {
        int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID);
        return new Factory(getApplicationContext(), widgetId);
    }

    private static final class Factory implements RemoteViewsFactory {
        private final Context context;
        private final int widgetId;
        private final List<JSONObject> notes = new ArrayList<>();
        private static final int[] CHECK_IDS = { R.id.note_check1, R.id.note_check2, R.id.note_check3 };

        Factory(Context context, int widgetId) {
            this.context = context;
            this.widgetId = widgetId;
        }

        @Override public void onCreate() { /* onDataSetChanged runs off the UI thread. */ }

        @Override public synchronized void onDataSetChanged() {
            JSONArray fetched = null;
            if (!SecureStore.token(context).isEmpty()) {
                try {
                    fetched = KeptApi.notes(context);
                    SecureStore.saveNotes(context, fetched.toString());
                } catch (Exception ignored) {
                    // Offline: show the last encrypted snapshot instead.
                }
            }
            if (fetched == null) {
                try { fetched = new JSONArray(SecureStore.cachedNotes(context)); }
                catch (Exception ignored) { fetched = new JSONArray(); }
            }
            String filter = SecureStore.preferences(context).getString("filter_" + widgetId, "all");
            notes.clear();
            for (int i = 0; i < fetched.length(); i++) {
                JSONObject note = fetched.optJSONObject(i);
                if (note != null && !note.optBoolean("archived") && !note.optBoolean("trashed")
                    && matches(note, filter)) notes.add(note);
            }
        }

        private boolean matches(JSONObject note, String filter) {
            if ("pinned".equals(filter)) return note.optBoolean("pinned");
            if (filter == null || !filter.startsWith("label:")) return true;
            String id = filter.substring(6);
            JSONArray labels = note.optJSONArray("labels");
            if (labels == null) return false;
            for (int i = 0; i < labels.length(); i++) {
                JSONObject label = labels.optJSONObject(i);
                if (label != null && (id.equals(String.valueOf(label.optLong("id", -1)))
                        || id.equalsIgnoreCase(label.optString("name")))) return true;
            }
            return false;
        }

        @Override public synchronized int getCount() { return notes.size(); }

        @Override public synchronized RemoteViews getViewAt(int position) {
            if (position < 0 || position >= notes.size()) return null;
            JSONObject note = notes.get(position);
            RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_note_row);
            String bg = note.optString("bgColor", "");
            WidgetAppearance.applyCard(context,row,R.id.note_card,widgetId,bg);
            boolean dark=WidgetAppearance.dark(context,widgetId);
            row.setTextColor(R.id.note_title,WidgetAppearance.text(dark));
            row.setTextColor(R.id.note_body,WidgetAppearance.text(dark));
            row.setTextColor(R.id.note_footer,WidgetAppearance.muted(dark));

            long noteId = note.optLong("id", -1L);
            boolean locked = note.optBoolean("locked")
                && !note.optBoolean("lockedContentAvailable", false);
            String title = KeptApi.plain(note.optString("noteTitle"));
            String body = locked ? "🔒 Locked note" : KeptApi.formatted(note.optString("noteBody",""));
            // A checklist's previewText contains the *same* items again as flattened text.
            if (note.optBoolean("isCbox") && body.isBlank()) body = "";
            row.setTextViewText(R.id.note_title, title);
            row.setViewVisibility(R.id.note_title, title.isEmpty() ? View.GONE : View.VISIBLE);
            row.setTextViewText(R.id.note_body, body);
            row.setViewVisibility(R.id.note_body, body.isEmpty() ? View.GONE : View.VISIBLE);

            Intent open = new Intent();
            open.putExtra(NoteEditorActivity.EXTRA_ID, noteId);
            open.putExtra(WidgetActionReceiver.EXTRA_ACTION, "open");
            row.setOnClickFillInIntent(R.id.note_card, open);

            int shown = 0;
            JSONArray checks = locked ? null : note.optJSONArray("checkBoxes");
            if (note.optBoolean("isCbox") && checks != null) {
                // Prefer unfinished items. The card API returns up to 8 visible checklist items.
                for (int i = 0; i < checks.length() && shown < CHECK_IDS.length; i++) {
                    JSONObject item = checks.optJSONObject(i);
                    if (item == null) continue;
                    String text = KeptApi.formatted(item.optString("data"));
                    if (text.isBlank()) continue;
                    int resId = CHECK_IDS[shown++];
                    boolean done = item.optBoolean("done");
                    row.setViewVisibility(resId, View.VISIBLE);
                    row.setTextViewText(resId, (done ? "☑   " : "☐   ") + text);
                    row.setTextColor(resId, done ? (dark ? 0xff9aa296 : 0xff858982) : WidgetAppearance.text(dark));
                    if (noteId > 0) {
                        Intent toggle = new Intent();
                        toggle.putExtra(NoteEditorActivity.EXTRA_ID, noteId);
                        toggle.putExtra(WidgetActionReceiver.EXTRA_ITEM, item.optLong("id"));
                        toggle.putExtra(WidgetActionReceiver.EXTRA_ACTION, "toggle");
                        row.setOnClickFillInIntent(resId, toggle);
                    }
                }
            }
            for (int i = shown; i < CHECK_IDS.length; i++)
                row.setViewVisibility(CHECK_IDS[i], View.GONE);

            String footer = note.optBoolean("pinned") ? "📌 Pinned" : "";
            if (checks != null && checks.length() > shown)
                footer += (footer.isEmpty() ? "" : "  ·  ") + "+" + (checks.length()-shown) + " more shown in editor";
            row.setTextViewText(R.id.note_footer, footer);
            row.setViewVisibility(R.id.note_footer, footer.isEmpty() ? View.GONE : View.VISIBLE);
            return row;
        }

        @Override public RemoteViews getLoadingView() { return null; }
        @Override public int getViewTypeCount() { return 1; }
        @Override public synchronized long getItemId(int position) {
            return position >= 0 && position < notes.size()
                ? notes.get(position).optLong("id", position) : position;
        }
        @Override public boolean hasStableIds() { return true; }
        @Override public void onDestroy() { synchronized (this) { notes.clear(); } }
    }
}
