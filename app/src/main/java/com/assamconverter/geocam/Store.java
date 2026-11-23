package com.assamconverter.geocam;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The entry log on this phone. Each photo/video is saved in the Gallery (Pictures/Movies › GeoTag Camera)
 * and listed here with its location, so it can be found, shared, navigated to and synced to the website.
 */
final class Store {
    static final class Entry {
        String code, kind = "photo", mime = "image/jpeg", uri = "", file = "", entryNo = "", place = "", address = "", note = "", sha256 = "";
        double lat, lng, alt = Double.NaN;
        float acc, heading = -1;
        long taken;
        int offset, w, h;
        double dur;
        boolean synced;

        JSONObject json() throws Exception {
            return new JSONObject().put("code", code).put("kind", kind).put("mime", mime).put("uri", uri).put("file", file).put("entryNo", entryNo)
                    .put("place", place).put("address", address).put("note", note).put("sha256", sha256).put("lat", lat).put("lng", lng)
                    .put("alt", Double.isNaN(alt) ? JSONObject.NULL : alt).put("acc", acc).put("heading", heading).put("taken", taken)
                    .put("offset", offset).put("w", w).put("h", h).put("dur", dur).put("synced", synced);
        }
        static Entry from(JSONObject o) {
            Entry e = new Entry();
            e.code = o.optString("code"); e.kind = o.optString("kind", "photo"); e.mime = o.optString("mime", "image/jpeg"); e.uri = o.optString("uri");
            e.file = o.optString("file"); e.entryNo = o.optString("entryNo"); e.place = o.optString("place"); e.address = o.optString("address");
            e.note = o.optString("note"); e.sha256 = o.optString("sha256"); e.lat = o.optDouble("lat"); e.lng = o.optDouble("lng");
            e.alt = o.isNull("alt") ? Double.NaN : o.optDouble("alt"); e.acc = (float) o.optDouble("acc", 0); e.heading = (float) o.optDouble("heading", -1);
            e.taken = o.optLong("taken"); e.offset = o.optInt("offset"); e.w = o.optInt("w"); e.h = o.optInt("h"); e.dur = o.optDouble("dur", 0);
            e.synced = o.optBoolean("synced");
            return e;
        }
        boolean video() { return "video".equals(kind); }
        String fileName() { return "GeoTag_" + entryNo.replaceAll("[^\\w-]+", "") + "_" + code + (video() ? (mime.contains("mp4") ? ".mp4" : ".webm") : ".jpg"); }
    }

    private static final Object LOCK = new Object();
    private final Context c;
    private final File db;

    Store(Context ctx) { c = ctx.getApplicationContext(); db = new File(c.getFilesDir(), "entries.json"); }

    File mediaDir() { File d = new File(c.getFilesDir(), "media"); if (!d.exists()) //noinspection ResultOfMethodCallIgnored
        d.mkdirs(); return d; }
    File thumb(String code) { File d = new File(c.getFilesDir(), "thumbs"); if (!d.exists()) //noinspection ResultOfMethodCallIgnored
        d.mkdirs(); return new File(d, code + ".jpg"); }

    List<Entry> all() {
        synchronized (LOCK) {
            List<Entry> out = new ArrayList<>();
            try {
                if (!db.exists()) return out;
                JSONArray a = new JSONArray(Api.read(new FileInputStream(db)));
                for (int i = 0; i < a.length(); i++) out.add(Entry.from(a.getJSONObject(i)));
            } catch (Exception ignored) { }
            java.util.Collections.sort(out, (x, y) -> Long.compare(y.taken, x.taken));
            return out;
        }
    }

    void save(List<Entry> list) {
        synchronized (LOCK) {
            try {
                JSONArray a = new JSONArray();
                for (Entry e : list) a.put(e.json());
                File tmp = new File(db.getPath() + ".tmp");
                try (FileOutputStream o = new FileOutputStream(tmp)) { o.write(a.toString().getBytes("UTF-8")); }
                //noinspection ResultOfMethodCallIgnored
                tmp.renameTo(db);
            } catch (Exception ignored) { }
        }
    }

    void put(Entry e) {
        synchronized (LOCK) {
            List<Entry> l = all();
            for (int i = 0; i < l.size(); i++) if (l.get(i).code.equals(e.code)) { l.set(i, e); save(l); return; }
            l.add(0, e);
            save(l);
        }
    }

