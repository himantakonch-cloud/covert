package com.assamconverter.geocam;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Draws the GeoTag stamp (same design as the website) onto a photo, a video frame or the live preview. */
final class Stamp {
    static final class Info {
        double lat, lng;
        float acc;
        String line1 = "", line2 = "", plus = "";
        long time;           // ms, 0 = don't show
        int offset;          // minutes east of GMT
        String note = "", entryNo = "", code = "", extra = "";
        Bitmap map;
    }
    static final class Opts {
        String coord = "both";
        boolean top = false, showMap = true;
        float size = 1f;
    }

    private static final Typeface BOLD = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    private static final Typeface NORMAL = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    private static final class Line {
        final String t; final float size; final Typeface f; final int color; final float h;
        Line(String t, float size, Typeface f, int color, float h) { this.t = t; this.size = size; this.f = f; this.color = color; this.h = h; }
    }

    static void draw(Canvas c, float W, float H, Info in, Opts o) {
        float u = Math.min(W, H) / 1000f * o.size;
        float margin = 28 * u, mapS = 250 * u, gap = 22 * u, pad = 24 * u;
        boolean showMap = o.showMap && in.map != null;
        float textX = margin + (showMap ? mapS + gap : 0) + pad, maxW = W - margin - pad - textX;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        float big = 44 * u, mid = 30 * u, small = 26 * u;
        int white = Color.WHITE, soft = Color.argb(235, 255, 255, 255);

        List<Line> L = new ArrayList<>();
        if (!in.line1.isEmpty()) L.add(new Line(in.line1, big, BOLD, white, big * 1.25f));
        p.setTypeface(NORMAL); p.setTextSize(mid);
        String addr = (in.plus.isEmpty() ? "" : in.plus) + (in.line2.isEmpty() ? "" : (in.plus.isEmpty() ? "" : ", ") + in.line2);
        for (String s : wrap(p, addr, maxW, 2)) L.add(new Line(s, mid, NORMAL, soft, mid * 1.32f));
        List<String> co = new ArrayList<>();
        String dec = Util.dec(in.lat, in.lng), dms = Util.dms(in.lat, "N", "S") + "  " + Util.dms(in.lng, "E", "W");
        if (!"dms".equals(o.coord)) co.add(dec);
        if (!"dec".equals(o.coord)) co.add(dms);
        for (int i = 0; i < co.size(); i++) L.add(new Line(co.get(i) + (i == 0 && in.acc > 0 ? String.format(Locale.US, "   ±%d m", Math.round(in.acc)) : ""), mid, BOLD, white, mid * 1.32f));
        if (!in.extra.isEmpty()) L.add(new Line(in.extra, mid, NORMAL, soft, mid * 1.32f));
        if (in.time > 0) L.add(new Line(Util.when(in.time, in.offset), mid, NORMAL, soft, mid * 1.32f));
        if (!in.note.isEmpty()) { p.setTypeface(NORMAL); p.setTextSize(mid); for (String s : wrap(p, "Note: " + in.note, maxW, 2)) L.add(new Line(s, mid, NORMAL, Color.rgb(255, 224, 138), mid * 1.32f)); }
        String last = (in.entryNo.isEmpty() ? "" : "No. " + in.entryNo + "  ·  ") + (in.code.isEmpty() ? "" : "Entry " + in.code + "  ·  verify: assamconverter.com/geocam/v/" + in.code);
        if (!last.isEmpty()) L.add(new Line(last, small, BOLD, Color.rgb(159, 227, 195), small * 1.35f));

        float textH = 0; for (Line l : L) textH += l.h;
        float boxH = Math.max(textH + pad * 1.6f, showMap ? mapS : 0);
        float boxX = margin + (showMap ? mapS + gap : 0), boxW = W - margin - boxX;
        float by = o.top ? margin + 46 * u : H - margin - boxH;

        // brand chip
        p.setTypeface(BOLD); p.setTextSize(small);
        String bt = "GeoTag · AssamConverter";
        float chipH = 40 * u, chipW = chipH + 14 * u + p.measureText(bt) + 22 * u;
        float chipX = W - margin - chipW, chipY = o.top ? by + boxH + 8 * u : by - chipH - 8 * u;
        p.setColor(Color.argb(158, 15, 15, 15));
        c.drawRoundRect(new RectF(chipX, chipY, chipX + chipW, chipY + chipH), chipH / 2, chipH / 2, p);
        brand(c, chipX + 6 * u, chipY + 5 * u, chipH - 10 * u);
        p.setColor(white);
        c.drawText(bt, chipX + chipH + 8 * u, chipY + chipH * .68f, p);

        // text box
        p.setColor(Color.argb(158, 12, 12, 12));
        c.drawRoundRect(new RectF(boxX, by, boxX + boxW, by + boxH), 18 * u, 18 * u, p);
        float y = by + pad * .8f + (boxH - textH - pad * 1.6f) / 2;
        for (Line l : L) {
            y += l.h;
            p.setTypeface(l.f); p.setTextSize(l.size); p.setColor(l.color);
            String t = l.t;
            float w = p.measureText(t);
            if (w > maxW) { p.setTextScaleX(Math.max(.6f, maxW / w)); }
            c.drawText(t, textX, y - l.h * .22f, p);
            p.setTextScaleX(1f);
        }

        // map
        if (showMap) {
            float mx = margin, my = by + boxH - mapS;
            RectF r = new RectF(mx, my, mx + mapS, my + mapS);
            c.save();
            Path clip = new Path(); clip.addRoundRect(r, 18 * u, 18 * u, Path.Direction.CW);
            c.clipPath(clip);
            c.drawBitmap(in.map, null, r, new Paint(Paint.FILTER_BITMAP_FLAG));
            c.restore();
            Paint s = new Paint(Paint.ANTI_ALIAS_FLAG); s.setStyle(Paint.Style.STROKE); s.setStrokeWidth(3 * u); s.setColor(Color.argb(217, 255, 255, 255));
            c.drawRoundRect(r, 18 * u, 18 * u, s);
            pin(c, mx + mapS / 2, my + mapS / 2, 46 * u);
            p.setTypeface(NORMAL); p.setTextSize(15 * u); p.setColor(Color.argb(166, 0, 0, 0)); p.setTextAlign(Paint.Align.RIGHT);
            c.drawText("© OpenStreetMap", mx + mapS - 8 * u, my + mapS - 8 * u, p);
            p.setTextAlign(Paint.Align.LEFT);
        }
    }

