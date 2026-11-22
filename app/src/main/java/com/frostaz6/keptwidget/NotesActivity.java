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
    private boolean gridMode;
    private Button layoutButton;
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

    /** Keep-like dark home screen: fixed search and capture surfaces around scrolling notes. */
    private void buildPage() {
        gridMode = SecureStore.preferences(this).getBoolean("notes_grid",true);
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(0xff1c1c1f);
        screen.setFitsSystemWindows(true);
        setContentView(screen);

        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setPadding(d(7),d(4),d(7),d(4));
        searchBar.setBackground(shape(0xff333439,26,0xff424349));
        LinearLayout.LayoutParams barParams=new LinearLayout.LayoutParams(-1,d(58));
        barParams.setMargins(d(12),d(13),d(12),d(8));
        screen.addView(searchBar,barParams);

        Button menu = compact("☰","Filter notes");
        searchBar.addView(menu,new LinearLayout.LayoutParams(d(49),d(46)));
        menu.setOnClickListener(this::openHomeMenu);

        search=ScreenUi.field(this,"Search your notes",
            android.text.InputType.TYPE_CLASS_TEXT);
        search.setSingleLine(true);
        search.setTextSize(17);
        search.setBackgroundColor(Color.TRANSPARENT);
        search.setPadding(d(2),0,d(2),0);
        searchBar.addView(search,new LinearLayout.LayoutParams(0,d(49),1f));
        search.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence text,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence text,int start,int before,int count){render();}
            @Override public void afterTextChanged(Editable text){}
        });

        layoutButton=compact(gridMode?"☷":"▦","Toggle notes grid or list view");
        searchBar.addView(layoutButton,new LinearLayout.LayoutParams(d(45),d(46)));
        layoutButton.setOnClickListener(v->{
            gridMode=!gridMode;
            SecureStore.preferences(this).edit().putBoolean("notes_grid",gridMode).apply();
            layoutButton.setText(gridMode?"☷":"▦");
            render();
        });
        Button settings=compact("⚙","Widget and account settings");
        searchBar.addView(settings,new LinearLayout.LayoutParams(d(45),d(46)));
        settings.setOnClickListener(v->startActivity(new Intent(this,WidgetSettingsActivity.class)));

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        screen.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(d(11),d(5),d(11),d(30));
        scroll.addView(root);

        LinearLayout filters=new LinearLayout(this);
        filters.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(filters,new LinearLayout.LayoutParams(-1,d(47)));
        filter=new Spinner(this);
        filters.addView(filter,new LinearLayout.LayoutParams(0,d(45),1f));
        filter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){
                if(position<filterValues.size())activeFilter=filterValues.get(position);
                render();
            }
        });
        Button refresh=compact("↻","Refresh Kept notes");
        filters.addView(refresh,new LinearLayout.LayoutParams(d(45),d(45)));
        refresh.setOnClickListener(v->refresh());
        Button sync=compact("⇅","Offline sync status");
        filters.addView(sync,new LinearLayout.LayoutParams(d(45),d(45)));
        sync.setOnClickListener(v->startActivity(new Intent(this,SyncStatusActivity.class)));

        hint=ScreenUi.hint(this,"Loading notes…");
        hint.setTextColor(0xffa8aea6);
        root.addView(hint,ScreenUi.margins(this,8,3,0,9));
        cards=new LinearLayout(this);
        cards.setOrientation(LinearLayout.VERTICAL);
        root.addView(cards);

        // Fixed compose bar: quick capture stays reachable while scrolling.
        LinearLayout capture=new LinearLayout(this);
        capture.setGravity(Gravity.CENTER_VERTICAL);
        capture.setPadding(d(14),d(6),d(14),d(6));
        capture.setBackgroundColor(0xff2d2e33);
        screen.addView(capture,new LinearLayout.LayoutParams(-1,d(68)));
        Button newList=compact("☑","New checklist");
        newList.setTextSize(25);
        capture.addView(newList,new LinearLayout.LayoutParams(d(60),d(52)));
        newList.setOnClickListener(v->create(true));
        Button newNote=compact("＋  Take a note…","Create note");
        newNote.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
        newNote.setTextSize(17);
        newNote.setTextColor(0xffdadbdd);
        LinearLayout.LayoutParams composeParams=new LinearLayout.LayoutParams(0,d(52),1f);
        composeParams.leftMargin=d(9);
        capture.addView(newNote,composeParams);
        newNote.setOnClickListener(v->create(false));
        installFilters();
    }

    private Button compact(String value,String description) {
        Button button=new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(21);
        button.setTextColor(0xffe3e3e6);
        button.setContentDescription(description);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setPadding(0,0,0,0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        return button;
    }

    private void openHomeMenu(View anchor) {
        PopupMenu menu=new PopupMenu(this,anchor);
        menu.getMenu().add("All notes");
        menu.getMenu().add("Pinned");
        menu.getMenu().add("Checklists");
        menu.getMenu().add("Widget settings");
        menu.getMenu().add("Sync status");
        menu.setOnMenuItemClickListener(item->{
            String name=item.getTitle().toString();
            String next="All notes".equals(name)?"all":
                "Pinned".equals(name)?"pinned":
                "Checklists".equals(name)?"checklists":null;
            if(next!=null){
                activeFilter=next;
                int idx=filterValues.indexOf(next);
                if(idx>=0)filter.setSelection(idx);
                render();
            }else if("Widget settings".equals(name))
                startActivity(new Intent(this,WidgetSettingsActivity.class));
            else if("Sync status".equals(name))
                startActivity(new Intent(this,SyncStatusActivity.class));
            return true;
        });
        menu.show();
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
        if(cards==null || search==null)return;
        cards.removeAllViews();
        String query=search.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
        ArrayList<JSONObject> visible=new ArrayList<>();
        for(JSONObject note:notes)if(matches(note,query))visible.add(note);
        if(visible.isEmpty()){
            TextView empty=ScreenUi.hint(this,notes.isEmpty()
                ?"Your notes appear here. Tap 'Take a note' to start."
                :"No notes match this search or filter.");
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(d(18),d(65),d(18),d(40));
            cards.addView(empty);
            return;
        }
        if("all".equals(activeFilter) && query.isEmpty()){
            ArrayList<JSONObject> pinned=new ArrayList<>();
            ArrayList<JSONObject> other=new ArrayList<>();
            for(JSONObject note:visible) {
                if(note.optBoolean("pinned"))pinned.add(note);
                else other.add(note);
            }
            if(!pinned.isEmpty()){
                heading("PINNED");
                renderCards(pinned);
            }
            if(!other.isEmpty()){
                if(!pinned.isEmpty())heading("OTHERS");
                renderCards(other);
            }
        }else renderCards(visible);
    }

    private void heading(String title) {
        TextView label=ScreenUi.hint(this,title);
        label.setTextSize(11);
        label.setLetterSpacing(0.11f);
        label.setTextColor(0xffaeb1b0);
        label.setPadding(d(10),d(20),d(5),d(9));
        cards.addView(label);
    }

    static int columnsForWidth(int widthDp,boolean grid) {
        return grid ? (widthDp>=610?3:widthDp>=270?2:1) : 1;
    }

    /** Balance Keep-style variable-height note cards in independent columns. */
    static int estimatedCardHeight(JSONObject note) {
        int title=KeptApi.plain(note.optString("noteTitle")).length();
        int score=4+Math.min(4,title/24);
        if(note.optBoolean("isCbox")){
            JSONArray checks=note.optJSONArray("checkBoxes");
            int unfinished=0;
            for(int i=0;checks!=null&&i<checks.length();i++){
                JSONObject item=checks.optJSONObject(i);
                if(item!=null&&!item.optBoolean("done"))unfinished++;
            }
            score+=Math.min(5,unfinished)*2;
        }else{
            String body=KeptApi.formatted(note.optString("noteBody"));
            score+=Math.min(11,Math.max(body.length()/33,
                body.isEmpty()?0:body.split("\\n",-1).length));
        }
        return score;
    }

    private void renderCards(ArrayList<JSONObject> shown) {
        int count=columnsForWidth(getResources().getConfiguration().screenWidthDp,gridMode);
        if(count==1){
            for(JSONObject note:shown)
                cards.addView(card(note),ScreenUi.margins(this,4,4,4,6));
            return;
        }
        LinearLayout holder=new LinearLayout(this);
        holder.setOrientation(LinearLayout.HORIZONTAL);
        cards.addView(holder);
        ArrayList<LinearLayout> columns=new ArrayList<>();
        int[] heights=new int[count];
        for(int i=0;i<count;i++){
            LinearLayout column=new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            columns.add(column);
            holder.addView(column,new LinearLayout.LayoutParams(0,-2,1f));
        }
        for(JSONObject note:shown){
            int selected=0;
            for(int i=1;i<count;i++)if(heights[i]<heights[selected])selected=i;
            columns.get(selected).addView(card(note),
                ScreenUi.margins(this,4,4,4,6));
            heights[selected]+=estimatedCardHeight(note);
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
            int completed=0;
            for(int i=0;items!=null && i<items.length();i++){
                JSONObject item=items.optJSONObject(i);
                if(item!=null && item.optBoolean("done"))completed++;
            }
            for(int i=0;items!=null && i<items.length() && shown<5;i++) {
                JSONObject item=items.optJSONObject(i);
                if(item==null || item.optBoolean("done"))continue;
                String text=KeptApi.plain(item.optString("data"));
                if(text.isEmpty())continue;
                TextView line=ScreenUi.label(this,
                    (item.optBoolean("done")?"☑ ":"☐ ")+text,13,false);
                line.setMaxLines(2);
                line.setEllipsize(android.text.TextUtils.TruncateAt.END);
                card.addView(line,ScreenUi.margins(this,0,8,0,0));
                shown++;
            }
            if(completed>0){
                TextView footer=ScreenUi.hint(this,"▸ ("+completed+") Completed items");
                footer.setTextColor(0xffb5b7b1);
                card.addView(footer,ScreenUi.margins(this,0,14,0,0));
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
        JSONObject cached=null;
        for(JSONObject note:notes)if(note.optLong("id",-1)==id){cached=note;break;}
        final JSONObject original=cached;
        if(original==null)return;
        io.execute(() -> {
            try {
                JSONObject patch=new JSONObject().put(key,value);
                try {
                    KeptApi.patchIfUnchanged(this,id,patch,original);
                }catch(java.io.IOException network){
                    if(network instanceof KeptApi.ApiException)throw network;
                    PendingSync.edit(this,id,patch,original);
                }
                runOnUiThread(this::refresh);
            } catch(Exception e) {
                runOnUiThread(() -> Toast.makeText(this,"Could not update note: "+e.getMessage(),
                    Toast.LENGTH_LONG).show());
            }
        });
    }
}
