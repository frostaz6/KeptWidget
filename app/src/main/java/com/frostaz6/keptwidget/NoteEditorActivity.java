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
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

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
    private Button save, addItem, colorButton, labelsButton;
    private CheckBox pinned;
    private boolean colorTouched = false, labelsTouched = false;
    private String selectedColor = "#FFFFFF";
    private JSONArray chosenLabels = new JSONArray();
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

        pinned = new CheckBox(this);
        pinned.setText("Pin this note");
        pinned.setTextSize(16);
        root.addView(pinned, ScreenUi.margins(this,0,0,0,8));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(actions, ScreenUi.margins(this,0,0,0,12));
        colorButton = ScreenUi.button(this, "🎨 Color", false);
        actions.addView(colorButton, new LinearLayout.LayoutParams(0,ScreenUi.dp(this,48),1f));
        colorButton.setOnClickListener(v -> pickColor());
        labelsButton = ScreenUi.button(this, "🏷 Labels", false);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0,ScreenUi.dp(this,48),1f);
        labelParams.leftMargin = ScreenUi.dp(this,8);
        actions.addView(labelsButton,labelParams);
        labelsButton.setOnClickListener(v -> pickLabels());

        save = ScreenUi.button(this, "Save to Kept", true);
        root.addView(save, ScreenUi.margins(this,0,0,0,10));
        save.setOnClickListener(v -> submit());
        if (noteId > 0) {
            Button archive = ScreenUi.button(this,"Archive this note",false);
            root.addView(archive,ScreenUi.margins(this,0,0,0,10));
            archive.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage("Archive this note in Kept?")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Archive",(d,w)->changeLifecycle("archived"))
                .show());
            Button trash = ScreenUi.button(this,"Move note to trash",false);
            root.addView(trash,ScreenUi.margins(this,0,0,0,10));
            trash.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage("Move this note to trash? You can restore it from Kept later.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Trash",(d,w)->changeLifecycle("trashed"))
                .show());
        }
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
                    pinned.setChecked(note.optBoolean("pinned"));
                    chosenLabels = note.optJSONArray("labels");
                    if (chosenLabels == null) chosenLabels = new JSONArray();
                    String existingColor = note.optString("bgColor","#FFFFFF");
                    selectedColor = existingColor.isEmpty() ? "#FFFFFF" : existingColor;
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
                    labelsButton.setText("🏷 Labels (" + chosenLabels.length() + ")");
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
                    payload.put("labels", chosenLabels);
                    payload.put("pinned", pinned.isChecked());
                    if (colorTouched) payload.put("bgColor",selectedColor);
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
                    if (pinned.isChecked() != before.optBoolean("pinned"))
                        payload.put("pinned",pinned.isChecked());
                    if (colorTouched) payload.put("bgColor",selectedColor);
                    if (labelsTouched) payload.put("labels",chosenLabels);
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
    private void pickColor() {
        final String[] names = {"Default","Red","Orange","Yellow","Green","Blue","Lavender","Pink","Gray"};
        final String[] values = {"#FFFFFF","#FADBD8","#FCE5CA","#FFF3BC","#DBEBD3",
            "#D6EAF6","#E8DDF6","#F6DAE6","#E8E8E8"};
        new AlertDialog.Builder(this).setTitle("Note color")
            .setItems(names,(dialog,index)-> {
                selectedColor=values[index];
                colorTouched=true;
                colorButton.setText("🎨 " + names[index]);
            }).show();
    }

    private void pickLabels() {
        labelsButton.setEnabled(false);
        Executors.newSingleThreadExecutor().execute(()-> {
            try {
                JSONArray catalog=KeptApi.labels(this);
                java.util.ArrayList<JSONObject> available=new java.util.ArrayList<>();
                java.util.ArrayList<String> names=new java.util.ArrayList<>();
                for(int i=0;i<catalog.length();i++){
                    JSONObject label=catalog.optJSONObject(i);
                    if(label!=null && !label.optString("name").isEmpty()){
                        available.add(label);
                        names.add(label.optString("name"));
                    }
                }
                boolean[] selected=new boolean[available.size()];
                for(int i=0;i<available.size();i++){
                    long id=available.get(i).optLong("id",-1);
                    for(int j=0;j<chosenLabels.length();j++) {
                        JSONObject existing=chosenLabels.optJSONObject(j);
                        if(existing!=null && existing.optLong("id",-2)==id) selected[i]=true;
                    }
                }
                runOnUiThread(()-> {
                    labelsButton.setEnabled(true);
                    new AlertDialog.Builder(this).setTitle("Labels")
                        .setMultiChoiceItems(names.toArray(new String[0]),selected,
                            (dialog,index,checked)->selected[index]=checked)
                        .setNegativeButton("Cancel",null)
                        .setPositiveButton("Apply",(dialog,which)-> {
                            JSONArray result=new JSONArray();
                            for(int i=0;i<available.size();i++)if(selected[i])result.put(available.get(i));
                            chosenLabels=result;
                            labelsTouched=true;
                            labelsButton.setText("🏷 Labels (" + chosenLabels.length() + ")");
                        }).show();
                });
            } catch(Exception e) {
                runOnUiThread(()->{
                    labelsButton.setEnabled(true);
                    status.setText("Can't load labels: " + e.getMessage());
                });
            }
        });
    }

    private void changeLifecycle(String field) {
        if (saving || original == null) return;
        saving = true;
        save.setEnabled(false);
        status.setText("Updating Kept…");
        Executors.newSingleThreadExecutor().execute(()-> {
            try {
                JSONObject change=new JSONObject().put(field,true);
                KeptApi.patch(this,noteId,change);
                CollectionWidgetProvider.refreshAll(this);
                runOnUiThread(()->finish());
            } catch(Exception e) {
                runOnUiThread(()->{
                    saving=false;
                    save.setEnabled(true);
                    status.setText("Could not update note: " + e.getMessage());
                });
            }
        });
    }

}