    void delete(Entry e) {
        synchronized (LOCK) {
            List<Entry> l = all();
            for (int i = 0; i < l.size(); i++) if (l.get(i).code.equals(e.code)) { l.remove(i); break; }
            save(l);
        }
        //noinspection ResultOfMethodCallIgnored
        new File(e.file).delete();
        //noinspection ResultOfMethodCallIgnored
        thumb(e.code).delete();
        try { if (!e.uri.isEmpty()) c.getContentResolver().delete(Uri.parse(e.uri), null, null); } catch (Exception ignored) { }
    }

    /** Small picture for lists and the camera button. */
    void makeThumb(String code, Bitmap src) {
        try {
            float s = 320f / Math.max(src.getWidth(), src.getHeight());
            Bitmap t = Bitmap.createScaledBitmap(src, Math.max(1, Math.round(src.getWidth() * s)), Math.max(1, Math.round(src.getHeight() * s)), true);
            try (FileOutputStream o = new FileOutputStream(thumb(code))) { t.compress(Bitmap.CompressFormat.JPEG, 80, o); }
            if (t != src) t.recycle();
        } catch (Exception ignored) { }
    }

    Bitmap loadThumb(String code) {
        File f = thumb(code);
        return f.exists() ? BitmapFactory.decodeFile(f.getPath()) : null;
    }

    /** Copy a finished file into the phone's Gallery; returns its content:// address (or "" if it failed). */
    String toGallery(File f, String name, String mime) {
        boolean video = mime.startsWith("video/");
        String folder = video ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES;
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, folder + "/GeoTag Camera");
                v.put(MediaStore.MediaColumns.IS_PENDING, 1);
                Uri coll = video ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) : MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                Uri u = c.getContentResolver().insert(coll, v);
                if (u == null) return "";
                try (OutputStream out = c.getContentResolver().openOutputStream(u); InputStream in = new FileInputStream(f)) {
                    if (out == null) return "";
                    byte[] b = new byte[64 * 1024]; int n;
                    while ((n = in.read(b)) > 0) out.write(b, 0, n);
                }
                v.clear();
                v.put(MediaStore.MediaColumns.IS_PENDING, 0);
                c.getContentResolver().update(u, v, null, null);
                return u.toString();
            } else {
                @SuppressWarnings("deprecation") File dir = new File(Environment.getExternalStoragePublicDirectory(folder), "GeoTag Camera");
                if (!dir.exists() && !dir.mkdirs()) return "";
                File dst = new File(dir, name);
                try (InputStream in = new FileInputStream(f); OutputStream out = new FileOutputStream(dst)) {
                    byte[] b = new byte[64 * 1024]; int n;
                    while ((n = in.read(b)) > 0) out.write(b, 0, n);
                }
                MediaScannerConnection.scanFile(c, new String[]{dst.getAbsolutePath()}, new String[]{mime}, null);
                return Uri.fromFile(dst).toString();
            }
        } catch (Exception e) { return ""; }
    }

    /** Send entries that aren't on the website yet (works without login: saved for this phone). */
    static void syncAll(Context ctx) {
        Api.NET.execute(() -> {
            Store s = new Store(ctx);
            for (Entry e : s.all()) {
                if (e.synced) continue;
                try {
                    JSONObject b = new JSONObject().put("code", e.code).put("kind", e.kind).put("lat", e.lat).put("lng", e.lng).put("acc", e.acc)
                            .put("taken", e.taken).put("offset", e.offset).put("place", e.place).put("address", e.address).put("note", e.note)
                            .put("entry_no", e.entryNo).put("sha256", e.sha256).put("w", e.w).put("h", e.h).put("dur", e.dur).put("device", Api.userAgent());
                    if (!Double.isNaN(e.alt)) b.put("alt", e.alt);
                    JSONObject r = Api.callNow(ctx, "entry_add", b);
                    if (r.optBoolean("ok")) { e.synced = true; s.put(e); }
                    else if ("network".equals(r.optString("error")) || r.optInt("status") == 403 || r.optInt("status") == 401) break;
                } catch (Exception ignored) { }
            }
        });
    }
}
