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
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.net.URL;

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
        s.setAllowContentAccess(false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                applyTheme();
            }

            // The UI never navigates anywhere: only the bundled page is allowed.
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                return true;
            }

            // Block every request that does not target the app's own assets (no remote content in the UI).
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                if (url != null && url.startsWith("file:///android_asset/")) return null;
                return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
            }
        });
        web.addJavascriptInterface(new Bridge(), "Android");
        setContentView(web);
        applyBars();
        web.loadUrl("file:///android_asset/index.html");
        // an update that has been installed leaves its APK behind: clean it up
        try {
            File apk = ApkProvider.apkFile(this);
            if (apk.exists() && getPackageManager().getPackageArchiveInfo(apk.getPath(), 0) != null
                    && getPackageManager().getPackageArchiveInfo(apk.getPath(), 0).versionName.equals(appVersion())) apk.delete();
        } catch (Exception ignored) {
        }
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
    /* ---------- Updates via GitHub Releases ----------
     * Only these fixed addresses are ever contacted, and only for the update check/download.
     * No app data is sent. Android itself refuses an update that is not signed with the same key.
     */
    private static final String REPO = "arthurtschik/Grupp-Checkan";
    private static final String API_LATEST = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final String DOWNLOAD_PREFIX = "https://github.com/" + REPO + "/releases/download/";
    private static final String WEB_PREFIX = "https://github.com/" + REPO;
    private volatile boolean downloading = false;

    private String appVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "0";
        }
    }

    private HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "Grupp-Checkan-App/" + appVersion());
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setInstanceFollowRedirects(true);
        return c;
    }

    private void checkUpdate(final boolean silent) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    HttpURLConnection c = open(API_LATEST);
                    if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
                    InputStream in = c.getInputStream();
                    JSONObject o = new JSONObject(new String(readAll(in), UTF8));
                    in.close();
                    JSONObject r = new JSONObject();
                    r.put("tag", o.optString("tag_name"));
                    r.put("name", o.optString("name"));
                    r.put("body", o.optString("body"));
                    r.put("html", o.optString("html_url"));
                    r.put("published", o.optString("published_at"));
                    JSONArray assets = o.optJSONArray("assets");
                    for (int i = 0; assets != null && i < assets.length(); i++) {
                        JSONObject a = assets.getJSONObject(i);
                        String u = a.optString("browser_download_url");
                        if (u.endsWith(".apk") && u.startsWith(DOWNLOAD_PREFIX)) {
                            r.put("apk", u);
                            r.put("size", a.optLong("size"));
                            break;
                        }
                    }
                    js("window.onUpdateInfo&&window.onUpdateInfo(" + r.toString() + "," + silent + ")");
                } catch (Exception e) {
                    js("window.onUpdateError&&window.onUpdateError(" + jsStr(String.valueOf(e.getMessage())) + "," + silent + ")");
                }
            }
        }).start();
    }

    private void downloadUpdate(final String url) {
        if (downloading) return;
        if (url == null || !url.startsWith(DOWNLOAD_PREFIX) || !url.endsWith(".apk")) {
            notifyJs("Ungültige Update-Adresse", false);
            return;
        }
        downloading = true;
        new Thread(new Runnable() {
            public void run() {
                File out = ApkProvider.apkFile(MainActivity.this);
                try {
                    HttpURLConnection c = open(url);
                    if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
                    long total = c.getContentLength();
                    InputStream in = c.getInputStream();
                    FileOutputStream fo = new FileOutputStream(out);
                    byte[] buf = new byte[16384];
                    long done = 0;
                    int n, lastPct = -1;
                    while ((n = in.read(buf)) > 0) {
                        fo.write(buf, 0, n);
                        done += n;
                        int pct = total > 0 ? (int) (done * 100 / total) : -1;
                        if (pct != lastPct) {
                            lastPct = pct;
                            js("window.onUpdateProgress&&window.onUpdateProgress(" + pct + ")");
                        }
                    }
                    fo.close();
                    in.close();
                    js("window.onUpdateDownloaded&&window.onUpdateDownloaded()");
                    runOnUiThread(new Runnable() {
                        public void run() {
                            installUpdate();
                        }
                    });
                } catch (Exception e) {
                    out.delete();
                    js("window.onUpdateError&&window.onUpdateError(" + jsStr("Download fehlgeschlagen") + ",false)");
                } finally {
                    downloading = false;
                }
            }
        }).start();
    }

    private void installUpdate() {
        if (!ApkProvider.apkFile(this).exists()) return;
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            js("window.onUpdateNeedsPermission&&window.onUpdateNeedsPermission()");
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {
            }
            return;
        }
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(ApkProvider.URI, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Exception e) {
            notifyJs("Installation konnte nicht gestartet werden", false);
        }
    }

    public class Bridge {
        @JavascriptInterface
        public String version() {
            return appVersion();
        }

        @JavascriptInterface
        public void checkUpdate(boolean silent) {
            MainActivity.this.checkUpdate(silent);
        }

        @JavascriptInterface
        public void downloadUpdate(String url) {
            MainActivity.this.downloadUpdate(url);
        }

        @JavascriptInterface
        public void installUpdate() {
            runOnUiThread(new Runnable() {
                public void run() {
                    MainActivity.this.installUpdate();
                }
            });
        }

        @JavascriptInterface
        public boolean hasDownloadedUpdate() {
            return ApkProvider.apkFile(MainActivity.this).exists();
        }

        /** Opens a page of this project's GitHub repository in the browser (nothing else is allowed). */
        @JavascriptInterface
        public void openRepoPage(String url) {
            if (url == null || !(url.equals(WEB_PREFIX) || url.startsWith(WEB_PREFIX + "/"))) return;
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public void exitApp() {
            runOnUiThread(new Runnable() {
                public void run() {
                    finishAffinity();
                }
            });
        }

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
