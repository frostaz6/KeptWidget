package com.frostaz6.keptwidget;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/** Accept a text share into the note composer; never save without user review. */
public final class ShareReceiverActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Intent source=getIntent();
        if (source==null || !Intent.ACTION_SEND.equals(source.getAction())) {
            finish();
            return;
        }
        if (SecureStore.token(this).isEmpty()) {
            Toast.makeText(this,"Open Kept Widget and sign in before sharing notes.",Toast.LENGTH_LONG).show();
            startActivity(new Intent(this,LoginActivity.class));
            finish();
            return;
        }
        CharSequence content=source.getCharSequenceExtra(Intent.EXTRA_TEXT);
        String subject=source.getStringExtra(Intent.EXTRA_SUBJECT);
        if (content==null || content.length()==0) {
            Toast.makeText(this,"No text to save.",Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        Intent edit=new Intent(this,NoteEditorActivity.class);
        edit.putExtra(NoteEditorActivity.EXTRA_INITIAL_TITLE,subject==null?"":subject);
        edit.putExtra(NoteEditorActivity.EXTRA_INITIAL_BODY,content.toString());
        startActivity(edit);
        finish();
    }
}
