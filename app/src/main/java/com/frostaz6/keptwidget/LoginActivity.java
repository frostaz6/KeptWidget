package com.frostaz6.keptwidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URI;
import java.util.concurrent.Executors;

public class LoginActivity extends Activity {
    private EditText server, username, password, totp;
    private TextView status;
    private Button signIn;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = ScreenUi.page(this, "Kept Widget");
        root.addView(ScreenUi.hint(this,
            "A Google Keep-style home-screen widget for your self-hosted Kept notes. Your credentials stay on this device."),
            ScreenUi.margins(this,0,0,0,18));
        status = ScreenUi.label(this, "", 15, true);
        root.addView(status, ScreenUi.margins(this,0,0,0,12));

        server = ScreenUi.field(this, "Kept server URL", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        server.setText(SecureStore.baseUrl(this));
        root.addView(server, ScreenUi.margins(this,0,0,0,12));
        username = ScreenUi.field(this, "Username", InputType.TYPE_CLASS_TEXT);
        root.addView(username, ScreenUi.margins(this,0,0,0,12));
        password = ScreenUi.field(this, "Password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(password, ScreenUi.margins(this,0,0,0,12));
        totp = ScreenUi.field(this, "2FA code (if enabled)", InputType.TYPE_CLASS_NUMBER);
        root.addView(totp, ScreenUi.margins(this,0,0,0,16));

        signIn = ScreenUi.button(this, "Sign in", true);
        root.addView(signIn, ScreenUi.margins(this,0,0,0,12));
        signIn.setOnClickListener(v -> doLogin());

        Button collection = ScreenUi.button(this, "Add note collection widget", false);
        root.addView(collection, ScreenUi.margins(this,0,8,0,10));
        collection.setOnClickListener(v -> pin(CollectionWidgetProvider.class));
        Button grid = ScreenUi.button(this, "Add two-column grid widget", false);
        root.addView(grid, ScreenUi.margins(this,0,0,0,10));
        grid.setOnClickListener(v -> pin(GridCollectionWidgetProvider.class));
        Button capture = ScreenUi.button(this, "Add quick capture widget", false);
        root.addView(capture, ScreenUi.margins(this,0,0,0,10));
        capture.setOnClickListener(v -> pin(CaptureWidgetProvider.class));
        Button single = ScreenUi.button(this, "Add single-note widget", false);
        root.addView(single, ScreenUi.margins(this,0,0,0,10));
        single.setOnClickListener(v -> pin(SingleNoteWidgetProvider.class));
        Button home = ScreenUi.button(this, "Open notes and search", false);
        root.addView(home, ScreenUi.margins(this,0,0,0,10));
        home.setOnClickListener(v -> startActivity(new Intent(this, NotesActivity.class)));
        Button browser = ScreenUi.button(this, "Open full Kept editor in browser", false);
        root.addView(browser, ScreenUi.margins(this,0,0,0,10));
        browser.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(SecureStore.baseUrl(this)));
            startActivity(intent);
        });
        Button signOut = ScreenUi.button(this, "Sign out and clear saved notes", false);
        root.addView(signOut, ScreenUi.margins(this,0,0,0,16));
        signOut.setOnClickListener(v -> {
            Runnable clear=()->{
                SecureStore.clear(this);
                status.setText("Signed out");
                password.setText("");
                CollectionWidgetProvider.refreshAll(this);
            };
            int pending=PendingSync.count(this);
            if(pending>0){
                new android.app.AlertDialog.Builder(this)
                    .setTitle("Unsynced edits")
                    .setMessage(pending+" offline change(s) will be permanently discarded if you sign out. Continue?")
                    .setNegativeButton("Cancel",null)
                    .setPositiveButton("Discard and sign out",(dialog,which)->clear.run()).show();
            } else clear.run();
        });
        root.addView(ScreenUi.hint(this,
            "Login uses Kept's native /api/auth/login endpoint. Supports password and TOTP accounts; OIDC-only accounts are not supported yet."),
            ScreenUi.margins(this,0,0,0,12));
        updateStatus();
    }

    @Override protected void onResume() {
        super.onResume();
        if (status != null) updateStatus();
    }

    private void updateStatus() {
        status.setText(SecureStore.token(this).isEmpty()
            ? "Not signed in" : "Connected to " + SecureStore.baseUrl(this));
    }

    private void doLogin() {
        String url = server.getText().toString().trim().replaceAll("/+$", "");
        String user = username.getText().toString().trim();
        String pass = password.getText().toString();
        String code = totp.getText().toString().trim();
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null || (uri.getPath() != null && !uri.getPath().isEmpty()))
                throw new IllegalArgumentException();
        } catch (Exception e) {
            status.setText("Enter a valid HTTPS server address, without a path.");
            return;
        }
        if (user.isEmpty() || pass.isEmpty()) {
            status.setText("Enter your Kept username and password.");
            return;
        }
        if(PendingSync.count(this)>0 && !url.equals(SecureStore.baseUrl(this))) {
            status.setText("There are unsynced edits on the current server. Sync or discard them before switching servers.");
            return;
        }
        String previousAccount=SecureStore.accountName(this);
        if(PendingSync.count(this)>0 && !previousAccount.isEmpty()
            && !previousAccount.equalsIgnoreCase(user)) {
            status.setText("Offline edits belong to "+previousAccount+
                ". Sync or review them before signing into another account.");
            return;
        }
        signIn.setEnabled(false);
        status.setText("Connecting…");
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                SecureStore.changeBaseUrl(this, url);
                String token = KeptApi.login(this, user, pass, code);
                if(!previousAccount.isEmpty() && !previousAccount.equalsIgnoreCase(user))
                    SecureStore.clear(this);
                SecureStore.saveToken(this, token);
                SecureStore.saveAccountName(this,user);
                CollectionWidgetProvider.refreshAll(this);
                runOnUiThread(() -> {
                    password.setText("");
                    totp.setText("");
                    signIn.setEnabled(true);
                    updateStatus();
                    Toast.makeText(this, "Connected to Kept", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, NotesActivity.class));
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    signIn.setEnabled(true);
                    status.setText("Login failed: " + e.getMessage());
                });
            }
        });
    }

    private void pin(Class<?> provider) {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        if (manager.isRequestPinAppWidgetSupported()) {
            manager.requestPinAppWidget(new ComponentName(this, provider), null, null);
        } else {
            Toast.makeText(this, "Long-press your home screen and select Widgets → Kept Widget.", Toast.LENGTH_LONG).show();
        }
    }
}
