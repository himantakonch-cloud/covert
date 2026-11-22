package com.assamconverter.geocam;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * window.GeoTagApp — what the web page can ask the app to do.
 * Every method only works while a page from our own website is open.
 */
public class AppBridge {

    private final MainActivity a;
    private final Map<String, File> files = new HashMap<>();
    private final Map<String, String> names = new HashMap<>();
    private final Map<String, String> mimes = new HashMap<>();

    AppBridge(MainActivity a) { this.a = a; }

    private boolean ours() { return a.host.equals(a.currentHost); }

    private File sharedDir() {
        File d = new File(a.getCacheDir(), "shared");
        if (!d.exists()) //noinspection ResultOfMethodCallIgnored
            d.mkdirs();
        return d;
    }

    /** Delete shared temp files older than a day. */
    void cleanOldFiles() {
        File[] list = sharedDir().listFiles();
        if (list == null) return;
        long old = System.currentTimeMillis() - 24L * 3600 * 1000;
        for (File f : list) if (f.lastModified() < old) //noinspection ResultOfMethodCallIgnored
            f.delete();
    }

    /** App details + the API key (the website sends it as X-Api-Key). */
    @JavascriptInterface
    public String info() {
        JSONObject o = new JSONObject();
        try {
            o.put("platform", "android");
            o.put("version", BuildConfig.VERSION_NAME);
            o.put("versionCode", BuildConfig.VERSION_CODE);
            o.put("package", BuildConfig.APPLICATION_ID);
            o.put("sdk", Build.VERSION.SDK_INT);
            o.put("device", Build.MANUFACTURER + " " + Build.MODEL);
            o.put("apiKey", ours() ? BuildConfig.API_KEY : "");
            o.put("nativeSave", true);
        } catch (Exception ignored) { }
        return o.toString();
    }

    /* ── Saving a photo / video / CSV: the page sends the file in base64 pieces ── */
    @JavascriptInterface
    public String saveBegin(String name, String mime) {
        if (!ours()) return "";
        String id = UUID.randomUUID().toString();
        String safe = name == null ? "file" : name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.length() > 80) safe = safe.substring(safe.length() - 80);
        File f = new File(sharedDir(), id.substring(0, 8) + "_" + safe);
        synchronized (files) { files.put(id, f); names.put(id, safe); mimes.put(id, mime == null ? "application/octet-stream" : mime); }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
        return id;
    }

    @JavascriptInterface
    public boolean saveChunk(String id, String b64) {
        File f;
        synchronized (files) { f = files.get(id); }
        if (f == null) return false;
        try (FileOutputStream out = new FileOutputStream(f, true)) {
            out.write(Base64.decode(b64, Base64.DEFAULT));
            return true;
        } catch (Exception e) { return false; }
    }

    /**
     * Finish: put the file in the Gallery (photos → Pictures/GeoTag Camera, videos → Movies/GeoTag Camera,
     * CSV → Download). gallery=false keeps it only for sharing. Result comes back in window.__gtSaved(id, json).
     */
    @JavascriptInterface
    public void saveEnd(final String id, final boolean gallery) {
        final File f;
        final String name, mime;
        synchronized (files) { f = files.get(id); name = names.get(id); mime = mimes.get(id); }
        if (f == null || !f.exists()) { reply(id, false, "File not found"); return; }
        if (!gallery) { reply(id, true, ""); return; }
        a.runOnUiThread(new Runnable() { public void run() {
            a.withStorage(new Runnable() { public void run() {
                new Thread(new Runnable() { public void run() {
                    try { String where = toGallery(f, name, mime); reply(id, true, where); }
                    catch (Exception e) { reply(id, false, "Could not save: " + e.getMessage()); }
                }}).start();
            }});
        }});
    }

    private String toGallery(File f, String name, String mime) throws Exception {
        boolean video = mime.startsWith("video/"), image = mime.startsWith("image/");
        String folder = video ? Environment.DIRECTORY_MOVIES : image ? Environment.DIRECTORY_PICTURES : Environment.DIRECTORY_DOWNLOADS;
        String sub = (image || video) ? "GeoTag Camera" : "";
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver cr = a.getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, folder + (sub.isEmpty() ? "" : "/" + sub));
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri coll = video ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                     : image ? MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                     : MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri u = cr.insert(coll, v);
            if (u == null) throw new Exception("no space");
            try (OutputStream out = cr.openOutputStream(u); InputStream in = new FileInputStream(f)) {
                copy(in, out);
            }
            v.clear();
            v.put(MediaStore.MediaColumns.IS_PENDING, 0);
            cr.update(u, v, null, null);
        } else {
            //noinspection deprecation
            File dir = new File(Environment.getExternalStoragePublicDirectory(folder), sub);
            if (!dir.exists() && !dir.mkdirs()) throw new Exception("cannot create folder");
            File dst = new File(dir, name);
            try (InputStream in = new FileInputStream(f); OutputStream out = new FileOutputStream(dst)) { copy(in, out); }
            MediaScannerConnection.scanFile(a, new String[]{dst.getAbsolutePath()}, new String[]{mime}, null);
        }
        return (image ? "Gallery › Pictures › GeoTag Camera" : video ? "Gallery › Movies › GeoTag Camera" : "Download folder");
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] b = new byte[64 * 1024];
        int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
    }

    private void reply(String id, boolean ok, String msg) {
        JSONObject o = new JSONObject();
        try { o.put("ok", ok); o.put("message", msg); } catch (Exception ignored) { }
        a.js("window.__gtSaved&&window.__gtSaved(" + JSONObject.quote(id) + "," + o.toString() + ")");
    }

    /** Share a file the page sent with saveBegin/saveChunk/saveEnd (WhatsApp, e-mail, Drive…). */
    @JavascriptInterface
    public boolean share(String id, final String text) {
        final File f;
        final String mime;
        synchronized (files) { f = files.get(id); mime = mimes.get(id); }
        if (!ours() || f == null || !f.exists()) return false;
        a.runOnUiThread(new Runnable() { public void run() {
            try {
                Uri u = FileProvider.getUriForFile(a, BuildConfig.APPLICATION_ID + ".files", f);
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType(mime);
                i.putExtra(Intent.EXTRA_STREAM, u);
                if (text != null && !text.isEmpty()) i.putExtra(Intent.EXTRA_TEXT, text);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                a.startActivity(Intent.createChooser(i, "Share"));
            } catch (Exception e) { Toast.makeText(a, "Could not share this file", Toast.LENGTH_SHORT).show(); }
        }});
        return true;
    }

    /** Share plain text (e.g. a verify link). */
    @JavascriptInterface
    public void shareText(final String text) {
        if (!ours()) return;
        a.runOnUiThread(new Runnable() { public void run() {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, text);
            a.startActivity(Intent.createChooser(i, "Share"));
        }});
    }

    @JavascriptInterface
    public void openUrl(String url) {
        if (!ours() || url == null) return;
        final Uri u = Uri.parse(url);
        a.runOnUiThread(new Runnable() { public void run() { a.openOutside(u); }});
    }

    @JavascriptInterface
    public void keepAwake(boolean on) { if (ours()) a.keepAwake(on); }

    @JavascriptInterface
    public void toast(final String msg) {
        a.runOnUiThread(new Runnable() { public void run() { Toast.makeText(a, msg, Toast.LENGTH_SHORT).show(); }});
    }

    /** Used by the offline page's "Try again" button. */
    @JavascriptInterface
    public void reload() { a.reload(); }
}
