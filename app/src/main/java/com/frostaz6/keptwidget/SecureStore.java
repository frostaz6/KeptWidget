package com.frostaz6.keptwidget;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Keeps tokens and offline widget note previews encrypted with Android Keystore. */
final class SecureStore {
    private static final String ALIAS = "KeptWidget.AES.v1";
    private static final String PRIVATE_PREFS = "kept_secure";
    private static final String UI_PREFS = "kept_preferences";
    private static final String DEFAULT_ORIGIN = "https://kept.mcnannay.com";

    private SecureStore() {}

    static SharedPreferences preferences(Context c) {
        return c.getApplicationContext().getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE);
    }

    static String baseUrl(Context c) {
        return preferences(c).getString("origin", DEFAULT_ORIGIN);
    }

    static synchronized void changeBaseUrl(Context c, String url) {
        String normalized = url.trim().replaceAll("/+$", "");
        if (!normalized.equals(baseUrl(c))) {
            clear(c);
            preferences(c).edit().putString("origin", normalized).apply();
        }
    }

    static String token(Context c) { return read(c, "token"); }
    static void saveToken(Context c, String value) { write(c, "token", value); }
    static String cachedNotes(Context c) { return read(c, "notes"); }
    static void saveNotes(Context c, String value) { write(c, "notes", value); }
    static String readPending(Context c) { return read(c,"pending"); }
    static void savePending(Context c,String data) { write(c,"pending",data); }
    static String singleNote(Context c, long id) { return read(c, "single_" + id); }
    static void saveSingleNote(Context c, long id, String value) { write(c, "single_" + id, value); }

    static void clear(Context c) {
        c.getApplicationContext().getSharedPreferences(PRIVATE_PREFS, Context.MODE_PRIVATE)
            .edit().clear().commit();
    }

    private static synchronized SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        SecretKey stored = (SecretKey) ks.getKey(ALIAS, null);
        if (stored != null) return stored;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build());
        return generator.generateKey();
    }

    private static synchronized void write(Context c, String name, String value) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = cipher.getIV();
            byte[] sealed = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(1 + iv.length + sealed.length);
            buffer.put((byte) iv.length).put(iv).put(sealed);
            String encoded = Base64.encodeToString(buffer.array(), Base64.NO_WRAP);
            c.getApplicationContext().getSharedPreferences(PRIVATE_PREFS, Context.MODE_PRIVATE)
                .edit().putString(name, encoded).apply();
        } catch (Exception e) {
            throw new IllegalStateException("Secure storage is unavailable on this device.", e);
        }
    }

    private static synchronized String read(Context c, String name) {
        String value = c.getApplicationContext().getSharedPreferences(PRIVATE_PREFS, Context.MODE_PRIVATE)
            .getString(name, null);
        if (value == null) return "";
        try {
            byte[] data = Base64.decode(value, Base64.NO_WRAP);
            ByteBuffer buffer = ByteBuffer.wrap(data);
            int ivLen = buffer.get() & 0xff;
            if (ivLen != 12 || buffer.remaining() < ivLen + 16) return "";
            byte[] iv = new byte[ivLen];
            buffer.get(iv);
            byte[] sealed = new byte[buffer.remaining()];
            buffer.get(sealed);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(sealed), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