    private static List<String> wrap(Paint p, String s, float maxW, int maxLines) {
        List<String> out = new ArrayList<>();
        if (s == null || s.trim().isEmpty()) return out;
        String[] words = s.trim().split("\\s+");
        StringBuilder line = new StringBuilder();
        int used = 0;
        for (String w : words) {
            String t = line.length() == 0 ? w : line + " " + w;
            if (p.measureText(t) <= maxW || line.length() == 0) { line = new StringBuilder(t); used++; }
            else {
                out.add(line.toString());
                line = new StringBuilder(w); used++;
                if (out.size() == maxLines) break;
            }
        }
        if (out.size() < maxLines && line.length() > 0) out.add(line.toString());
        if (used < words.length && !out.isEmpty()) {
            String l = out.get(out.size() - 1);
            while (l.length() > 1 && p.measureText(l + "…") > maxW) l = l.substring(0, l.length() - 1);
            out.set(out.size() - 1, l + "…");
        }
        return out;
    }

    private static void pin(Canvas c, float x, float y, float s) {
        Path pth = new Path();
        pth.moveTo(x, y);
        pth.cubicTo(x - s * .2f, y - s * .45f, x - s * .5f, y - s * .6f, x - s * .5f, y - s);
        pth.arcTo(new RectF(x - s * .5f, y - s * 1.5f, x + s * .5f, y - s * .5f), 180, 180);
        pth.cubicTo(x + s * .5f, y - s * .6f, x + s * .2f, y - s * .45f, x, y);
        pth.close();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.rgb(224, 48, 43)); c.drawPath(pth, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(s * .08f); p.setColor(Color.WHITE); c.drawPath(pth, p);
        p.setStyle(Paint.Style.FILL); c.drawCircle(x, y - s, s * .2f, p);
    }

    private static void brand(Canvas c, float x, float y, float h) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(x, y, x + h, y + h, Color.rgb(255, 106, 53), Color.rgb(184, 64, 26), Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(x, y, x + h, y + h), h * .28f, h * .28f, p);
        Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
        s.setStyle(Paint.Style.STROKE); s.setStrokeWidth(h * .11f); s.setStrokeCap(Paint.Cap.ROUND); s.setStrokeJoin(Paint.Join.ROUND); s.setColor(Color.WHITE);
        Path a = new Path(); a.moveTo(x + h * .33f, y + h * .3f); a.lineTo(x + h * .5f, y + h / 2); a.lineTo(x + h * .33f, y + h * .7f);
        c.drawPath(a, s);
        s.setAlpha(153);
        Path b = new Path(); b.moveTo(x + h * .52f, y + h * .3f); b.lineTo(x + h * .69f, y + h / 2); b.lineTo(x + h * .52f, y + h * .7f);
        c.drawPath(b, s);
    }

    /** Rule-of-thirds grid for the preview. */
    static void grid(Canvas c, float W, float H) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.argb(115, 255, 255, 255)); p.setStrokeWidth(Math.max(1, W / 500));
        for (int i = 1; i < 3; i++) { c.drawLine(W * i / 3, 0, W * i / 3, H, p); c.drawLine(0, H * i / 3, W, H * i / 3, p); }
    }
}
