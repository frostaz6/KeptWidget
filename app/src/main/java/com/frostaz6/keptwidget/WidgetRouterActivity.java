package com.frostaz6.keptwidget;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import java.util.concurrent.Executors;

/**
 * Invisible activity used as a home-screen click target. Starting an activity
 * directly from the launcher avoids background-activity-launch restrictions on
 * newer Android versions. A checkbox action updates and immediately finishes.
 */
public class WidgetRouterActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Intent click = getIntent();
        if (click == null) { finish(); return; }
        if (SecureStore.token(this).isEmpty()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        long id = click.getLongExtra(NoteEditorActivity.EXTRA_ID, -1L);
        if (id < 1) { finish(); return; }
        if ("toggle".equals(click.getStringExtra(WidgetActionReceiver.EXTRA_ACTION))) {
            long itemId = click.getLongExtra(WidgetActionReceiver.EXTRA_ITEM, -1L);
            if (itemId < 0) { finish(); return; }
            Executors.newSingleThreadExecutor().execute(() -> {
                String problem = null;
                try {
                    KeptApi.toggleCheckbox(this, id, itemId);
                } catch (java.io.IOException e) {
                    if(!(e instanceof KeptApi.ApiException)) {
                        try {
                            if(PendingSync.checkbox(this,id,itemId)) {
                                problem="Saved offline • will sync automatically";
                            } else problem="Can't queue offline: note wasn't cached. Open it once online.";
                        }catch(Exception queued){problem=queued.getMessage();}
                    }else problem=e.getMessage();
                } catch (Exception e) {
                    problem = e.getMessage();
                }
                CollectionWidgetProvider.refreshAll(this);
                final String message = problem;
                runOnUiThread(() -> {
                    if (message != null) Toast.makeText(this, "Checklist update failed: " + message,
                        Toast.LENGTH_LONG).show();
                    finish();
                });
            });
        } else {
            Intent open = new Intent(this, NoteEditorActivity.class);
            open.putExtra(NoteEditorActivity.EXTRA_ID, id);
            startActivity(open);
            finish();
        }
    }
}
