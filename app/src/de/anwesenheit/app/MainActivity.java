package de.anwesenheit.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.security.KeyStore;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

public class MainActivity extends Activity {
    private static final int REQ_SAVE = 41;
    private static final int REQ_OPEN = 42;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private WebView web;
    private Vibrator vibrator;
    private String pendingSaveContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        web = new WebView(this);
        web.setBackgroundColor(isDark() ? Color.rgb(15, 19, 27) : Color.rgb(244, 246, 251));
        web.setHapticFeedbackEnabled(true);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setTextZoom(100);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                applyTheme();
            }
        });
        web.addJavascriptInterface(new Bridge(), "Android");
        setContentView(web);
        applyBars();
        web.loadUrl("file:///android_asset/index.html");
    }

    private boolean isDark() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private void applyBars() {
        Window w = getWindow();
        boolean dark = isDark();
        int bar = dark ? Color.rgb(26, 32, 43) : Color.WHITE;
        w.setStatusBarColor(bar);
        w.setNavigationBarColor(bar);
        View decor = w.getDecorView();
        int flags = 0;
        if (!dark) {
            if (Build.VERSION.SDK_INT >= 23) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decor.setSystemUiVisibility(flags);
    }

    private void applyTheme() {
        js("window.setTheme&&window.setTheme(" + isDark() + ")");
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyBars();
        applyTheme();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) js("window.onAppResume&&window.onAppResume()");
    }

    private void js(final String code) {
        web.post(new Runnable() {
            public void run() {
                web.evaluateJavascript(code, null);
            }
        });
    }

    private static String jsStr(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case ' ': b.append("\\u2028"); break;
                case ' ': b.append("\\u2029"); break;
                default: b.append(c);
            }
        }
        return b.append('"').toString();
    }

    private void notifyJs(String msg, boolean ok) {
        js("window.onNativeResult&&window.onNativeResult(" + jsStr(msg) + "," + ok + ")");
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.onBackPressed?window.onBackPressed():false", new ValueCallback<String>() {
            public void onReceiveValue(String handled) {
                if (!"true".equals(handled)) MainActivity.super.onBackPressed();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_SAVE && pendingSaveContent != null) {
            try {
                OutputStream os = getContentResolver().openOutputStream(uri, "wt");
                os.write(pendingSaveContent.getBytes(UTF8));
                os.close();
                notifyJs("Datei gespeichert", true);
            } catch (Exception e) {
                notifyJs("Speichern fehlgeschlagen", false);
            }
            pendingSaveContent = null;
        } else if (requestCode == REQ_OPEN) {
            try {
                InputStream is = getContentResolver().openInputStream(uri);
                String text = new String(readAll(is), UTF8);
                is.close();
                js("window.onFileOpened&&window.onFileOpened(" + jsStr(text) + ")");
            } catch (Exception e) {
                notifyJs("Datei konnte nicht gelesen werden", false);
            }
        }
    }

    private static byte[] readAll(InputStream is) throws java.io.IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    /* ---------- Encryption at rest ----------
     * The data file is encrypted with AES-256-GCM. The key is created once in the Android Keystore
     * (hardware-backed where available) and can never be exported, so a copied file is unreadable.
     * File format: "GCK1" | iv length (1 byte) | iv | ciphertext+tag
     */
    private static final byte[] MAGIC = {'G', 'C', 'K', '1'};
    private static final String KEY_ALIAS = "grupp_checkan_data_v1";

    private static boolean isEncrypted(byte[] raw) {
        return raw.length > 5 && Arrays.equals(Arrays.copyOf(raw, 4), MAGIC);
    }

    private SecretKey dataKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) return (SecretKey) ks.getKey(KEY_ALIAS, null);
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return kg.generateKey();
    }

    private byte[] encrypt(byte[] plain) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, dataKey());
        byte[] iv = c.getIV(), ct = c.doFinal(plain);
        byte[] out = new byte[5 + iv.length + ct.length];
        System.arraycopy(MAGIC, 0, out, 0, 4);
        out[4] = (byte) iv.length;
        System.arraycopy(iv, 0, out, 5, iv.length);
        System.arraycopy(ct, 0, out, 5 + iv.length, ct.length);
        return out;
    }

    private byte[] decrypt(byte[] raw) throws Exception {
        int ivLen = raw[4] & 0xff;
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, dataKey(), new GCMParameterSpec(128, Arrays.copyOfRange(raw, 5, 5 + ivLen)));
        return c.doFinal(raw, 5 + ivLen, raw.length - 5 - ivLen);
    }

    /** Atomically writes the data file, encrypted. Falls back to plain text only if the keystore is broken, so no data is lost. */
    private void writeData(String json) {
        byte[] bytes = json.getBytes(UTF8);
        try {
            bytes = encrypt(bytes);
        } catch (Exception ignored) {
        }
        try {
            File tmp = new File(getFilesDir(), "anwesenheit.json.tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            out.write(bytes);
            out.getFD().sync();
            out.close();
            if (!tmp.renameTo(dataFile())) {
                dataFile().delete();
                tmp.renameTo(dataFile());
            }
        } catch (Exception ignored) {
        }
    }

    private File dataFile() {
        return new File(getFilesDir(), "anwesenheit.json");
    }

    /** Methods callable from JavaScript as window.Android.* */
    public class Bridge {
        @JavascriptInterface
        public String loadData() {
            synchronized (MainActivity.this) {
                File f = dataFile();
                if (!f.exists()) return null;
                byte[] raw;
                try {
                    FileInputStream in = new FileInputStream(f);
                    raw = readAll(in);
                    in.close();
                } catch (Exception e) {
                    return null;
                }
                if (!isEncrypted(raw)) {
                    // data from an older version: hand it over and store it encrypted right away
                    String s = new String(raw, UTF8);
                    writeData(s);
                    return s;
                }
                try {
                    return new String(decrypt(raw), UTF8);
                } catch (Exception e) {
                    // key no longer available (e.g. app data moved to another device): keep the file aside, start empty
                    f.renameTo(new File(getFilesDir(), "anwesenheit.unreadable-" + System.currentTimeMillis()));
                    return null;
                }
            }
        }

        @JavascriptInterface
        public void saveData(String json) {
            synchronized (MainActivity.this) {
                writeData(json);
            }
        }

        @JavascriptInterface
        public void haptic(final String kind) {
            runOnUiThread(new Runnable() {
                public void run() {
                    doHaptic(kind);
                }
            });
        }

        @JavascriptInterface
        public void saveFile(String name, String content, String mime) {
            pendingSaveContent = content;
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType(mime);
            i.putExtra(Intent.EXTRA_TITLE, name);
            try {
                startActivityForResult(i, REQ_SAVE);
            } catch (Exception e) {
                notifyJs("Kein Dateimanager gefunden", false);
            }
        }

        @JavascriptInterface
        public void openFile() {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            try {
                startActivityForResult(i, REQ_OPEN);
            } catch (Exception e) {
                notifyJs("Kein Dateimanager gefunden", false);
            }
        }

        @JavascriptInterface
        public void shareFile(String name, String content) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_SUBJECT, name.replace(".txt", "").replace('_', ' '));
            i.putExtra(Intent.EXTRA_TEXT, content);
            try {
                startActivity(Intent.createChooser(i, "Anwesenheitsliste teilen"));
            } catch (Exception e) {
                notifyJs("Teilen nicht möglich", false);
            }
        }
    }

    private void doHaptic(String kind) {
        if (vibrator == null || !vibrator.hasVibrator()) {
            web.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            return;
        }
        // Crisp predefined effects on Android 10+
        if (Build.VERSION.SDK_INT >= 29) {
            int fx = -1;
            if ("tick".equals(kind)) fx = VibrationEffect.EFFECT_TICK;
            else if ("light".equals(kind)) fx = VibrationEffect.EFFECT_CLICK;
            else if ("medium".equals(kind)) fx = VibrationEffect.EFFECT_HEAVY_CLICK;
            else if ("heavy".equals(kind)) fx = VibrationEffect.EFFECT_HEAVY_CLICK;
            else if ("undo".equals(kind)) fx = VibrationEffect.EFFECT_DOUBLE_CLICK;
            if (fx != -1) {
                try { vibrator.vibrate(VibrationEffect.createPredefined(fx)); return; } catch (Exception ignored) { }
            }
        }
        long[] pattern;
        int[] amps;
        if ("tick".equals(kind)) { pattern = new long[]{0, 10}; amps = new int[]{0, 90}; }
        else if ("light".equals(kind)) { pattern = new long[]{0, 18}; amps = new int[]{0, 120}; }
        else if ("medium".equals(kind)) { pattern = new long[]{0, 35}; amps = new int[]{0, 180}; }
        else if ("heavy".equals(kind)) { pattern = new long[]{0, 60}; amps = new int[]{0, 255}; }
        else if ("success".equals(kind)) { pattern = new long[]{0, 18, 60, 28}; amps = new int[]{0, 140, 0, 220}; }
        else if ("undo".equals(kind)) { pattern = new long[]{0, 14, 70, 14}; amps = new int[]{0, 120, 0, 120}; }
        else if ("done".equals(kind)) { pattern = new long[]{0, 30, 70, 30, 70, 90}; amps = new int[]{0, 170, 0, 200, 0, 255}; }
        else if ("warn".equals(kind)) { pattern = new long[]{0, 40, 60, 40}; amps = new int[]{0, 200, 0, 200}; }
        else { pattern = new long[]{0, 15}; amps = new int[]{0, 120}; }
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                if (vibrator.hasAmplitudeControl())
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, amps, -1));
                else
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                vibrator.vibrate(pattern, -1);
            }
        } catch (Exception ignored) {
        }
    }
}
