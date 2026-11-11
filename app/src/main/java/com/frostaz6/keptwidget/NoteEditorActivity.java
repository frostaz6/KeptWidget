package com.frostaz6.keptwidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class NoteEditorActivity extends Activity {
    public static final String EXTRA_ID = "note_id";
    public static final String EXTRA_LIST = "make_list";
    private static final AtomicLong ITEM_IDS = new AtomicLong(System.currentTimeMillis());

    private final List<ItemRow> items = new ArrayList<>();
    private long noteId = -1L;
    private boolean isChecklist;
    private JSONObject original;
    private EditText title, body;
    private TextView status;
    private LinearLayout itemHost;
    private Button save, addItem;
    private boolean saving;

    private static final class ItemRow {
        final CheckBox checked;
        final EditText text;
        final long id;
        final JSONObject original;
        ItemRow(CheckBox checked, EditText text, long id, JSONObject original) {
            this.checked = checked;
            this.text = text;
            this.id = id;
            this.original = original;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (SecureStore.token(this).isEmpty()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        noteId = getIntent().getLongExtra(EXTRA_ID, -1L);
        isChecklist = getIntent().getBooleanExtra(EXTRA_LIST, false);
        LinearLayout root = ScreenUi.page(this, noteId > 0 ? "Edit note" :
            (isChecklist ? "New checklist" : "New note"));
        status = ScreenUi.hint(this, noteId > 0 ? "Loading note…" : "Syncs to your Kept server.");
        root.addView(status, ScreenUi.margins(this,0,0,0,12));
        title = ScreenUi.field(this, "Title", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        root.addView(title, ScreenUi.margins(this,0,0,0,14));
        body = ScreenUi.field(this, "Write a note…", InputType.TYPE_CLASS_TEXT |
            InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        body.setSingleLine(false);
        body.setMinLines(5);
        body.setGravity(Gravity.TOP | Gravity.START);
        root.addView(body, ScreenUi.margins(this,0,0,0,12));
        itemHost = new LinearLayout(this);
        itemHost.setOrientation(LinearLayout.VERTICAL);
        root.addView(itemHost, ScreenUi.margins(this,0,0,0,10));
        addItem = ScreenUi.button(this, "+ Add checklist item", false);
        root.addView(addItem, ScreenUi.margins(this,0,0,0,14));
        addItem.setOnClickListener(v -> addRow(null));
        save = ScreenUi.button(this, "Save to Kept", true);
        root.addView(save, ScreenUi.margins(this,0,0,0,10));
        save.setOnClickListener(v -> submit());
        Button browser = ScreenUi.button(this, "Open full Kept web editor", false);
        root.addView(browser, ScreenUi.margins(this,0,0,0,10));
        browser.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
            Uri.parse(SecureStore.baseUrl(this)))));
        Button cancel = ScreenUi.button(this, "Close", false);
        root.addView(cancel, ScreenUi.margins(this,0,0,0,8));
        cancel.setOnClickListener(v -> finish());
        if (noteId > 0) load();
        else updateType();
    }

    private void updateType() {
        body.setVisibility(isChecklist ? View.GONE : View.VISIBLE);
        itemHost.setVisibility(isChecklist ? View.VISIBLE : View.GONE);
        addItem.setVisibility(isChecklist ? View.VISIBLE : View.GONE);
        if (isChecklist && items.isEmpty()) addRow(null);
    }

    private void load() {
        long id = noteId;
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                JSONObject note = KeptApi.note(this, id);
                runOnUiThread(() -> {
                    original = note;
                    isChecklist = note.optBoolean("isCbox");
                    title.setText(KeptApi.plain(note.optString("noteTitle")));
                    body.setText(KeptApi.plain(note.optString("noteBody")));
                    if (note.optBoolean("locked") && !note.optBoolean("lockedContentAvailable", false)) {
                        status.setText("Locked notes must be edited through Kept.");
                        save.setEnabled(false);
                        return;
                    }
                    items.clear();
                    itemHost.removeAllViews();
                    JSONArray checks = note.optJSONArray("checkBoxes");
                    if (checks != null) for (int i = 0; i < checks.length(); i++) {
                        JSONObject entry = checks.optJSONObject(i);
                        if (entry != null) addRow(entry);
                    }
                    status.setText(isChecklist ? "Tap checkboxes to complete items.": "Edit your note below.");
                    updateType();
                });
            } catch (Exception e) {
                runOnUiThread(() -> { status.setText(e.getMessage()); save.setEnabled(false); });
            }
        });
    }

    private void addRow(JSONObject existing) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        CheckBox checked = new CheckBox(this);
        checked.setChecked(existing != null && existing.optBoolean("done"));
        line.addView(checked, new LinearLayout.LayoutParams(ScreenUi.dp(this,48),
            LinearLayout.LayoutParams.WRAP_CONTENT));
        EditText edit = ScreenUi.field(this, "List item", InputType.TYPE_CLASS_TEXT |
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        edit.setText(existing == null ? "" : KeptApi.plain(existing.optString("data")));
        line.addView(edit, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button remove = ScreenUi.button(this, "×", false);
        line.addView(remove, new LinearLayout.LayoutParams(ScreenUi.dp(this,42),
            ScreenUi.dp(this,42)));
        long id = existing == null ? ITEM_IDS.incrementAndGet() : existing.optLong("id", ITEM_IDS.incrementAndGet());
        ItemRow row = new ItemRow(checked, edit, id, existing);
        items.add(row);
        itemHost.addView(line, ScreenUi.margins(this,0,0,0,6));
        remove.setOnClickListener(v -> { items.remove(row); itemHost.removeView(line); });
    }

    private JSONArray currentItems() throws Exception {
        JSONArray result = new JSONArray();
        for (ItemRow row : items) {
            String text = row.text.getText().toString().trim();
            if (text.isEmpty()) continue;
            JSONObject entry = row.original == null ? new JSONObject()
                : new JSONObject(row.original.toString());
            entry.put("id", row.id);
            entry.put("done", row.checked.isChecked());
            if (row.original == null || !text.equals(KeptApi.plain(row.original.optString("data"))))
                entry.put("data", KeptApi.toHtml(text));
            if (!entry.has("indentLevel")) entry.put("indentLevel", 0);
            result.put(entry);
        }
        return result;
    }

    private void submit() {
        if (saving || (noteId > 0 && original == null)) return;
        String titleText = title.getText().toString().trim();
        String bodyText = body.getText().toString();
        final JSONArray checks;
        try { checks = isChecklist ? currentItems() : new JSONArray(); }
        catch (Exception e) { status.setText(e.getMessage()); return; }
        if (noteId <= 0 && titleText.isEmpty() && bodyText.trim().isEmpty() && checks.length() == 0) {
            status.setText("Enter a title, note, or checklist item first.");
            return;
        }
        if (noteId > 0 && !isChecklist && original != null
            && !bodyText.equals(KeptApi.plain(original.optString("noteBody")))
            && original.optString("noteBody").matches("(?s).*<(?!(?:br)[\\s/>])[a-zA-Z][^>]*>.*")) {
            new AlertDialog.Builder(this).setTitle("Rich text formatting")
                .setMessage("Saving the edited body will replace rich formatting with plain text. Continue?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> performSave(titleText, bodyText, checks)).show();
            return;
        }
        performSave(titleText, bodyText, checks);
    }

    private void performSave(String titleText, String bodyText, JSONArray checks) {
        saving = true;
        save.setEnabled(false);
        status.setText("Saving…");
        long id = noteId;
        JSONObject before = original;
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                if (id <= 0) {
                    payload.put("noteTitle", titleText);
                    payload.put("noteBody", isChecklist ? "" : KeptApi.toHtml(bodyText));
                    payload.put("checkBoxes", checks);
                    payload.put("isCbox", isChecklist);
                    payload.put("images", new JSONArray());
                    payload.put("labels", new JSONArray());
                    KeptApi.add(this, payload);
                } else {
                    if (!titleText.equals(KeptApi.plain(before.optString("noteTitle"))))
                        payload.put("noteTitle", titleText);
                    if (isChecklist) {
                        JSONArray old = before.optJSONArray("checkBoxes");
                        if (!checks.toString().equals(old == null ? "[]" : old.toString()))
                            payload.put("checkBoxes", checks);
                    } else if (!bodyText.equals(KeptApi.plain(before.optString("noteBody")))) {
                        payload.put("noteBody", KeptApi.toHtml(bodyText));
                    }
                    if (payload.length() != 0) KeptApi.patch(this, id, payload);
                }
                CollectionWidgetProvider.refreshAll(this);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Saved to Kept", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    saving = false;
                    save.setEnabled(true);
                    status.setText("Save failed: " + e.getMessage());
                });
            }
        });
    }
}
