package com.frostaz6.keptwidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Notes home screen. All UI renders on main thread; networking runs on a single executor. */
public final class NotesActivity extends Activity {
    private static final int BG = 0xff16191a;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ArrayList<JSONObject> notes = new ArrayList<>();
    private final ArrayList<JSONObject> labels = new ArrayList<>();
    private final ArrayList<String> filterNames = new ArrayList<>();
    private final ArrayList<String> filterValues = new ArrayList<>();
    private LinearLayout root, cards;
    private EditText search;
    private Spinner filter;
    private TextView hint;
    private boolean loading;
    private String activeFilter = "all";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (SecureStore.token(this).isEmpty()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        buildPage();
        showCached();
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        if(PendingSync.count(this)>0)PendingSync.schedule(this);
        if (root != null && !loading) refresh();
    }

    @Override protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private void buildPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(d(14), d(22), d(14), d(32));
        scroll.addView(root);
        setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            root.setPadding(d(14), d(20) + insets.getSystemWindowInsetTop(), d(14),
                d(32) + insets.getSystemWindowInsetBottom());
            return insets;
        });

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(top);
        TextView brand = ScreenUi.label(this, "Kept", 30, true);
        brand.setTextColor(0xffc5ed9a);
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        Button refresh = small("↻");
        top.addView(refresh);
        refresh.setOnClickListener(v -> refresh());
        Button pendingButton=small("⇅");
        pendingButton.setContentDescription("Offline sync status");
        top.addView(pendingButton);
        pendingButton.setOnClickListener(v->startActivity(new Intent(this,SyncStatusActivity.class)));
        Button settings = small("⚙");
        top.addView(settings);
        settings.setOnClickListener(v -> startActivity(new Intent(this, WidgetSettingsActivity.class)));

        search = ScreenUi.field(this, "⌕  Search notes", android.text.InputType.TYPE_CLASS_TEXT);
        search.setSingleLine(true);
        GradientDrawable searchBg = shape(0xff292f2e, 16, 0xff444d46);
        search.setBackground(searchBg);
        search.setPadding(d(15), d(12), d(15), d(12));
        root.addView(search, ScreenUi.margins(this,0,14,0,12));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { render(); }
            @Override public void afterTextChanged(Editable s) { }
        });

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(controls);
        filter = new Spinner(this);
        controls.addView(filter, new LinearLayout.LayoutParams(0, d(48), 1f));
        filter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                if (pos < filterValues.size()) activeFilter = filterValues.get(pos);
                render();
            }
        });
        Button addList = small("☑");
        controls.addView(addList);
        addList.setContentDescription("New checklist");
        addList.setOnClickListener(v -> create(true));
        Button addNote = small("+");
        controls.addView(addNote);
        addNote.setContentDescription("New note");
        addNote.setOnClickListener(v -> create(false));

        hint = ScreenUi.hint(this, "Loading notes…");
        root.addView(hint, ScreenUi.margins(this,4,12,0,12));
        cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.VERTICAL);
        root.addView(cards);
        installFilters();
    }

    private void create(boolean checklist) {
        Intent edit = new Intent(this, NoteEditorActivity.class);
        edit.putExtra(NoteEditorActivity.EXTRA_LIST, checklist);
        startActivity(edit);
    }

    private Button small(String label) {
        Button b = ScreenUi.button(this, label, false);
        b.setTextSize(20);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(d(48), d(48));
        p.leftMargin = d(5);
        b.setLayoutParams(p);
        return b;
    }

    private int d(int px) { return ScreenUi.dp(this,px); }

    private GradientDrawable shape(int fill, int radius, int stroke) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fill);
        bg.setCornerRadius(d(radius));
        bg.setStroke(d(1), stroke);
        return bg;
    }

    private void showCached() {
        try {
            JSONArray data = PendingSync.overlay(this,new JSONArray(SecureStore.cachedNotes(this)));
            notes.clear();
            for (int i=0;i<data.length();i++) {
                JSONObject note = data.optJSONObject(i);
                if (note != null) notes.add(note);
            }
            if (!notes.isEmpty()) {
                hint.setText("Offline snapshot • Pull refreshed when online");
                render();
            }
        } catch(Exception ignored) { }
    }

    private void refresh() {
        if (loading) return;
        loading = true;
        hint.setText("Syncing notes…");
        io.execute(() -> {
            try {
                JSONArray fetched = KeptApi.notes(this);
                JSONArray allLabels = KeptApi.labels(this);
                SecureStore.saveNotes(this, fetched.toString());
                JSONArray showing=PendingSync.overlay(this,fetched);
                runOnUiThread(() -> {
                    loading = false;
                    notes.clear();
                    for (int i=0;i<showing.length();i++) {
                        JSONObject note = showing.optJSONObject(i);
                        if (note!=null) notes.add(note);
                    }
                    labels.clear();
                    for (int i=0;i<allLabels.length();i++) {
                        JSONObject label = allLabels.optJSONObject(i);
                        if (label!=null) labels.add(label);
                    }
                    installFilters();
                    hint.setText(notes.size() + " notes • " +
                        (PendingSync.count(this)>0?PendingSync.count(this)+" pending edits":
                            "Synced"));
                    render();
                    CollectionWidgetProvider.refreshAll(this);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    loading = false;
                    hint.setText("Offline · " + error.getMessage());
                    render();
                });
            }
        });
    }

    private void installFilters() {
        filterNames.clear();
        filterValues.clear();
        addFilter("All notes", "all");
        addFilter("Pinned", "pinned");
        addFilter("Checklists", "checklists");
        for (JSONObject label : labels) {
            addFilter("🏷  " + label.optString("name"), "label:" + label.optLong("id",-1));
        }
        int chosen = Math.max(0, filterValues.indexOf(activeFilter));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item, filterNames);
        filter.setAdapter(adapter);
        filter.setSelection(chosen);
    }

    private void addFilter(String title, String value) {
        filterNames.add(title);
        filterValues.add(value);
    }

    private boolean matches(JSONObject n, String query) {
        if (n.optBoolean("trashed") || n.optBoolean("archived")) return false;
        if (activeFilter.equals("pinned") && !n.optBoolean("pinned")) return false;
        if (activeFilter.equals("checklists") && !n.optBoolean("isCbox")) return false;
        if (activeFilter.startsWith("label:")) {
            JSONArray labs = n.optJSONArray("labels");
            boolean found = false;
            for (int i=0; labs!=null && i<labs.length(); i++) {
                JSONObject l = labs.optJSONObject(i);
                if (l!=null && activeFilter.equals("label:" + l.optLong("id",-1))) found = true;
            }
            if (!found) return false;
        }
        if (query.isEmpty()) return true;
        StringBuilder hay = new StringBuilder();
        hay.append(KeptApi.plain(n.optString("noteTitle"))).append(' ')
           .append(KeptApi.plain(n.optString("previewText",n.optString("noteBody"))));
        JSONArray checks = n.optJSONArray("checkBoxes");
        for (int i=0; checks!=null && i<checks.length(); i++) {
            JSONObject c=checks.optJSONObject(i);
            if(c!=null) hay.append(' ').append(KeptApi.plain(c.optString("data")));
        }
        return hay.toString().toLowerCase(java.util.Locale.ROOT).contains(query);
    }

    private void render() {
        if (cards == null) return;
        cards.removeAllViews();
        String q = search.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
        ArrayList<JSONObject> shown = new ArrayList<>();
        for (JSONObject n : notes) if (matches(n, q)) shown.add(n);
        if (shown.isEmpty()) {
            TextView empty = ScreenUi.hint(this, notes.isEmpty() ?
                "No notes yet. Tap + to make your first note." : "No notes match your search or filter.");
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(d(18),d(50),d(18),d(40));
            cards.addView(empty);
            return;
        }
        int columns = getResources().getConfiguration().screenWidthDp >= 610 ? 3 : 2;
        for (int start=0; start<shown.size();start+=columns) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            cards.addView(row);
            for (int i=0;i<columns;i++) {
                JSONObject note = start+i<shown.size()? shown.get(start+i): null;
                if (note==null) {
                    View spacer = new View(this);
                    row.addView(spacer,new LinearLayout.LayoutParams(0,d(1),1f));
                    continue;
                }
                View card = card(note);
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1f);
                p.setMargins(d(4),d(4),d(4),d(4));
                row.addView(card,p);
            }
        }
    }

    private View card(JSONObject note) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(d(12), d(13), d(12), d(11));
        int color = 0xff303338;
        try {
            String raw=note.optString("bgColor","");
            if(raw.startsWith("#")) color=WidgetAppearance.noteColor(raw,true);
        } catch (IllegalArgumentException ignored) { }
        card.setBackground(shape(color, 16, 0xff515652));
        card.setElevation(d(1));
        String title = KeptApi.plain(note.optString("noteTitle"));
        boolean locked = note.optBoolean("locked") && !note.optBoolean("lockedContentAvailable", false);
        if(!title.isEmpty()) {
            TextView t=ScreenUi.label(this,title,15,true);
            t.setMaxLines(3);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(t);
        }
        if (locked) {
            card.addView(ScreenUi.hint(this,"🔒 Locked"),ScreenUi.margins(this,0,8,0,0));
        } else if (note.optBoolean("isCbox")) {
            JSONArray items=note.optJSONArray("checkBoxes");
            int shown=0;
            for(int i=0;items!=null && i<items.length() && shown<5;i++) {
                JSONObject item=items.optJSONObject(i);
                if(item==null)continue;
                String text=KeptApi.plain(item.optString("data"));
                if(text.isEmpty())continue;
                TextView line=ScreenUi.label(this,
                    (item.optBoolean("done")?"☑ ":"☐ ")+text,13,false);
                line.setMaxLines(2);
                line.setEllipsize(android.text.TextUtils.TruncateAt.END);
                card.addView(line,ScreenUi.margins(this,0,8,0,0));
                shown++;
            }
        } else {
            String body=KeptApi.formatted(note.optString("noteBody"));
            if(!body.isEmpty()) {
                TextView text=ScreenUi.label(this,body,13,false);
                text.setMaxLines(12);
                text.setEllipsize(android.text.TextUtils.TruncateAt.END);
                card.addView(text,ScreenUi.margins(this,0,8,0,0));
            }
        }
        if (note.optBoolean("syncPending")) {
            TextView queued=ScreenUi.hint(this,note.optBoolean("syncConflict")?
                "⚠ Sync conflict — tap Sync status" : "⏳ Waiting to sync");
            card.addView(queued,ScreenUi.margins(this,0,7,0,0));
        }
        if (note.optBoolean("pinned")) {
            TextView pin=ScreenUi.hint(this,"📌 Pinned");
            card.addView(pin,ScreenUi.margins(this,0,8,0,0));
        }
        long id=note.optLong("id",-1);
        if(id>0) {
            card.setOnClickListener(v -> {
                Intent edit = new Intent(this, NoteEditorActivity.class);
                edit.putExtra(NoteEditorActivity.EXTRA_ID,id);
                startActivity(edit);
            });
            card.setOnLongClickListener(v -> {
                PopupMenu menu=new PopupMenu(this,card);
                menu.getMenu().add(note.optBoolean("pinned")?"Unpin":"Pin");
                menu.getMenu().add("Archive");
                menu.getMenu().add("Move to trash");
                menu.setOnMenuItemClickListener(item -> {
                    String name=item.getTitle().toString();
                    if(name.equals("Move to trash")) {
                        new AlertDialog.Builder(this).setMessage("Move this note to Kept's trash?")
                            .setNegativeButton("Cancel",null)
                            .setPositiveButton("Move to trash",(dialog,which)->update(id,"trashed",true))
                            .show();
                    } else if(name.equals("Archive")) update(id,"archived",true);
                    else update(id,"pinned",!note.optBoolean("pinned"));
                    return true;
                });
                menu.show();
                return true;
            });
        }
        return card;
    }

    private void update(long id, String key, boolean value) {
        io.execute(() -> {
            try {
                JSONObject patch=new JSONObject();
                patch.put(key,value);
                KeptApi.patch(this,id,patch);
                runOnUiThread(this::refresh);
            } catch(Exception e) {
                runOnUiThread(() -> Toast.makeText(this,"Could not update note: "+e.getMessage(),
                    Toast.LENGTH_LONG).show());
            }
        });
    }
}
