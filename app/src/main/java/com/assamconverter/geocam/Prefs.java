package com.assamconverter.geocam;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import org.json.JSONObject;

/** All settings and the login, stored on the phone. */
final class Prefs {
    private final SharedPreferences p;

    Prefs(Context c) { p = c.getApplicationContext().getSharedPreferences("geocam", Context.MODE_PRIVATE); }

    String s(String k, String d) { return p.getString(k, d); }
    boolean b(String k, boolean d) { return p.getBoolean(k, d); }
    int i(String k, int d) { return p.getInt(k, d); }
    void put(String k, String v) { p.edit().putString(k, v).apply(); }
    void put(String k, boolean v) { p.edit().putBoolean(k, v).apply(); }
    void put(String k, int v) { p.edit().putInt(k, v).apply(); }

    /* stamp */
    String coord() { return s("coord", "both"); }           // both | dec | dms
    boolean top() { return "top".equals(s("position", "bottom")); }
    float size() { try { return Float.parseFloat(s("size", "1")); } catch (Exception e) { return 1f; } }
    boolean map() { return b("map", true); }
    boolean addr() { return b("addr", true); }
    boolean extra() { return b("extra", true); }             // altitude + direction

    /* entries */
    String prefix() { return s("prefix", ""); }
    int next() { return i("next", 1); }
    String entryNo() { return prefix() + next(); }
    String note() { return s("note", ""); }

    /* camera */
    boolean grid() { return b("grid", false); }
    int timer() { return i("timer", 0); }
    boolean sound() { return b("sound", true); }
    boolean hiQuality() { return !"std".equals(s("pq", "high")); }
    boolean video1080() { return "1080".equals(s("vq", "720")); }
    boolean back() { return b("backCam", true); }

    /* account */
    String token() { return s("token", ""); }
    String userName() { return s("userName", ""); }
    String userEmail() { return s("userEmail", ""); }
    boolean loggedIn() { return !token().isEmpty(); }
    void setUser(String token, JSONObject u) {
        p.edit().putString("token", token).putString("userName", u == null ? "" : u.optString("name"))
                .putString("userEmail", u == null ? "" : u.optString("email")).apply();
    }
    void logout() { p.edit().remove("token").remove("userName").remove("userEmail").apply(); }

    /** Random id of this phone: photos taken without login belong to it (moved to the account on login). */
    String device() {
        String d = s("dev", "");
        if (!d.matches("[a-f0-9]{32}")) {
            byte[] b = new byte[16];
            Util.RND.nextBytes(b);
            d = Util.hex(b);
            put("dev", d);
        }
        return d;
    }

    /* theme: auto | light | dark */
    String theme() { return s("theme", "auto"); }
    static void applyTheme(Context c) {
        String t = new Prefs(c).theme();
        AppCompatDelegate.setDefaultNightMode("light".equals(t) ? AppCompatDelegate.MODE_NIGHT_NO
                : "dark".equals(t) ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    /* server settings remembered from the last check */
    boolean requireLogin() { return b("requireLogin", false); }
    String latest() { return s("latest", ""); }
    String minVersion() { return s("minVersion", ""); }
    String apkUrl() { return s("apkUrl", BuildConfig.BASE_URL + "/geocam/install.php"); }
    String updateNote() { return s("updateNote", ""); }
    int maxVideo() { return i("maxVideo", 180); }
}
