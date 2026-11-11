package com.frostaz6.keptwidget;

import android.content.Context;
import android.text.Html;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class KeptApi {
    private KeptApi() {}
    private static final Object WRITE_LOCK = new Object();

    static final class ApiException extends IOException {
        final int status;
        ApiException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    static String login(Context context, String username, String password, String code)
            throws IOException, JSONException {
        JSONObject input = new JSONObject();
        input.put("username", username);
        input.put("password", password);
        if (code != null && !code.trim().isEmpty()) input.put("totpToken", code.trim());
        JSONObject result = (JSONObject) call(context, "POST", "/api/auth/login", input, "");
        String token = result.optString("token");
        if (token.isEmpty()) throw new IOException("Kept did not return a login token.");
        return token;
    }

    static JSONArray notes(Context c) throws IOException, JSONException {
        String token = requireToken(c);
        JSONArray all = new JSONArray();
        String cursor = "";
        for (int page = 0; page < 4; page++) {
            String path = "/api/notes?view=card&limit=80";
            if (!cursor.isEmpty()) path += "&cursor=" + URLEncoder.encode(cursor, "UTF-8");
            Object response = call(c, "GET", path, null, token);
            JSONArray found;
            if (response instanceof JSONObject) {
                JSONObject o = (JSONObject) response;
                found = o.optJSONArray("notes");
                cursor = o.optString("nextCursor", "");
                if ("null".equals(cursor)) cursor = "";
            } else {
                found = (JSONArray) response;
                cursor = "";
            }
            if (found == null) break;
            for (int i = 0; i < found.length(); i++) {
                JSONObject n = found.optJSONObject(i);
                if (n != null && !n.optBoolean("trashed") && !n.optBoolean("archived")) all.put(n);
            }
            if (cursor.isEmpty() || found.length() == 0) break;
        }
        return all;
    }

    static JSONArray labels(Context c) throws IOException, JSONException {
        Object value = call(c, "GET", "/api/labels", null, requireToken(c));
        return value instanceof JSONArray ? (JSONArray) value : new JSONArray();
    }

    static JSONObject note(Context c, long id) throws IOException, JSONException {
        return (JSONObject) call(c, "GET", "/api/notes/" + id, null, requireToken(c));
    }

    static JSONObject add(Context c, JSONObject note) throws IOException, JSONException {
        return (JSONObject) call(c, "POST", "/api/notes", note, requireToken(c));
    }

    static void patch(Context c, long id, JSONObject fields) throws IOException, JSONException {
        call(c, "PATCH", "/api/notes/" + id, fields, requireToken(c));
    }

    /** Always reload the whole note: card previews intentionally truncate checklists. */
    static boolean toggleCheckbox(Context c, long noteId, long itemId) throws IOException, JSONException {
        synchronized (WRITE_LOCK) {
            JSONObject original = note(c, noteId);
            if (original.optBoolean("locked") && !original.optBoolean("lockedContentAvailable", false))
                throw new IOException("Locked notes must be unlocked in Kept.");
            JSONArray boxes = original.optJSONArray("checkBoxes");
            if (boxes == null) throw new IOException("This note has no checklist.");
            boolean found = false;
            for (int i = 0; i < boxes.length(); i++) {
                JSONObject item = boxes.optJSONObject(i);
                if (item != null && item.optLong("id", -1) == itemId) {
                    item.put("done", !item.optBoolean("done"));
                    found = true;
                    break;
                }
            }
            if (!found) throw new IOException("Checklist item changed; refresh the widget.");
            JSONObject changes = new JSONObject();
            changes.put("checkBoxes", boxes);
            patch(c, noteId, changes);
            return true;
        }
    }

    static String plain(String html) {
        if (html == null || html.isEmpty()) return "";
        return Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().trim();
    }

    static String toHtml(String text) {
        String value = text == null ? "" : text;
        return value.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;").replace("\n", "<br>");
    }

    static String requireToken(Context c) throws IOException {
        String token = SecureStore.token(c);
        if (token.isEmpty()) throw new IOException("Sign in to Kept Widget.");
        return token;
    }

    private static Object call(Context c, String method, String path, JSONObject body, String token)
            throws IOException, JSONException {
        String origin = SecureStore.baseUrl(c);
        URL base = new URL(origin);
        if (!"https".equalsIgnoreCase(base.getProtocol()) || base.getUserInfo() != null
                || base.getQuery() != null || base.getRef() != null || !"/".equals(base.getPath().isEmpty() ? "/" : base.getPath()))
            throw new IOException("Use a secure HTTPS server origin (without a path).");
        HttpURLConnection connection = (HttpURLConnection) new URL(origin + path).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        if (!token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
        try {
            if (body != null) {
                byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(data.length);
                try (OutputStream out = connection.getOutputStream()) { out.write(data); }
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String raw = "";
            if (stream != null) {
                try (InputStream input = stream) {
                    byte[] bytes = input.readNBytes(2_000_000);
                    raw = new String(bytes, StandardCharsets.UTF_8);
                }
            }
            if (status < 200 || status >= 300) {
                if (status == 401) throw new ApiException(status, "Session expired. Sign in again.");
                if (status >= 300 && status < 400) throw new ApiException(status, "Unexpected redirect. Check the Kept URL.");
                String message = "Kept returned HTTP " + status;
                try {
                    JSONObject error = new JSONObject(raw);
                    message = error.optString("error_description", error.optString("message", message));
                    if (message.length() > 200) message = message.substring(0, 200);
                } catch (JSONException ignored) { }
                throw new ApiException(status, message);
            }
            if (raw.isBlank()) return new JSONObject();
            Object parsed = new JSONTokener(raw).nextValue();
            if (!(parsed instanceof JSONObject) && !(parsed instanceof JSONArray))
                throw new IOException("Unexpected response from Kept.");
            return parsed;
        } finally {
            connection.disconnect();
        }
    }
}
