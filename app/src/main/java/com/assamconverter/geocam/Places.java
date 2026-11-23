package com.assamconverter.geocam;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.LruCache;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Place name, address and map picture for a GPS point.
 *  - village / area name: our website (admin place names → OpenStreetMap nearest village)
 *  - full address: OpenStreetMap Nominatim
 *  - map: OpenStreetMap tiles
 * Everything is remembered on the phone so each spot is looked up once.
 */
final class Places {
    static final class Addr {
        String line1 = "", line2 = "", town = "", state = "";
    }

    private final SharedPreferences cache;
    private final Context ctx;

    Places(Context c) {
        ctx = c.getApplicationContext();
        cache = ctx.getSharedPreferences("places", Context.MODE_PRIVATE);
    }

    /** Runs on a background thread. */
    Addr lookup(double lat, double lng) {
        String k = String.format(Locale.US, "a2_%.4f,%.4f", lat, lng);
        String c = cache.getString(k, null);
        Addr a = new Addr();
        if (c != null) {
            try { JSONObject j = new JSONObject(c); a.line1 = j.optString("l1"); a.line2 = j.optString("l2"); a.town = j.optString("t"); a.state = j.optString("s"); return a; } catch (Exception ignored) { }
        }
        String town = "", state = "", country = "", road = "", hamlet = "", poi = "", postcode = "", district = "";
        List<String> parts = new ArrayList<>();
        byte[] b = Api.get(String.format(Locale.US, "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=18&addressdetails=1&accept-language=en&lat=%.6f&lon=%.6f", lat, lng), 9000);
        boolean got = false;
        if (b != null) {
            try {
                JSONObject j = new JSONObject(new String(b, "UTF-8")), ad = j.optJSONObject("address");
                if (ad == null) ad = new JSONObject();
                town = first(ad, "city", "town", "municipality", "county", "state_district", "village");
                state = ad.optString("state");
                country = ad.optString("country");
                road = ad.optString("road");
                postcode = ad.optString("postcode");
                district = ad.optString("state_district");
                hamlet = first(ad, "hamlet", "neighbourhood", "quarter", "city_block", "residential", "isolated_dwelling", "suburb", "village");
                String cat = j.optString("category");
                if (!j.optString("name").isEmpty() && !"boundary place highway landuse natural railway waterway".contains(cat.isEmpty() ? "x" : cat)) poi = j.optString("name");
                got = true;
            } catch (Exception ignored) { }
        }
        // village / area name from our website (admin names + nearest named village on OpenStreetMap)
        String village = "";
        try {
            JSONObject r = Api.callNow(ctx, "place", new JSONObject().put("lat", lat).put("lng", lng));
            JSONObject p = r.optJSONObject("place");
            if (p != null) {
                village = p.optString("name");
                if (town.isEmpty()) town = p.optString("district");
            }
        } catch (Exception ignored) { }
        String local = !poi.isEmpty() ? poi : !village.isEmpty() ? village : hamlet;
        if (local.equals(town)) local = "";
        a.town = town;
        a.state = state;
        a.line1 = local.isEmpty() ? join(town, state, country) : join(local, town, state);
        for (String s : new String[]{local, road.isEmpty() ? "" : road, hamlet, town, district, state, postcode, country}) if (!s.isEmpty() && !parts.contains(s)) parts.add(s);
        a.line2 = android.text.TextUtils.join(", ", parts);
        if (a.line1.isEmpty()) a.line1 = "Location";
        if (got) {
            try { cache.edit().putString(k, new JSONObject().put("l1", a.line1).put("l2", a.line2).put("t", a.town).put("s", a.state).toString()).apply(); } catch (Exception ignored) { }
        }
        return a;
    }

    private static String first(JSONObject o, String... keys) {
        for (String k : keys) { String v = o.optString(k); if (!v.isEmpty()) return v; }
        return "";
    }

    private static String join(String... xs) {
        List<String> out = new ArrayList<>();
        for (String x : xs) if (x != null && !x.isEmpty() && !out.contains(x)) out.add(x);
        return android.text.TextUtils.join(", ", out);
    }

    /* ── map picture: 3×3 OpenStreetMap tiles around the point, centred ── */
    private static final LruCache<String, Bitmap> TILES = new LruCache<>(40);

    static Bitmap map(double lat, double lng, int size) {
        int zoom = 16;
        double n = Math.pow(2, zoom), xf = (lng + 180) / 360 * n, lr = Math.toRadians(lat);
        double yf = (1 - Math.log(Math.tan(lr) + 1 / Math.cos(lr)) / Math.PI) / 2 * n;
        int x0 = (int) Math.floor(xf) - 1, y0 = (int) Math.floor(yf) - 1;
        Bitmap big = Bitmap.createBitmap(768, 768, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(big);
        c.drawColor(Color.rgb(229, 227, 223));
        int ok = 0;
        for (int dx = 0; dx < 3; dx++) for (int dy = 0; dy < 3; dy++) {
            String k = zoom + "/" + (x0 + dx) + "/" + (y0 + dy);
            Bitmap t = TILES.get(k);
            if (t == null) {
                byte[] b = Api.get("https://tile.openstreetmap.org/" + k + ".png", 8000);
                if (b != null) { t = BitmapFactory.decodeByteArray(b, 0, b.length); if (t != null) TILES.put(k, t); }
            }
            if (t != null) { c.drawBitmap(t, dx * 256, dy * 256, null); ok++; }
        }
        if (ok == 0) return null;
        // show a 300×300 window centred on the point
        float px = (float) ((xf - x0) * 256), py = (float) ((yf - y0) * 256);
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas o = new Canvas(out);
        Matrix m = new Matrix();
        m.postTranslate(150 - px, 150 - py);
        m.postScale(size / 300f, size / 300f);
        o.drawBitmap(big, m, new Paint(Paint.FILTER_BITMAP_FLAG));
        big.recycle();
        return out;
    }
}
