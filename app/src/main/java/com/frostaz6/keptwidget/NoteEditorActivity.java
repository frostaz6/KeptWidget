package com.frostaz6.keptwidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.content.ClipData;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.widget.ImageButton;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
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
    public static final String EXTRA_INITIAL_TITLE = "initial_title";
    public static final String EXTRA_INITIAL_BODY = "initial_body";
    private static final AtomicLong ITEM_IDS = new AtomicLong(System.currentTimeMillis());

    private final List<ItemRow> items = new ArrayList<>();
    private long noteId = -1L;
    private boolean isChecklist;
    private JSONObject original;
    private EditText title, body;
    private TextView status;
    private LinearLayout itemHost, completedHost, editorRoot;
    private ScrollView editorScroll;
    private TextView completedHeader;
    private boolean showCompleted = false;
    private ImageButton pinButton;
    private Button save, addItem, labelsButton;
    private boolean isPinned;
    private boolean colorTouched = false, labelsTouched = false;
    private String selectedColor = "#FFFFFF";
    private JSONArray chosenLabels = new JSONArray();
    private boolean saving;
    private android.window.OnBackInvokedCallback backCallback;

    private static final class ItemRow {
        final CheckBox checked;
        final EditText text;
        final LinearLayout line;
        final long id;
        final JSONObject original;
        ItemRow(CheckBox checked, EditText text, LinearLayout line, long id, JSONObject original) {
            this.checked = checked;
            this.text = text;
            this.line = line;
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
        getWindow().setStatusBarColor(0xff1c1c1f);
        getWindow().setNavigationBarColor(0xff1c1c1f);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        editorRoot = new LinearLayout(this);
        editorRoot.setOrientation(LinearLayout.VERTICAL);
        editorRoot.setFitsSystemWindows(true);
        editorRoot.setBackgroundColor(0xff1c1c1f);
        setContentView(editorRoot);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            backCallback = this::requestClose;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,backCallback);
        }

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(ScreenUi.dp(this,12),ScreenUi.dp(this,5),
            ScreenUi.dp(this,12),ScreenUi.dp(this,5));
        editorRoot.addView(toolbar,new LinearLayout.LayoutParams(-1,ScreenUi.dp(this,60)));

        ImageButton back = icon(R.drawable.ic_editor_back, "Back and save note");
        toolbar.addView(back,iconParams());
        back.setOnClickListener(v -> requestClose());
        toolbar.addView(new View(this),new LinearLayout.LayoutParams(0,1,1f));

        pinButton = icon(R.drawable.ic_editor_pin,"Pin or unpin note");
        toolbar.addView(pinButton,iconParams());
        pinButton.setOnClickListener(v -> {
            isPinned = !isPinned;
            updatePinVisual();
        });
        ImageButton share = icon(R.drawable.ic_editor_share,"Collaborators (open Kept)");
        toolbar.addView(share,iconParams());
        share.setOnClickListener(v -> {
            Toast.makeText(this,"Manage collaborators in Kept's full editor",Toast.LENGTH_SHORT).show();
            openKeptWeb();
        });
        ImageButton archive = icon(R.drawable.ic_editor_archive,"Archive note");
        toolbar.addView(archive,iconParams());
        archive.setOnClickListener(v -> confirmLifecycle("archived"));

        editorScroll = new ScrollView(this);
        editorScroll.setFillViewport(true);
        editorScroll.setClipToPadding(false);
        editorRoot.addView(editorScroll,new LinearLayout.LayoutParams(-1,0,1f));

        LinearLayout contents = new LinearLayout(this);
        contents.setOrientation(LinearLayout.VERTICAL);
        contents.setPadding(ScreenUi.dp(this,24),ScreenUi.dp(this,36),
            ScreenUi.dp(this,20),ScreenUi.dp(this,30));
        editorScroll.addView(contents,new ScrollView.LayoutParams(-1,-2));

        title = ScreenUi.field(this,"Title",
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        title.setTextSize(26);
        title.setHintTextColor(0xff818185);
        title.setTextColor(0xffededf0);
        title.setBackgroundColor(Color.TRANSPARENT);
        title.setPadding(0,ScreenUi.dp(this,6),0,ScreenUi.dp(this,10));
        title.setSingleLine(false);
        title.setMaxLines(3);
        contents.addView(title,ScreenUi.margins(this,0,0,0,12));

        status = ScreenUi.hint(this,noteId>0?"Loading note…":"");
        status.setTextColor(0xffbfc3bd);
        contents.addView(status,ScreenUi.margins(this,0,0,0,6));

        body = ScreenUi.field(this,"Note",
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE |
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        body.setSingleLine(false);
        body.setMinLines(12);
        body.setTextSize(17);
        body.setGravity(Gravity.TOP | Gravity.START);
        body.setBackgroundColor(Color.TRANSPARENT);
        body.setPadding(0,ScreenUi.dp(this,8),0,ScreenUi.dp(this,12));
        contents.addView(body,ScreenUi.margins(this,0,0,0,8));

        itemHost = new LinearLayout(this);
        itemHost.setOrientation(LinearLayout.VERTICAL);
        contents.addView(itemHost,ScreenUi.margins(this,0,0,0,0));

        addItem = ScreenUi.button(this,"+  List item",false);
        addItem.setBackgroundColor(Color.TRANSPARENT);
        addItem.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        addItem.setTextColor(0xffadb2af);
        addItem.setTextSize(16);
        addItem.setPadding(ScreenUi.dp(this,12),ScreenUi.dp(this,9),0,ScreenUi.dp(this,9));
        contents.addView(addItem,ScreenUi.margins(this,31,3,0,8));
        addItem.setOnClickListener(v -> insertRow(null,firstCompletedIndex(),true));

        completedHeader = ScreenUi.label(this,"▸ Completed items",15,false);
        completedHeader.setTextColor(0xffb9b9bd);
        completedHeader.setPadding(ScreenUi.dp(this,12),ScreenUi.dp(this,20),
            ScreenUi.dp(this,6),ScreenUi.dp(this,18));
        contents.addView(completedHeader,ScreenUi.margins(this,0,12,0,0));
        completedHeader.setOnClickListener(v -> {
            showCompleted = !showCompleted;
            refreshCompletedGroup();
        });
        completedHost = new LinearLayout(this);
        completedHost.setOrientation(LinearLayout.VERTICAL);
        contents.addView(completedHost);

        // The original saving / offline conflict flow remains in place.
        // The editor saves on Back; Save is also available from overflow.
        save = ScreenUi.button(this,"Save",true);
        labelsButton = ScreenUi.button(this,"Labels",false);
        itemHost.setOnDragListener((view,event) -> onChecklistDrag(event,false));
        completedHost.setOnDragListener((view,event) -> onChecklistDrag(event,true));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(ScreenUi.dp(this,14),ScreenUi.dp(this,5),
            ScreenUi.dp(this,14),ScreenUi.dp(this,5));
        bottom.setBackgroundColor(0xff3b3b40);
        editorRoot.addView(bottom,new LinearLayout.LayoutParams(-1,ScreenUi.dp(this,62)));

        ImageButton add = icon(R.drawable.ic_editor_add,"Add checklist item");
        bottom.addView(add,iconParams());
        add.setOnClickListener(v -> {
            if (isChecklist) insertRow(null,firstCompletedIndex(),true);
            else {
                body.requestFocus();
                android.view.inputmethod.InputMethodManager keyboard =
                    (android.view.inputmethod.InputMethodManager)
                        getSystemService(INPUT_METHOD_SERVICE);
                if (keyboard != null) keyboard.showSoftInput(body,
                    android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        });
        ImageButton palette = icon(R.drawable.ic_editor_palette,"Choose note color");
        bottom.addView(palette,iconParams());
        palette.setOnClickListener(v -> pickColor());
        ImageButton formatting = icon(R.drawable.ic_editor_format,"Text formatting in Kept");
        bottom.addView(formatting,iconParams());
        formatting.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setTitle("Advanced formatting")
            .setMessage("Rich-text formatting is available in the full Kept editor. Open it without altering this note?")
            .setNegativeButton("Stay here",null)
            .setPositiveButton("Open Kept",(dialog,which)->openKeptWeb()).show());
        bottom.addView(new View(this),new LinearLayout.LayoutParams(0,1,1f));
        ImageButton more = icon(R.drawable.ic_editor_more,"More note actions");
        bottom.addView(more,iconParams());
        more.setOnClickListener(this::showActionsMenu);

        if (noteId > 0) load();
        else {
            title.setText(getIntent().getStringExtra(EXTRA_INITIAL_TITLE));
            body.setText(getIntent().getStringExtra(EXTRA_INITIAL_BODY));
            updateType();
        }
    }

    private ImageButton icon(int drawable,String accessibility) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(drawable);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setColorFilter(0xffd5d5da);
        button.setPadding(ScreenUi.dp(this,9),ScreenUi.dp(this,9),
            ScreenUi.dp(this,9),ScreenUi.dp(this,9));
        button.setScaleType(ImageButton.ScaleType.FIT_CENTER);
        button.setContentDescription(accessibility);
        return button;
    }

    private LinearLayout.LayoutParams iconParams() {
        return new LinearLayout.LayoutParams(ScreenUi.dp(this,49),ScreenUi.dp(this,49));
    }

    private void updatePinVisual() {
        if (pinButton != null)
            pinButton.setColorFilter(isPinned?0xffd7efaa:0xffc7c7cb);
    }

    private void openKeptWeb() {
        startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(SecureStore.baseUrl(this))));
    }

    private void confirmLifecycle(String field) {
        if (noteId < 1 || original == null) {
            Toast.makeText(this,"Save the new note first",Toast.LENGTH_SHORT).show();
            return;
        }
        String action = "archived".equals(field) ? "Archive" : "Move to trash";
        new AlertDialog.Builder(this)
            .setTitle(action+" note?")
            .setMessage("Any unsaved edits in this screen will not be included. Save them first if needed.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton(action,(dialog,which)->changeLifecycle(field))
            .show();
    }

    private void showActionsMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this,anchor);
        menu.getMenu().add("Save note");
        menu.getMenu().add("Labels");
        menu.getMenu().add("Pin / unpin");
        if (noteId > 0) {
            menu.getMenu().add("Archive");
            menu.getMenu().add("Move to trash");
        }
        menu.getMenu().add("Open full Kept editor");
        menu.setOnMenuItemClickListener(item -> {
            String action=item.getTitle().toString();
            if ("Save note".equals(action)) submit();
            else if ("Labels".equals(action)) pickLabels();
            else if ("Pin / unpin".equals(action)) { isPinned=!isPinned; updatePinVisual(); }
            else if ("Archive".equals(action)) confirmLifecycle("archived");
            else if ("Move to trash".equals(action)) confirmLifecycle("trashed");
            else if ("Open full Kept editor".equals(action)) openKeptWeb();
            return true;
        });
        menu.show();
    }

    private void applyEditorColor() {
        int background = 0xff1c1c1f;
        if (selectedColor!=null
            && !"#FFFFFF".equalsIgnoreCase(selectedColor)) {
            background = WidgetAppearance.noteColor(selectedColor,true);
        }
        editorRoot.setBackgroundColor(background);
        getWindow().setStatusBarColor(background);
    }

    private void requestClose() {
        if (saving) return;
        if (noteId < 1 && title.getText().toString().trim().isEmpty()
            && body.getText().toString().trim().isEmpty()) {
            boolean content=false;
            for (ItemRow row:items) if (!row.text.getText().toString().trim().isEmpty()) {
                content=true;
                break;
            }
            if (!content) { finish(); return; }
        }
        submit();
    }

    @Override @Deprecated public void onBackPressed() {
        requestClose();
    }

    @Override protected void onDestroy() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && backCallback != null)
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        super.onDestroy();
    }

    private int firstCompletedIndex() {
        // Older Kept notes may interleave checked and unchecked JSON rows.
        // Insert after the last unchecked item rather than at the first checked
        // entry, so a new item appears at the end of the visible active group.
        int index=0;
        for(int i=0;i<items.size();i++)
            if(!items.get(i).checked.isChecked()) index=i+1;
        return index;
    }

    private void refreshCompletedGroup() {
        if (completedHost==null || completedHeader==null) return;
        int completed=0;
        for(ItemRow row:items) if(row.checked.isChecked()) completed++;
        completedHeader.setVisibility(isChecklist && completed>0?View.VISIBLE:View.GONE);
        completedHeader.setText((showCompleted?"▾ ":"▸ ")+
            "("+completed+") Completed items");
        completedHost.setVisibility(showCompleted?View.VISIBLE:View.GONE);
    }

    private void onCheckChanged(ItemRow row) {
        if (itemHost == null || completedHost == null) return;
        row.text.setPaintFlags(row.text.getPaintFlags() &
            ~Paint.STRIKE_THRU_TEXT_FLAG |
            (row.checked.isChecked()?Paint.STRIKE_THRU_TEXT_FLAG:0));
        row.text.setTextColor(row.checked.isChecked()?0xff99999e:0xffe7e7eb);
        if (!items.contains(row)) return;
        items.remove(row);
        int insert= row.checked.isChecked()?items.size():firstCompletedIndex();
        items.add(insert,row);
        android.view.ViewGroup previous = (android.view.ViewGroup)row.line.getParent();
        if(previous!=null)previous.removeView(row.line);
        LinearLayout host=row.checked.isChecked()?completedHost:itemHost;
        host.addView(row.line,new LinearLayout.LayoutParams(-1,ScreenUi.dp(this,57)));
        refreshCompletedGroup();
    }

    private boolean onChecklistDrag(DragEvent event,boolean completed) {
        if(event.getAction()==DragEvent.ACTION_DRAG_STARTED)
            return event.getLocalState() instanceof ItemRow;
        if(event.getAction()==DragEvent.ACTION_DROP &&
            event.getLocalState() instanceof ItemRow)
            return dropChecklistItem((ItemRow)event.getLocalState(),completed,
                event.getY());
        return true;
    }

    /** Only reorder within a completion group; never change a checkbox by dragging. */
    private boolean dropChecklistItem(ItemRow dragged,boolean completed,float y) {
        if(dragged==null || dragged.checked.isChecked()!=completed)return false;
        LinearLayout host=completed?completedHost:itemHost;
        int newIndex=0;
        for(int i=0;i<host.getChildCount();i++) {
            View child=host.getChildAt(i);
            if(child==dragged.line)continue;
            if(y>child.getTop()+child.getHeight()/2f)newIndex++;
        }
        items.remove(dragged);
        int unfinished=0;
        for(ItemRow item:items)if(!item.checked.isChecked())unfinished++;
        int within=completed?items.size()-unfinished:unfinished;
        int insert=(completed?unfinished:0)+Math.min(within,newIndex);
        items.add(insert,dragged);
        host.removeView(dragged.line);
        host.addView(dragged.line,Math.min(newIndex,host.getChildCount()));
        return true;
    }

    private void updateType() {
        body.setVisibility(isChecklist ? View.GONE : View.VISIBLE);
        itemHost.setVisibility(isChecklist ? View.VISIBLE : View.GONE);
        addItem.setVisibility(isChecklist ? View.VISIBLE : View.GONE);
        if(isChecklist && items.isEmpty()) insertRow(null,0,false);
        refreshCompletedGroup();
    }

    private void load() {
        long id=noteId;
        Executors.newSingleThreadExecutor().execute(()->{
            try {
                JSONObject note=KeptApi.note(this,id);
                SecureStore.saveSingleNote(this,id,note.toString());
                runOnUiThread(()->displayNote(note,false));
            } catch(Exception ex) {
                // Only full-note snapshots are safe for offline edits. Card previews
                // truncate checklists and must never be saved as whole notes.
                String raw=SecureStore.singleNote(this,id);
                if(!raw.isBlank()) {
                    try {
                        JSONObject cached=new JSONObject(raw);
                        JSONArray overlay=PendingSync.overlay(this,new JSONArray().put(cached));
                        JSONObject note=overlay.optJSONObject(0);
                        runOnUiThread(()->displayNote(note,true));
                        return;
                    } catch(Exception ignored){}
                }
                runOnUiThread(()->{
                    status.setText("No full offline copy. Open this note once while online to enable offline editing.");
                    save.setEnabled(false);
                });
            }
        });
    }

    private void displayNote(JSONObject note,boolean offline) {
        original=note;
        isChecklist=note.optBoolean("isCbox");
        title.setText(KeptApi.plain(note.optString("noteTitle")));
        isPinned = note.optBoolean("pinned");
        updatePinVisual();
        chosenLabels=note.optJSONArray("labels");
        if(chosenLabels==null)chosenLabels=new JSONArray();
        String color=note.optString("bgColor","#FFFFFF");
        selectedColor=color.isEmpty()?"#FFFFFF":color;
        body.setText(KeptApi.formatted(note.optString("noteBody")));
        if(note.optBoolean("locked")&&!note.optBoolean("lockedContentAvailable",false)){
            status.setText("Locked notes must be edited in Kept.");
            save.setEnabled(false);
            return;
        }
        items.clear();
        itemHost.removeAllViews();
        JSONArray checks=note.optJSONArray("checkBoxes");
        if(checks!=null)for(int i=0;i<checks.length();i++){
            JSONObject entry=checks.optJSONObject(i);
            if(entry!=null)addRow(entry);
        }
        status.setText(offline?"Offline copy • changes will sync when online":"");
        labelsButton.setText("Labels ("+chosenLabels.length()+")");
        applyEditorColor();
        updateType();
    }

    /** Restores an existing checklist item in its original order, without taking focus. */
    private void addRow(JSONObject existing) {
        insertRow(existing, items.size(), false);
    }

    /**
     * Enter is a structural separator in a checklist, not a newline character.
     * Split at the cursor so typing in the middle of an item behaves like Keep.
     */
    static final class TextSplit {
        final String first;
        final String second;
        TextSplit(String first, String second) {
            this.first = first;
            this.second = second;
        }
    }

    static TextSplit splitChecklistText(String value, int start, int end) {
        String safe = value == null ? "" : value;
        int left = Math.max(0, Math.min(start, safe.length()));
        int right = Math.max(left, Math.min(end, safe.length()));
        return new TextSplit(safe.substring(0, left), safe.substring(right));
    }

    private void insertNextRow(ItemRow current) {
        int index = items.indexOf(current);
        if (index < 0) return;
        EditText edit = current.text;
        TextSplit parts = splitChecklistText(edit.getText().toString(),
            edit.getSelectionStart(), edit.getSelectionEnd());
        // Text before the caret stays with the existing item; the rest moves to the new item.
        edit.setText(parts.first);
        ItemRow next = insertRow(null, index + 1, true);
        next.text.setText(parts.second);
        next.text.setSelection(0);
    }

    private ItemRow insertRow(JSONObject existing,int index,boolean focus) {
        LinearLayout line=new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setMinimumHeight(ScreenUi.dp(this,57));

        ImageButton grip=icon(R.drawable.ic_editor_drag,"Long press and drag to reorder");
        grip.setColorFilter(0xffbcbcc1);
        line.addView(grip,new LinearLayout.LayoutParams(ScreenUi.dp(this,35),
            ScreenUi.dp(this,48)));

        CheckBox checked=new CheckBox(this);
        checked.setChecked(existing!=null && existing.optBoolean("done"));
        checked.setButtonTintList(new ColorStateList(
            new int[][] {new int[]{android.R.attr.state_checked},new int[]{}},
            new int[]{0xffd6e3ba,0xffc6c6cb}));
        checked.setPadding(0,0,0,0);
        line.addView(checked,new LinearLayout.LayoutParams(ScreenUi.dp(this,44),
            ScreenUi.dp(this,46)));

        EditText edit=ScreenUi.field(this,"List item",InputType.TYPE_CLASS_TEXT |
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        edit.setSingleLine(true);
        edit.setMaxLines(1);
        edit.setTextSize(16);
        edit.setImeOptions(EditorInfo.IME_ACTION_NEXT |
            EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        edit.setTextColor(0xffe7e7eb);
        edit.setHintTextColor(0xff8d8d92);
        edit.setBackgroundColor(Color.TRANSPARENT);
        edit.setPadding(ScreenUi.dp(this,2),ScreenUi.dp(this,11),
            ScreenUi.dp(this,3),ScreenUi.dp(this,11));
        edit.setText(existing==null?"":KeptApi.plain(existing.optString("data")));
        line.addView(edit,new LinearLayout.LayoutParams(0,
            ScreenUi.dp(this,52),1f));

        TextView remove=ScreenUi.label(this,"×",24,false);
        remove.setGravity(Gravity.CENTER);
        remove.setTextColor(0xffc5c5ca);
        remove.setContentDescription("Remove checklist item");
        remove.setVisibility(View.GONE);
        line.addView(remove,new LinearLayout.LayoutParams(ScreenUi.dp(this,38),
            ScreenUi.dp(this,52)));

        long id=existing==null?ITEM_IDS.incrementAndGet():
            existing.optLong("id",ITEM_IDS.incrementAndGet());
        ItemRow row=new ItemRow(checked,edit,line,id,existing);

        // Samsung IME sends NEXT; a Bluetooth keyboard can send physical Enter.
        edit.setOnKeyListener((v,keyCode,event)-> {
            if(keyCode!=KeyEvent.KEYCODE_ENTER || event.isShiftPressed())return false;
            if(event.getAction()==KeyEvent.ACTION_DOWN &&
                event.getRepeatCount()==0)insertNextRow(row);
            return true;
        });
        edit.setOnEditorActionListener((v,actionId,event)-> {
            if(event!=null){
                if(event.getKeyCode()!=KeyEvent.KEYCODE_ENTER ||
                    event.isShiftPressed())return false;
                if(event.getAction()==KeyEvent.ACTION_DOWN &&
                    event.getRepeatCount()==0)insertNextRow(row);
                return true;
            }
            if(actionId==EditorInfo.IME_ACTION_NEXT ||
               actionId==EditorInfo.IME_ACTION_DONE ||
               actionId==EditorInfo.IME_ACTION_GO ||
               actionId==EditorInfo.IME_ACTION_UNSPECIFIED ||
               actionId==EditorInfo.IME_NULL){
                insertNextRow(row);
                return true;
            }
            return false;
        });
        edit.setOnFocusChangeListener((v,hasFocus)->remove.setVisibility(
            hasFocus?View.VISIBLE:View.GONE));

        int target=Math.max(0,Math.min(index,items.size()));
        int groupIndex=0;
        for(int j=0;j<target;j++){
            if(items.get(j).checked.isChecked()==checked.isChecked())groupIndex++;
        }
        items.add(target,row);
        LinearLayout host=checked.isChecked()?completedHost:itemHost;
        host.addView(line,Math.min(groupIndex,host.getChildCount()),
            new LinearLayout.LayoutParams(-1,ScreenUi.dp(this,57)));

        checked.setOnCheckedChangeListener((button,done)->onCheckChanged(row));
        if(checked.isChecked()){
            edit.setPaintFlags(edit.getPaintFlags()|Paint.STRIKE_THRU_TEXT_FLAG);
            edit.setTextColor(0xff99999e);
        }
        remove.setOnClickListener(v-> {
            items.remove(row);
            LinearLayout parent=(LinearLayout)line.getParent();
            if(parent!=null)parent.removeView(line);
            refreshCompletedGroup();
        });

        grip.setOnLongClickListener(v->{
            ClipData clip=ClipData.newPlainText("Kept checklist row","");
            line.startDragAndDrop(clip,new View.DragShadowBuilder(line),row,0);
            return true;
        });
        line.setOnDragListener((view,event)->{
            if(event.getAction()==DragEvent.ACTION_DRAG_STARTED)
                return event.getLocalState() instanceof ItemRow;
            if(event.getAction()==DragEvent.ACTION_DROP &&
                event.getLocalState() instanceof ItemRow) {
                return dropChecklistItem((ItemRow)event.getLocalState(),
                    checked.isChecked(),event.getY()+line.getTop());
            }
            return true;
        });

        refreshCompletedGroup();
        if(focus){
            edit.requestFocus();
            edit.post(()->{
                edit.requestFocus();
                android.view.inputmethod.InputMethodManager keyboard=
                    (android.view.inputmethod.InputMethodManager)
                        getSystemService(INPUT_METHOD_SERVICE);
                if(keyboard!=null)keyboard.showSoftInput(edit,
                    android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            });
        }
        return row;
    }

    /** Keep existing empty list entries; drop only never-edited new placeholders. */
    static boolean shouldPersistRow(String text, boolean existedOnServer) {
        return existedOnServer || (text != null && !text.trim().isEmpty());
    }

    private JSONArray currentItems() throws Exception {
        JSONArray result = new JSONArray();
        for (ItemRow row : items) {
            String text = row.text.getText().toString().trim();
            // Existing blank checklist entries are valid Kept data: never delete
            // them simply because the user opened the note and saved it.
            // Newly-created empty placeholders are not written until edited.
            if (!shouldPersistRow(text, row.original != null)) continue;
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
                    payload.put("pinned", isPinned);
                    if (colorTouched) payload.put("bgColor",selectedColor);
                    payload.put("syncId","keptwidget-"+java.util.UUID.randomUUID());
                    try {
                        KeptApi.add(this,payload);
                    } catch(java.io.IOException network) {
                        if(network instanceof KeptApi.ApiException)throw network;
                        PendingSync.create(this,payload);
                        runOnUiThread(()->{
                            Toast.makeText(this,"Saved offline • will sync when online",Toast.LENGTH_LONG).show();
                            finish();
                        });
                        return;
                    }
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
                    if (isPinned != before.optBoolean("pinned"))
                        payload.put("pinned",isPinned);
                    if (colorTouched) payload.put("bgColor",selectedColor);
                    if (labelsTouched) payload.put("labels",chosenLabels);
                    if (payload.length() != 0) {
                        try {
                            KeptApi.patchIfUnchanged(this,id,payload,before);
                        }catch(java.io.IOException network){
                            if(network instanceof KeptApi.ApiException)throw network;
                            PendingSync.edit(this,id,payload,before);
                            runOnUiThread(()->{
                                Toast.makeText(this,"Edit queued offline • sync resumes automatically",Toast.LENGTH_LONG).show();
                                finish();
                            });
                            return;
                        }
                    }
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
                applyEditorColor();
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
                try {
                    KeptApi.patchIfUnchanged(this,noteId,change,original);
                } catch(java.io.IOException network) {
                    if(network instanceof KeptApi.ApiException)throw network;
                    PendingSync.edit(this,noteId,change,original);
                }
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
