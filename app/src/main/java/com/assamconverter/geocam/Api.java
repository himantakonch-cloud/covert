package com.assamconverter.geocam;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Talks to the website: https://assamconverter.com/geocam/api.php?a=...
 * Every request carries the app's API key (X-Api-Key) — that is how the website knows it is the official app.
 */
final class Api {
    interface Done { void done(JSONObject r); }

    static final ExecutorService NET = Executors.newFixedThreadPool(3);
    private static final Handler UI = new Handler(Looper.getMainLooper());

    static String userAgent() {
        return "GeoTagCameraNative/" + BuildConfig.VERSION_NAME + " (Android " + Build.VERSION.RELEASE + "; " + Build.MANUFACTURER + " " + Build.MODEL + ")";
    }

    /** Runs in the background; the answer comes back on the main thread. Network errors → {"ok":false,"error":"network"} */
    static void call(Context c, String action, JSONObject body, Done cb) {
        final Context app = c.getApplicationContext();
        NET.execute(() -> {
            JSONObject r = callNow(app, action, body);
            if (cb != null) UI.post(() -> cb.done(r));
        });
    }

    static JSONObject callNow(Context c, String action, JSONObject body) {
        Prefs p = new Prefs(c);
        HttpURLConnection h = null;
        try {
            JSONObject b = body == null ? new JSONObject() : body;
            b.put("dev", p.device());
            h = (HttpURLConnection) new URL(BuildConfig.BASE_URL + "/geocam/api.php?a=" + action).openConnection();
            h.setConnectTimeout(12000);
            h.setReadTimeout(25000);
            h.setRequestMethod("POST");
            h.setDoOutput(true);
            h.setRequestProperty("Content-Type", "application/json");
            h.setRequestProperty("Accept", "application/json");
            h.setRequestProperty("User-Agent", userAgent());
            h.setRequestProperty("X-Api-Key", BuildConfig.API_KEY);
            h.setRequestProperty("X-App-Version", BuildConfig.VERSION_NAME);
            if (p.loggedIn()) {
                h.setRequestProperty("Authorization", "Bearer " + p.token());
                h.setRequestProperty("X-Geo-Token", p.token());
            }
            try (OutputStream o = h.getOutputStream()) { o.write(b.toString().getBytes(StandardCharsets.UTF_8)); }
            int code = h.getResponseCode();
            InputStream in = code >= 400 ? h.getErrorStream() : h.getInputStream();
            String txt = read(in);
            JSONObject r;
            try { r = new JSONObject(txt); } catch (Exception e) { r = new JSONObject().put("ok", false).put("error", "server").put("message", "Server error (" + code + ")"); }
            r.put("status", code);
            if (code == 401 && "login_required".equals(r.optString("error")) && p.loggedIn()) p.logout();
            return r;
        } catch (Exception e) {
            try { return new JSONObject().put("ok", false).put("error", "network").put("message", "No internet connection"); } catch (Exception x) { return new JSONObject(); }
        } finally {
            if (h != null) h.disconnect();
        }
    }

    static String read(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        in.close();
        return b.toString("UTF-8");
    }

    /** Plain GET (map tiles, address service). */
    static byte[] get(String url, int timeoutMs) {
        HttpURLConnection h = null;
        try {
            h = (HttpURLConnection) new URL(url).openConnection();
            h.setConnectTimeout(timeoutMs);
            h.setReadTimeout(timeoutMs);
            h.setRequestProperty("User-Agent", userAgent() + " +https://www.assamconverter.com/geocam/");
            h.setRequestProperty("Accept-Language", "en");
            if (h.getResponseCode() >= 400) return null;
            InputStream in = h.getInputStream();
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            in.close();
            return b.toByteArray();
        } catch (Exception e) {
            return null;
        } finally {
            if (h != null) h.disconnect();
        }
    }
}
