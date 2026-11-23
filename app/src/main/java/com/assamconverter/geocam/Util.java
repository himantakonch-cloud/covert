package com.assamconverter.geocam;

import android.content.Context;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.TimeZone;

/** Small helpers: entry codes, coordinates, time text, file hashes. */
final class Util {
    private Util() { }

    static final SecureRandom RND = new SecureRandom();

    /** 8-letter entry code without look-alike characters (no 0/O/1/I), e.g. K7F2Q9XM */
    static String newCode() {
        final String a = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 8; i++) b.append(a.charAt(RND.nextInt(a.length())));
        return b.toString();
    }

    static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format(Locale.US, "%02x", x));
        return s.toString();
    }

    static String sha256(InputStream in) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            in.close();
            return hex(md.digest());
        } catch (Exception e) { return ""; }
    }

    static String dms(double v, String pos, String neg) {
        double a = Math.abs(v);
        int d = (int) Math.floor(a);
        double mf = (a - d) * 60;
        int m = (int) Math.floor(mf);
        double s = (mf - m) * 60;
        return d + "°" + String.format(Locale.US, "%02d′%04.1f″", m, s) + (v >= 0 ? pos : neg);
    }

    static String dec(double lat, double lng) {
        return String.format(Locale.US, "Lat %.6f°  Long %.6f°", lat, lng);
    }

    /** Open Location Code (Plus Code), 10 digits */
    static String plusCode(double lat, double lng) {
        final String A = "23456789CFGHJMPQRVWX";
        double la = Math.min(Math.max(lat, -90), 90 - 1e-9) + 90;
        double lo = (((lng + 180) % 360) + 360) % 360;
        StringBuilder code = new StringBuilder();
        double res = 20;
        for (int i = 0; i < 5; i++) {
            int a = (int) Math.floor(la / res + 1e-9), b = (int) Math.floor(lo / res + 1e-9);
            la -= a * res; lo -= b * res;
            code.append(A.charAt(a)).append(A.charAt(b));
            if (i == 3) code.append('+');
            res /= 20;
        }
        return code.toString();
    }

    static int offsetMin() { return TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000; }

    static final String[] MON = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    static final String[] DAY = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};

    /** "Monday, 23 Nov 2026  ·  09:21 AM  (GMT+05:30)" */
    static String when(long ms, int offMin) {
        java.util.Calendar c = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
        c.setTimeInMillis(ms + offMin * 60000L);
        int h = c.get(java.util.Calendar.HOUR_OF_DAY), h12 = h % 12 == 0 ? 12 : h % 12, o = Math.abs(offMin);
        return DAY[c.get(java.util.Calendar.DAY_OF_WEEK) - 1] + ", " + String.format(Locale.US, "%02d", c.get(java.util.Calendar.DAY_OF_MONTH)) + " "
                + MON[c.get(java.util.Calendar.MONTH)] + " " + c.get(java.util.Calendar.YEAR) + "  ·  "
                + String.format(Locale.US, "%02d:%02d %s  (GMT%s%02d:%02d)", h12, c.get(java.util.Calendar.MINUTE), h < 12 ? "AM" : "PM",
                offMin >= 0 ? "+" : "-", o / 60, o % 60);
    }

    /** EXIF style "2026:11:23 09:21:05" in local time */
    static String exifTime(long ms, int offMin) {
        java.util.Calendar c = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
        c.setTimeInMillis(ms + offMin * 60000L);
        return String.format(Locale.US, "%04d:%02d:%02d %02d:%02d:%02d", c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1,
                c.get(java.util.Calendar.DAY_OF_MONTH), c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE), c.get(java.util.Calendar.SECOND));
    }

    static double distM(double a1, double o1, double a2, double o2) {
        double r = Math.PI / 180;
        double x = Math.pow(Math.sin((a2 - a1) * r / 2), 2) + Math.cos(a1 * r) * Math.cos(a2 * r) * Math.pow(Math.sin((o2 - o1) * r / 2), 2);
        return 2 * 6371000 * Math.asin(Math.min(1, Math.sqrt(x)));
    }

    static String dir(double h) {
        String[] d = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return d[(int) Math.round(h / 45) % 8];
    }

    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static int cmpVersion(String a, String b) {
        String[] x = (a == null ? "0" : a).split("\\."), y = (b == null ? "0" : b).split("\\.");
        for (int i = 0; i < 4; i++) {
            int p = i < x.length ? parse(x[i]) : 0, q = i < y.length ? parse(y[i]) : 0;
            if (p != q) return p < q ? -1 : 1;
        }
        return 0;
    }

    private static int parse(String s) { try { return Integer.parseInt(s.replaceAll("\\D", "")); } catch (Exception e) { return 0; } }
}
